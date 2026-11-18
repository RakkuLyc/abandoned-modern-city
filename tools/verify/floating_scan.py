"""Finds natural vegetation floating in a saved world (Minecraft 1.21 region files, no game needed).

    python3 tools/verify/floating_scan.py WORLD          (e.g. run/selftest_world_20261027)

Every generated chunk of the world is read; the chunks are split into groups of adjacent chunks (one per battery
target) and each group is scanned apart. A block is "soft" when it is natural decoration (leaves, logs, wood, stems,
vines, plants, snow layers, huge mushroom blocks, cobwebs...), "solid" when it is anything else but air or a fluid.
Soft blocks touching a solid block (by a face, an edge or a corner: acacia trunks and large oak branches only touch
by an edge) stand, and so does everything soft joined to them; soft blocks at the edge of the loaded area count as
standing. What is left floats: the script prints how many blocks, of which kinds, and the chunks with the most, with
one example each (its height above the first non-air block under it).

Known false positives (not floating in the game): lily pads on water (water is not solid here), a moss carpet now
and then, and glow lichen in caves (a few per target; why the script flags them was not investigated). The mod's own check of the same thing is the audit's vegetation_floating (gen/Floating), which
counts fluids and planned blocks as support. Written for finding 78 (18 Nov 2026): before the fix it found the
crowns, vines and snow left in the air in the cities built on mountains; after it, only the false positives above.
Needs numpy. ~20 s for a quick battery world, ~3 min and a few GB of memory for a full one.
"""
import os
import re
import struct
import sys
import zlib
from collections import Counter

import numpy as np

