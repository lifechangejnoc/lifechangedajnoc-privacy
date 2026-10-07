"""Tabella colori dei blocchi (media top/lato) dalle texture vanilla 26.2.

Uso: python3 -I colors.py <cartella assets vanilla (contiene assets/minecraft)> <blocks_26.2.json> <uscita colors.tsv>
Le texture si possono prendere dal client jar o dal repo InventivetalentDev/minecraft-assets (branch 26.2).
"""
import json
import os
import sys

from PIL import Image

root, blocks_json, out = sys.argv[1], sys.argv[2], sys.argv[3]
A = os.path.join(root, 'assets', 'minecraft')
blocks = json.load(open(blocks_json))

GRASS = (124, 189, 107)
FOLIAGE = (89, 174, 48)
TINT = {
    'grass_block': ('top', GRASS), 'short_grass': ('all', GRASS), 'tall_grass': ('all', GRASS), 'fern': ('all', GRASS),
    'large_fern': ('all', GRASS), 'sugar_cane': ('all', GRASS), 'vine': ('all', FOLIAGE), 'lily_pad': ('all', (32, 128, 48)),
    'oak_leaves': ('all', FOLIAGE), 'jungle_leaves': ('all', FOLIAGE), 'acacia_leaves': ('all', FOLIAGE),
    'dark_oak_leaves': ('all', FOLIAGE), 'mangrove_leaves': ('all', (141, 177, 39)), 'spruce_leaves': ('all', (97, 153, 97)),
    'birch_leaves': ('all', (128, 167, 85)), 'water': ('all', (63, 118, 228)), 'bush': ('all', GRASS),
    'potted_fern': ('all', GRASS), 'attached_melon_stem': ('all', FOLIAGE),
}


def load_json(p):
    try:
        with open(p) as f:
            return json.load(f)
    except Exception:
        return None


def model_textures(ref, depth=0):
    if depth > 8 or ref is None:
        return {}
    ref = ref.replace('minecraft:', '')
    m = load_json(os.path.join(A, 'models', ref + '.json'))
    if m is None:
        return {}
    tex = {}
    if 'parent' in m:
        tex.update(model_textures(m['parent'], depth + 1))
    tex.update(m.get('textures', {}))
    return tex


def resolve(tex, key, depth=0):
    v = tex.get(key)
    while depth < 8:
        if isinstance(v, dict):
            v = v.get('sprite') or v.get('texture')
        if isinstance(v, str) and v.startswith('#'):
            v = tex.get(v[1:])
            depth += 1
            continue
        break
    return v if isinstance(v, str) else None


def first_model(bs):
    if bs is None:
        return None
    if 'variants' in bs:
        v = next(iter(bs['variants'].values()))
        if isinstance(v, list):
            v = v[0]
        return v.get('model')
    if 'multipart' in bs:
        for part in bs['multipart']:
            ap = part.get('apply')
            if isinstance(ap, list):
                ap = ap[0]
            if ap and 'model' in ap:
                return ap['model']
    return None


cache = {}


def avg(texref):
    if texref is None:
        return None
    if texref in cache:
        return cache[texref]
    p = os.path.join(A, 'textures', texref.replace('minecraft:', '') + '.png')
    try:
        im = Image.open(p).convert('RGBA')
    except Exception:
        cache[texref] = None
        return None
    w = im.width
    im = im.crop((0, 0, w, min(w, im.height)))
    px = list(im.get_flattened_data()) if hasattr(im, 'get_flattened_data') else list(im.getdata())
    op = [q for q in px if q[3] > 40]
    if not op:
        cache[texref] = ((0, 0, 0), 0.0)
        return cache[texref]
    r = sum(q[0] for q in op) / len(op)
    g = sum(q[1] for q in op) / len(op)
    b = sum(q[2] for q in op) / len(op)
    res = ((int(r), int(g), int(b)), len(op) / len(px))
    cache[texref] = res
    return res


TOPK = ['top', 'end', 'up', 'all', 'texture', 'cross', 'plant', 'side', 'particle']
SIDEK = ['side', 'all', 'front', 'north', 'texture', 'cross', 'plant', 'wall', 'particle']

rows = []
for bid in sorted(blocks):
    bs = load_json(os.path.join(A, 'blockstates', bid + '.json'))
    model = first_model(bs)
    tex = model_textures(model) if model else {}
    top = side = None
    for k in TOPK:
        t = resolve(tex, k)
        if t:
            top = avg(t)
            if top:
                break
    for k in SIDEK:
        t = resolve(tex, k)
        if t:
            side = avg(t)
            if side:
                break
    if top is None and side is None:
        top = side = avg('block/' + bid)
    if top is None:
        top = side
    if side is None:
        side = top
    if top is None:
        continue
    ct, at = top
    cs, as_ = side
    if bid in TINT:
        which, tint = TINT[bid]

        def mul(c):
            return tuple(int(c[i] * tint[i] / 255) for i in range(3))
        if which in ('all', 'top'):
            ct = mul(ct)
        if which == 'all':
            cs = mul(cs)
    rows.append(f'{bid}\t{ct[0]},{ct[1]},{ct[2]}\t{cs[0]},{cs[1]},{cs[2]}\t{at:.2f}\t{as_:.2f}')

with open(out, 'w') as f:
    f.write('\n'.join(rows) + '\n')
print(len(rows), 'blocchi')
