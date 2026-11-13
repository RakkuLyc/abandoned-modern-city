"""Lists the blocks that differ in some chunks between two saved worlds (Minecraft 1.21 region files).

    python3 tools/verify/chunk_diff.py WORLD_A WORLD_B CX,CZ [CX,CZ ...] [--ymin Y] [--ymax Y]

For example: python3 tools/verify/chunk_diff.py run/selftest_world_ref_4dd94ae run/selftest_world_20261027 -17,24
Reads the region files directly (no game needed): prints, per chunk, how many blocks differ and the first ones with
their full block states, then a count of the differing pairs by block. Use it after compare_block_detail.py has found
the chunks. Vanilla terrain differs between two runs anyway (ores, caves, trees), so look at the heights and columns
the mod builds.
"""
import struct
import sys
import zlib
from collections import Counter


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


def main(argv):
    ymin, ymax, args = -10 ** 9, 10 ** 9, []
    i = 0
    while i < len(argv):
        if argv[i] == '--ymin':
            ymin = int(argv[i + 1])
            i += 2
        elif argv[i] == '--ymax':
            ymax = int(argv[i + 1])
            i += 2
        else:
            args.append(argv[i])
            i += 1
    if len(args) < 3:
        sys.exit(__doc__)
    world_a, world_b = args[0], args[1]
    pairs = Counter()
    for arg in args[2:]:
        cx, cz = map(int, arg.split(','))
        a, b = blocks(chunk(world_a, cx, cz)), blocks(chunk(world_b, cx, cz))
        diffs = [(k, a.get(k), b.get(k)) for k in sorted(set(a) | set(b))
                 if ymin <= k[1] <= ymax and a.get(k) != b.get(k)]
        print(f"chunk {cx},{cz}: {len(diffs)} blocks differ")
        for (lx, y, lz), va, vb in diffs[:10]:
            print(f"   {cx * 16 + lx},{y},{cz * 16 + lz}: A {va}   B {vb}")
        for _, va, vb in diffs:
            pairs[((va or '-').split('[')[0], (vb or '-').split('[')[0])] += 1
    print("differing blocks (A -> B):")
    for (va, vb), n in pairs.most_common(20):
        print(f"   {n} {va} -> {vb}")


main(sys.argv[1:])
