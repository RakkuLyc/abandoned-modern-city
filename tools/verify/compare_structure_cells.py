#!/usr/bin/env python3
"""Compares two listings of structure_cells.sh (before, after) and sorts the differences:
- plain code: a cell of the template changed;
- flags: the hard flag (H) or the opening (O) changed (hard flags added/removed are counted apart);
- copies: a code of one of the 3 copies changed; 'air -> chest' (a drawn chest) is counted per structure and seed;
- other: anything else (a different set of lines, the overhanging leaves).
    python3 tools/verify/compare_structure_cells.py before.txt after.txt
"""
import collections
import sys


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    plain = flags = other = 0
    hard_added = collections.Counter()
    hard_removed = collections.Counter()
    chests = collections.Counter()
    copies_other = 0
    examples = []
    with open(sys.argv[1]) as a, open(sys.argv[2]) as b:
        for la, lb in zip(a, b):
            if la == lb:
                continue
            fa, fb = la.split(), lb.split()
            if fa[:3] != fb[:3] or fa[1] == 'over' or len(fa) < 7 or len(fb) < 7:
                other += 1
                if len(examples) < 5:
                    examples.append((la.strip(), lb.strip()))
                continue
            if fa[3] != fb[3]:
                plain += 1
                if len(examples) < 5:
                    examples.append((la.strip(), lb.strip()))
            ha, hb = 'H' in fa[7:], 'H' in fb[7:]
            if ha != hb:
                (hard_added if hb else hard_removed)[fa[0]] += 1
            if [x for x in fa[7:] if x != 'H'] != [x for x in fb[7:] if x != 'H']:
                flags += 1
            for k in range(3):
                if fa[4 + k] != fb[4 + k]:
                    if fa[4 + k] == 'AIR' and fb[4 + k].startswith('minecraft:chest['):
                        chests[(fa[0], k)] += 1
                    else:
                        copies_other += 1
                        if len(examples) < 5:
                            examples.append((la.strip(), lb.strip()))
    print(f'plain code differences: {plain}')
    print(f'opening differences: {flags}')
    print(f'hard flags added: {sum(hard_added.values())} {dict(hard_added)}')
    print(f'hard flags removed: {sum(hard_removed.values())} {dict(hard_removed)}')
    print(f'copies: air -> chest {sum(chests.values())} (per structure and seed, the 4 rotations: '
          f'{dict(sorted(chests.items()))}), other copy differences {copies_other}')
    print(f'other differences: {other}')
    for ea, eb in examples:
        print('  before:', ea[:160])
        print('  after: ', eb[:160])


if __name__ == '__main__':
    main()
