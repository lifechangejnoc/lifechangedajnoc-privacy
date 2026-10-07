#!/usr/bin/env python3
"""Pepita v2: picconi 3D, armature, icone GUI, riempitivi, container, font, tooltip, blocchi, NPC, logo animato.

Chiamato da gen.py prima dello zip: gen2.run(out_dir, by_item, pick_cases) -> scrive anche assets/minecraft/items/*.json
(un solo file per oggetto vanilla con tutti i case) e verifica il pack.
"""
import copy
import io
import json
import math
import os
import shutil
import zipfile
from collections import deque

from PIL import Image, ImageDraw

from px import hexc, lerp, mul, hnoise, MATS, sprite, parse, render, add_outline
import icons
import small
import blocks

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = '/home/claude/mcref/client262.jar'
SKINPACK = '/home/claude/pepita/assets/skinpack/assets/pepita'
DRIVE = '/home/claude/pepita/assets/drive'
NS = 'pepita'

SETS = ['aureo', 'smeraldo', 'glaciale', 'inferno', 'ametista', 'abisso', 'faraone', 'sakura', 'tempesta', 'eclisse']
PIECES = ['helmet', 'chestplate', 'leggings', 'boots']
PICKAXES = ['wooden_pickaxe', 'stone_pickaxe', 'iron_pickaxe', 'golden_pickaxe', 'diamond_pickaxe', 'netherite_pickaxe']
GUI_IDS = ['gui_indietro', 'gui_chiudi', 'gui_avanti', 'gui_conferma', 'gui_annulla', 'gui_info', 'gui_lucchetto',
           'gui_soldi', 'gui_pepite', 'gui_gemme', 'gui_quantum', 'gui_battlepass', 'gui_premium', 'gui_cella',
           'gui_unisci', 'gui_traguardo', 'gui_hub', 'gui_prigione', 'gui_miniere', 'gui_pvp', 'gui_tutorial',
           'gui_selettore', 'gui_armatura', 'gui_skin', 'gui_lucky', 'gui_keyall', 'gui_classifica',
           'gui_impostazioni', 'gui_negozio', 'gui_casse', 'gui_incantesimi', 'gui_rankup', 'gui_prestigio',
           'gui_giornaliero', 'gui_gang']
EMBLEMS = ['main', 'miniere', 'incantesimi', 'negozio', 'skin', 'casse', 'armatura', 'battlepass', 'traguardi',
           'celle', 'selettore', 'conferma', 'classifiche', 'impostazioni', 'quantum', 'tutorial', 'lucky']
EMBLEM_SYM = {'main': 'pepite', 'miniere': 'piccone', 'incantesimi': 'libro', 'negozio': 'soldi', 'skin': 'pennello',
              'casse': 'cassa', 'armatura': 'corazza', 'battlepass': 'battlepass', 'traguardi': 'trofeo',
              'celle': 'sbarre', 'selettore': 'bussola', 'conferma': 'spunta', 'classifiche': 'corona',
              'impostazioni': 'ingranaggio', 'quantum': 'quantum', 'tutorial': 'tomo', 'lucky': 'lucky'}
FONT_ICONS = ['soldi', 'pepite', 'gemme', 'quantum', 'chiave', 'lucky', 'battlepass', 'spada', 'piccone', 'stella']
TOOLTIPS = ['oro', 'quantum', 'leggendario']
OVERSIZED = {'paper', 'black_stained_glass_pane', 'gray_stained_glass_pane', 'leather_helmet', 'leather_chestplate',
             'leather_leggings', 'leather_boots', *PICKAXES}

OUT = None
_jar = None


def jar():
    global _jar
    if _jar is None:
        _jar = zipfile.ZipFile(JAR)
    return _jar


def vimg(path):
    return Image.open(io.BytesIO(jar().read(path))).convert('RGBA')


def vjson(path):
    return json.loads(jar().read(path))


def save(img, rel):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p, optimize=True)


def wjson(obj, rel, mini=False):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        if mini:
            json.dump(obj, f, separators=(',', ':'), ensure_ascii=False)
        else:
            json.dump(obj, f, indent=2, ensure_ascii=False)


def anim_meta(rel, frametime, interpolate=False):
    wjson({"animation": {"frametime": frametime, "interpolate": interpolate}}, rel + '.mcmeta')


def case(when, model):
    return {"when": when, "model": {"type": "minecraft:model", "model": model}}


def generated_model(name, tex=None, extra=None, parent="minecraft:item/generated"):
    m = {"parent": parent, "textures": {"layer0": f"{NS}:item/{tex or name}"}}
    if extra:
        m.update(extra)
    wjson(m, f'assets/{NS}/models/item/{name}.json')
    return f"{NS}:item/{name}"


# =============================================================== 1-2. asset dell'utente
def copy_user_assets():
    # modelli 3D (minificati)
    for f in sorted(os.listdir(os.path.join(SKINPACK, 'models/item'))):
        d = json.load(open(os.path.join(SKINPACK, 'models/item', f), encoding='utf-8'))
        d.pop('credit', None)
        for el in d.get('elements', []):
            el.pop('name', None)
        wjson(d, f'assets/{NS}/models/item/{f}', mini=True)
    for f in sorted(os.listdir(os.path.join(SKINPACK, 'equipment'))):
        d = json.load(open(os.path.join(SKINPACK, 'equipment', f), encoding='utf-8'))
        wjson(d, f'assets/{NS}/equipment/{f}', mini=True)
    for sub in ('textures/item', 'textures/entity/equipment/humanoid', 'textures/entity/equipment/humanoid_leggings'):
        src = os.path.join(SKINPACK, sub)
        for f in sorted(os.listdir(src)):
            im = Image.open(os.path.join(src, f))
            save(im, f'assets/{NS}/{sub}/{f}')
    # i modelli oggetto dei pezzi d'armatura usano le texture equipment: vanno aggiunte all'atlante "items"
    sources = []
    for layer in ('humanoid', 'humanoid_leggings'):
        for s in SETS:
            sources.append({"type": "minecraft:single", "resource": f"{NS}:entity/equipment/{layer}/{s}"})
    wjson({"sources": sources}, 'assets/minecraft/atlases/items.json')


# =============================================================== 1. tier dei picconi
PICK_TPL = [
    "................",
    "................",
    "......aaaaa.....",
    ".....abcddcaef..",
    "......agggddhi..",
    "..........ecdg..",
    ".........efidcg.",
    "........ehi.gdg.",
    ".......efi..gdg.",
    "......ehi...gcg.",
    ".....efi....gbg.",
    "....ehi......g..",
    "...efi..........",
    "..ehi...........",
    "..ii............",
    "................",
]
HEAD_T = {'g': 0, 'a': 1, 'd': 2, 'c': 3, 'b': 4}
HANDLE_T = {'i': 0, 'e': 1, 'f': 2, 'h': 3}


def vanilla_pick_mask():
    """ruoli dei pixel del piccone vanilla (stessa sagoma delle vecchie skin 2D): tono 0..1."""
    head, handle = {}, {}
    for y, row in enumerate(PICK_TPL):
        for x, ch in enumerate(row):
            if ch in HEAD_T:
                head[(x, y)] = HEAD_T[ch] / 4
            elif ch in HANDLE_T:
                handle[(x, y)] = HANDLE_T[ch] / 3
    return head, handle


def tier_pepita():
    head, handle = vanilla_pick_mask()
    gold = MATS['gold']
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    lums = sorted(set(head.values()))
    for (x, y), l in head.items():
        t = lums.index(l) / max(1, len(lums) - 1)
        px[x, y] = [gold[0], gold[1], gold[2], gold[3], gold[4], gold[5]][min(5, int(t * 5.99))]
    hl = sorted(set(handle.values()))
    wood = [hexc('1a0d04'), hexc('3b200b'), hexc('5c3414'), hexc('80501f')]
    for (x, y), l in handle.items():
        t = hl.index(l) / max(1, len(hl) - 1)
        px[x, y] = wood[min(3, int(t * 3.99))]
    # fasce d'oro sul manico
    for (x, y) in handle:
        if (x - y) in (-7, -6, -1, 0):
            px[x, y] = gold[3] if handle[(x, y)] > 0.4 else gold[1]
    # pepite incastonate nella testa
    for (cx, cy) in [(7, 2), (12, 5), (13, 9)]:
        if (cx, cy) in head:
            px[cx, cy] = hexc('fffbe6')
            for ox, oy in ((1, 0), (0, 1)):
                if (cx + ox, cy + oy) in head:
                    px[cx + ox, cy + oy] = hexc('ffd64a')
            for ox, oy in ((-1, 0), (0, -1)):
                if (cx + ox, cy + oy) in head:
                    px[cx + ox, cy + oy] = gold[1]
    return img


def tier_quantum(frames=8):
    head, handle = vanilla_pick_mask()
    cy, mg = MATS['cyan'], MATS['magenta']
    out = Image.new('RGBA', (16, 16 * frames))
    lums = sorted(set(head.values()))
    hl = sorted(set(handle.values()))
    steel = [hexc('121318'), hexc('2a2c33'), hexc('43464f'), hexc('646874')]
    for f in range(frames):
        img = Image.new('RGBA', (16, 16))
        px = img.load()
        for (x, y), l in head.items():
            t = lums.index(l) / max(1, len(lums) - 1)
            i = min(5, int(t * 5.99))
            c = cy[i]
            # sfumatura viola verso le punte (cristallo ciano/viola)
            k = max(0, (x - y - 4) / 10.0) + max(0, (y - x) / 10.0)
            if i >= 1:
                c = lerp(c, MATS['purple'][i], min(0.7, k * 1.4))
            # onda di energia che percorre la testa
            w = (x + y + f * 2) % 16
            if w in (0, 1) and i >= 2:
                c = lerp(c, hexc('ffffff'), 0.55 if w == 0 else 0.3)
            px[x, y] = c
        for (x, y), l in handle.items():
            t = hl.index(l) / max(1, len(hl) - 1)
            px[x, y] = steel[min(3, int(t * 3.99))]
        # anelli luminosi sul manico che pulsano
        p = 0.5 + 0.5 * math.sin(f / frames * 2 * math.pi)
        for (x, y) in handle:
            if (x - y) in (-9, -8, -3, -2, 3, 4):
                px[x, y] = lerp(mg[2], mg[5], p) if (x - y) in (-3, -2) else lerp(cy[2], cy[5], 1 - p)
        out.paste(img, (0, 16 * f))
    return out


