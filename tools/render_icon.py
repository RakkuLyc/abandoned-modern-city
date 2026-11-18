"""Isometric 3D render of a structure (.nbt) with the game's block textures: the mod's icon.

    python3 tools/render_icon.py STRUCTURE.nbt TEXTURES.jar OUT.png [size] [view] [noground]

STRUCTURE.nbt  e.g. src/main/resources/amcs/buildings/abandoned_crash_building.nbt
TEXTURES.jar   the Minecraft resources ModDevGradle prepares, e.g.
               build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar
size           side of the square PNG (default 512)
view           the side seen from above: nw (default), ne, sw or se; or front: straight at the -Z side, each block
               seen from the front with its texture, darker the deeper it lies; or oblique: the -Z side seen
               straight, its top and its east side drawn going back up and to the right (a cabinet projection)
noground       leave out the lowest layer of the template (the ground around the structure)

Seen from above and from one corner (north-west by default: the front of the structure, its -Z side, and its west side). Every block is a
cube with its texture (stairs, slabs, walls and the like use the texture of their material; small plants, torches and
other thin things are left out); faces hidden by an opaque neighbour are not drawn; grass, leaves, vines and water get
the game's tints. A picture for the mod list and the project pages, not an exact view of the game. Needs Pillow and
numpy. Written for the 1.0.0 icon (abandoned_crash_building).
"""
import gzip
import io
import os
import sys
import zipfile

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'verify'))
from floating_scan import read_nbt  # noqa: E402

A = 8            # half the width of a block on screen (pixels)
H = 10           # height of a block on screen (pixels)
SHADE = {'top': 1.0, 'left': 0.80, 'right': 0.62}
SKIP = ('air', 'cave_air', 'void_air', 'structure_void', 'barrier', 'light')
THIN = ('short_grass', 'tall_grass', 'fern', 'large_fern', 'dead_bush', 'torch', 'wall_torch', 'redstone_torch',
        'redstone_wall_torch', 'lever', 'button', 'pressure_plate', 'rail', 'flower_pot', 'sapling', 'tulip', 'poppy',
        'dandelion', 'cornflower', 'oxeye_daisy', 'azure_bluet', 'allium', 'orchid', 'lily_of_the_valley', 'bush',
        'sign', 'banner', 'tripwire', 'string', 'cobweb', 'chain', 'lantern', 'candle', 'skull', 'head', 'pointed',
        'sugar_cane', 'kelp', 'seagrass', 'roots', 'sprouts', 'mushroom', 'glow_lichen', 'ladder')
TRANSLUCENT = ('glass', 'pane', 'iron_bars', 'leaves', 'vine', 'water', 'ice', 'slab', 'stairs', 'wall', 'fence',
               'carpet', 'trapdoor', 'door', 'bars', 'gate', 'snow')
GRASS = (145, 189, 89)
FOLIAGE = (119, 171, 47)
TINTS = {'grass_block_top': GRASS, 'oak_leaves': FOLIAGE, 'jungle_leaves': FOLIAGE, 'acacia_leaves': FOLIAGE,
         'dark_oak_leaves': FOLIAGE, 'mangrove_leaves': FOLIAGE, 'vine': FOLIAGE, 'birch_leaves': (128, 167, 85),
         'spruce_leaves': (97, 153, 97), 'water_still': (63, 118, 228), 'lily_pad': (32, 128, 48)}
MATERIAL = [('_stairs', ''), ('_slab', ''), ('_wall', ''), ('_fence_gate', '_planks'), ('_fence', '_planks'),
            ('_pressure_plate', ''), ('_carpet', '_wool'), ('_pane', ''), ('_trapdoor', '_trapdoor'), ('_door', '_door_top')]


class Textures:
    def __init__(self, jar):
        self.z = zipfile.ZipFile(jar)
        self.names = set(self.z.namelist())
        self.cache = {}

    def _load(self, name):
        p = f'assets/minecraft/textures/block/{name}.png'
        if p not in self.names:
            return None
        im = Image.open(io.BytesIO(self.z.read(p))).convert('RGBA')
        im = im.crop((0, 0, 16, 16)) if im.size != (16, 16) else im
        if name in TINTS:
            a = np.asarray(im).astype(np.float32)
            t = np.array(TINTS[name], np.float32) / 255.0
            a[..., :3] = a[..., :3] * t
            im = Image.fromarray(a.clip(0, 255).astype(np.uint8), 'RGBA')
        return im

    def get(self, name):
        if name not in self.cache:
            self.cache[name] = self._load(name)
        return self.cache[name]

    def faces(self, block):
        """(top, side) textures of a block name, or None."""
        n = block
        if n == 'grass_block':
            return self.get('grass_block_top'), self.get('dirt')
        if n == 'water':
            w = self.get('water_still')
            return w, w
        cands = [n]
        for suffix, repl in MATERIAL:
            if n.endswith(suffix):
                base = n[:-len(suffix)] + repl
                cands += [base, base + 's', base.replace('brick', 'bricks'), base + '_planks', base + '_block']
        if n.endswith('_log') or n.endswith('_wood') or n.endswith('_stem'):
            side = self.get(n.replace('_wood', '_log'))
            top = self.get(n.replace('_wood', '_log') + '_top') or side
            return top, side
        for c in cands:
            top = self.get(c + '_top') or self.get(c)
            side = self.get(c + '_side') or self.get(c)
            if top is not None or side is not None:
                return top or side, side or top
        return None


