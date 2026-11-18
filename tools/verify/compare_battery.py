"""Compares a battery report (tools/selftest.sh, or ./gradlew runServer -PselfTest=...) with a reference report.

    python3 tools/verify/compare_battery.py REPORT [REFERENCE]

REFERENCE defaults to tools/verify/reference/battery-20261027-full.txt (use battery-20261027-quick-overgrowth-off.txt
for a quick run with structureOvergrowth = false). Compared, target by target: the "mod blocks:" digest (the blocks the
mod decides) and the "stats:" counts of both audits, without loot_containers_anywhere (it also counts vanilla chests,
which differ from one run to the next) and vegetation_checked (the natural vegetation above the ground, vanilla trees
included, which differ too: 350,389 and 352,020 in t01 in two runs of the same generation, 18 Nov 2026) and
vegetation_floating_vanilla (floating groups of Minecraft's own, far from what the mod builds). Timings are not compared. Exits with 1 on any difference or if the report does
not end with RESULT: PASS.
"""
import os
import re
import sys

if len(sys.argv) < 2:
    sys.exit(__doc__)
report = sys.argv[1]
reference = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), 'reference',
                                                                  'battery-20261027-full.txt')


def stable(path):
    out = []
    target = None
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if line.startswith('== '):
            target = line[3:]
            out.append(('target', target))
        elif 'mod blocks:' in line:
            out.append(('mod blocks', line.split('mod blocks:', 1)[1].strip()))
        elif 'stats:' in line:
            stats = line.split('stats:', 1)[1].strip()
            for vanilla in ('loot_containers_anywhere', 'vegetation_checked', 'vegetation_floating_vanilla'):
                stats = re.sub(r'(, )?' + vanilla + r'=\d+', '', stats).replace('{, ', '{')
            out.append(('stats', stats))
    return out


a, b = stable(reference), stable(report)
passed = any(line.startswith('RESULT: PASS') for line in open(report, encoding='utf-8'))
differences = 0
for i in range(max(len(a), len(b))):
    x = a[i] if i < len(a) else None
    y = b[i] if i < len(b) else None
    if x != y:
        differences += 1
        print(f"  reference: {x}\n  report:    {y}")
targets = sum(1 for k, _ in b if k == 'target')
digests = sum(1 for k, _ in b if k == 'mod blocks')
print(f"{report}: {'RESULT: PASS' if passed else 'not PASS'}; {targets} targets, {digests} mod blocks digests; "
      + ("identical to the reference" if differences == 0 else f"{differences} lines differ from the reference")
      + f" ({os.path.basename(reference)})")
sys.exit(0 if passed and differences == 0 else 1)
