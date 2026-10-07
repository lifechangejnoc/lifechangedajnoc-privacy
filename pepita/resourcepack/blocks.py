"""Texture dei blocchi: Lucky Block (sponge), Minerale Quantico (budding_amethyst), Nucleo Quantico (lodestone)."""
import math
from PIL import Image
from px import hexc, lerp, mul, hnoise, MATS, sprite, add_outline, parse

QMARK = [
    ".####.",
    "##..##",
    "....##",
    "...##.",
    "..##..",
    "..##..",
    "......",
    "..##..",
]


def lucky_block(frames=16):
    out = Image.new('RGBA', (16, 16 * frames))
    outline = hexc('5a3004')
    bevel_l, bevel_d = hexc('fff0a0'), hexc('b0680a')
    inner = hexc('8a4a06')
    fld = [hexc('ffd85a'), hexc('f7bb2e'), hexc('e59c1c'), hexc('cf8414')]
    for f in range(frames):
        img = Image.new('RGBA', (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                ring = min(x, y, 15 - x, 15 - y)
                if ring == 0:
                    c = outline
                elif ring == 1:
                    c = bevel_l if (y == 1 and x < 15 - 1) or (x == 1 and y < 14) else bevel_d
                    if (x, y) in ((14, 1), (1, 14)):
                        c = hexc('e0a020')
                elif ring == 2:
                    c = inner
                else:
                    t = (x + y - 6) / 18.0
                    c = fld[min(3, max(0, int(t * 3.2)))]
                    # trama a rombi incisa (molto leggera) per il "lavorato"
                    if (x + y) % 4 == 0 and (x - y) % 4 == 0:
                        c = lerp(c, fld[3], 0.35)
                px[x, y] = c
        # borchie agli angoli
        for (cx, cy) in ((1, 1), (13, 1), (1, 13), (13, 13)):
            px[cx, cy] = hexc('fff8d8')
            px[cx + 1, cy] = hexc('ffd85a')
            px[cx, cy + 1] = hexc('ffd85a')
            px[cx + 1, cy + 1] = hexc('9a5a08')
        # piccole tacche sui lati del bordo
        for k in (6, 9):
            px[k, 1] = hexc('e0a020'); px[1, k] = hexc('e0a020')
            px[k, 14] = hexc('8a4a06'); px[14, k] = hexc('8a4a06')
        # punto interrogativo con ombra
        pulse = 0.5 + 0.5 * math.sin(f / frames * 2 * math.pi)
        qcol = lerp(hexc('fff3c0'), hexc('ffffff'), pulse)
        ox, oy = 5, 4
        for r, row in enumerate(QMARK):
            for c, ch in enumerate(row):
                if ch == '#':
                    px[ox + c + 1, oy + r + 1] = hexc('9a5206')
        for r, row in enumerate(QMARK):
            for c, ch in enumerate(row):
                if ch == '#':
                    px[ox + c, oy + r] = qcol
        # bordo luminoso del "?" (lato in alto a sinistra)
        # riflesso diagonale che attraversa il blocco
        shift = -6 + f * 3
        for y in range(16):
            for x in range(16):
                d = x + y - shift
                if 0 <= d <= 2:
                    c = px[x, y]
                    k = 0.55 if d == 1 else 0.3
                    if min(x, y, 15 - x, 15 - y) == 0:
                        k *= 0.4
                    px[x, y] = lerp(c, hexc('ffffff'), k)
        # scintille
        sp = {3: (11, 4), 4: (11, 4), 9: (4, 11), 10: (4, 11), 13: (12, 11), 14: (12, 11)}
        if f in sp:
            sx, sy = sp[f]
            big = f in (4, 10, 14)
            px[sx, sy] = hexc('ffffff')
            if big:
                for ox2, oy2 in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    px[sx + ox2, sy + oy2] = lerp(px[sx + ox2, sy + oy2], hexc('ffffff'), 0.7)
        out.paste(img, (0, 16 * f))
    return out


def tile_noise(x, y, period, cell, seed):
    """value noise tassellabile (periodo = 16)."""
    gx, gy = x / cell, y / cell
    x0, y0 = int(math.floor(gx)), int(math.floor(gy))
    fx, fy = gx - x0, gy - y0
    n = period // cell

    def v(i, j):
        return hnoise(i % n, j % n, seed)
    sx = fx * fx * (3 - 2 * fx)
    sy = fy * fy * (3 - 2 * fy)
    a = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * sx
    b = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * sx
    return a + (b - a) * sy


ROCK = [hexc('15121e'), hexc('1f1a2b'), hexc('2a2439'), hexc('383049'), hexc('4a4060')]

QCRYST = [
    "................",
    "..4.............",
    ".432.......d....",
    ".432......dcb...",
    ".432......dcb...",
    "..2.......dcb...",
    "...........b....",
    "......d.........",
    ".....dcb........",
    ".....dcb....4...",
    "......b....432..",
    "...........432..",
    "...4.......432..",
    "..432.......2...",
    "..432...........",
    "...2............",
]


def rock_base(seed=3):
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            n = tile_noise(x, y, 16, 4, seed) * 0.7 + tile_noise(x, y, 16, 2, seed + 1) * 0.3
            idx = int(n * 4.2)
            idx = max(0, min(4, idx))
            px[x, y] = ROCK[idx]
    # crepe scure
    for (x, y) in [(5, 1), (6, 2), (6, 3), (7, 4), (13, 7), (14, 8), (14, 9), (8, 13), (9, 14), (2, 8), (3, 9)]:
        px[x, y] = ROCK[0]
    # bordi di luce sulle crepe
    for (x, y) in [(4, 1), (5, 2), (5, 3), (12, 7), (13, 8), (7, 13), (1, 8)]:
        px[x, y] = ROCK[3]
    return img


def quantum_ore(frames=8):
    out = Image.new('RGBA', (16, 16 * frames))
    cy = MATS['cyan']
    mg = MATS['magenta']
    for f in range(frames):
        ph = f / frames
        pc = 0.5 + 0.5 * math.sin(ph * 2 * math.pi)
        pm = 0.5 + 0.5 * math.sin(ph * 2 * math.pi + math.pi)
        img = rock_base()
        px = img.load()
        tones = {}
        for y, row in enumerate(QCRYST):
            for x, ch in enumerate(row):
                if ch in '1234':
                    t = int(ch)
                    c = cy[1 + t]
                    c = lerp(c, cy[5], 0.35 * pc) if t >= 3 else lerp(c, cy[4], 0.3 * pc)
                    tones[(x, y)] = c
                elif ch in 'abcd':
                    t = 'abcd'.index(ch) + 1
                    c = mg[1 + t]
                    c = lerp(c, mg[5], 0.35 * pm) if t >= 3 else lerp(c, mg[4], 0.3 * pm)
                    tones[(x, y)] = c
        # alone luminoso sulla roccia vicino ai cristalli
        for y in range(16):
            for x in range(16):
                if (x, y) in tones:
                    continue
                best = None
                for (cx, cy2), c in tones.items():
                    d = abs(cx - x) + abs(cy2 - y)
                    if d <= 2 and (best is None or d < best[0]):
                        best = (d, c)
                if best:
                    d, c = best
                    k = (0.38 if d == 1 else 0.16) * (0.6 + 0.4 * (pc if c[2] > c[0] else pm))
                    if d == 1:
                        base = ROCK[0]
                        px[x, y] = lerp(base, c, k)
                    else:
                        px[x, y] = lerp(px[x, y], c, k)
        for p, c in tones.items():
            px[p] = c
        # scintilla bianca sul cristallo più grande a fasi alterne
        if f in (1, 2):
            px[1, 2] = hexc('ffffff')
        if f in (5, 6):
            px[10, 3] = hexc('ffffff')
        if f == 3:
            px[11, 10] = hexc('ffffff')
        if f == 7:
            px[2, 13] = hexc('ffffff')
        out.paste(img, (0, 16 * f))
    return out


STEEL = [hexc('15171d'), hexc('23262f'), hexc('333744'), hexc('474c5c'), hexc('626879'), hexc('8a90a3')]
BRASS = [hexc('4a2c08'), hexc('8a5a12'), hexc('c48a24'), hexc('ecc05a')]


def steel_frame(px, w=16, h=16, ring_w=2):
    for y in range(h):
        for x in range(w):
            ring = min(x, y, w - 1 - x, h - 1 - y)
            if ring == 0:
                px[x, y] = STEEL[4] if (x == 0 or y == 0) and not (x == w - 1 or y == h - 1) else STEEL[1]
            elif ring == 1:
                px[x, y] = STEEL[3] if (x == 1 or y == 1) and x < w - 2 and y < h - 2 else STEEL[2]


def nucleus_top(frames=8):
    out = Image.new('RGBA', (16, 16 * frames))
    cy = MATS['cyan']
    for f in range(frames):
        ph = f / frames
        p = 0.5 + 0.5 * math.sin(ph * 2 * math.pi)
        img = Image.new('RGBA', (16, 16))
        px = img.load()
        # piastra scura
        for y in range(16):
            for x in range(16):
                px[x, y] = STEEL[1] if (x + y) % 2 else STEEL[0]
                px[x, y] = STEEL[1]
        steel_frame(px)
        # rivetti
        for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
            px[x, y] = STEEL[5]
            px[x + (1 if x < 8 else -1), y] = STEEL[2]
        # anello di ottone e nucleo
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
                if 4.3 <= d < 5.4:
                    light = (x + y) < 15
                    px[x, y] = BRASS[3] if light and d < 4.9 else BRASS[2] if light else BRASS[1]
                elif d < 4.3:
                    t = d / 4.3
                    core = [lerp(hexc('ffffff'), cy[5], 0.3), cy[5], cy[4], cy[3], cy[2]]
                    i = int(t * 4.6 - p * 0.9)
                    i = max(0, min(4, i))
                    px[x, y] = core[i]
        # rune magenta sulle diagonali
        mg = MATS['magenta']
        rune = lerp(mg[3], mg[5], p)
        for (x, y) in ((3, 4), (4, 3), (12, 4), (11, 3), (3, 11), (4, 12), (12, 11), (11, 12)):
            px[x, y] = rune
        # tacche dell'anello (cardinali)
        for (x, y) in ((8, 2), (7, 2), (2, 7), (2, 8), (13, 7), (13, 8), (7, 13), (8, 13)):
            px[x, y] = lerp(cy[2], cy[4], p)
        out.paste(img, (0, 16 * f))
    return out


def nucleus_side(frames=8):
    out = Image.new('RGBA', (16, 16 * frames))
    cy = MATS['cyan']
    mg = MATS['magenta']
    for f in range(frames):
        ph = f / frames
        img = Image.new('RGBA', (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                px[x, y] = STEEL[1]
        # fasce alta e bassa in acciaio con rivetti
        for x in range(16):
            px[x, 0] = STEEL[4]
            px[x, 1] = STEEL[3]
            px[x, 2] = STEEL[0]
            px[x, 13] = STEEL[0]
            px[x, 14] = STEEL[3]
            px[x, 15] = STEEL[2]
        for x in (2, 13):
            px[x, 1] = STEEL[5]
            px[x, 14] = STEEL[5]
        # pannelli laterali
        for y in range(3, 13):
            px[0, y] = STEEL[3]
            px[15, y] = STEEL[0]
            px[1, y] = STEEL[2]
            px[14, y] = STEEL[2]
        # canale centrale di energia (cornice di ottone)
        for y in range(3, 13):
            px[5, y] = BRASS[2]
            px[10, y] = BRASS[1]
            for x in range(6, 10):
                # flusso che sale
                v = ((y + f * 1.5) % 8) / 8.0
                k = 0.5 + 0.5 * math.cos(v * 2 * math.pi)
                base = cy[3] if x in (7, 8) else cy[2]
                c = lerp(base, cy[5], k * (0.8 if x in (7, 8) else 0.4))
                px[x, y] = c
        for x in range(5, 11):
            px[x, 3] = BRASS[3] if x < 10 else BRASS[2]
            px[x, 12] = BRASS[1]
        # circuiti
        p = 0.5 + 0.5 * math.sin(ph * 2 * math.pi)
        line = lerp(cy[1], cy[3], p)
        for (x, y) in ((2, 5), (3, 5), (4, 5), (2, 6), (2, 7), (11, 9), (12, 9), (13, 9), (13, 8), (13, 7),
                       (2, 10), (3, 10), (4, 10), (11, 5), (12, 5), (13, 5)):
            px[x, y] = line
        for (x, y) in ((2, 8), (13, 10), (13, 4), (2, 11)):
            px[x, y] = lerp(mg[3], mg[5], p)
        out.paste(img, (0, 16 * f))
    return out
