"""Finds where two battery runs differ, from the per-chunk files of the "mod blocks:" digest.

    python3 tools/verify/compare_block_detail.py A/renders/t01_blocks.txt B/renders/t01_blocks.txt

Each line of those files is "cx cz roads XXXX lots XXXX rest XXXX columns <256 hex digits>" (WorldFingerprint): a
short digest of the roads, the lots and the rest (crown leaves, block entities) of a chunk, and one hex digit per
column. Prints the chunks whose digests differ, with the world x, z of the columns whose digit differs (a column can
differ with the same digit: the digit is a 4-bit summary). Then tools/verify/chunk_diff.py shows the blocks.
"""
import sys
from collections import Counter

if len(sys.argv) != 3:
    sys.exit(__doc__)


def load(path):
    out = {}
    for line in open(path, encoding='utf-8'):
        f = line.split()
        parts = {f[2]: f[3], f[4]: f[5], f[6]: f[7]}
        columns = f[9] if len(f) > 9 else ''
        out[(int(f[0]), int(f[1]))] = (parts, columns)
    return out


a, b = load(sys.argv[1]), load(sys.argv[2])
if a.keys() != b.keys():
    sys.exit("the two files cover different chunks")
by_part = Counter()
differ = 0
for key in sorted(a):
    (pa, ca), (pb, cb) = a[key], b[key]
    parts = [p for p in pa if pa[p] != pb[p]]
    if not parts:
        continue
    differ += 1
    by_part.update(parts)
    cols = [i for i in range(min(len(ca), len(cb))) if ca[i] != cb[i]]
    where = ', '.join(f"{key[0] * 16 + (i & 15)},{key[1] * 16 + (i >> 4)}" for i in cols[:12])
    print(f"  chunk {key[0]},{key[1]} {parts} columns: {where}{' ...' if len(cols) > 12 else ''}")
print(f"{len(a)} chunks, {differ} differ" + (f" (by part: {dict(by_part)})" if differ else ""))
sys.exit(1 if differ else 0)
