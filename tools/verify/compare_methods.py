"""Compares two sets of bytecode listings (tools/verify/javap_dump.sh) method by method.

    python3 tools/verify/compare_methods.py BEFORE_DIR AFTER_DIR [--owners A,B,...] [--ignore-slots]

Every method (and field) of BEFORE is looked up in AFTER: in the same class, or moved to another class with the same
signature. Bodies are compared after removing what changes without changing what the code does: instruction offsets,
branch and switch targets, constant pool numbers (and the ldc / ldc_w choice they cause), exception table offsets,
lambda numbers and spacing. With --owners, calls and field accesses to those classes are compared without the class
name (for code moved between them, a call that used to be inside one class now names the other). --ignore-slots also
ignores local variable numbers (an added parameter shifts them).

Prints a summary and every method that is not identical in place; exits with 1 when a method changed or disappeared.
"""
import os
import re
import sys


def parse_args(argv):
    dirs, owners, slots = [], [], False
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == '--owners':
            owners = [o.replace('.', '/') for o in argv[i + 1].split(',')]
            i += 2
            continue
        if a == '--ignore-slots':
            slots = True
        else:
            dirs.append(a)
        i += 1
    if len(dirs) != 2:
        sys.exit(__doc__)
    return dirs[0], dirs[1], owners, slots


BEFORE, AFTER, OWNERS, IGNORE_SLOTS = parse_args(sys.argv[1:])
LAMBDA = re.compile(r'lambda\$(\w+)\$\d+')


def norm(line):
    line = re.sub(r'^\s*\d+: ', '', line)                                  # instruction offset
    line = re.sub(r'#\d+(:|, *\d+)?', '#', line)                            # constant pool numbers
    line = re.sub(r'\b(goto|goto_w|if\w*|jsr) +\d+', r'\1 L', line)         # branch targets
    line = re.sub(r'^\s*(default|-?\d+): \d+$', 'case', line)               # switch table targets
    line = re.sub(r'^\s*\d+\s+\d+\s+\d+\s+(Class \S+|any)$', r'catch \1', line)  # exception table offsets
    line = re.sub(r'\bldc(_w)? ', 'ldc ', line)
    line = LAMBDA.sub(r'lambda$\1$N', line)
    for o in OWNERS:
        line = re.sub(r'(Method|Field|InterfaceMethod) ' + re.escape(o) + r'\.', r'\1 ', line)
    if IGNORE_SLOTS:
        line = re.sub(r'\b([ilfda](load|store))(_\d| \d+)', r'\1 N', line)
        line = re.sub(r'\biinc +\d+,', 'iinc N,', line)
    return re.sub(r'\s+', ' ', line).strip()


def members(path):
    """{(class, member signature): normalized body} of one listing."""
    out, cls, cur = {}, None, None
    for raw in open(path, encoding='utf-8'):
        line = raw.rstrip('\n')
        if line and not line.startswith(' '):
            m = re.search(r'\b(?:class|interface|enum) ([\w.$]+)', line)
            if m:
                cls = m.group(1)
            continue
        if line.startswith('  ') and not line.startswith('    ') and line.strip() not in ('', 'Code:', '}'):
            sig = re.sub(r'^(public |private |protected |static |final |synchronized |native |abstract )*', '',
                         line.strip())
            cur = (cls, LAMBDA.sub(r'lambda$\1$N', sig))
            out[cur] = []
            continue
        if cur and line.startswith('    ') and line.strip() != 'Code:':
            out[cur].append(norm(line))
    return out


def load(d):
    out = {}
    for f in sorted(os.listdir(d)):
        out.update(members(os.path.join(d, f)))
    return out


before, after = load(BEFORE), load(AFTER)
by_sig = {}
for (cls, sig), body in after.items():
    by_sig.setdefault(sig, []).append((cls, body))
counts = {'identical in place': 0, 'moved, same body': 0, 'changed': 0, 'gone': 0}
lines = []
matched = set()
for (cls, sig), body in sorted(before.items()):
    if after.get((cls, sig)) == body:
        counts['identical in place'] += 1
        matched.add((cls, sig))
        continue
    same = [c for c, b in by_sig.get(sig, []) if b == body and c != cls]
    if same:
        counts['moved, same body'] += 1
        matched.add((same[0], sig))
        lines.append(f"MOVED    {cls}.{sig} -> {same[0]}")
    elif (cls, sig) in after or by_sig.get(sig):
        counts['changed'] += 1
        where = cls if (cls, sig) in after else by_sig[sig][0][0]
        matched.add((where, sig))
        lines.append(f"CHANGED  {cls}.{sig}" + ('' if where == cls else f" (now in {where})"))
    else:
        counts['gone'] += 1
        lines.append(f"GONE     {cls}.{sig}")
new = [f"NEW      {c}.{s}" for (c, s) in sorted(after) if (c, s) not in matched and (c, s) not in before]
print(f"members before: {len(before)}, after: {len(after)}; " + ', '.join(f"{k}: {v}" for k, v in counts.items())
      + f", new: {len(new)}")
for line in lines + new:
    print('  ' + line)
sys.exit(1 if counts['changed'] or counts['gone'] else 0)
