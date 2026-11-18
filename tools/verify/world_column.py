"""Prints the blocks of some columns of a saved world (Minecraft 1.21 region files, no game needed).

    python3 tools/verify/world_column.py WORLD X,Z,Y0,Y1 [X,Z,Y0,Y1 ...]

For each column, from Y1 down to Y0, the block state at each height (air included), on one line. Used with
floating_scan.py to look at what it found (finding 78, 18 Nov 2026). To see what the plan puts in the same column,
use /amcs column X Z Y0 Y1 in the game.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from floating_scan import blocks, chunk  # noqa: E402


def column(world, x, z, y0, y1):
    b = blocks(chunk(world, x >> 4, z >> 4))
    return [f"{y}:{b.get((x & 15, y, z & 15), '?').replace('minecraft:', '')}" for y in range(y1, y0 - 1, -1)]


if __name__ == '__main__':
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    for spec in sys.argv[2:]:
        x, z, y0, y1 = map(int, spec.split(','))
        print(f"column {x},{z}: " + ' | '.join(column(sys.argv[1], x, z, y0, y1)))