def compass_selettore():
    """bussola d'oro a forma di pepita (forma irregolare) con quadrante e ago."""
    rows = [
        "................",
        "......ggg.......",
        "....ggggggg.....",
        "...ggggggggg....",
        "..gggggggggggg..",
        "..gggggggggggg..",
        ".ggggggggggggg..",
        ".gggggggggggggg.",
        ".gggggggggggggg.",
        ".gggggggggggggg.",
        "..ggggggggggggg.",
        "..gggggggggggg..",
        "...gggggggggg...",
        ".....gggggg.....",
        "................",
        "................",
    ]
    img = sprite(rows, volume=0.9)
    px = img.load()
    # bugne sulla superficie (pepita)
    for p in [(5, 3), (11, 4), (3, 9), (13, 9), (9, 12)]:
        px[p] = MATS['gold'][5]
    for p in [(6, 3), (12, 5), (4, 10), (13, 10), (10, 12)]:
        px[p] = MATS['gold'][2]
    # quadrante
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8.5)
            if d <= 3.9:
                px[x, y] = hexc('f3e6c0') if d < 3.0 else hexc('5a3004')
    for p in [(6, 6), (7, 6), (6, 7)]:
        px[p] = hexc('fffbe6')
    # ago
    for p in [(10, 6), (9, 7)]:
        px[p] = hexc('d8302f')
    px[(10, 6)] = hexc('ff6a5a')
    px[(8, 8)] = hexc('2a2a33')
    for p in [(7, 9), (6, 10)]:
        px[p] = hexc('9fa2ae')
    # gemma in cima
    px[(7, 1)] = hexc('22c8e0')
    px[(8, 1)] = hexc('0d8aa2')
    return img


# =============================================================== 4. riempitivi
def stone_dark_tile(gold=False):
    base = [hexc('1d1916'), hexc('25201c'), hexc('2c2622'), hexc('342d28'), hexc('3d3530')]
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            n = blocks.tile_noise(x, y, 16, 8, 41) * 0.6 + blocks.tile_noise(x, y, 16, 4, 42) * 0.4
            px[x, y] = base[1 + min(2, int(n * 3.2))]
    # reticolo a rombi scolpito (continuo tra le tessere: diagonali x-y≡0 e x+y≡15 mod 16)
    for y in range(16):
        for x in range(16):
            on_a = (x - y) % 16 == 0
            on_b = (x + y) % 16 == 15
            if on_a or on_b:
                px[x, y] = base[0]
    for y in range(16):
        for x in range(16):
            # luce sul bordo inferiore della scanalatura (incisione)
            if ((x - y) % 16 == 15) and not ((x + y) % 16 == 15):
                px[x, y] = base[4]
            if ((x + y) % 16 == 0) and not ((x - y) % 16 == 0):
                px[x, y] = base[4]
    if gold:
        g = MATS['gold']
        for y in range(16):
            for x in range(16):
                on_a = (x - y) % 16 == 0
                on_b = (x + y) % 16 == 15
                if on_a or on_b:
                    px[x, y] = g[3] if (x + y) % 2 == 0 else g[4]
                elif ((x - y) % 16 == 15) or ((x + y) % 16 == 0):
                    px[x, y] = g[1]
        # borchie agli incroci (centro e angoli -> al centro e ai bordi delle tessere)
        for (cx, cy) in [(7, 7), (8, 8), (7, 8), (8, 7)]:
            px[cx, cy] = g[5] if (cx, cy) == (7, 7) else g[4] if (cx, cy) != (8, 8) else g[2]
        for (cx, cy) in [(0, 0), (15, 15), (0, 15), (15, 0)]:
            px[cx, cy] = g[4]
    return img


def write_fillers():
    for name, gold in (('riempi', False), ('riempi_oro', True)):
        save(stone_dark_tile(gold), f'assets/{NS}/textures/item/{name}.png')
        m = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{name}"},
            "display": {
                "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.125, 1.125, 1.125]},
            },
        }
        wjson(m, f'assets/{NS}/models/item/{name}.json')
    wjson({"textures": {"particle": f"{NS}:item/riempi"}, "elements": []}, f'assets/{NS}/models/item/vuoto.json')


# =============================================================== 5. container
VC = {
    (0, 0, 0, 255): 'K', (255, 255, 255, 255): 'W', (198, 198, 198, 255): 'C', (85, 85, 85, 255): 'G',
    (55, 55, 55, 255): 'D', (139, 139, 139, 255): 'S',
}
IRON_HI, IRON_MID, IRON_LO, IRON_OUT = hexc('5e5852'), hexc('48433e'), hexc('2e2a26'), hexc('120f0c')
GOLD_HI, GOLD_MID, GOLD_LO, GOLD_OUT = hexc('ffd64a'), hexc('e6a425'), hexc('a8620a'), hexc('3a2204')
SLOT_EDGE, SLOT_IN, SLOT_IN2, SLOT_LIP = hexc('24201c'), hexc('5a544c'), hexc('4d4842'), hexc('c9bda8')
STONE = [hexc('8a8073'), hexc('978c7e'), hexc('a3988a'), hexc('ada294'), hexc('b8ad9e')]


def classify(img):
    w, h = img.size
    px = img.load()
    cls = {}
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            cls[(x, y)] = None if c[3] == 0 else VC[c]
    # contorno = nero connesso al trasparente
    outline = set()
    dq = deque()
    for (x, y), k in cls.items():
        if k == 'K':
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                q = (x + ox, y + oy)
                if q not in cls or cls[q] is None:
                    outline.add((x, y))
                    dq.append((x, y))
                    break
    while dq:
        x, y = dq.popleft()
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + ox, y + oy)
            if cls.get(q) == 'K' and q not in outline:
                outline.add(q)
                dq.append(q)
    # distanza (4-vicini) dal contorno
    dist = {}
    dq = deque()
    for p in outline:
        dist[p] = 0
        dq.append(p)
    while dq:
        p = dq.popleft()
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (p[0] + ox, p[1] + oy)
            if cls.get(q) is not None and q not in dist:
                dist[q] = dist[p] + 1
                dq.append(q)
    return cls, outline, dist


def stone_px(x, y):
    """pietra lavorata calda: conci 24x12 sfalsati, malta appena più scura, luce sul bordo alto."""
    row = y // 12
    off = 12 if row % 2 else 0
    bx = (x + off) % 24
    by = y % 12
    if by == 11 or bx == 23:
        return STONE[0]
    n = blocks.tile_noise(x % 48, y % 48, 48, 6, 7 + row) * 0.85 + hnoise(x, y, 5) * 0.15
    c = STONE[1 + min(2, int(n * 3.0))]
    if by == 0 or bx == 0:
        c = STONE[4] if by == 0 else STONE[3]
    elif by == 10:
        c = lerp(c, STONE[0], 0.5)
    return c


def retexture(name, plaque=False):
    vimg_ = vimg(f'assets/minecraft/textures/gui/container/{name}.png')
    w, h = vimg_.size
    cls, outline, dist = classify(vimg_)
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    o = out.load()
    bb = vimg_.getchannel('A').getbbox()
    X0, Y0, X1, Y1 = bb[0], bb[1], bb[2] - 1, bb[3] - 1

    def corner_zone(x, y, r=8):
        return (min(x - X0, X1 - x) <= r) and (min(y - Y0, Y1 - y) <= r)

    for (x, y), k in cls.items():
        if k is None:
            continue
        d = dist.get((x, y), 99)
        if (x, y) in outline:
            c = GOLD_OUT if corner_zone(x, y, 7) else IRON_OUT
        elif d <= 2 or (d == 3 and k in ('W', 'G')):
            hi = k == 'W'
            lo = k == 'G'
            if corner_zone(x, y, 7):
                c = GOLD_HI if hi else GOLD_LO if lo else GOLD_MID
            else:
                c = IRON_HI if hi else IRON_LO if lo else IRON_MID
        elif k == 'C':
            c = stone_px(x, y)
        elif k == 'K':
            c = hexc('16120f')  # riquadro del giocatore
        elif k == 'D':
            c = SLOT_EDGE
        elif k == 'S':
            # seconda fila d'ombra dentro allo slot
            if cls.get((x - 1, y)) == 'D' or cls.get((x, y - 1)) == 'D':
                c = SLOT_IN2
            else:
                c = SLOT_IN
        elif k == 'W':
            c = SLOT_LIP
        elif k == 'G':
            c = IRON_LO
        o[x, y] = c
    # rivetti sulla cornice (2x2 dentro la fascia di 2px)
    def rivet(x, y):
        if all(dist.get((x + a, y + b), 99) in (1, 2) and cls.get((x + a, y + b)) for a in (0, 1) for b in (0, 1)):
            o[x, y] = hexc('d6cfc2')
            o[x + 1, y] = hexc('9a9288')
            o[x, y + 1] = hexc('9a9288')
            o[x + 1, y + 1] = hexc('4a443e')
    step = 22
    for x in range(X0 + 16, X1 - 16, step):
        rivet(x, Y0 + 1)
        rivet(x, Y1 - 2)
    for y in range(Y0 + 16, Y1 - 16, step):
        rivet(X0 + 1, y)
        rivet(X1 - 2, y)
    # squadrette d'oro negli angoli (solo su pixel di pietra)
    for (cx, cy, sx, sy) in ((X0 + 3, Y0 + 3, 1, 1), (X1 - 3, Y0 + 3, -1, 1), (X0 + 3, Y1 - 3, 1, -1), (X1 - 3, Y1 - 3, -1, -1)):
        for i in range(3):
            for j in range(3 - i):
                x, y = cx + sx * i, cy + sy * j
                if cls.get((x, y)) == 'C' and dist.get((x, y), 0) >= 3:
                    o[x, y] = GOLD_HI if (i + j) == 0 else GOLD_MID if (i + j) == 1 else GOLD_LO
    if plaque:
        draw_plaque(o, 4, 4, X1 - 4, 15, cls)
    return out, vimg_


