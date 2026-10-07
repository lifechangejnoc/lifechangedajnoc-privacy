"""Icone GUI 16x16 di Pepita (pixel art disegnata a mano su griglia ASCII)."""
import math
from PIL import Image
from px import sprite, mirror, overlay, grid_from_fn, parse, render, MATS, hexc, lerp, mul, hnoise


def center16(img, dy=0):
    bb = img.getchannel('A').getbbox()
    if not bb:
        return img
    c = img.crop(bb)
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    out.alpha_composite(c, ((16 - c.width) // 2, (16 - c.height) // 2 + dy))
    return out


def T(mat):
    """digits 0-4 -> toni del materiale, 5 -> bianco."""
    d = {str(i): (mat, i) for i in range(5)}
    d['5'] = ('white', 5)
    return d


def chk(rows):
    for r in rows:
        assert len(r) == 16, (len(r), r)
    return rows


# ------------------------------------------------------------------ frecce / azioni
ARROW = chk([
    "................",
    "................",
    ".......g........",
    "......gg........",
    ".....ggg........",
    "....gggg........",
    "...gggggggggggg.",
    "..ggggggggggggg.",
    "..ggggggggggggg.",
    "...gggggggggggg.",
    "....gggg........",
    ".....ggg........",
    "......gg........",
    ".......g........",
    "................",
    "................",
])


def i_indietro():
    return center16(sprite(ARROW))


def i_avanti():
    return center16(sprite(mirror(ARROW)))


def i_chiudi():
    rows = chk([
        "................",
        "................",
        "..rr........rr..",
        "..rrr......rrr..",
        "...rrr....rrr...",
        "....rrr..rrr....",
        ".....rrrrrr.....",
        "......rrrr......",
        "......rrrr......",
        ".....rrrrrr.....",
        "....rrr..rrr....",
        "...rrr....rrr...",
        "..rrr......rrr..",
        "..rr........rr..",
        "................",
        "................",
    ])
    return center16(sprite(rows))


def i_conferma():
    rows = chk([
        "................",
        "................",
        "................",
        ".............ee.",
        "............eee.",
        "...........eee..",
        "..........eee...",
        ".ee......eee....",
        ".eee....eee.....",
        "..eee..eee......",
        "...eeeeee.......",
        "....eeee........",
        ".....ee.........",
        "................",
        "................",
        "................",
    ])
    return center16(sprite(rows))


def i_annulla():
    def f(x, y):
        d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        on_slash = abs((x - y)) <= 1 and 3 <= x <= 12
        if 4.6 <= d <= 6.9:
            return 'r'
        if d < 4.6 and on_slash:
            return 'r'
        return None
    return center16(sprite(grid_from_fn(16, 16, f)))


def i_info():
    def f(x, y):
        d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        if d <= 7.0:
            return 'b'
        return None
    base = grid_from_fn(16, 16, f)
    letter = chk([
        "................",
        "................",
        "................",
        ".......WW.......",
        ".......WW.......",
        "................",
        "......uuu.......",
        ".......uu.......",
        ".......uu.......",
        ".......uu.......",
        ".......uu.......",
        "......uuuu......",
        "................",
        "................",
        "................",
        "................",
    ])
    return sprite(overlay(base, letter))


def i_lucchetto():
    rows = chk([
        "................",
        ".....iiiiii.....",
        "....iiiiiiii....",
        "....ii....ii....",
        "....ii....ii....",
        "....ii....ii....",
        "..gggggggggggg..",
        "..gyyyyyyyyyyg..",
        "..ggggggggggg g.".replace(' ', 'g')[:16],
        "..ggggggggggggg.",
        "..gggggggggggg..",
        "..gggggggggggg..",
        "..gggggggggggg..",
        "..oooooooooooo..",
        "................",
        "................",
    ])
    rows[8] = "..gggggkkggggg.."
    rows[9] = "..gggggkkggggg.."
    rows[10] = "..ggggggkgggggg."[:15] + "."
    rows[10] = "..ggggggkggggg.."
    rows[11] = "..ggggggkggggg.."
    return center16(sprite(chk(rows)))


# ------------------------------------------------------------------ valute
def i_soldi():
    rows = chk([
        "................",
        "......l..l......",
        "......llll......",
        ".......ll.......",
        "......gggg......",
        ".....llllll.....",
        "....llllllll....",
        "...llllllllll...",
        "..llllllllllll..",
        "..llllllllllll..",
        "..llllllllllll..",
        "..llllllllllll..",
        "...llllllllll...",
        "....llllllll....",
        "................",
        "................",
    ])
    dollar = chk([
        "................",
        "................",
        "................",
        "................",
        "................",
        ".......EE.......",
        "......EEEE......",
        "......EE........",
        "......EEEE......",
        "........EE......",
        "......EEEE......",
        ".......EE.......",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'E': ('green', 4)}
    return center16(sprite(overlay(rows, dollar), ex))


def nugget_rows():
    return chk([
        "................",
        "................",
        "................",
        "......aaa.......",
        ".....aaaaa......",
        "....aaaaaaa.....",
        "....aaaaaaaa....",
        "...aaaaaaaaaa...",
        "...bbbaaaaaaa...",
        "..bbbbbaaaaccc..",
        ".bbbbbbbaaccccc.",
        ".bbbbbbbbcccccc.",
        ".bbbbbbbbcccccc.",
        "..bbbbbb..cccc..",
        "................",
        "................",
    ])


def i_pepite():
    ex = {'a': ('gold#1', None), 'b': ('gold#2', None), 'c': ('gold#3', None)}
    img = sprite(nugget_rows(), ex, volume=0.9)
    for p in [(7, 5), (3, 10), (11, 10)]:
        img.putpixel(p, hexc('fffbe0'))
    return center16(img)


GEM = chk([
    "................",
    "................",
    "....34444432....",
    "...3445554432...",
    "..344444443322..",
    ".23333333332221.",
    "..433333322211..",
    "...4333322211...",
    "....43322211....",
    ".....432211.....",
    "......4221......",
    ".......21.......",
    "................",
    "................",
    "................",
    "................",
])


def i_gemme():
    return center16(sprite(GEM, T('purple')))


def crystal_rows():
    return chk([
        "................",
        "........4.......",
        ".......443......",
        "......44321.....",
        "......43C21.....",
        "...A..43321..A..",
        "..ABE.43321.ABE.",
        "..ABE.43C21.ABE.",
        "..ABD.43321.ABD.",
        "..ABD.43321.ABD.",
        "..ABD.43C21.ABD.",
        "..BBD.43321.BBD.",
        "......43321.....",
        ".......432......",
        "........1.......",
        "................",
    ])


def crystal_pal(phase=0.0):
    """cristallo quantico: corpo ciano, cristalli laterali magenta pulsanti."""
    p = 0.5 + 0.5 * math.sin(phase * 2 * math.pi)
    d = T('cyan')
    d['A'] = ('magenta', 4)
    d['B'] = ('magenta', 3 if p > 0.3 else 2)
    d['E'] = ('magenta', 2 if p > 0.6 else 1)
    d['D'] = ('magenta', 1)
    d['C'] = ('white', 4) if p > 0.5 else ('cyan', 4)
    return d


def i_quantum_static():
    return center16(sprite(crystal_rows(), crystal_pal(0.25)))


def quantum_anim(frames=8):
    out = Image.new('RGBA', (16, 16 * frames), (0, 0, 0, 0))
    rows = crystal_rows()
    sparks = [(3, 2), (13, 3), (2, 12), (14, 11), (10, 1), (5, 14), (12, 13), (1, 4)]
    for f in range(frames):
        ph = f / frames
        img = center16(sprite(rows, crystal_pal(ph)))
        # bagliore che sale lungo il cristallo
        band = 13 - int(ph * 14)
        px = img.load()
        for y in range(16):
            for x in range(16):
                c = px[x, y]
                if c[3] and abs(y - band) <= 0 and c[0] + c[1] + c[2] > 150:
                    px[x, y] = lerp(c, hexc('ffffff'), 0.55)
        sx, sy = sparks[f % len(sparks)]
        if px[sx, sy][3] == 0:
            px[sx, sy] = hexc('e8ffff')
        sx, sy = sparks[(f + 4) % len(sparks)]
        if px[sx, sy][3] == 0:
            px[sx, sy] = hexc('ffd0f6')
        out.alpha_composite(img, (0, 16 * f))
    return out


# ------------------------------------------------------------------ pass / premium
def i_battlepass():
    rows = chk([
        "................",
        "................",
        "................",
        ".gggggggggggggg.",
        ".gggoggggggggggg"[:16],
        "..ggogggggggggg.",
        "..ggogggggggggg.",
        ".gggoggggggggggg"[:16],
        ".gggggggggggggg.",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    rows = chk([
        "................",
        "................",
        "................",
        ".gggggggggggggg.",
        ".gggoggggggggggg"[:15] + ".",
        "..ggoggggggggg..",
        "..ggogggggggggg."[:15] + ".",
        ".gggoggggggggggg"[:15] + ".",
        ".gggggggggggggg.",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    # biglietto più alto
    rows = chk([
        "................",
        "................",
        ".gggggggggggggg.",
        ".gGGGoGGGGGGGGg.",
        ".gggoggggggggg g".replace(' ', 'g')[:15] + ".",
        "..ggogggggggggg."[:16],
        "..ggogggggggggg.",
        "..ggogggggggggg.",
        "..ggogggggggggg.",
        ".gggoggggggggg g".replace(' ', 'g')[:15] + ".",
        ".gggggggggggggg.",
        ".oooooooooooooo.",
        "................",
        "................",
        "................",
        "................",
    ])
    rows[5] = "..ggoggggggggg.."
    rows[6] = "..ggoggggggggg.."
    rows[7] = "..ggoggggggggg.."
    rows[8] = "..ggoggggggggg.."
    star = chk([
        "................",
        "................",
        "................",
        "................",
        ".........r......",
        "........rrr.....",
        "......rrrrrrr...",
        ".......rrrrr....",
        ".......rr.rr....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    star = [r.replace('r', 'p') for r in star]
    img = sprite(overlay(rows, star))
    return center16(img)


def i_premium():
    rows = chk([
        "................",
        "................",
        "..G....GG....G..",
        "..g...gggg...g..",
        "..gg..gggg..gg..",
        "..ggg.gggg.ggg..",
        "..gggggggggggg..",
        "..gggggggggggg..",
        "..ggrggbbggrgg..",
        "..gggggggggggg..",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'r': ('red', 3), 'b': ('blue', 3)}
    img = sprite(rows, ex)
    img.putpixel((4, 8), hexc('ff6a5a'))
    return center16(img)


# ------------------------------------------------------------------ prigione
def i_cella():
    rows = chk([
        "................",
        ".....ssssss.....",
        "...ssssssssss...",
        "..ssssssssssss..",
        ".sssDDDDDDDDsss.",
        ".ssDDDDDDDDDDss.",
        ".ssDDDDDDDDDDss.",
        ".ssDDDDDDDDDDss.",
        ".ssDDDDDDDDDDss.",
        ".ssDDDDDDDDDDss.",
        ".ssDDDDDDDDDDss.",
        ".ssDDDDDDDDDDss.",
        ".ssssssssssssss.",
        ".ssssssssssssss.",
        "................",
        "................",
    ])
    bars = chk([
        "................",
        "................",
        "................",
        "................",
        "....i..ii..i....",
        "...iiiiiiiiii...",
        "....i..ii..i....",
        "....i..ii..i....",
        "....i..ii..i....",
        "....i..ii..i....",
        "...iiiiiiiiii...",
        "....i..ii..i....",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'D': ('dark', 0)}
    g = parse(overlay(rows, bars), ex)
    img = render(g, volume=0.3)
    # giunti tra i blocchi di pietra
    px = img.load()
    for (x, y) in [(5, 1), (10, 1), (3, 2), (8, 2), (12, 2), (1, 12), (6, 13), (11, 12), (14, 7), (1, 8)]:
        if px[x, y][3]:
            px[x, y] = MATS['stone'][2]
    return center16(img)


def i_prigione():
    """manette"""
    rows = chk([
        "................",
        "................",
        ".......aa.......",
        ".....aa..aa.....",
        "....a......a....",
        "....a......a....",
        "..iiii....iiii..",
        ".ii..ii..ii..ii.",
        ".i....i..i....i.",
        ".i....i..i....i.",
        ".ii..ii..ii..ii.",
        "..iiii....iiii..",
        "................",
        "................",
        "................",
        "................",
    ])
    lock = chk([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...jj......jj...",
        "...jj......jj...",
        "................",
        "................",
        "................",
    ])
    ex = {'j': ('steel', 2)}
    return center16(sprite(overlay(rows, lock), ex))


def i_miniere():
    rows = chk([
        "................",
        "................",
        "................",
        ".....gg.........",
        "....gggg.ss.....",
        "...gggggssss.gg.",
        "..sssggssssggggg"[:15] + ".",
        ".iiiiiiiiiiiiii.",
        ".ijjjjjjjjjjjji.",
        "..ijjjjjjjjjji..",
        "..ijjjjjjjjjji..",
        "..iiiiiiiiiiii..",
        "...kk......kk...",
        "..kKKk....kKKk..",
        "...kk......kk...",
        "................",
    ])
    rows[6] = "..sssggssssgggg."
    ex = {'j': ('iron', 1), 'K': ('steel', 3)}
    img = sprite(rows, ex)
    px = img.load()
    for p in [(5, 4), (13, 6)]:
        px[p] = hexc('fff3b0')
    return center16(img)


SWORD = chk([
    "................",
    ".............Ii.",
    "............Iii.",
    "...........Iii..",
    "..........Iii...",
    ".........Iii....",
    "........Iii.....",
    ".......Iii......",
    "..g...Iii.......",
    "...g.Iii........",
    "....gii.........",
    "....wgg.........",
    "...w...g........",
    "..w.............",
    ".G..............",
    "................",
])


def i_pvp():
    a = SWORD
    b = mirror(SWORD)
    ex = {'I': ('iron', 4)}
    g1 = parse(b, ex)
    img1 = render(g1)
    img2 = render(parse(a, ex))
    img1.alpha_composite(img2)
    return center16(img1)


def i_tutorial():
    rows = chk([
        "................",
        "................",
        "................",
        "..nnnn....nnnn..",
        ".nnnnnnn.nnnnnnn"[:16],
        ".nnnnnnnnnnnnnn.",
        ".nnnnnnnnnnnnnn.",
        ".nnnnnnnnnnnnnn.",
        ".nnnnnnnnnnnnnn.",
        ".nnnnnnnnnnnnnn.",
        ".nnnnnnnnnnnnnn.",
        ".nnnnnnnnnnnnnn.",
        "rrrrrrrrrrrrrrrr",
        ".rrrrrrr.rrrrrr.",
        "................",
        "................",
    ])
    rows[4] = ".nnnnnnn.nnnnnn."
    rows[4] = ".nnnnnnnnnnnnnn."
    lines = chk([
        "................",
        "................",
        "................",
        "................",
        "...xxxx..xxxx...",
        "................",
        "..xxxxx..xxxxx..",
        "................",
        "..xxxx...xxxxx..",
        "................",
        "..xxxxx..xxxx...",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    gut = chk([
        "................",
        "................",
        "................",
        "................",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        ".......XX.......",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'x': ('paper', 1), 'X': ('paper', 2)}
    rows = overlay(overlay(rows, lines), gut)
    rows[13] = ".rrrrrr..rrrrrr."
    return center16(sprite(rows, ex, volume=0.3))


def i_selettore():
    def f(x, y):
        d = math.hypot(x + 0.5 - 8, y + 0.5 - 8.5)
        if d <= 6.9:
            return 'g' if d > 5.0 else 'n'
        if y == 1 and 7 <= x <= 8:
            return 'g'
        return None
    base = grid_from_fn(16, 16, f)
    needle = chk([
        "................",
        "................",
        "................",
        "................",
        "..........RR....",
        ".........rRr....",
        "........rr......",
        ".......kK.......",
        "......Kk........",
        ".....uu.........",
        "....uuu.........",
        "....uu..........",
        "................",
        "................",
        "................",
        "................",
    ])
    needle = chk([
        "................",
        "................",
        "................",
        "................",
        "...........r....",
        "..........rr....",
        ".........rr.....",
        "........rk......",
        ".......ku.......",
        "......uu........",
        ".....uu.........",
        ".....u..........",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'r': ('red', 3), 'u': ('white', 3), 'k': ('black', 0), 'n': ('paper', None)}
    return sprite(overlay(base, needle), ex)


def i_armatura():
    rows = chk([
        "................",
        "................",
        "..iii......iii..",
        ".iiiiigggiiiiii.",
        ".iiiiiigggiiiii.",
        ".iiiiiiiiiiiiii.",
        "...iiiiiiiiii...",
        "...iiiiiiiiii...",
        "...iiiiiiiiii...",
        "...iiiiiiiiii...",
        "...iiiiiiiiii...",
        "...gggggggggg...",
        "...iiiiiiiiii...",
        "....iiiiiiii....",
        "................",
        "................",
    ])
    rows = chk([
        "................",
        "................",
        "..gii......iig..",
        ".giiiig..giiiig.",
        ".iiiiiigiiiiiii.",
        ".iiiiiiiiiiiiii.",
        "...iiiiiiiiii...",
        "...iiiiggiiii...",
        "...iiiiggiiii...",
        "...iiiiiiiiii...",
        "...iiiiiiiiii...",
        "...gggggggggg...",
        "...iiiiiiiiii...",
        "....iiiiiiii....",
        "................",
        "................",
    ])
    return center16(sprite(rows, volume=0.8))


def i_skin():
    rows = chk([
        "................",
        "................",
        "....wwwwwww.....",
        "..wwwwwwwwwww...",
        ".wwwwwwwwwwwww..",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwww..wwww.",
        ".wwwwwwww..wwww.",
        ".wwwwwwwwwwwwww.",
        "..wwwwwwwwwwww..",
        "...wwwww..ww....",
        "....www.........",
        "................",
        "................",
        "................",
        "................",
    ])
    blobs = chk([
        "................",
        "................",
        "................",
        "....rr..bb......",
        "...rRr.bBb......",
        "...rr...bb..ee..",
        "...........eEe..",
        "..pp........ee..",
        ".ppPp...........",
        "..ppp...yy......",
        "........yy......",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'R': ('red', 4), 'B': ('blue', 4), 'E': ('green', 4), 'P': ('purple', 4),
          'y': ('gold', 4), 'r': ('red', 2), 'b': ('blue', 2), 'e': ('green', 2), 'p': ('purple', 2)}
    return center16(sprite(overlay(rows, blobs), ex))


def i_keyall():
    def key_rows():
        return chk([
            "................",
            "...gggg.........",
            "..gg..gg........",
            "..g....g........",
            "..gg..gg........",
            "...gggg.........",
            "....gg..........",
            "....gg..........",
            "....gggg........",
            "....gg..........",
            "....ggg.........",
            "....gg..........",
            "................",
            "................",
            "................",
            "................",
        ])
    k1 = key_rows()
    k2 = [r.replace('g', 'i') for r in key_rows()]
    k3 = [r.replace('g', 'c') for r in key_rows()]
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    a = sprite(k2)
    b = sprite(k3)
    c = sprite(k1)
    img.alpha_composite(a.rotate(0), (-2, 3))
    img.alpha_composite(b, (8, 3))
    img.alpha_composite(c, (3, 1))
    return center16(img)


DIGITS = {
    '1': [".#.", "##.", ".#.", ".#.", "###"],
    '2': ["##.", "..#", ".#.", "#..", "###"],
    '3': ["##.", "..#", ".#.", "..#", "##."],
}


def put_digit(img, d, x0, y0, col):
    for r, row in enumerate(DIGITS[d]):
        for c, ch in enumerate(row):
            if ch == '#':
                img.putpixel((x0 + c, y0 + r), col)


def i_classifica():
    rows = []
    for y in range(16):
        L = 'i' if y in (6, 7) else '1' if 8 <= y <= 13 else '.'
        C = 'g' if y in (3, 4) else '2' if 5 <= y <= 13 else '.'
        R = 'l' if y in (8, 9) else '3' if 10 <= y <= 13 else '.'
        rows.append('.' + L * 4 + C * 6 + R * 4 + '.')
    rows[1] = "........G......."
    rows[2] = ".......GGG......"
    ex = {'1': ('stone#a', None), '2': ('stone#b', None), '3': ('stone#c', None), 'G': ('gold', 4)}
    img = sprite(rows, ex, volume=0.4)
    put_digit(img, '1', 7, 6, MATS['gold'][5])
    put_digit(img, '2', 2, 8 + 1, MATS['iron'][5])
    put_digit(img, '3', 12, 10 - 1 + 1, MATS['leather'][5])
    return center16(img)


def i_impostazioni():
    def f(x, y):
        dx, dy = x + 0.5 - 8, y + 0.5 - 8
        d = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        tooth = (math.cos(a * 8) > 0.25)
        if d <= 1.9:
            return None
        if d <= 5.0:
            return 'i'
        if d <= 7.0 and tooth:
            return 'i'
        return None
    return sprite(grid_from_fn(16, 16, f), volume=0.9)


def i_negozio():
    rows = chk([
        "................",
        "................",
        "..rWrWrWrWrWrW..",
        ".rWrWrWrWrWrWrW.",
        ".rWrWrWrWrWrWrW.",
        "..rr.ww.rr.ww...",
        "..w..........w..",
        "..w..........w..",
        "..w..........w..",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwwwwwwwww.",
        ".ww..........ww.",
        ".ww..........ww.",
        "................",
        "................",
    ])
    rows[5] = "..rrWWrrWWrrWW.."
    goods = chk([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "....gg..ee......",
        "...gggg.ee.pp...",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'W': ('white', 4)}
    return center16(sprite(overlay(rows, goods), ex))


def i_casse():
    rows = chk([
        "................",
        "................",
        "................",
        "..wwwwwwwwwwww..",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwwwwwwwww.",
        ".gggggggggggggg.",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwwwwwwwww.",
        ".wwwwwwwwwwwwww.",
        "................",
        "................",
        "................",
    ])
    trim = chk([
        "................",
        "................",
        "................",
        "...o........o...",
        "..oo........oo..",
        "..oo........oo..",
        "......gggg......",
        "..oo..gkkg..oo..",
        "..oo..gggg..oo..",
        "..oo........oo..",
        "..oo........oo..",
        "..oo........oo..",
        "..oo........oo..",
        "................",
        "................",
        "................",
    ])
    ex = {'o': ('gold', 2)}
    return center16(sprite(overlay(rows, trim), ex, volume=0.4))


def i_incantesimi():
    rows = chk([
        "................",
        "................",
        "...pppppppppp...",
        "..pppppppppppn..",
        "..pPppppppppnn..",
        "..ppppgggpppnn..",
        "..pppg...gppnn..",
        "..pppg...gppnn..",
        "..ppppgggpppnn..",
        "..ppppppppppnn..",
        "..ppppppppppnn..",
        "..ppppppppppn...",
        "..PPPPPPPPPP....",
        "................",
        "................",
        "................",
    ])
    rows[6] = "..pppgMMMgppnn.."
    rows[7] = "..pppgMMMgppnn.."
    ex = {'M': ('magenta', 4), 'P': ('purple', 1)}
    img = sprite(rows, ex)
    px = img.load()
    for p, c in [((13, 1), 'fff3b0'), ((14, 2), 'ffd64a'), ((12, 0), 'ffd64a'), ((1, 13), 'f0dcff')]:
        px[p] = hexc(c)
    return center16(img)


def i_rankup():
    def chev(y0, ch):
        r = ['................'] * 16
        r = list(r)
        for i in range(5):
            line = ['.'] * 16
            for x in range(16):
                if abs(x - 7.5) <= i + 1.5 and abs(x - 7.5) >= i - 1.5 + 0.0 and abs(x - 7.5) > i - 1.5:
                    pass
            r[y0 + i] = ''.join(line)
        return r
    rows = chk([
        "................",
        ".......ee.......",
        "......eeee......",
        ".....eeeeee.....",
        "....eeeeeeee....",
        "...eeee..eeee...",
        "..eeee....eeee..",
        "..eee......eee..",
        ".......gg.......",
        "......gggg......",
        ".....gggggg.....",
        "....gggggggg....",
        "...gggg..gggg...",
        "..gggg....gggg..",
        "..ggg......ggg..",
        "................",
    ])
    return center16(sprite(rows))


def i_prestigio():
    rows = chk([
        "................",
        "...rrr....bbb...",
        "...rrrr..bbbb...",
        "....rrrrbbbb....",
        ".....rrrbbb.....",
        "......gggg......",
        "....gggggggg....",
        "...gggggggggg...",
        "...gggggggggg...",
        "..gggggggggggg..",
        "..gggggggggggg..",
        "...gggggggggg...",
        "...gggggggggg...",
        "....gggggggg....",
        "......gggg......",
        "................",
    ])
    star = chk([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        ".......pp.......",
        "......pppp......",
        "....pppppppp....",
        ".....pppppp.....",
        ".....pp..pp.....",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'p': ('purple', None)}
    img = sprite(overlay(rows, star), ex)
    return center16(img)


def i_giornaliero():
    rows = chk([
        "................",
        "....i......i....",
        "..rrirrrrrrirr..",
        "..rrrrrrrrrrrr..",
        "..rrrrrrrrrrrr..",
        "..nnnnnnnnnnnn..",
        "..nnnnnnnnnnnn..",
        "..nnnnnnnnnnnn..",
        "..nnnnnnnnnnnn..",
        "..nnnnnnnnnnnn..",
        "..nnnnnnnnnnnn..",
        "..nnnnnnnnnnnn..",
        "..nnnnnnnnnnnn..",
        "................",
        "................",
        "................",
    ])
    tick = chk([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...........e....",
        "..........ee....",
        "....e....ee.....",
        "....ee..ee......",
        ".....eeee.......",
        "......ee........",
        "................",
        "................",
        "................",
        "................",
    ])
    ex = {'e': ('green', 2)}
    img = sprite(overlay(rows, tick), ex, volume=0.3)
    px = img.load()
    for p in [(4, 1), (11, 1)]:
        px[p] = MATS['iron'][4]
    return center16(img)


def i_gang():
    rows = chk([
        "................",
        ".gggggggggggggg.",
        ".grrrrrrrrrrrrg.",
        ".grrrrrrrrrrrrg.",
        ".grrrrrrrrrrrrg.",
        ".grrrrrrrrrrrrg.",
        ".grrrrrrrrrrrrg.",
        ".grrrrrrrrrrrrg.",
        "..grrrrrrrrrrg..",
        "..grrrrrrrrrrg..",
        "...grrrrrrrrg...",
        "....grrrrrrg....",
        ".....grrrrg.....",
        "......gggg......",
        "................",
        "................",
    ])
    skull = chk([
        "................",
        "................",
        "................",
        "......hhhh......",
        ".....hhhhhh.....",
        ".....hkhhkh.....",
        ".....hkhhkh.....",
        ".....hhhhhh.....",
        "......hkkh......",
        "......h.hh......",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    skull[9] = "......hhhh......"
    skull[8] = "......hhhh......"
    skull[9] = "......h..h......"
    skull[9] = "......hhhh......"
    ex = {'k': ('black', 1), 'h': ('bone', None)}
    g = parse(overlay(rows, skull), ex)
    img = render(g, volume=0.4)
    px = img.load()
    for p in [(7, 9), (8, 9)]:
        px[p] = MATS['bone'][2]
    px[(7, 8)] = MATS['bone'][2]
    return center16(img)


def i_hub():
    rows = chk([
        "........r.......",
        "........rrr.....",
        "........rr......",
        "........i.......",
        "......s.s.s.....",
        "......sssss.....",
        "..s.s.sssss.s.s.",
        "..sssssssssssss.",
        "..sssssssssssss.",
        "..sssssssssssss.",
        "..sssssssssssss.",
        "..sssssDDDsssss.",
        "..ssssDDDDDssss.",
        "..ssssDDDDDssss.",
        "................",
        "................",
    ])
    ex = {'D': ('wood', 1)}
    img = sprite(rows, ex, volume=0.3)
    px = img.load()
    for p in [(8, 8), (4, 9), (12, 9)]:
        px[p] = hexc('ffd64a')  # finestre illuminate
    for p in [(5, 11), (11, 11), (4, 12), (12, 12), (8, 9)]:
        pass
    return center16(img)


def i_unisci():
    rows = chk([
        "................",
        "....y.....y.....",
        "...yGy...yGy....",
        "....y..y..y.....",
        ".......G........",
        "..iiiiiiiiiiii..",
        "iiiiiiiiiiiiiii.",
        ".iiiiiiiiiiiiii.",
        "...iiiiiiiiii...",
        ".....iiiiii.....",
        ".....iiiiii.....",
        "....iiiiiiii....",
        "..iiiiiiiiiiii..",
        "..iiiiiiiiiiii..",
        "................",
        "................",
    ])
    ex = {'i': ('steel', None)}
    return center16(sprite(rows, ex, volume=0.6))


def i_traguardo():
    rows = chk([
        "................",
        "...gggggggggg...",
        ".ggggggggggggggg"[:15] + ".",
        ".g.gggggggggg.g.",
        ".g.gggggggggg.g.",
        "..ggggggggggggg."[:15] + ".",
        "....gggggggg....",
        ".....gggggg.....",
        "......gggg......",
        ".......gg.......",
        ".......gg.......",
        ".....gggggg.....",
        "....wwwwwwww....",
        "....wwwwwwww....",
        "................",
        "................",
    ])
    rows[5] = "..gggggggggggg.."
    img = sprite(rows)
    px = img.load()
    for p in [(5, 2), (5, 3), (5, 4)]:
        px[p] = MATS['gold'][6 - 1]
    return center16(img)


# ------------------------------------------------------------------ lucky block (icona isometrica)
def iso_block(top, left, right, size=16):
    """Proietta tre facce 16x16 in un cubo isometrico 16x16 (stile icona di inventario)."""
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = out.load()
    tp, lp, rp = top.load(), left.load(), right.load()
    # vertici: in alto (8,0) .. facce: top rombo y 0..7, laterali y 4..15
    for y in range(size):
        for x in range(size):
            X, Y = x + 0.5, y + 0.5
            # coordinate del rombo superiore
            u = (X - 8) / 8 + (Y - 4) / 4
            v = -(X - 8) / 8 + (Y - 4) / 4
            if -1 <= u <= 1 and -1 <= v <= 1 and Y <= 8:
                tu = int((u + 1) / 2 * 15.999)
                tv = int((v + 1) / 2 * 15.999)
                px[x, y] = tp[15 - tv, tu] if False else tp[min(15, max(0, tu)), min(15, max(0, 15 - tv))]
                continue
            if X <= 8:
                # faccia sinistra
                a = X / 8
                yy = Y - (4 + a * 4)
                if 0 <= yy <= 8 + 0.0 and Y >= 4:
                    tx = int(a * 15.999)
                    ty = int(yy / 8 * 15.999)
                    if 0 <= ty <= 15:
                        px[x, y] = mul(lp[tx, ty], 0.82)
            else:
                a = (X - 8) / 8
                yy = Y - (8 - a * 4)
                if 0 <= yy <= 8:
                    tx = int(a * 15.999)
                    ty = int(yy / 8 * 15.999)
                    if 0 <= ty <= 15:
                        px[x, y] = mul(rp[tx, ty], 0.64)
    return out


Q5 = [
    ".###.",
    "##.##",
    "...##",
    "..##.",
    "..##.",
    ".....",
    "..##.",
]


def i_lucky(sparkle=False):
    """lucky block in prospettiva obliqua: faccia frontale con '?', sopra e lato destro."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    F0, F1, Y0, Y1, D = 1, 11, 4, 14, 3
    top = [hexc('fff2a0'), hexc('ffd85a'), hexc('f2b52a')]
    side = [hexc('c47a10'), hexc('a8620a'), hexc('8a4c06')]
    for k in range(1, D + 1):
        y = Y0 - k
        for x in range(F0 + k, F1 + k + 1):
            edge = x in (F0 + k, F1 + k) or k == D
            px[x, y] = top[0] if k == D and not x in (F0 + k, F1 + k) else (hexc('e09a1c') if edge else top[1 if (x + k) % 5 else 2])
        x = F1 + k
        for y in range(Y0 - k, Y1 - k + 1):
            edge = y in (Y0 - k, Y1 - k)
            px[x, y] = hexc('6a3a04') if edge else side[(k - 1) % 3]
    # fronte
    for y in range(Y0, Y1 + 1):
        for x in range(F0, F1 + 1):
            ring = min(x - F0, y - Y0, F1 - x, Y1 - y)
            if ring == 0:
                c = hexc('fff0a0') if (x == F0 or y == Y0) else hexc('b0680a')
            elif ring == 1:
                c = hexc('8a4a06')
            else:
                t = (x - F0 + y - Y0) / 20.0
                c = [hexc('ffd85a'), hexc('f7bb2e'), hexc('e59c1c')][min(2, int(t * 3))]
            px[x, y] = c
    for r, row in enumerate(Q5):
        for c, ch in enumerate(row):
            if ch == '#':
                px[F0 + 3 + c + 1, Y0 + 2 + r + 1] = hexc('9a5206')
    for r, row in enumerate(Q5):
        for c, ch in enumerate(row):
            if ch == '#':
                px[F0 + 3 + c, Y0 + 2 + r] = hexc('fffbe8')
    # contorno esterno
    g = [[('gold', 0) if px[x, y][3] else None for x in range(16)] for y in range(16)]
    from px import add_outline
    add_outline(img, g)
    if sparkle:
        for (x, y), c in (((14, 0), 'ffffff'), ((0, 2), 'fff3b0'), ((15, 13), 'fff3b0')):
            if px[x, y][3] == 0:
                px[x, y] = hexc(c)
    return img


ICONS = {
    'gui_indietro': i_indietro, 'gui_chiudi': i_chiudi, 'gui_avanti': i_avanti, 'gui_conferma': i_conferma,
    'gui_annulla': i_annulla, 'gui_info': i_info, 'gui_lucchetto': i_lucchetto, 'gui_soldi': i_soldi,
    'gui_pepite': i_pepite, 'gui_gemme': i_gemme, 'gui_quantum': i_quantum_static, 'gui_battlepass': i_battlepass,
    'gui_premium': i_premium, 'gui_cella': i_cella, 'gui_unisci': i_unisci, 'gui_traguardo': i_traguardo,
    'gui_hub': i_hub, 'gui_prigione': i_prigione, 'gui_miniere': i_miniere, 'gui_pvp': i_pvp,
    'gui_tutorial': i_tutorial, 'gui_selettore': i_selettore, 'gui_armatura': i_armatura, 'gui_skin': i_skin,
    'gui_keyall': i_keyall, 'gui_classifica': i_classifica, 'gui_impostazioni': i_impostazioni,
    'gui_negozio': i_negozio, 'gui_casse': i_casse, 'gui_incantesimi': i_incantesimi, 'gui_rankup': i_rankup,
    'gui_prestigio': i_prestigio, 'gui_giornaliero': i_giornaliero, 'gui_gang': i_gang,
    'gui_lucky': lambda: i_lucky(True),
}