def warp(tex, p0, eu, ev, size, shade):
    """The 16 x 16 texture mapped on the parallelogram p0 + u*eu + v*ev (u, v in 0..16) of a sprite of `size`."""
    m = np.array([[eu[0], ev[0]], [eu[1], ev[1]]], np.float64) / 16.0
    inv = np.linalg.inv(m)
    a, b = inv[0]
    d, e = inv[1]
    c = -(a * p0[0] + b * p0[1])
    f = -(d * p0[0] + e * p0[1])
    out = tex.transform(size, Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
    arr = np.asarray(out).astype(np.float32)
    # outside the parallelogram the transform repeats the edge: clear it
    ys, xs = np.mgrid[0:size[1], 0:size[0]]
    u = a * (xs + 0.5) + b * (ys + 0.5) + c
    v = d * (xs + 0.5) + e * (ys + 0.5) + f
    inside = (u >= 0) & (u < 16) & (v >= 0) & (v < 16)
    arr[~inside] = 0
    arr[..., :3] *= shade
    return Image.fromarray(arr.clip(0, 255).astype(np.uint8), 'RGBA')


def sprites(tex):
    """The 3 visible faces of a cube (top, left = north side, right = west side) as sprites of (2A, A + H)."""
    top, side = tex
    size = (2 * A, A + H)
    out = {}
    # screen of a block-local point (x, y, z), x to the right and down, z to the left and down, y up
    def s(x, y, z):
        return ((x - z) * A + A, (x + z) * A / 2 - y * H + H)
    if top is not None:
        p0 = s(0, 1, 0)
        out['top'] = warp(top, p0, np.subtract(s(1, 1, 0), p0), np.subtract(s(0, 1, 1), p0), size, SHADE['top'])
    if side is not None:
        p0 = s(0, 1, 1)
        out['left'] = warp(side, p0, np.subtract(s(1, 1, 1), p0), np.subtract(s(0, 0, 1), p0), size, SHADE['left'])
        p0 = s(1, 1, 1)
        out['right'] = warp(side, p0, np.subtract(s(1, 1, 0), p0), np.subtract(s(1, 0, 1), p0), size, SHADE['right'])
    return out


def main():
    nbt_path, jar, out_path = sys.argv[1:4]
    side_px = int(sys.argv[4]) if len(sys.argv) > 4 else 512
    view = sys.argv[5] if len(sys.argv) > 5 else 'nw'
    root = read_nbt(gzip.decompress(open(nbt_path, 'rb').read()))
    if view == 'front':
        return front(root, nbt_path, jar, out_path, side_px)
    if view == 'oblique':
        return oblique(root, nbt_path, jar, out_path, side_px, 'noground' in sys.argv[6:])
    sx, sy, sz = root['size']
    pal = [p['Name'].replace('minecraft:', '') for p in root['palette']]
    tx = Textures(jar)
    grid = {}
    for b in root['blocks']:
        x, y, z = b['pos']
        name = pal[b['state']]
        if name in SKIP or any(t in name for t in THIN):
            continue
        # the faces drawn are those toward +x and +z: flip the axes so the chosen sides become those
        gx = sx - 1 - x if 'w' in view else x
        gz = sz - 1 - z if 'n' in view else z
        grid[(gx, y, gz)] = name
    opaque = {k for k, n in grid.items() if not any(t in n for t in TRANSLUCENT)}
    cache = {}
    missing = set()
    w = (sx + sz) * A + 2 * A
    h = (sx + sz) * A // 2 + sy * H + 2 * H
    canvas = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    for (x, y, z) in sorted(grid, key=lambda k: (k[0] + k[2] + k[1], k[1])):
        name = grid[(x, y, z)]
        if name not in cache:
            f = tx.faces(name)
            cache[name] = sprites(f) if f else None
            if f is None:
                missing.add(name)
        sp = cache[name]
        if not sp:
            continue
        px = (x - z) * A + sz * A
        py = (x + z) * A // 2 - y * H + sy * H
        for face, nb in (('top', (x, y + 1, z)), ('left', (x, y, z + 1)), ('right', (x + 1, y, z))):
            if face in sp and nb not in opaque:
                canvas.alpha_composite(sp[face], (int(px), int(py)))
    box = canvas.getbbox()
    canvas = canvas.crop(box)
    side = max(canvas.size)
    pad = side // 16
    sq = Image.new('RGBA', (side + 2 * pad, side + 2 * pad), (0, 0, 0, 0))
    sq.alpha_composite(canvas, ((sq.width - canvas.width) // 2, (sq.height - canvas.height) // 2))
    sq.resize((side_px, side_px), Image.LANCZOS).save(out_path)
    print(f"{out_path}: {len(grid)} blocks drawn from {nbt_path}; no texture for {len(missing)}: "
          f"{', '.join(sorted(missing)[:20])}")


def front(root, nbt_path, jar, out_path, side_px):
    """Straight at the -Z side: for each column x and height y the nearest block (smallest z), its side texture
    darkened with its depth."""
    sx, sy, sz = root['size']
    pal = [p['Name'].replace('minecraft:', '') for p in root['palette']]
    tx = Textures(jar)
    near = {}
    for b in root['blocks']:
        x, y, z = b['pos']
        name = pal[b['state']]
        if name in SKIP or any(t in name for t in THIN):
            continue
        if (x, y) not in near or z < near[(x, y)][0]:
            near[(x, y)] = (z, name)
    zs = [z for z, _ in near.values()]
    z0, z1 = min(zs), max(zs)
    canvas = Image.new('RGBA', (sx * 16, sy * 16), (0, 0, 0, 0))
    for (x, y), (z, name) in near.items():
        f = tx.faces(name)
        if not f or f[1] is None:
            continue
        shade = 1.0 - 0.6 * (z - z0) / max(1, z1 - z0)
        a = np.asarray(f[1]).astype(np.float32)
        a[..., :3] *= shade
        canvas.alpha_composite(Image.fromarray(a.clip(0, 255).astype(np.uint8), 'RGBA'),
                               (x * 16, (sy - 1 - y) * 16))
    canvas = canvas.crop(canvas.getbbox())
    side = max(canvas.size)
    pad = side // 16
    sq = Image.new('RGBA', (side + 2 * pad, side + 2 * pad), (0, 0, 0, 0))
    sq.alpha_composite(canvas, ((sq.width - canvas.width) // 2, (sq.height - canvas.height) // 2))
    sq.resize((side_px, side_px), Image.LANCZOS).save(out_path)
    print(f"{out_path}: front view of {nbt_path}, {len(near)} columns")


def oblique(root, nbt_path, jar, out_path, side_px, noground=False):
    """The -Z side straight, the depth going up and to the right at half scale: front, top and east faces."""
    S, D = 16, 8
    sx, sy, sz = root['size']
    pal = [p['Name'].replace('minecraft:', '') for p in root['palette']]
    tx = Textures(jar)
    grid = {}
    for b in root['blocks']:
        x, y, z = b['pos']
        name = pal[b['state']]
        if name in SKIP or any(t in name for t in THIN) or noground and y == 0:
            continue
        grid[(x, y, z)] = name
    opaque = {k for k, n in grid.items() if not any(t in n for t in TRANSLUCENT)}

    def s(x, y, z):
        return (x * S + z * D, D + (1 - y) * S - z * D)
    size = (S + D, S + D)
    cache = {}
    canvas = Image.new('RGBA', (sx * S + sz * D + 2 * S, sy * S + sz * D + 2 * S), (0, 0, 0, 0))
    for (x, y, z) in sorted(grid, key=lambda k: (-k[2], k[1], k[0])):
        name = grid[(x, y, z)]
        if name not in cache:
            f = tx.faces(name)
            sp = {}
            if f:
                top, side = f
                fog = 1.0
                if side is not None:
                    p0 = s(0, 1, 0)
                    sp['front'] = warp(side, p0, np.subtract(s(1, 1, 0), p0), np.subtract(s(0, 0, 0), p0), size, 0.9)
                    p0 = s(1, 1, 0)
                    sp['east'] = warp(side, p0, np.subtract(s(1, 1, 1), p0), np.subtract(s(1, 0, 0), p0), size, 0.65)
                if top is not None:
                    p0 = s(0, 1, 1)
                    sp['top'] = warp(top, p0, np.subtract(s(1, 1, 1), p0), np.subtract(s(0, 1, 0), p0), size, 1.0)
            cache[name] = sp
        sp = cache[name]
        px = x * S + z * D
        py = (sy - 1 - y) * S + (sz - z) * D
        for face, nb in (('top', (x, y + 1, z)), ('east', (x + 1, y, z)), ('front', (x, y, z - 1))):
            if face in sp and nb not in opaque:
                canvas.alpha_composite(sp[face], (int(px), int(py)))
    canvas = canvas.crop(canvas.getbbox())
    side = max(canvas.size)
    pad = side // 16
    sq = Image.new('RGBA', (side + 2 * pad, side + 2 * pad), (0, 0, 0, 0))
    sq.alpha_composite(canvas, ((sq.width - canvas.width) // 2, (sq.height - canvas.height) // 2))
    sq.resize((side_px, side_px), Image.LANCZOS).save(out_path)
    print(f"{out_path}: oblique view of {nbt_path}, {len(grid)} blocks")


if __name__ == '__main__':
    main()