def read_nbt(data):
    pos = [0]

    def u(fmt, n):
        v = struct.unpack_from('>' + fmt, data, pos[0])[0]
        pos[0] += n
        return v

    def string():
        n = u('H', 2)
        s = data[pos[0]:pos[0] + n].decode('utf-8', 'replace')
        pos[0] += n
        return s

    def payload(t):
        if t == 1:
            return u('b', 1)
        if t == 2:
            return u('h', 2)
        if t == 3:
            return u('i', 4)
        if t == 4:
            return u('q', 8)
        if t == 5:
            return u('f', 4)
        if t == 6:
            return u('d', 8)
        if t == 7:
            n = u('i', 4)
            v = data[pos[0]:pos[0] + n]
            pos[0] += n
            return v
        if t == 8:
            return string()
        if t == 9:
            et = u('b', 1)
            n = u('i', 4)
            return [payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tt = u('b', 1)
                if tt == 0:
                    return out
                k = string()
                out[k] = payload(tt)
        if t == 11:
            n = u('i', 4)
            v = struct.unpack_from('>%di' % n, data, pos[0])
            pos[0] += 4 * n
            return list(v)
        if t == 12:
            n = u('i', 4)
            v = struct.unpack_from('>%dq' % n, data, pos[0])
            pos[0] += 8 * n
            return list(v)
        raise ValueError(f"unknown NBT tag {t}")

    t = u('b', 1)
    string()
    return payload(t)


def chunk(world, cx, cz):
    with open(f"{world}/region/r.{cx >> 5}.{cz >> 5}.mca", 'rb') as f:
        head = f.read(4096)
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        offset = int.from_bytes(head[i:i + 3], 'big')
        if offset == 0:
            raise ValueError(f"chunk {cx},{cz} is not in {world}")
        f.seek(offset * 4096)
        n = int.from_bytes(f.read(4), 'big')
        compression = f.read(1)[0]
        raw = f.read(n - 1)
    if compression != 2:
        raise ValueError(f"chunk {cx},{cz}: compression {compression} is not zlib")
    return read_nbt(zlib.decompress(raw))


def state_name(p):
    props = p.get('Properties')
    if not props:
        return p['Name']
    return p['Name'] + '[' + ','.join(f"{k}={v}" for k, v in sorted(props.items())) + ']'


def blocks(c):
    """{(local x, y, local z): block state} of a chunk."""
    out = {}
    for s in c['sections']:
        y0 = s['Y'] * 16
        bs = s.get('block_states')
        if not bs:
            continue
        palette = [state_name(p) for p in bs['palette']]
        if len(palette) == 1:
            for i in range(4096):
                out[(i & 15, y0 + (i >> 8), (i >> 4) & 15)] = palette[0]
            continue
        bits = max(4, (len(palette) - 1).bit_length())
        per = 64 // bits
        mask = (1 << bits) - 1
        data = bs['data']
        for i in range(4096):
            v = (data[i // per] & 0xFFFFFFFFFFFFFFFF) >> ((i % per) * bits) & mask
            out[(i & 15, y0 + (i >> 8), (i >> 4) & 15)] = palette[v]
    return out



AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
FLUID = {'minecraft:water', 'minecraft:lava', 'minecraft:bubble_column'}
SOFT_RE = re.compile(r'(_leaves|_log|_wood|_stem|_hyphae|vine|vines|vines_plant|_sapling|_mushroom|_flower|_tulip|'
                     r'short_grass|tall_grass|^minecraft:fern|large_fern|dandelion|poppy|orchid|allium|bluet|daisy|'
                     r'cornflower|lily_of_the_valley|dead_bush|sweet_berry_bush|azalea|moss_carpet|snow$|cocoa|bee_nest|'
                     r'mangrove_roots|propagule|hanging_roots|glow_lichen|spore_blossom|cobweb|sugar_cane|bamboo|'
                     r'lily_pad|pink_petals|seagrass|kelp|pitcher|torchflower|lilac|peony|rose_bush|sunflower|'
                     r'mushroom_block)')

def classify(name):
    base = name.split('[')[0]
    if base in AIR: return 0
    if base in FLUID: return 3
    if SOFT_RE.search(base): return 1
    return 2

def chunks_in(world):
    for fn in os.listdir(world + '/region'):
        m = re.match(r'r\.(-?\d+)\.(-?\d+)\.mca', fn)
        if not m: continue
        rx, rz = int(m.group(1)), int(m.group(2))
        with open(f"{world}/region/{fn}", 'rb') as f:
            head = f.read(4096)
        for i in range(1024):
            if int.from_bytes(head[4*i:4*i+3], 'big'):
                yield rx * 32 + (i & 31), rz * 32 + (i >> 5)

YMIN, YMAX = -64, 320
H = YMAX - YMIN

def chunk_grid(c):
    """(16,H,16) class array and names array index -> name, only full status chunks."""
    g = np.zeros((16, H, 16), np.uint8)
    names = {}
    for s in c['sections']:
        y0 = s['Y'] * 16
        if y0 < YMIN or y0 >= YMAX: continue
        bs = s.get('block_states')
        if not bs: continue
        pal = [state_name(p) for p in bs['palette']]
        cls = np.array([classify(n) for n in pal], np.uint8)
        if len(pal) == 1:
            idx = np.zeros(4096, np.int64)
        else:
            bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1
            data = np.array(bs['data'], dtype=np.int64).view(np.uint64)
            i = np.arange(4096)
            idx = ((data[i // per] >> ((i % per) * bits).astype(np.uint64)) & np.uint64(mask)).astype(np.int64)
        arr = cls[idx].reshape(16, 16, 16)  # y, z, x
        g[:, y0 - YMIN:y0 - YMIN + 16, :] = arr.transpose(2, 0, 1)  # x, y, z
        names[s['Y']] = (pal, idx.reshape(16, 16, 16))
    return g, names

def main(world):
    cs = list(chunks_in(world))
    data = {}
    for cx, cz in cs:
        try:
            c = chunk(world, cx, cz)
        except Exception as e:
            continue
        if c.get('Status', '') not in ('minecraft:full', 'full'):
            continue
        data[(cx, cz)] = chunk_grid(c)
    # clusters of adjacent chunks (one per target), each scanned apart
    left = set(data)
    while left:
        seed = left.pop(); comp = {seed}; stack = [seed]
        while stack:
            cx, cz = stack.pop()
            for d in ((1,0),(-1,0),(0,1),(0,-1)):
                n = (cx+d[0], cz+d[1])
                if n in left:
                    left.remove(n); comp.add(n); stack.append(n)
        scan_cluster(world, {k: data[k] for k in comp})

def scan_cluster(world, data):
    xs = [k[0] for k in data]; zs = [k[1] for k in data]
    x0, z0 = min(xs), min(zs)
    W, D = (max(xs) - x0 + 1) * 16, (max(zs) - z0 + 1) * 16
    G = np.zeros((W, H, D), np.uint8)
    have = np.zeros((W, D), bool)
    for (cx, cz), (g, _) in data.items():
        G[(cx-x0)*16:(cx-x0)*16+16, :, (cz-z0)*16:(cz-z0)*16+16] = g
        have[(cx-x0)*16:(cx-x0)*16+16, (cz-z0)*16:(cz-z0)*16+16] = True
    soft = G == 1
    solid = G == 2
    def dil(m):
        # 26 neighbours: tree trunks and branches (acacia, fancy oak) touch only by an edge or a corner
        o = m.copy()
        a = m.copy(); a[1:] |= m[:-1]; a[:-1] |= m[1:]
        b = a.copy(); b[:, 1:] |= a[:, :-1]; b[:, :-1] |= a[:, 1:]
        o = b.copy(); o[:, :, 1:] |= b[:, :, :-1]; o[:, :, :-1] |= b[:, :, 1:]
        return o
    anch = soft & dil(solid)
    # soft at the edge of the loaded area: unknown neighbours, count as anchored
    edge = ~have
    edge2 = np.zeros_like(have); edge2[1:] |= edge[:-1]; edge2[:-1] |= edge[1:]; edge2[:, 1:] |= edge[:, :-1]; edge2[:, :-1] |= edge[:, 1:]
    edge2[0, :] = edge2[-1, :] = True; edge2[:, 0] = edge2[:, -1] = True
    anch |= soft & edge2[:, None, :]
    for it in range(400):
        n = soft & dil(anch)
        if (n == anch).all(): break
        anch = n
    floating = soft & ~anch
    pts = np.argwhere(floating)
    print(f"{world} chunks {x0},{z0}..{max(xs)},{max(zs)}: {len(data)} chunks, soft {soft.sum()}, floating {len(pts)} (iterations {it})")
    if not len(pts): return
    # group by chunk, report height above the first non-air below
    kinds = Counter(); per_chunk = Counter(); samples = {}
    for x, y, z in pts:
        cx, cz = x // 16 + x0, z // 16 + z0
        per_chunk[(cx, cz)] += 1
        g, names = data[(cx, cz)]
        wy = y + YMIN
        pal, idx = names[wy >> 4]
        nm = pal[idx[wy & 15, z & 15, x & 15]].split('[')[0]
        kinds[nm] += 1
        if (cx, cz) not in samples:
            yy = y - 1
            while yy >= 0 and G[x, yy, z] == 0: yy -= 1
            samples[(cx, cz)] = (x + x0*16, wy, z + z0*16, nm, wy - (yy + YMIN))
    print('  kinds:', kinds.most_common(12))
    print('  chunks with floating blocks:', len(per_chunk))
    for k, n in per_chunk.most_common(25):
        s = samples[k]
        print(f'   chunk {k}: {n} blocks, e.g. {s[3]} at {s[0]},{s[1]},{s[2]}, {s[4]} above the block under it')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
