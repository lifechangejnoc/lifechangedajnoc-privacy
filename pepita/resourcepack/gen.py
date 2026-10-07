#!/usr/bin/env python3
"""Generatore del resource pack di Pepita: skin dei picconi, armature, oggetti, logo."""
import json, math, os, random, shutil, colorsys, hashlib, zipfile
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), 'out')
NS = 'pepita'


def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def ramp(cols, t):
    t = max(0.0, min(1.0, t))
    if len(cols) == 1:
        return cols[0]
    f = t * (len(cols) - 1)
    i = min(int(f), len(cols) - 2)
    return lerp(cols[i], cols[i + 1], f - i)


def mul(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def hnoise(x, y, seed=0):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def save(img, rel):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def wjson(obj, rel):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


# ---------------------------------------------------------------- PICCONI
# Sagoma del piccone vanilla: H = testa (con livello di luce), S = manico
PICK = [
    "................",
    "................",
    "......00000.....",
    ".....012332045..",
    "......06663378..",
    "..........4236..",
    ".........458326.",
    "........478.636.",
    ".......458..636.",
    "......478...626.",
    ".....458....616.",
    "....478......6..",
    "...458..........",
    "..478...........",
    "..88............",
    "................",
]
HEAD_SHADE = {'6': 0.0, '0': 0.18, '3': 0.55, '2': 0.72, '1': 1.0}
HANDLE_SHADE = {'8': 0.0, '4': 0.35, '5': 0.62, '7': 1.0}


def pick_cells():
    for y, row in enumerate(PICK):
        for x, ch in enumerate(row):
            if ch in HEAD_SHADE:
                yield x, y, 'head', HEAD_SHADE[ch]
            elif ch in HANDLE_SHADE:
                yield x, y, 'handle', HANDLE_SHADE[ch]


def make_pick(head, handle, frames=1):
    img = Image.new('RGBA', (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        for x, y, part, s in pick_cells():
            c = head(x, y, s, f) if part == 'head' else handle(x, y, s, f)
            if c is not None:
                img.putpixel((x, y + 16 * f), c)
    return img


def wood(cols):
    return lambda x, y, s, f: ramp(cols, s)


def skin_pepita():
    gold = [hexc('5c3a00'), hexc('a86b00'), hexc('e0a526'), hexc('ffd54a'), hexc('fff2a8')]
    sparkles = [(6, 3), (9, 4), (12, 6), (13, 9), (8, 2)]

    def head(x, y, s, f):
        c = ramp(gold, s)
        if hnoise(x, y, 5) > 0.72 and s > 0.3:
            c = ramp(gold, min(1, s + 0.25))
        sx, sy = sparkles[f % len(sparkles)]
        if (x, y) == (sx, sy):
            c = hexc('ffffff')
        return c
    handle = wood([hexc('2a1a08'), hexc('4a2f12'), hexc('d9a21b'), hexc('ffd54a')])
    return make_pick(head, handle, 5), 4


def skin_pizza():
    crust = [hexc('6b3a12'), hexc('a8621f'), hexc('d98c3a')]

    def head(x, y, s, f):
        if s <= 0.2:
            return ramp(crust, 0.4 + s * 2)
        n = hnoise(x, y, 11)
        if n > 0.6:
            return hexc('fff3c4') if n > 0.8 else hexc('f5e2a0')  # mozzarella
        if n < 0.14:
            return hexc('2e7d32')  # basilico
        return ramp([hexc('9b1c1c'), hexc('c62828'), hexc('e53935')], s)
    handle = wood([hexc('7a4a1a'), hexc('b07a3a'), hexc('e0b064'), hexc('f3d08e')])
    return make_pick(head, handle), 0


def skin_arcobaleno():
    def head(x, y, s, f):
        hue = ((x + y) / 26.0 + f / 8.0) % 1.0
        r, g, b = colorsys.hsv_to_rgb(hue, 0.75, 0.35 + 0.65 * s)
        return (int(r * 255), int(g * 255), int(b * 255), 255)

    def handle(x, y, s, f):
        return ramp([hexc('5a5a5a'), hexc('bdbdbd'), hexc('ffffff')], s)
    return make_pick(head, handle, 8), 2


def skin_lava():
    def head(x, y, s, f):
        n = hnoise(x, (y + f) % 16, 21)
        if s < 0.25:
            return hexc('1e0f08')
        if n > 0.55:
            return ramp([hexc('c43c00'), hexc('ff7a00'), hexc('ffd000')], (n - 0.55) / 0.45)
        return ramp([hexc('24140c'), hexc('3b2418')], s)

    def handle(x, y, s, f):
        c = ramp([hexc('0c0614'), hexc('1a0f26'), hexc('3a2350')], s)
        if hnoise(x, y + f, 3) > 0.85:
            c = hexc('8a4fd6')
        return c
    return make_pick(head, handle, 8), 3


def skin_ghiaccio():
    ice = [hexc('1d5f8a'), hexc('4fb3d9'), hexc('9fe8ff'), hexc('e8fbff')]

    def head(x, y, s, f):
        c = ramp(ice, s)
        if hnoise(x, y, 7) > 0.8:
            c = hexc('ffffff')
        return c
    handle = wood([hexc('4a6a80'), hexc('8fb3c9'), hexc('d7ecf7'), hexc('ffffff')])
    return make_pick(head, handle), 0


def skin_neon():
    def head(x, y, s, f):
        pulse = 0.55 + 0.45 * math.sin(f / 4.0 * 2 * math.pi)
        if s < 0.25:
            return mul(hexc('ff2bd6'), 0.6 + 0.4 * pulse)
        if s > 0.9:
            return mul(hexc('2bf5ff'), 0.7 + 0.3 * (1 - pulse))
        return ramp([hexc('120018'), hexc('2a0a3a')], s)

    def handle(x, y, s, f):
        if s > 0.9:
            return hexc('2bf5ff')
        return ramp([hexc('050008'), hexc('1a1024'), hexc('3a2a4a')], s)
    return make_pick(head, handle, 4), 3


def skin_galassia():
    stars = [(6, 3), (9, 3), (11, 6), (13, 8), (12, 4), (8, 2), (14, 10), (7, 4)]

    def head(x, y, s, f):
        c = ramp([hexc('0b0420'), hexc('2a1060'), hexc('5b2aa0'), hexc('d17bff')], s * 0.9 + hnoise(x, y, 9) * 0.1)
        for i, (sx, sy) in enumerate(stars):
            if (x, y) == (sx, sy) and (i + f) % 3 == 0:
                return hexc('fff6b7')
        return c

    def handle(x, y, s, f):
        return ramp([hexc('0b0420'), hexc('22104a'), hexc('4a2a80')], s)
    return make_pick(head, handle, 6), 4


def skin_drago():
    bone = [hexc('6b6450'), hexc('a39b7e'), hexc('d6cfb5'), hexc('f3efe0')]
    head = lambda x, y, s, f: ramp(bone, s)
    handle = wood([hexc('2a0606'), hexc('5a1010'), hexc('8e1b1b'), hexc('c62828')])
    return make_pick(head, handle), 0


def skin_caramella():
    def head(x, y, s, f):
        stripe = ((x + y) // 2) % 2 == 0
        base = hexc('e53935') if stripe else hexc('fafafa')
        return mul(base, 0.55 + 0.45 * s)
    handle = wood([hexc('9e9e9e'), hexc('e0e0e0'), hexc('ffffff'), hexc('ffffff')])
    return make_pick(head, handle), 0


def skin_pane():
    crust = [hexc('5a2e0c'), hexc('9a5a22'), hexc('c68642'), hexc('e3b778')]

    def head(x, y, s, f):
        c = ramp(crust, s)
        if s > 0.4 and (x - y) % 4 == 0:
            c = hexc('f3dcae')  # tagli del filone
        elif s > 0.5 and hnoise(x, y, 19) > 0.8:
            c = hexc('fbf1dc')  # farina
        return c
    handle = wood([hexc('3b2410'), hexc('6b4a26'), hexc('9c7140'), hexc('c49a62')])
    return make_pick(head, handle), 0


def skin_forchettone():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    silver_d, silver, silver_l = hexc('6f7880'), hexc('b8c0c8'), hexc('eef2f5')
    # manico
    for i in range(0, 7):
        img.putpixel((i, 14 - i), silver_d)
        img.putpixel((i + 1, 14 - i), silver if i % 3 else silver_l)
    # rebbi della forchetta
    for (x, y) in [(12, 2), (13, 1), (13, 3), (14, 2), (14, 4), (15, 3)]:
        img.putpixel((x, y), silver_l if (x + y) % 2 else silver)
    # matassa di spaghetti al pomodoro
    cx, cy, r = 10.0, 5.5, 3.7
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d <= r:
                ang = math.atan2(dy, dx)
                band = int((ang / (2 * math.pi) * 3 + d * 0.9)) % 2
                c = hexc('f2d16b') if band else hexc('d4a93a')
                if d > r - 0.8:
                    c = hexc('b8862a')
                if math.hypot(x - 9.0, y - 4.2) < 1.7:
                    c = hexc('c62828') if hnoise(x, y, 4) > 0.3 else hexc('8e1b1b')
                if (x, y) in [(10, 3), (8, 5)]:
                    c = hexc('2e7d32')
                img.putpixel((x, y), c)
    # filo di spaghetto che pende
    for (x, y) in [(7, 8), (7, 9), (8, 10)]:
        img.putpixel((x, y), hexc('f2d16b'))
    return img, 0


PICK_SKINS = {
    'piccone_pepita': skin_pepita, 'piccone_pizza': skin_pizza, 'piccone_forchettone': skin_forchettone,
    'piccone_arcobaleno': skin_arcobaleno, 'piccone_lava': skin_lava, 'piccone_ghiaccio': skin_ghiaccio,
    'piccone_neon': skin_neon, 'piccone_galassia': skin_galassia, 'piccone_drago': skin_drago,
    'piccone_caramella': skin_caramella, 'piccone_pane': skin_pane,
}


# ---------------------------------------------------------------- OGGETTI
def blank():
    return Image.new('RGBA', (16, 16), (0, 0, 0, 0))


def disc(img, cx, cy, r, colfn):
    for y in range(img.height):
        for x in range(img.width):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                c = colfn(x, y, d / r)
                if c:
                    img.putpixel((x, y), c)


def item_bomba(kind):
    img = blank()
    if kind == 'normale':
        body = [hexc('0d0d0d'), hexc('2b2b2b'), hexc('4a4a4a'), hexc('7a7a7a')]
        r, cx, cy = 5.2, 7.5, 9.5
    elif kind == 'grande':
        body = [hexc('1a0a00'), hexc('5a2a00'), hexc('a84a00'), hexc('ff9f43')]
        r, cx, cy = 6.0, 7.5, 9.0
    else:
        body = [hexc('3a3a00'), hexc('8a8a00'), hexc('d8d81a'), hexc('fffe6a')]
        r, cx, cy = 6.0, 7.5, 9.0

    def col(x, y, t):
        light = 1 - math.hypot(x + 0.5 - (cx - 2), y + 0.5 - (cy - 2)) / (r * 1.6)
        c = ramp(body, max(0, light))
        if t > 0.88:
            c = mul(c, 0.6)
        if kind == 'grande' and abs(y - cy) < 1:
            c = hexc('c62828')
        if kind == 'nucleare':
            ang = math.atan2(y + 0.5 - cy, x + 0.5 - cx)
            dd = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 1.3 < dd < 4.6 and int((ang + math.pi) / (math.pi / 3)) % 2 == 0:
                c = hexc('111111')
            if dd <= 1.2:
                c = hexc('111111')
        return c
    disc(img, cx, cy, r, col)
    for (x, y) in [(10, 4), (11, 3), (12, 2)]:
        img.putpixel((x, y), hexc('8a6a3a'))
    img.putpixel((13, 1), hexc('ffd000'))
    img.putpixel((14, 0), hexc('ff7a00'))
    img.putpixel((13, 0), hexc('fff2a8'))
    img.putpixel((14, 1), hexc('ff7a00'))
    return img


def item_chiave(main, dark, light):
    img = blank()
    # anello
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 4.5, y + 0.5 - 4.5)
            if 1.6 <= d <= 3.6:
                img.putpixel((x, y), light if d < 2.4 else main if d < 3.1 else dark)
    # gambo
    for i in range(6, 14):
        img.putpixel((i, i), main)
        img.putpixel((i + 1, i), dark)
        if i - 1 >= 0:
            img.putpixel((i, i - 1), light if i % 2 else main)
    # denti
    for (x, y) in [(10, 12), (11, 13), (12, 11), (13, 12)]:
        img.putpixel((x, y), dark)
    return img


def item_gemma():
    img = blank()
    pts = {}
    for y in range(16):
        for x in range(16):
            cx, cy = 7.5, 8.0
            dx, dy = abs(x + 0.5 - cx), y + 0.5 - cy
            # diamante: parte alta trapezio, parte bassa triangolo
            if -5 <= dy <= -1 and dx <= 6 - (dy + 5) * 0.2 + 1:
                inside = dx <= 4.5 + (dy + 5) * 0.4
            elif -1 < dy <= 6:
                inside = dx <= 6.2 * (1 - (dy + 1) / 7.2)
            else:
                inside = False
            if inside:
                facet = (x // 3 + (y // 2)) % 3
                base = [hexc('7a2aa8'), hexc('b45ae0'), hexc('e6a8ff')][facet]
                if dy < -1:
                    base = lerp(base, hexc('ffffff'), 0.25)
                pts[(x, y)] = base
    for (x, y), c in pts.items():
        edge = any((x + ox, y + oy) not in pts for ox, oy in [(1, 0), (-1, 0), (0, 1), (0, -1)])
        img.putpixel((x, y), mul(c, 0.55) if edge else c)
    img.putpixel((5, 4), hexc('ffffff'))
    img.putpixel((6, 4), hexc('ffffff'))
    return img


def item_pepita(frames=6):
    img = Image.new('RGBA', (16, 16 * frames), (0, 0, 0, 0))
    gold = [hexc('6b4300'), hexc('a86b00'), hexc('e0a526'), hexc('ffd54a'), hexc('fff2a8')]
    cells = {}
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.2, (y + 0.5 - 8.8) / 5.0
            v = dx * dx + dy * dy + (hnoise(x, y, 77) - 0.5) * 0.45
            if v <= 1.0:
                cells[(x, y)] = v
    for f in range(frames):
        for (x, y), v in cells.items():
            light = 1 - math.hypot(x - 5, y - 6) / 11
            c = ramp(gold, max(0, min(1, light * 1.1 + (hnoise(x, y, 3) - 0.5) * 0.25)))
            edge = any((x + ox, y + oy) not in cells for ox, oy in [(1, 0), (-1, 0), (0, 1), (0, -1)])
            if edge:
                c = mul(c, 0.62)
            img.putpixel((x, y + 16 * f), c)
        # scintilla che si sposta
        sp = [(5, 5), (8, 4), (11, 6), (6, 9), (10, 9), (7, 6)][f % 6]
        for ox, oy, cc in [(0, 0, 'ffffff'), (1, 0, 'fff6b7'), (-1, 0, 'fff6b7'), (0, 1, 'fff6b7'), (0, -1, 'fff6b7')]:
            px, py = sp[0] + ox, sp[1] + oy
            if (px, py) in cells:
                img.putpixel((px, py + 16 * f), hexc(cc))
    return img


GLYPHS = {
    'P': ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    'E': ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    'I': ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"],
    'T': ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    'A': [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
}


def item_menu():
    img = blank()
    gold = [hexc('7a4a00'), hexc('c78a12'), hexc('ffd54a'), hexc('fff2a8')]

    def col(x, y, t):
        light = 1 - math.hypot(x - 5, y - 5) / 14
        c = ramp(gold, light)
        if t > 0.86:
            c = mul(c, 0.55)
        return c
    disc(img, 8, 8, 7.4, col)
    g = GLYPHS['P']
    for r, row in enumerate(g):
        for cidx, ch in enumerate(row):
            if ch == '#':
                img.putpixel((6 + cidx, 4 + r), hexc('5c3200'))
    return img


def make_logo():
    scale = 2
    text = 'PEPITA'
    w = (len(text) * 6 - 1) * scale + 4
    h = 7 * scale + 4
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    mask = set()
    for i, ch in enumerate(text):
        for r, row in enumerate(GLYPHS[ch]):
            for c, v in enumerate(row):
                if v == '#':
                    for sy in range(scale):
                        for sx in range(scale):
                            mask.add((2 + (i * 6 + c) * scale + sx, 2 + r * scale + sy))
    # contorno
    for (x, y) in list(mask):
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1, 2):
                p = (x + ox, y + oy)
                if p not in mask and 0 <= p[0] < w and 0 <= p[1] < h:
                    img.putpixel(p, hexc('3a2200'))
    for (x, y) in mask:
        t = (y - 2) / (7 * scale)
        img.putpixel((x, y), ramp([hexc('fff2a8'), hexc('ffd54a'), hexc('ffa726'), hexc('e07b00')], t))
    return img


# ---------------------------------------------------------------- ARMATURE
FACES = {
    'head': {'top': (8, 0, 8, 8), 'bottom': (16, 0, 8, 8), 'right': (0, 8, 8, 8), 'front': (8, 8, 8, 8), 'left': (16, 8, 8, 8), 'back': (24, 8, 8, 8)},
    'body': {'top': (20, 16, 8, 4), 'bottom': (28, 16, 8, 4), 'right': (16, 20, 4, 12), 'front': (20, 20, 8, 12), 'left': (28, 20, 4, 12), 'back': (32, 20, 8, 12)},
    'arm': {'top': (44, 16, 4, 4), 'bottom': (48, 16, 4, 4), 'right': (40, 20, 4, 12), 'front': (44, 20, 4, 12), 'left': (48, 20, 4, 12), 'back': (52, 20, 4, 12)},
    'leg': {'top': (4, 16, 4, 4), 'bottom': (8, 16, 4, 4), 'right': (0, 20, 4, 12), 'front': (4, 20, 4, 12), 'left': (8, 20, 4, 12), 'back': (12, 20, 4, 12)},
}
FACE_LIGHT = {'top': 1.12, 'front': 1.0, 'back': 0.9, 'right': 0.95, 'left': 0.95, 'bottom': 0.8}


def locate(x, y):
    for part, faces in FACES.items():
        for face, (fx, fy, fw, fh) in faces.items():
            if fx <= x < fx + fw and fy <= y < fy + fh:
                return part, face, x - fx, y - fy, fw, fh
    return None


def load_mask(layer):
    import zipfile as zf
    z = zf.ZipFile('/home/claude/mcref/client262.jar')
    im = Image.open(__import__('io').BytesIO(z.read(f'assets/minecraft/textures/entity/equipment/{layer}/diamond.png'))).convert('RGBA')
    return {(x, y) for y in range(32) for x in range(64) if im.getpixel((x, y))[3] > 0}


MASK_H = None
MASK_L = None


def cap_mask():
    m = set()
    for x in range(8, 16):
        for y in range(0, 8):
            m.add((x, y))
    for x in range(0, 32):
        for y in range(8, 11):
            m.add((x, y))
    return m


def make_armor(pattern, cap=False):
    hum = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    leg = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for layer, img, mask in (('humanoid', hum, MASK_H), ('humanoid_leggings', leg, MASK_L)):
        m = set(mask)
        if layer == 'humanoid' and cap:
            m = {p for p in m if not (p[1] < 16 and p[0] < 32)} | cap_mask()
        for (x, y) in m:
            loc = locate(x, y)
            if not loc:
                continue
            part, face, u, v, fw, fh = loc
            # nel livello "humanoid" le gambe sono gli stivali
            p = 'boot' if (layer == 'humanoid' and part == 'leg') else ('pants' if layer == 'humanoid_leggings' else part)
            c = pattern(p, face, u, v, fw, fh, x, y)
            if c is None:
                continue
            c = mul(c, FACE_LIGHT[face])
            edge = (u == 0 or u == fw - 1 or v == fh - 1) and face not in ('top', 'bottom')
            if edge:
                c = mul(c, 0.84)
            img.putpixel((x, y), c)
    return hum, leg


def p_galeotto(p, face, u, v, fw, fh, x, y):
    orange, dark = hexc('f26b1d'), hexc('c9500d')
    if p == 'head':
        return dark if (face != 'top' and v == 10 - 8) else orange
    if p == 'boot':
        return hexc('444444') if v == fh - 2 else hexc('262626')
    c = orange
    if hnoise(x, y, 1) > 0.85:
        c = hexc('e8621a')
    if p == 'body' and face == 'front':
        if u in (3, 4):
            c = dark if v % 2 else hexc('d9560f')
        if 5 <= u <= 6 and 2 <= v <= 3:
            c = hexc('f5f5f5') if not (u == 6 and v == 3) else hexc('222222')
    if p == 'arm' and face not in ('top', 'bottom') and v >= fh - 2:
        c = dark
    if p == 'pants' and face == 'front' and v == 0:
        c = dark
    return c


def p_righe(p, face, u, v, fw, fh, x, y):
    if p == 'boot':
        return hexc('1a1a1a')
    vv = v if face not in ('top', 'bottom') else 0
    return hexc('1e1e1e') if (vv // 2) % 2 == 0 else hexc('f0f0f0')


def p_pepita(p, face, u, v, fw, fh, x, y):
    gold = [hexc('a86b00'), hexc('e0a526'), hexc('ffd54a'), hexc('fff2a8')]
    n = hnoise(x, y, 31)
    c = ramp(gold, 0.45 + (n - 0.5) * 0.6 - v / 40)
    if n > 0.82:
        c = hexc('fff2a8')
    elif n < 0.12:
        c = hexc('b8780a')
    if p == 'body' and face == 'front' and 2 <= u <= 5 and 3 <= v <= 6:
        c = hexc('ffffff') if (u, v) == (3, 4) else hexc('fff6b7') if hnoise(u, v, 2) > 0.5 else hexc('ffd54a')
    return c


def p_neon(p, face, u, v, fw, fh, x, y):
    base = hexc('160020')
    if face in ('top', 'bottom'):
        return hexc('2a0a3a')
    if u == 0 or u == fw - 1:
        return hexc('ff2bd6')
    if face == 'front' and u == fw // 2:
        return hexc('2bf5ff')
    if v % 4 == 0 and hnoise(x, y, 8) > 0.6:
        return hexc('2bf5ff')
    return base


def p_lava(p, face, u, v, fw, fh, x, y):
    n = hnoise(x, y, 51)
    m = hnoise(x // 2, y // 2, 52)
    if abs(m - 0.5) < 0.08:
        return hexc('ffd000') if abs(m - 0.5) < 0.03 else hexc('ff7a00')
    return hexc('2a1d17') if n < 0.5 else hexc('3b2a22')


def p_ghiaccio(p, face, u, v, fw, fh, x, y):
    ice = [hexc('3a87b5'), hexc('5fb8e0'), hexc('8fd8f2'), hexc('bfefff'), hexc('ffffff')]
    n = hnoise(x // 2, y // 2, 61)
    c = ramp(ice, 0.3 + n * 0.6 - v / 30)
    if face == 'top' or v == 0:
        c = lerp(c, hexc('ffffff'), 0.4)
    return c


def p_galassia(p, face, u, v, fw, fh, x, y):
    c = ramp([hexc('3a1d6e'), hexc('1a0b3d')], v / max(1, fh))
    n = hnoise(x, y, 71)
    if n > 0.94:
        return hexc('fff6b7')
    if n > 0.9:
        return hexc('ffffff')
    if hnoise(x // 3, y // 3, 72) > 0.82:
        c = lerp(c, hexc('d17bff'), 0.45)
    return c


def p_guardia(p, face, u, v, fw, fh, x, y):
    navy, dark = hexc('23324f'), hexc('1a2540')
    if p == 'head':
        return hexc('ffd54a') if (face != 'top' and v == 2) else navy
    if p == 'boot':
        return hexc('151515')
    c = navy if hnoise(x, y, 81) > 0.2 else dark
    if p == 'body' and face == 'front':
        if u in (3, 4) and v in (2, 5, 8):
            c = hexc('ffd54a')
        if 5 <= u <= 6 and 2 <= v <= 3:
            c = hexc('ffd54a')
        if v >= 10:
            c = hexc('111111') if not (u in (3, 4)) else hexc('ffd54a')
    if p == 'body' and face != 'front' and v >= 10:
        c = hexc('111111')
    if p == 'pants' and face in ('right', 'left') and u == fw // 2:
        c = hexc('ffd54a')
    return c


ARMOR = {
    'galeotto': (p_galeotto, True), 'righe': (p_righe, True), 'pepita': (p_pepita, False), 'neon': (p_neon, False),
    'lava': (p_lava, False), 'ghiaccio': (p_ghiaccio, False), 'galassia': (p_galassia, False), 'guardia': (p_guardia, True),
}


# ---------------------------------------------------------------- MAIN
def main():
    global MASK_H, MASK_L
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    MASK_H = load_mask('humanoid')
    MASK_L = load_mask('humanoid_leggings')

    wjson({"pack": {"description": [{"text": "PEPITA", "color": "#FFD54A", "bold": True},
                                     {"text": " • skin, armature e oggetti\n", "color": "gray"},
                                     {"text": "Il prison della Corsa all'Oro", "color": "#FFA726"}],
                    "min_format": 88, "max_format": 99}}, 'pack.mcmeta')

    previews = []
    pick_cases = []
    for name, fn in PICK_SKINS.items():
        img, ft = fn()
        save(img, f'assets/{NS}/textures/item/{name}.png')
        if img.height > 16:
            wjson({"animation": {"frametime": ft or 2, "interpolate": False}}, f'assets/{NS}/textures/item/{name}.png.mcmeta')
        wjson({"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}}, f'assets/{NS}/models/item/{name}.json')
        pick_cases.append({"when": name, "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        previews.append((name, img.crop((0, 0, 16, 16))))

    simple = {
        'menu': ('nether_star', item_menu(), None),
        'gemma': ('amethyst_shard', item_gemma(), None),
        'chiave_comune': ('tripwire_hook', item_chiave(hexc('d9d9d9'), hexc('6a6a6a'), hexc('ffffff')), None),
        'chiave_rara': ('tripwire_hook', item_chiave(hexc('55e6ff'), hexc('1e6f8a'), hexc('d8fbff')), None),
        'chiave_leggendaria': ('tripwire_hook', item_chiave(hexc('d17bff'), hexc('5a2a80'), hexc('f3d6ff')), None),
        'chiave_pepita': ('tripwire_hook', item_chiave(hexc('ffd54a'), hexc('8a5a00'), hexc('fff6b7')), None),
        'bomba_normale': ('fire_charge', item_bomba('normale'), None),
        'bomba_grande': ('fire_charge', item_bomba('grande'), None),
        'bomba_nucleare': ('fire_charge', item_bomba('nucleare'), None),
        'pepita_oro': ('gold_nugget', item_pepita(), 3),
    }
    by_item = {}
    for name, (vanilla, img, ft) in simple.items():
        save(img, f'assets/{NS}/textures/item/{name}.png')
        if img.height > 16:
            wjson({"animation": {"frametime": ft}}, f'assets/{NS}/textures/item/{name}.png.mcmeta')
        wjson({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}, f'assets/{NS}/models/item/{name}.json')
        by_item.setdefault(vanilla, []).append({"when": name, "model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        previews.append((name, img.crop((0, 0, 16, 16))))
    # i case dei picconi vanno su tutti i picconi vanilla + paper; gli items.json (uno per oggetto, con tutti
    # i case e fallback = definizione vanilla) vengono scritti da gen2.run()

    # armature
    arm_prev = []
    for name, (fn, cap) in ARMOR.items():
        hum, leg = make_armor(fn, cap)
        save(hum, f'assets/{NS}/textures/entity/equipment/humanoid/{name}.png')
        save(leg, f'assets/{NS}/textures/entity/equipment/humanoid_leggings/{name}.png')
        wjson({"layers": {"humanoid": [{"texture": f"{NS}:{name}"}], "humanoid_leggings": [{"texture": f"{NS}:{name}"}]}},
              f'assets/{NS}/equipment/{name}.json')
        arm_prev.append((name, hum, leg))

    # logo per la tab
    logo = make_logo()
    save(logo, f'assets/{NS}/textures/font/logo.png')
    wjson({"providers": [{"type": "bitmap", "file": f"{NS}:font/logo.png", "ascent": 14, "height": 18, "chars": [""]}]},
          f'assets/{NS}/font/logo.json')

    # icona del pack e del server
    nug = item_pepita(1)
    for size, path in ((128, 'pack.png'), (64, '../server-icon.png')):
        bg = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(bg)
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=size // 6, fill=hexc('1a1208'), outline=hexc('ffa726'), width=max(2, size // 32))
        n = nug.resize((int(size * 0.8), int(size * 0.8)), Image.NEAREST)
        bg.alpha_composite(n, ((size - n.width) // 2, (size - n.height) // 2 + size // 20))
        p = os.path.join(OUT, path)
        bg.save(p)

    # anteprima per il controllo
    sheet = Image.new('RGBA', (8 * 72, 6 * 72 + 4 * 140), hexc('2b2b2b'))
    for i, (name, im) in enumerate(previews):
        sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), ((i % 8) * 72 + 4, (i // 8) * 72 + 4))
    y0 = 4 * 72
    for i, (name, hum, leg) in enumerate(arm_prev):
        sheet.alpha_composite(hum.resize((128, 64), Image.NEAREST), ((i % 4) * 144, y0 + (i // 4) * 140))
        sheet.alpha_composite(leg.resize((128, 64), Image.NEAREST), ((i % 4) * 144, y0 + (i // 4) * 140 + 66))
    sheet.alpha_composite(logo.resize((logo.width * 3, logo.height * 3), Image.NEAREST), (300, y0 + 290))
    sheet.save(os.path.join(os.path.dirname(__file__), 'preview.png'))

    # Pepita v2: picconi 3D, armature, icone, riempitivi, container, font, tooltip, blocchi, NPC, logo
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen2
    gen2.run(OUT, by_item, pick_cases)

    # zip
    zpath = os.path.join(os.path.dirname(__file__), 'PepitaResourcePack.zip')
    if os.path.exists(zpath):
        os.remove(zpath)
    with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for root, _, files in os.walk(OUT):
            for f in sorted(files):
                full = os.path.join(root, f)
                rel = os.path.relpath(full, OUT)
                if rel.startswith('..'):
                    continue
                z.write(full, rel)
    sha1 = hashlib.sha1(open(zpath, 'rb').read()).hexdigest()
    print('zip', zpath, os.path.getsize(zpath), 'sha1', sha1)
    with open(os.path.join(os.path.dirname(__file__), 'PepitaResourcePack.sha1'), 'w') as f:
        f.write(sha1 + '\n')


if __name__ == '__main__':
    main()