def draw_plaque(o, x0, y0, x1, y1, cls=None):
    """targa scura per il titolo, bordo in bronzo/oro."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if cls is not None and cls.get((x, y)) != 'C':
                continue
            if y == y0:
                c = hexc('c79a3c')
            elif y == y1:
                c = hexc('5a3a12')
            elif x == x0:
                c = hexc('a87a28')
            elif x == x1:
                c = hexc('6a4614')
            elif y == y0 + 1:
                c = hexc('120e0b')
            else:
                c = hexc('2a221c') if (y - y0) < (y1 - y0) // 2 + 1 else hexc('241d18')
            o[x, y] = c
    # chiodi d'oro alle estremità
    for x in (x0 + 2, x1 - 2):
        yc = (y0 + y1) // 2
        o[x, yc] = hexc('ffd64a')
        o[x, yc + 1] = hexc('a8620a')


def slot_sprite():
    v = vimg('assets/minecraft/textures/gui/sprites/container/slot.png')
    out = Image.new('RGBA', v.size)
    for y in range(18):
        for x in range(18):
            k = VC[v.getpixel((x, y))]
            if k == 'D':
                c = SLOT_EDGE
            elif k == 'W':
                c = SLOT_LIP
            else:
                lk = VC[v.getpixel((x - 1, y))] if x > 0 else None
                uk = VC[v.getpixel((x, y - 1))] if y > 0 else None
                c = SLOT_IN2 if 'D' in (lk, uk) else SLOT_IN
            out.putpixel((x, y), c)
    return out, v


def write_containers():
    res = {}
    for name in ('generic_54', 'inventory', 'hopper', 'dispenser'):
        img, v = retexture(name, plaque=(name == 'generic_54'))
        save(img, f'assets/minecraft/textures/gui/container/{name}.png')
        res[name] = (img, v)
    s, v = slot_sprite()
    save(s, 'assets/minecraft/textures/gui/sprites/container/slot.png')
    res['slot'] = (s, v)
    return res


# =============================================================== 6. font
def frame_glyph(n):
    W, H = 176, n * 18 + 17
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    o = img.load()
    g = MATS['gold']
    # targa del titolo (y 3..15)
    draw_plaque(o, 4, 3, W - 5, 15)
    # terminali a voluta d'oro ai lati della targa
    for (x0, s) in ((4, 1), (W - 5, -1)):
        pts = [(0, 0), (0, 12), (-1, 1), (-1, 11), (-2, 3), (-2, 9), (-2, 4), (-2, 8), (-3, 5), (-3, 6), (-3, 7), (1, 6), (2, 6)]
        for (dx, dy) in pts:
            x, y = x0 + dx * s, 3 + dy
            if 0 <= x < W:
                o[x, y] = g[3] if dy < 6 else g[2]
        o[x0 + 2 * s, 9] = g[5]
    # colonne laterali nei margini (x 1..5 e 170..174) lungo le righe di slot
    for side in (0, 1):
        for y in range(17, H):
            xs = [1, 2, 3, 4, 5] if side == 0 else [170, 171, 172, 173, 174]
            for i, x in enumerate(xs):
                if i in (1, 2, 3):
                    # catena: maglie alternate frontali/laterali, periodo 6
                    t = (y - 17) % 6
                    ii = i - 1
                    if t in (0, 4):
                        if ii == 1:
                            o[x, y] = hexc('8d8a86') if t == 0 else hexc('4a4744')
                    elif t in (1, 2, 3):
                        if ii in (0, 2):
                            o[x, y] = hexc('b6b2ab') if ii == 0 else hexc('5c5853')
                        else:
                            o[x, y] = hexc('2a2724') if t == 2 else (0, 0, 0, 0)
                    elif t == 5:
                        if ii == 1:
                            o[x, y] = hexc('6f6b66')
        # borchie d'oro all'altezza di ogni riga
        for r in range(n):
            yc = 17 + r * 18 + 8
            xc = 2 if side == 0 else 172
            for (dx, dy, c) in ((0, 0, g[5]), (1, 0, g[3]), (2, 0, g[2]), (0, 1, g[3]), (1, 1, g[3]), (2, 1, g[1]),
                                (0, 2, g[2]), (1, 2, g[1]), (2, 2, g[0])):
                o[xc + dx, yc + dy] = c
            o[xc - 1, yc + 1] = MATS['gold'][0]
            o[xc + 3, yc + 1] = MATS['gold'][0]
    # angoli in alto decorati (filigrana d'oro)
    corner = [
        "GGGGGGg.",
        "Gyyyygo.",
        "Gyo.....",
        "Gy......",
        "Gy......",
        "Go......",
        "g.......",
    ]
    cm = {'G': g[5], 'y': g[4], 'g': g[3], 'o': g[1]}
    for r, row in enumerate(corner):
        for c, ch in enumerate(row):
            if ch == '.':
                continue
            o[c, r] = cm[ch]
            o[W - 1 - c, r] = cm[ch] if ch not in ('G',) else g[4]
    # piccola gemma ciano al centro della targa sopra il bordo
    for x in range(84, 92):
        pass
    # base delle colonne (terminali in fondo, ancora fuori dalla griglia)
    for xc in (1, 170):
        for dx in range(5):
            o[xc + dx, H - 1] = g[2] if dx not in (0, 4) else g[1]
    return img


def emblem(symname):
    img = Image.new('RGBA', (12, 12), (0, 0, 0, 0))
    o = img.load()
    g = MATS['gold']
    for y in range(12):
        for x in range(12):
            # ottagono
            cx, cy = abs(x - 5.5), abs(y - 5.5)
            if cx + cy > 8.0:
                continue
            edge = cx + cy > 7.0 or max(cx, cy) > 5.0
            ring = cx + cy > 6.0 or max(cx, cy) > 4.0
            if edge:
                o[x, y] = g[0]
            elif ring:
                o[x, y] = g[4] if (x + y) < 11 else g[2]
            else:
                o[x, y] = hexc('1c140c')
    s = small.sym(symname)
    sp = s.load()
    for y in range(8):
        for x in range(8):
            c = sp[x, y]
            if c[3] == 0:
                continue
            # i contorni scuri del simbolo spariscono sul fondo scuro
            if sum(c[:3]) < 110:
                continue
            o[2 + x, 2 + y] = c
    return img


def logo_crop():
    im = Image.open(os.path.join(DRIVE, 'logo_pepita.png')).convert('RGBA')
    bb = im.getchannel('A').point(lambda a: 255 if a > 40 else 0).getbbox()
    im = im.crop(bb)
    s = max(im.size)
    sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    sq.alpha_composite(im, ((s - im.width) // 2, (s - im.height) // 2))
    return im, sq


def clean_alpha(img, thr=110):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            c = px[x, y]
            if c[3] < thr:
                px[x, y] = (0, 0, 0, 0)
            else:
                px[x, y] = (c[0], c[1], c[2], 255)
    return img


def write_fonts():
    space = {}
    for i in range(1, 9):
        space[chr(0xF800 + i)] = -i
        space[chr(0xF820 + i)] = i
    space[''] = -16
    space[''] = -32
    space[''] = -64
    space[''] = -128
    space[''] = -256
    space[''] = 16
    space[''] = 32
    space[''] = 64
    space[''] = 128
    providers = [{"type": "space", "advances": space}]
    frames = {}
    for n in range(1, 7):
        img = frame_glyph(n)
        frames[n] = img
        save(img, f'assets/{NS}/textures/font/gui/cornice_{n}.png')
        providers.append({"type": "bitmap", "file": f"{NS}:font/gui/cornice_{n}.png", "height": n * 18 + 17,
                          "ascent": 13, "chars": [chr(0xE000 + n)]})
    ems = {}
    for i, name in enumerate(EMBLEMS):
        img = emblem(EMBLEM_SYM[name])
        ems[name] = img
        save(img, f'assets/{NS}/textures/font/gui/emblema_{name}.png')
        providers.append({"type": "bitmap", "file": f"{NS}:font/gui/emblema_{name}.png", "height": 12,
                          "ascent": 11, "chars": [chr(0xE010 + i)]})
    wjson({"providers": providers}, f'assets/{NS}/font/gui.json')

    ip = []
    ics = {}
    for i, name in enumerate(FONT_ICONS):
        img = small.sym(name)
        ics[name] = img
        save(img, f'assets/{NS}/textures/font/icons/{name}.png')
        ip.append({"type": "bitmap", "file": f"{NS}:font/icons/{name}.png", "height": 8, "ascent": 7,
                   "chars": [chr(0xE100 + i)]})
    wjson({"providers": ip}, f'assets/{NS}/font/icons.json')

    # logo: mantiene  (logo piccolo di gen.py) e aggiunge  = logo ufficiale alto 40px
    im, _ = logo_crop()
    hh = 160
    ww = round(im.width * hh / im.height)
    big = clean_alpha(im.resize((ww, hh), Image.LANCZOS), 100)
    save(big, f'assets/{NS}/textures/font/logo_ufficiale.png')
    wjson({"providers": [
        {"type": "bitmap", "file": f"{NS}:font/logo.png", "ascent": 14, "height": 18, "chars": [""]},
        {"type": "bitmap", "file": f"{NS}:font/logo_ufficiale.png", "ascent": 34, "height": 40, "chars": [""]},
    ]}, f'assets/{NS}/font/logo.json')
    return frames, ems, ics, big


# =============================================================== 7. tooltip
TT = {
    'oro': dict(bg=(28, 18, 6, 242), out=hexc('1a0f02'), l1=hexc('ffd64a'), l1d=hexc('c98a1c'), l2=hexc('7a4508'),
                l2d=hexc('5a3004'), gem=None),
    'quantum': dict(bg=(18, 8, 31, 240), out=hexc('0a0414'), l1=hexc('22c8e0'), l1d=hexc('9e1c88'), l2=hexc('3a1460'),
                    l2d=hexc('2a0e48'), gem='quantum'),
    'leggendario': dict(bg=(24, 10, 34, 242), out=hexc('120618'), l1=hexc('ffd64a'), l1d=hexc('c98a1c'),
                        l2=hexc('9b4ff0'), l2d=hexc('6c26b4'), gem='purple'),
}


def tooltip_bg(style):
    s = TT[style]
    img = Image.new('RGBA', (100, 100), (0, 0, 0, 0))
    o = img.load()
    for y in range(8, 92):
        for x in range(8, 92):
            if (x in (8, 91)) and (y in (8, 91)):
                continue
            o[x, y] = s['bg']
    return img


def tooltip_frame(style):
    s = TT[style]
    img = Image.new('RGBA', (100, 100), (0, 0, 0, 0))
    o = img.load()

    def put(x, y, c):
        if 0 <= x < 100 and 0 <= y < 100:
            o[x, y] = c

    # linee (d = distanza dal bordo dello sprite): 7 = contorno, 8 = linea principale, 9 = linea interna
    for i in range(7, 93):
        for (x, y, side) in ((i, 7, 't'), (i, 92, 'b'), (7, i, 'l'), (92, i, 'r')):
            put(x, y, s['out'])
    for i in range(8, 92):
        for (x, y, side) in ((i, 8, 't'), (i, 91, 'b'), (8, i, 'l'), (91, i, 'r')):
            put(x, y, s['l1'] if side in ('t', 'l') else s['l1d'])
        for (x, y, side) in ((i, 9, 't'), (i, 90, 'b'), (9, i, 'l'), (90, i, 'r')):
            if 9 <= i <= 90:
                put(x, y, s['l2'] if side in ('t', 'l') else s['l2d'])
    # angoli ornati (dentro i 10px del bordo nine-slice)
    if style == 'quantum':
        cy, mg = MATS['cyan'], MATS['magenta']
        for (cx, cyy, sx, sy) in ((5, 5, 1, 1), (94, 5, -1, 1), (5, 94, 1, -1), (94, 94, -1, -1)):
            # stella luminosa a 4 punte con alone
            for k in range(-4, 5):
                a = max(0, 255 - abs(k) * 55)
                col = cy[5] if abs(k) <= 1 else cy[4] if abs(k) <= 2 else cy[3]
                put(cx + k, cyy, (col[0], col[1], col[2], a))
                put(cx, cyy + k, (col[0], col[1], col[2], a))
            for (dx, dy) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                put(cx + dx, cyy + dy, (mg[4][0], mg[4][1], mg[4][2], 170))
            for (dx, dy) in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
                put(cx + dx, cyy + dy, (mg[3][0], mg[3][1], mg[3][2], 70))
            put(cx, cyy, hexc('ffffff'))
            # punta della cornice che si illumina vicino all'angolo
            for k in range(0, 2):
                put(cx + sx * (3 + k), cyy + sy * 3, cy[4])
                put(cx + sx * 3, cyy + sy * (3 + k), cy[4])
    else:
        g = MATS['gold']
        gem = MATS['purple'] if s['gem'] == 'purple' else MATS['red']
        for (cx, cyy, sx, sy) in ((8, 8, 1, 1), (91, 8, -1, 1), (8, 91, 1, -1), (91, 91, -1, -1)):
            # squadretta a L che sporge verso l'esterno + diamante con gemma
            for k in range(0, 6):
                put(cx - sx * 2, cyy + sy * k, g[3] if k < 4 else g[2])
                put(cx + sx * k, cyy - sy * 2, g[3] if k < 4 else g[2])
                put(cx - sx * 3, cyy + sy * k, g[0])
                put(cx + sx * k, cyy - sy * 3, g[0])
            for k in range(-1, 6):
                put(cx - sx * 1, cyy + sy * k, s['out'])
                put(cx + sx * k, cyy - sy * 1, s['out'])
            # diamante d'angolo
            dx0, dy0 = cx - sx * 3, cyy - sy * 3
            for yy in range(-3, 4):
                for xx in range(-3, 4):
                    d = abs(xx) + abs(yy)
                    if d <= 3:
                        if d == 3:
                            c = g[0]
                        elif d == 2:
                            c = g[4] if (xx * sx + yy * sy) < 0 else g[2]
                        else:
                            c = gem[4] if (xx, yy) == (0, 0) else gem[3] if (xx * sx + yy * sy) <= 0 else gem[2]
                            if style == 'oro':
                                c = g[5] if (xx, yy) == (0, 0) else g[4] if (xx * sx + yy * sy) <= 0 else g[3]
                        put(dx0 + xx, dy0 + yy, c)
            put(dx0 - sx, dy0 - sy, hexc('ffffff') if style != 'oro' else hexc('fffbe6'))
    return img


def tooltip_mcmeta():
    bg = vjson('assets/minecraft/textures/gui/sprites/tooltip/background.png.mcmeta')
    fr = vjson('assets/minecraft/textures/gui/sprites/tooltip/frame.png.mcmeta')
    return bg, fr


def write_tooltips():
    bgm, frm = tooltip_mcmeta()
    res = {}
    for st in TOOLTIPS:
        bg = tooltip_bg(st)
        fr = tooltip_frame(st)
        save(bg, f'assets/{NS}/textures/gui/sprites/tooltip/{st}_background.png')
        save(fr, f'assets/{NS}/textures/gui/sprites/tooltip/{st}_frame.png')
        wjson(bgm, f'assets/{NS}/textures/gui/sprites/tooltip/{st}_background.png.mcmeta')
        wjson(frm, f'assets/{NS}/textures/gui/sprites/tooltip/{st}_frame.png.mcmeta')
        res[st] = (bg, fr)
    return res


def nine_slice(img, w, h, border, stretch_inner=False):
    """emula il rendering nine_slice di Minecraft (bordi e centro ripetuti, centro stirato se richiesto)."""
    W, H = img.size
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    b = border
    cw, ch = W - 2 * b, H - 2 * b

    def tile(src, dst_box):
        x0, y0, x1, y1 = dst_box
        for yy in range(y0, y1, src.height):
            for xx in range(x0, x1, src.width):
                part = src.crop((0, 0, min(src.width, x1 - xx), min(src.height, y1 - yy)))
                out.alpha_composite(part, (xx, yy))
    out.alpha_composite(img.crop((0, 0, b, b)), (0, 0))
    out.alpha_composite(img.crop((W - b, 0, W, b)), (w - b, 0))
    out.alpha_composite(img.crop((0, H - b, b, H)), (0, h - b))
    out.alpha_composite(img.crop((W - b, H - b, W, H)), (w - b, h - b))
    tile(img.crop((b, 0, W - b, b)), (b, 0, w - b, b))
    tile(img.crop((b, H - b, W - b, H)), (b, h - b, w - b, h))
    tile(img.crop((0, b, b, H - b)), (0, b, b, h - b))
    tile(img.crop((W - b, b, W, H - b)), (w - b, b, w, h - b))
    center = img.crop((b, b, W - b, H - b))
    if stretch_inner:
        out.alpha_composite(center.resize((max(1, w - 2 * b), max(1, h - 2 * b)), Image.NEAREST), (b, b))
    else:
        tile(center, (b, b, w - b, h - b))
    return out


# =============================================================== 8. blocchi
def write_blocks():
    lb = blocks.lucky_block()
    save(lb, 'assets/minecraft/textures/block/sponge.png')
    anim_meta('assets/minecraft/textures/block/sponge.png', 2)
    qo = blocks.quantum_ore()
    save(qo, 'assets/minecraft/textures/block/budding_amethyst.png')
    anim_meta('assets/minecraft/textures/block/budding_amethyst.png', 3, True)
    nt = blocks.nucleus_top()
    save(nt, 'assets/minecraft/textures/block/lodestone_top.png')
    anim_meta('assets/minecraft/textures/block/lodestone_top.png', 3, True)
    ns = blocks.nucleus_side()
    save(ns, 'assets/minecraft/textures/block/lodestone_side.png')
    anim_meta('assets/minecraft/textures/block/lodestone_side.png', 3, True)
    return {'sponge': lb, 'budding_amethyst': qo, 'lodestone_top': nt, 'lodestone_side': ns}


# =============================================================== 9. NPC
SKIN_FACES = {
    'head': {'top': (8, 0, 8, 8), 'bottom': (16, 0, 8, 8), 'right': (0, 8, 8, 8), 'front': (8, 8, 8, 8), 'left': (16, 8, 8, 8), 'back': (24, 8, 8, 8)},
    'body': {'top': (20, 16, 8, 4), 'bottom': (28, 16, 8, 4), 'right': (16, 20, 4, 12), 'front': (20, 20, 8, 12), 'left': (28, 20, 4, 12), 'back': (32, 20, 8, 12)},
    'rarm': {'top': (44, 16, 4, 4), 'bottom': (48, 16, 4, 4), 'right': (40, 20, 4, 12), 'front': (44, 20, 4, 12), 'left': (48, 20, 4, 12), 'back': (52, 20, 4, 12)},
    'rleg': {'top': (4, 16, 4, 4), 'bottom': (8, 16, 4, 4), 'right': (0, 20, 4, 12), 'front': (4, 20, 4, 12), 'left': (8, 20, 4, 12), 'back': (12, 20, 4, 12)},
    'lleg': {'top': (20, 48, 4, 4), 'bottom': (24, 48, 4, 4), 'right': (16, 52, 4, 12), 'front': (20, 52, 4, 12), 'left': (24, 52, 4, 12), 'back': (28, 52, 4, 12)},
    'larm': {'top': (36, 48, 4, 4), 'bottom': (40, 48, 4, 4), 'right': (32, 52, 4, 12), 'front': (36, 52, 4, 12), 'left': (40, 52, 4, 12), 'back': (44, 52, 4, 12)},
}
OVERLAY_OFF = {'head': (32, 0), 'body': (0, 16), 'rarm': (0, 16), 'rleg': (0, 16), 'lleg': (-16, 0), 'larm': (16, 0)}


def npc_guardiano():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    o = img.load()
    NAVY = [hexc('0e1426'), hexc('162036'), hexc('1d2a47'), hexc('263759'), hexc('31466e')]
    SK = [hexc('8a5238'), hexc('b0714e'), hexc('c98a64'), hexc('dba07a'), hexc('e8b690')]
    HAIR = [hexc('3a2a1e'), hexc('4e3a2a'), hexc('6a5240'), hexc('8a7a6e')]
    MUST = [hexc('2a1a10'), hexc('3e2818'), hexc('5a3c24')]
    GOLD = MATS['gold']
    BLK = [hexc('0c0c0e'), hexc('18181c'), hexc('26262c'), hexc('3a3a42')]
    SHIRT = [hexc('7d95b8'), hexc('a3b8d6')]

    def face(part, fname):
        return SKIN_FACES[part][fname]

    def fill(part, fname, fn, overlay=False):
        x0, y0, w, h = face(part, fname)
        if overlay:
            ox, oy = OVERLAY_OFF[part]
            x0, y0 = x0 + ox, y0 + oy
        for v in range(h):
            for u in range(w):
                c = fn(u, v, w, h)
                if c is not None:
                    o[x0 + u, y0 + v] = c

    def navy(u, v, w, h, seed=0):
        n = hnoise(u, v, seed)
        c = NAVY[2] if n < 0.75 else NAVY[3]
        if v == 0:
            c = NAVY[3]
        return c

    # ---------------- testa
    def head_side(fname):
        def fn(u, v, w, h):
            if v <= 2:
                return NAVY[2] if v < 2 else BLK[1]  # berretto + fascia
            if fname == 'back':
                return HAIR[1] if v < 6 else (HAIR[2] if v == 6 else SK[1])
            # lati: capelli corti brizzolati vicino alla nuca, orecchio
            back_side = (u <= 2) if fname == 'right' else (u >= 5)
            if back_side and v <= 5:
                return HAIR[2] if (u + v) % 3 else HAIR[3]
            ear = (u == 4 if fname == 'right' else u == 3) and v in (4, 5)
            if ear:
                return SK[1]
            if v == 7:
                return SK[1]
            # basette e baffi che girano sul lato
            front_side = (u >= 6) if fname == 'right' else (u <= 1)
            if front_side and v == 6:
                return MUST[1]
            return SK[2] if v < 6 else SK[1]
        return fn
    for fname in ('right', 'left', 'back'):
        fill('head', fname, head_side(fname))
    fill('head', 'top', lambda u, v, w, h: NAVY[3] if (u + v) % 5 else NAVY[2])
    fill('head', 'bottom', lambda u, v, w, h: SK[1])
    FACE = [
        "NNNYYNNN",
        "NNNooNNN",
        "BBBBBBBB",
        "sHHssHHs",
        "sWEssEWs",
        "ssssnsss",
        "MMMMMMMM",
        "Mm.mm.mM",
    ]
    FACE[7] = "MssqqssM"
    fmap = {'N': NAVY[3], 'Y': GOLD[4], 'o': GOLD[2], 'B': BLK[1], 's': SK[3], 'H': HAIR[0], 'W': hexc('f2f2f2'),
            'E': hexc('2a3a5a'), 'n': SK[1], 'M': MUST[1], 'q': hexc('8a3a2a')}
    fill('head', 'front', lambda u, v, w, h: fmap[FACE[v][u]])
    # rifiniture del viso: guance e punte dei baffi
    x0, y0 = 8, 8
    o[x0 + 1, y0 + 5] = SK[4]
    o[x0 + 6, y0 + 5] = SK[2]
    o[x0 + 2, y0 + 6] = MUST[2]
    o[x0 + 5, y0 + 6] = MUST[0]
    o[x0 + 3, y0 + 3] = SK[4]
    o[x0 + 4, y0 + 3] = SK[3]
    # cappello (overlay): visiera nera e stemma d'oro
    def hat_front(u, v, w, h):
        if v == 0:
            return NAVY[4]
        if v == 1:
            return GOLD[4] if u in (3, 4) else NAVY[3]
        if v == 2:
            return BLK[2] if u not in (0, 7) else BLK[1]
        if v == 3:
            return BLK[3] if 1 <= u <= 6 else None  # visiera
        return None
    fill('head', 'front', hat_front, overlay=True)
    o[40 + 3, 8 + 0] = GOLD[3]
    o[40 + 4, 8 + 0] = GOLD[3]
    o[40 + 3, 8 + 1] = GOLD[5]
    o[40 + 4, 8 + 1] = GOLD[2]
    for fname in ('right', 'left', 'back'):
        fill('head', fname, lambda u, v, w, h: NAVY[3] if v == 0 else NAVY[2] if v == 1 else BLK[1] if v == 2 else None, overlay=True)
    fill('head', 'top', lambda u, v, w, h: NAVY[4] if (u + v) % 4 else NAVY[3], overlay=True)
    # ---------------- corpo
    BODY = [
        "NNSccSNN",
        "NNNSSNNN",
        "NNNNYNNN",
        "NGGNNNNN",
        "NGGNYNNN",
        "NNNNNNNN",
        "NNNNYNNN",
        "NNNNNNNN",
        "BBBBGBBB",
        "BBBGgGBB",
        "NNNNNkNN",
        "NNNNNKkN",
    ]
    BODY[8] = "BBBYYBBB"
    BODY[9] = "BBBooBBB"
    BODY[10] = "NNNNNkNN"
    BODY[11] = "NNNNkKkN"
    bmap = {'N': NAVY[2], 'S': SHIRT[0], 'c': SHIRT[1], 'Y': GOLD[4], 'G': GOLD[3], 'g': GOLD[2], 'o': GOLD[2],
            'B': BLK[1], 'k': GOLD[3], 'K': hexc('cfd2dc')}
    def body_front(u, v, w, h):
        c = bmap[BODY[v][u]]
        if BODY[v][u] == 'N':
            if u == 0:
                c = NAVY[1]
            elif u == 7:
                c = NAVY[1]
            elif hnoise(u, v, 3) > 0.85:
                c = NAVY[3]
        return c
    fill('body', 'front', body_front)
    # distintivo a stella sul petto (pixel singoli)
    o[20 + 1, 20 + 3] = GOLD[5]
    o[20 + 2, 20 + 3] = GOLD[3]
    o[20 + 1, 20 + 4] = GOLD[3]
    o[20 + 2, 20 + 4] = GOLD[1]

    def body_back(u, v, w, h):
        if v in (8, 9):
            return BLK[1]
        if v == 0:
            return NAVY[3]
        return NAVY[2] if hnoise(u, v, 9) < 0.85 else NAVY[1]
    fill('body', 'back', body_back)

    def body_side(fname):
        def fn(u, v, w, h):
            if v in (8, 9):
                return BLK[1]
            # mazzo di chiavi appeso al fianco sinistro (lato 'left' del modello)
            if fname == 'left' and v >= 9:
                keys = {(1, 9): GOLD[3], (1, 10): GOLD[4], (2, 10): hexc('cfd2dc'), (1, 11): hexc('9fa2ae'),
                        (2, 11): hexc('cfd2dc'), (0, 11): GOLD[2]}
                if (u, v) in keys:
                    return keys[(u, v)]
            return NAVY[2] if v else NAVY[3]
        return fn
    fill('body', 'right', body_side('right'))
    fill('body', 'left', body_side('left'))
    fill('body', 'top', lambda u, v, w, h: NAVY[3] if not (2 <= u <= 5 and v <= 1) else SHIRT[0])
    fill('body', 'bottom', lambda u, v, w, h: NAVY[1])

    # ---------------- braccia (manica blu, polsino oro, mano)
    for arm in ('rarm', 'larm'):
        for fname in ('front', 'back', 'right', 'left'):
            def arm_fn(u, v, w, h, fname=fname):
                if v >= 10:
                    return SK[3] if v == 10 else SK[2]
                if v == 9:
                    return GOLD[3] if fname != 'back' else GOLD[2]
                if v == 8:
                    return NAVY[1]
                if v == 0:
                    return NAVY[3]
                if fname == 'front' and v == 2 and u in (1, 2):
                    return GOLD[2]  # spallina
                return NAVY[2] if hnoise(u, v + 31, 4) < 0.85 else NAVY[3]
            fill(arm, fname, arm_fn)
        fill(arm, 'top', lambda u, v, w, h: NAVY[4] if v < 2 else NAVY[3])
        fill(arm, 'bottom', lambda u, v, w, h: SK[2])
        # spalline d'oro sull'overlay (top + prima riga)
        fill(arm, 'top', lambda u, v, w, h: GOLD[3] if (u + v) % 2 else GOLD[4], overlay=True)
        for fname in ('front', 'back', 'right', 'left'):
            fill(arm, fname, lambda u, v, w, h: (GOLD[2] if v == 0 else None), overlay=True)

    # ---------------- gambe (pantaloni con banda dorata, stivali neri)
    for leg in ('rleg', 'lleg'):
        for fname in ('front', 'back', 'right', 'left'):
            def leg_fn(u, v, w, h, fname=fname, leg=leg):
                if v >= 8:
                    if v == 8:
                        return BLK[3]
                    if v == 11:
                        return BLK[0]
                    if fname == 'front' and v == 9 and u in (1, 2):
                        return hexc('5a5a66')  # lucido sugli stivali
                    return BLK[1]
                outer = (leg == 'rleg' and fname == 'right') or (leg == 'lleg' and fname == 'left')
                if outer and u in (1, 2):
                    return GOLD[2] if u == 1 else GOLD[1]
                if fname == 'front' and ((leg == 'rleg' and u == 3) or (leg == 'lleg' and u == 0)):
                    return NAVY[1]
                return NAVY[2] if hnoise(u, v + 77, 2) < 0.85 else NAVY[1]
            fill(leg, fname, leg_fn)
        fill(leg, 'top', lambda u, v, w, h: NAVY[2])
        fill(leg, 'bottom', lambda u, v, w, h: BLK[0])
    return img


def skin_flat(img, back=False):
    """vista frontale piatta (testa, corpo, braccia, gambe + overlay)."""
    out = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    f = 'back' if back else 'front'

    def put(part, dx, dy, overlay=False):
        x0, y0, w, h = SKIN_FACES[part][f]
        if overlay:
            ox, oy = OVERLAY_OFF[part]
            x0, y0 = x0 + ox, y0 + oy
        out.alpha_composite(img.crop((x0, y0, x0 + w, y0 + h)), (dx, dy))
    if not back:
        put('head', 4, 0); put('body', 4, 8); put('rarm', 0, 8); put('larm', 12, 8); put('rleg', 4, 20); put('lleg', 8, 20)
        for p, d in (('head', (4, 0)), ('body', (4, 8)), ('rarm', (0, 8)), ('larm', (12, 8)), ('rleg', (4, 20)), ('lleg', (8, 20))):
            put(p, d[0], d[1], True)
    else:
        put('head', 4, 0); put('body', 4, 8); put('larm', 0, 8); put('rarm', 12, 8); put('lleg', 4, 20); put('rleg', 8, 20)
        for p, d in (('head', (4, 0)), ('body', (4, 8)), ('larm', (0, 8)), ('rarm', (12, 8)), ('lleg', (4, 20)), ('rleg', (8, 20))):
            put(p, d[0], d[1], True)
    return out


# =============================================================== 10. logo animato
def sparkle(img, cx, cy, r, strength=1.0):
    px = img.load()
    W, H = img.size
    for k in range(-r, r + 1):
        a = (1 - abs(k) / (r + 1)) * strength
        for (x, y) in ((cx + k, cy), (cx, cy + k)):
            if 0 <= x < W and 0 <= y < H:
                c = px[x, y]
                base = c if c[3] else (255, 240, 170, 0)
                nc = lerp((base[0], base[1], base[2], 255), hexc('ffffff'), a)
                px[x, y] = (nc[0], nc[1], nc[2], max(c[3], int(255 * min(1, a * 1.6))) if a > 0.25 or c[3] else 0)
    for (dx, dy) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        x, y = cx + dx, cy + dy
        if r >= 3 and 0 <= x < W and 0 <= y < H:
            c = px[x, y]
            if c[3]:
                px[x, y] = lerp(c, hexc('fff6c8'), 0.5 * strength)


def logo_frames(n=12, size=192):
    _, sq = logo_crop()
    base = clean_alpha(sq.resize((size, size), Image.LANCZOS), 110)
    bp = base.load()
    # pixel "dorati" (non contorno scuro) su cui passa il riflesso
    gold = set()
    for y in range(size):
        for x in range(size):
            c = bp[x, y]
            if c[3] and (c[0] + c[1]) > 260:
                gold.add((x, y))
    # scintille: sui cubetti che fluttuano attorno alla pepita + punti della scritta
    s0 = size / 1215.0
    spots = [(430, 230), (350, 345), (935, 320), (240, 545), (995, 490), (1025, 615), (600, 260), (880, 800), (180, 850)]
    # (coordinate nel logo ritagliato e quadrato, approssimate)
    frames = Image.new('RGBA', (size, size * n), (0, 0, 0, 0))
    for f in range(n):
        img = base.copy()
        px = img.load()
        # riflesso diagonale (banda morbida) nei primi 8 frame
        if f < 8:
            pos = -40 + f * (size + 80) / 7.0
            for (x, y) in gold:
                d = (x + y * 0.6) - pos * 1.6
                if abs(d) < 22:
                    k = (1 - abs(d) / 22) ** 1.5 * 0.75
                    px[x, y] = lerp(px[x, y], hexc('fffbe6'), k)
        for i, (sx, sy) in enumerate(spots):
            ph = (f + i * 5) % n
            if ph < 4:
                strength = [0.5, 1.0, 0.8, 0.35][ph]
                r = [2, 5, 4, 2][ph]
                sparkle(img, int(sx * s0), int(sy * s0), r, strength)
        frames.paste(img, (0, size * f))
    return frames, base


def write_logo():
    frames, still = logo_frames()
    save(frames, f'assets/{NS}/textures/item/logo.png')
    anim_meta(f'assets/{NS}/textures/item/logo.png', 3, False)
    save(frames.crop((0, 0, 192, 192)), f'assets/{NS}/textures/item/logo_statico.png')
    disp = {"display": {
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "none": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    }}
    generated_model('logo', extra=disp)
    generated_model('logo_statico', extra=disp)
    return frames


# =============================================================== item definitions
def vanilla_item_model(item):
    return vjson(f'assets/minecraft/items/{item}.json')['model']


def write_items(by_item):
    for vanilla, cases in sorted(by_item.items()):
        seen = set()
        uniq = []
        for c in cases:
            if c['when'] in seen:
                continue
            seen.add(c['when'])
            uniq.append(c)
        model = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                 "cases": uniq, "fallback": vanilla_item_model(vanilla)}
        vdef = vjson(f'assets/minecraft/items/{vanilla}.json')
        defn = {k: v for k, v in vdef.items() if k != 'model'}
        defn["model"] = model
        if vanilla in OVERSIZED:
            # dal 1.21.6 i modelli in GUI vengono ritagliati a 16x16 se non marcati come oversized:
            # serve al riempitivo (scala 1.125 -> 18px) e ai modelli 3D dei set
            defn["oversized_in_gui"] = True
        wjson(defn, f'assets/minecraft/items/{vanilla}.json', mini=True)


# =============================================================== validazione
def validate(out_dir, by_item):
    errs = []
    files = {}
    for root, _, fs in os.walk(out_dir):
        for f in fs:
            full = os.path.join(root, f)
            files[os.path.relpath(full, out_dir).replace(os.sep, '/')] = full
    vnames = set(jar().namelist())

    def exists(rel):
        return rel in files or rel in vnames

    def tex_exists(ref):
        ns, p = (ref.split(':', 1) if ':' in ref else ('minecraft', ref))
        return exists(f'assets/{ns}/textures/{p}.png')

    def model_exists(ref):
        ns, p = (ref.split(':', 1) if ':' in ref else ('minecraft', ref))
        if p.startswith('builtin/'):
            return True
        return exists(f'assets/{ns}/models/{p}.json')

    def load_model(ref):
        ns, p = (ref.split(':', 1) if ':' in ref else ('minecraft', ref))
        rel = f'assets/{ns}/models/{p}.json'
        if rel in files:
            return json.load(open(files[rel], encoding='utf-8'))
        return json.loads(jar().read(rel))

    njson = 0
    for rel, full in files.items():
        if rel.endswith('.json') or rel.endswith('.mcmeta'):
            try:
                d = json.load(open(full, encoding='utf-8'))
                njson += 1
            except Exception as e:
                errs.append(f'JSON non valido {rel}: {e}')
                continue
            if rel.endswith('.png.mcmeta'):
                png = rel[:-7]
                if png not in files:
                    errs.append(f'mcmeta senza png: {rel}')
                elif 'animation' in d:
                    im = Image.open(files[png])
                    if im.height % im.width != 0 or im.height == im.width:
                        errs.append(f'animazione con dimensioni errate {png} {im.size}')
    # modelli: texture e parent
    checked = set()

    def check_model(ref, ctx):
        if ref in checked:
            return
        checked.add(ref)
        if not model_exists(ref):
            errs.append(f'modello mancante {ref} ({ctx})')
            return
        if ref.split(':')[-1].startswith('builtin/'):
            return
        m = load_model(ref)
        if 'parent' in m:
            check_model(m['parent'] if ':' in m['parent'] else 'minecraft:' + m['parent'], ref)
        for k, v in m.get('textures', {}).items():
            if v.startswith('#') or 'trims/' in v:  # trims/*: texture virtuali dell'atlante (paletted_permutations)
                continue
            if not tex_exists(v):
                errs.append(f'texture mancante {v} in {ref}')
    for rel in files:
        if rel.startswith(f'assets/{NS}/models/') and rel.endswith('.json'):
            check_model(f"{NS}:{rel[len(f'assets/{NS}/models/'):-5]}", rel)

    def walk_item(node, ctx):
        if isinstance(node, dict):
            if node.get('type') in ('minecraft:model', 'model') and 'model' in node:
                check_model(node['model'], ctx)
            for v in node.values():
                walk_item(v, ctx)
        elif isinstance(node, list):
            for v in node:
                walk_item(v, ctx)
    for rel, full in files.items():
        if rel.startswith('assets/minecraft/items/'):
            walk_item(json.load(open(full, encoding='utf-8')), rel)
            d = json.load(open(full, encoding='utf-8'))
            whens = [c['when'] for c in d['model'].get('cases', [])]
            if len(whens) != len(set(whens)):
                errs.append(f'case duplicati in {rel}')
    # equipment
    for rel, full in files.items():
        if '/equipment/' in rel and rel.endswith('.json') and '/textures/' not in rel:
            d = json.load(open(full, encoding='utf-8'))
            for layer, lst in d['layers'].items():
                for e in lst:
                    ns, p = e['texture'].split(':')
                    if f'assets/{ns}/textures/entity/equipment/{layer}/{p}.png' not in files:
                        errs.append(f'texture equipment mancante {layer}/{p}')
    # atlas
    at = json.load(open(files['assets/minecraft/atlases/items.json']))
    for s in at['sources']:
        if not tex_exists(s['resource']):
            errs.append(f'atlas: texture mancante {s["resource"]}')
    # font
    for rel, full in files.items():
        if rel.startswith(f'assets/{NS}/font/'):
            d = json.load(open(full, encoding='utf-8'))
            for p in d['providers']:
                if p['type'] != 'bitmap':
                    continue
                ns, fp = p['file'].split(':')
                trel = f'assets/{ns}/textures/{fp}'
                if trel not in files:
                    errs.append(f'font: file mancante {p["file"]}')
                    continue
                im = Image.open(files[trel])
                if p['ascent'] > p['height']:
                    errs.append(f'font: ascent > height in {p["file"]}')
                rows = len(p['chars'])
                cols = max(len(r) for r in p['chars'])
                if im.height % rows or im.width % cols:
                    errs.append(f'font: griglia errata {p["file"]}')
                if im.width > 256 or im.height > 256:
                    errs.append(f'font: glifo troppo grande {p["file"]} {im.size}')
    # dimensioni attese
    expect = {}
    for n in range(1, 7):
        expect[f'assets/{NS}/textures/font/gui/cornice_{n}.png'] = (176, n * 18 + 17)
    for e in EMBLEMS:
        expect[f'assets/{NS}/textures/font/gui/emblema_{e}.png'] = (12, 12)
    for i in FONT_ICONS:
        expect[f'assets/{NS}/textures/font/icons/{i}.png'] = (8, 8)
    for c in ('generic_54', 'inventory', 'hopper', 'dispenser'):
        expect[f'assets/minecraft/textures/gui/container/{c}.png'] = (256, 256)
    expect['assets/minecraft/textures/gui/sprites/container/slot.png'] = (18, 18)
    for st in TOOLTIPS:
        expect[f'assets/{NS}/textures/gui/sprites/tooltip/{st}_background.png'] = (100, 100)
        expect[f'assets/{NS}/textures/gui/sprites/tooltip/{st}_frame.png'] = (100, 100)
    expect[f'assets/{NS}/textures/entity/npc/guardiano.png'] = (64, 64)
    expect[f'assets/{NS}/textures/item/logo.png'] = (192, 192 * 12)
    expect[f'assets/{NS}/textures/item/logo_statico.png'] = (192, 192)
    for rel, size in expect.items():
        if rel not in files:
            errs.append(f'manca {rel}')
        elif Image.open(files[rel]).size != size:
            errs.append(f'dimensioni {rel}: {Image.open(files[rel]).size} != {size}')
    # geometria dei container invariata: stessa maschera alpha e stessa struttura di classi
    for c in ('generic_54', 'inventory', 'hopper', 'dispenser'):
        new = Image.open(files[f'assets/minecraft/textures/gui/container/{c}.png']).convert('RGBA')
        old = vimg(f'assets/minecraft/textures/gui/container/{c}.png')
        if list(new.getchannel('A').getdata()) != list(old.getchannel('A').getdata()):
            errs.append(f'container {c}: alpha diversa dal vanilla')
        # ogni pixel slot vanilla (D/S/W interni) deve restare uno dei colori degli slot
        cls, outline, dist = classify(old)
        npx = new.load()
        for (x, y), k in cls.items():
            if k in ('D', 'S') and dist.get((x, y), 0) > 3:
                if npx[x, y] not in (SLOT_EDGE, SLOT_IN, SLOT_IN2):
                    errs.append(f'container {c}: pixel slot alterato {(x, y)}')
                    break
    # cornici dei font: niente pixel dentro la griglia slot
    for n in range(1, 7):
        im = Image.open(files[f'assets/{NS}/textures/font/gui/cornice_{n}.png']).convert('RGBA')
        a = im.load()
        bad = [(x, y) for y in range(17, 17 + n * 18) for x in range(7, 169) if a[x, y][3] != 0]
        if bad:
            errs.append(f'cornice_{n}: {len(bad)} pixel dentro la griglia degli slot')
    # skin NPC: zone non usate del formato devono essere trasparenti, corpo opaco
    sk = Image.open(files[f'assets/{NS}/textures/entity/npc/guardiano.png']).convert('RGBA')
    for part, fcs in SKIN_FACES.items():
        for fn_, (x0, y0, w, h) in fcs.items():
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    if sk.getpixel((x, y))[3] != 255:
                        errs.append(f'skin: pixel non opaco nel layer base {part}/{fn_} {(x, y)}')
                        break
    # tutti gli id richiesti
    need = {
        'paper': GUI_IDS + ['quantum', 'lucky', 'riempi', 'riempi_oro', 'vuoto', 'logo', 'logo_statico'] +
        ['piccone_' + s for s in SETS],
        'compass': ['selettore'], 'nether_star': ['menu'],
        'black_stained_glass_pane': ['riempi', 'riempi_oro', 'vuoto'],
        'gray_stained_glass_pane': ['riempi', 'riempi_oro', 'vuoto'],
    }
    for p in PICKAXES:
        need[p] = ['piccone_' + s for s in SETS] + ['tier_pepita', 'tier_quantum', 'piccone_pepita', 'piccone_pane']
    for piece in PIECES:
        need[f'leather_{piece}'] = [f'{s}_{piece}' for s in SETS]
    for item, ids in need.items():
        rel = f'assets/minecraft/items/{item}.json'
        if rel not in files:
            errs.append(f'manca {rel}')
            continue
        d = json.load(open(files[rel]))
        whens = {c['when'] for c in d['model']['cases']}
        miss = [i for i in ids if i not in whens]
        if miss:
            errs.append(f'{item}: case mancanti {miss}')
    return errs, njson, len(files)


# =============================================================== anteprima
def label(d, xy, text, col=(235, 235, 235, 255)):
    d.text(xy, text, fill=col)


def mock_tooltip(style_imgs, w, h, lines, title_col):
    bg, fr = style_imgs
    W, H = w + 24, h + 24
    canvas = nine_slice(bg, W, H, 9)
    canvas.alpha_composite(nine_slice(fr, W, H, 10, True))
    d = ImageDraw.Draw(canvas)
    y = 12
    for i, t in enumerate(lines):
        d.text((12, y), t, fill=title_col if i == 0 else (200, 200, 210, 255))
        y += 10
    return canvas


def make_preview(path, parts):
    S = 4
    W = 1600
    sheet = Image.new('RGBA', (W, 3400), hexc('1e1b18'))
    d = ImageDraw.Draw(sheet)
    y = 10
    label(d, (10, y), 'PEPITA v2 - anteprima (icone 16x16 @4x su slot nuovo, poi 1x)')
    y += 18
    # icone GUI
    allic = parts['icons']
    for i, (name, im) in enumerate(allic):
        x = 10 + (i % 12) * 130
        yy = y + (i // 12) * 100
        d.rectangle((x - 4, yy - 4, x + 16 * S + 3, yy + 16 * S + 3), fill=SLOT_IN)
        sheet.alpha_composite(im.resize((16 * S, 16 * S), Image.NEAREST), (x, yy))
        sheet.alpha_composite(im, (x + 16 * S + 8, yy))
        label(d, (x, yy + 16 * S + 6), name[:18])
    y += ((len(allic) + 11) // 12) * 100 + 10
    # container
    label(d, (10, y), 'Container (2x) - vanilla vs Pepita')
    y += 16
    x = 10
    for name in ('generic_54', 'inventory', 'hopper', 'dispenser'):
        img, v = parts['containers'][name]
        bb = v.getchannel('A').getbbox()
        c = img.crop(bb)
        vc = v.crop(bb)
        if name == 'generic_54':
            sheet.alpha_composite(vc.resize((c.width * 2, c.height * 2), Image.NEAREST), (x, y))
            x += c.width * 2 + 10
        cc = c.resize((c.width * 2, c.height * 2), Image.NEAREST)
        sheet.alpha_composite(cc, (x, y))
        # testo vanilla per verificare la leggibilità
        dd = ImageDraw.Draw(sheet)
        if name == 'generic_54':
            dd.text((x + 16, y + 10), 'Miniere', fill=hexc('ffd64a'))
            dd.text((x + 16, y + 129 * 2 + 2), 'Inventario', fill=hexc('404040'))
            # riempitivi + cornice glifo sopra il container (6 righe)
            gl = parts['frames'][6]
            tile = parts['riempi']
            tile_o = parts['riempi_oro']
            comp = c.copy()
            for r in range(6):
                for col in range(9):
                    t = tile_o if (r in (0, 5) or col in (0, 8)) else tile
                    tt = t.resize((18, 18), Image.NEAREST)
                    if (r, col) in ((2, 3), (2, 5), (3, 4)):
                        continue
                    comp.alpha_composite(tt, (7 + col * 18, 17 + r * 18))
            comp.alpha_composite(gl, (0, 0))
            em = parts['emblems']['miniere']
            comp.alpha_composite(em, (8, 4))
            dc = ImageDraw.Draw(comp)
            for k, ic in enumerate([parts['icons_map']['gui_miniere'], parts['icons_map']['gui_lucky'], parts['icons_map']['gui_quantum']]):
                comp.alpha_composite(ic, (8 + (3 + k) * 18 + (0 if k != 1 else -18), 18 + 2 * 18 + (18 if k == 1 else 0)))
            big = comp.resize((comp.width * 2, comp.height * 2), Image.NEAREST)
            ImageDraw.Draw(big).text((22 * 2 + 4, 6 * 2 - 2), 'Miniere', fill=hexc('ffd64a'))
            ImageDraw.Draw(big).text((16, 129 * 2 + 2), 'Inventario', fill=hexc('404040'))
            sheet.alpha_composite(big, (x + cc.width + 10, y))
            x += cc.width * 2 + 30
            y_after = y + cc.height + 10
        elif name == 'inventory':
            dd.text((x + 97 * 2, y + 8 * 2), 'Crafting', fill=hexc('404040'))
            sheet.alpha_composite(cc, (10, y_after))
            x2 = 10 + cc.width + 10
        else:
            sheet.alpha_composite(cc, (x2, y_after))
            dd.text((x2 + 16, y_after + 12), name, fill=hexc('404040'))
            x2 += cc.width + 10
    # cancella i duplicati disegnati a destra della riga (inventory/hopper/dispenser già disegnati sotto)
    y = y_after + 340
    # slot sprite
    label(d, (10, y), 'slot.png (8x) | riempi / riempi_oro tassellati 4x4 (scala gui 1.125 -> 18px) | font icone 8x8 @6x | emblemi 12x12 @6x')
    y += 16
    sheet.alpha_composite(parts['slot'].resize((18 * 8, 18 * 8), Image.NEAREST), (10, y))
    xx = 170
    for t in (parts['riempi'], parts['riempi_oro']):
        tt = t.resize((18, 18), Image.NEAREST)
        pan = Image.new('RGBA', (72, 72))
        for i in range(4):
            for j in range(4):
                pan.alpha_composite(tt, (i * 18, j * 18))
        sheet.alpha_composite(pan.resize((144, 144), Image.NEAREST), (xx, y))
        xx += 154
    for i, (n, im) in enumerate(parts['font_icons'].items()):
        sheet.alpha_composite(im.resize((48, 48), Image.NEAREST), (xx + (i % 5) * 56, y + (i // 5) * 72))
        label(d, (xx + (i % 5) * 56, y + (i // 5) * 72 + 50), n[:8])
    xx += 5 * 56 + 10
    for i, (n, im) in enumerate(parts['emblems'].items()):
        sheet.alpha_composite(im.resize((72, 72), Image.NEAREST), (xx + (i % 9) * 80, y + (i // 9) * 92))
        label(d, (xx + (i % 9) * 80, y + (i // 9) * 92 + 74), n[:11])
    y += 200
    # cornici dei menu
    label(d, (10, y), 'Glifi cornice N=1..6 (2x, sopra il generic_54)')
    y += 16
    xx = 10
    gen54 = parts['containers']['generic_54'][0]
    for n in range(1, 7):
        gl = parts['frames'][n]
        bgc = gen54.crop((0, 0, 176, n * 18 + 17)).copy()
        bgc.alpha_composite(gl)
        sheet.alpha_composite(bgc.resize((176 * 2 // 1, (n * 18 + 17) * 2), Image.NEAREST), (xx, y))
        xx += 176 * 2 + 10
        if n == 4:
            xx = 10
            y += 6 * 18 * 2 + 50
    y += 6 * 18 * 2 + 50
    # tooltip
    label(d, (10, y), 'Tooltip (nine-slice, 2x) a dimensioni diverse')
    y += 16
    xx = 10
    for st, col in (('oro', hexc('ffd64a')), ('quantum', hexc('7af0ff')), ('leggendario', hexc('c98cff'))):
        for (w, h, lines) in ((150, 48, ['Corazza del Custode', 'Vantaggi:', ' +24% Soldi', ' +20% Token']),
                              (90, 18, ['Pepita d\'oro', 'x64']), (220, 90, ['Piccone Eclisse', 'Livello 120', 'Fortuna X', 'Efficienza L', '', 'Clicca per migliorare', 'il piccone'])):
            tt = mock_tooltip(parts['tooltips'][st], w, h, lines, col)
            bgw = Image.new('RGBA', tt.size, hexc('3a5a2a'))
            bgw.alpha_composite(tt)
            big = bgw.resize((tt.width * 2, tt.height * 2), Image.NEAREST)
            if xx + big.width > W:
                xx = 10
                y += 260
            sheet.alpha_composite(big, (xx, y))
            xx += big.width + 10
        xx = 10
        y += 260
    # blocchi (frame 0 + 3x3 tassellato)
    label(d, (10, y), 'Blocchi: lucky (sponge), minerale quantico, nucleo top/side - frame e tassellati')
    y += 16
    xx = 10
    for n, im in parts['blocks'].items():
        nf = im.height // 16
        for f in range(0, nf, max(1, nf // 4)):
            fr = im.crop((0, 16 * f, 16, 16 * f + 16))
            sheet.alpha_composite(fr.resize((64, 64), Image.NEAREST), (xx, y))
            xx += 68
        fr = im.crop((0, 0, 16, 16))
        t = Image.new('RGBA', (48, 48))
        for i in range(3):
            for j in range(3):
                t.paste(fr, (i * 16, j * 16))
        sheet.alpha_composite(t.resize((96, 96), Image.NEAREST), (xx, y))
        xx += 110
    y += 110
    # NPC + logo + tier
    label(d, (10, y), 'NPC guardiano (skin 64x64 @4x, vista frontale/posteriore @8x) | logo animato frame 0/3/6 | logo font 40px | tier piccone | bussola')
    y += 16
    sk = parts['npc']
    sheet.alpha_composite(sk.resize((256, 256), Image.NEAREST), (10, y))
    sheet.alpha_composite(skin_flat(sk).resize((128, 256), Image.NEAREST), (280, y))
    sheet.alpha_composite(skin_flat(sk, True).resize((128, 256), Image.NEAREST), (420, y))
    xx = 560
    lf = parts['logo']
    for f in (0, 3, 6):
        sheet.alpha_composite(lf.crop((0, 192 * f, 192, 192 * f + 192)), (xx, y))
        xx += 200
    big = parts['logo_font']
    sheet.alpha_composite(big.resize((big.width // 4 * 2, 80), Image.LANCZOS), (xx, y))
    sheet.alpha_composite(big.resize((big.width // 4, 40), Image.NEAREST), (xx, y + 90))
    xx += 100
    for im in parts['tiers']:
        sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), (xx, y + 140))
        xx += 72
    y += 270
    sheet = sheet.crop((0, 0, W, y + 10))
    sheet.save(path)


# =============================================================== MAIN
def run(out_dir, by_item, pick_cases):
    global OUT
    OUT = out_dir
    copy_user_assets()

    # ---- picconi: vecchie skin 2D (pick_cases da gen.py) + 3D + tier
    pick = list(pick_cases)
    for s in SETS:
        pick.append(case('piccone_' + s, f'{NS}:item/{s}'))
    tp = tier_pepita()
    save(tp, f'assets/{NS}/textures/item/tier_pepita.png')
    generated_model('tier_pepita', parent='minecraft:item/handheld')
    tq = tier_quantum()
    save(tq, f'assets/{NS}/textures/item/tier_quantum.png')
    anim_meta(f'assets/{NS}/textures/item/tier_quantum.png', 2)
    generated_model('tier_quantum', parent='minecraft:item/handheld')
    pick.append(case('tier_pepita', f'{NS}:item/tier_pepita'))
    pick.append(case('tier_quantum', f'{NS}:item/tier_quantum'))
    for p in PICKAXES:
        by_item.setdefault(p, []).extend(copy.deepcopy(pick))
    paper = by_item.setdefault('paper', [])
    paper.extend(copy.deepcopy(pick))

    # ---- armature
    for piece in PIECES:
        cs = by_item.setdefault(f'leather_{piece}', [])
        for s in SETS:
            cs.append(case(f'{s}_{piece}', f'{NS}:item/{s}_{piece}'))

    # ---- icone GUI (paper)
    icon_imgs = []
    icon_map = {}
    for gid in GUI_IDS:
        im = icons.ICONS[gid]()
        assert im.size == (16, 16), gid
        save(im, f'assets/{NS}/textures/item/{gid}.png')
        paper.append(case(gid, generated_model(gid)))
        icon_imgs.append((gid, im))
        icon_map[gid] = im
    qa = icons.quantum_anim()
    save(qa, f'assets/{NS}/textures/item/quantum.png')
    anim_meta(f'assets/{NS}/textures/item/quantum.png', 2)
    paper.append(case('quantum', generated_model('quantum')))
    icon_imgs.append(('quantum (anim)', qa.crop((0, 0, 16, 16))))
    lk = icons.i_lucky(False)
    save(lk, f'assets/{NS}/textures/item/lucky.png')
    paper.append(case('lucky', generated_model('lucky')))
    icon_imgs.append(('lucky', lk))
    cp = compass_selettore()
    save(cp, f'assets/{NS}/textures/item/selettore.png')
    by_item.setdefault('compass', []).append(case('selettore', generated_model('selettore')))
    icon_imgs.append(('selettore (compass)', cp))
    icon_imgs.append(('tier_pepita', tp))
    icon_imgs.append(('tier_quantum', tq.crop((0, 0, 16, 16))))

    # ---- riempitivi
    write_fillers()
    for item in ('black_stained_glass_pane', 'gray_stained_glass_pane', 'paper'):
        cs = by_item.setdefault(item, [])
        for n in ('riempi', 'riempi_oro', 'vuoto'):
            cs.append(case(n, f'{NS}:item/{n}'))

    # ---- container, font, tooltip, blocchi, npc, logo
    conts = write_containers()
    frames, ems, ics, logo_big = write_fonts()
    tts = write_tooltips()
    blk = write_blocks()
    npc = npc_guardiano()
    save(npc, f'assets/{NS}/textures/entity/npc/guardiano.png')
    logo = write_logo()
    paper.append(case('logo', f'{NS}:item/logo'))
    paper.append(case('logo_statico', f'{NS}:item/logo_statico'))

    write_items(by_item)

    errs, njson, nfiles = validate(out_dir, by_item)
    parts = {
        'icons': icon_imgs, 'icons_map': icon_map, 'containers': {k: v for k, v in conts.items() if k != 'slot'},
        'slot': conts['slot'][0], 'riempi': stone_dark_tile(False), 'riempi_oro': stone_dark_tile(True),
        'font_icons': ics, 'emblems': ems, 'frames': frames, 'tooltips': tts, 'blocks': blk, 'npc': npc,
        'logo': logo, 'logo_font': logo_big, 'tiers': [tp, tq.crop((0, 0, 16, 16)), cp],
    }
    make_preview(os.path.join(HERE, 'preview2.png'), parts)
    print(f'validazione: {njson} JSON/mcmeta ok, {nfiles} file, {len(errs)} errori')
    for e in errs:
        print('  ERRORE', e)
    if errs:
        raise SystemExit('validazione fallita')
    return parts
