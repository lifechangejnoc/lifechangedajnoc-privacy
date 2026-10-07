"""Mini motore di pixel art per Pepita: palette, materiali, sprite ASCII con ombreggiatura e contorno automatici."""
import math
from PIL import Image


def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def mul(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def add(c, v):
    return (max(0, min(255, c[0] + v)), max(0, min(255, c[1] + v)), max(0, min(255, c[2] + v)), c[3])


def hnoise(x, y, seed=0):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


# materiale = [contorno, t0 ombra profonda, t1 scuro, t2 medio, t3 chiaro, t4 luce]
def M(*cols):
    return [hexc(c) for c in cols]


MATS = {
    'gold':    M('3a1f04', '7a4508', 'b46c0e', 'e6a425', 'ffd64a', 'fff3b0'),
    'iron':    M('16171c', '3a3c45', '5c5f6a', '878b96', 'b9bdc6', 'eef0f4'),
    'steel':   M('121318', '2a2c33', '43464f', '646874', '8e929d', 'c7cad2'),
    'stone':   M('1d1915', '3f3730', '5e544a', '7f7468', 'a39787', 'cfc3b0'),
    'wood':    M('231206', '4f2c10', '74451b', '9a6129', 'c28545', 'e3b16f'),
    'red':     M('330508', '6e0d12', 'a3171d', 'd8302f', 'ff6a5a', 'ffc2b8'),
    'green':   M('052610', '0d5420', '178230', '2fb84a', '6be37a', 'c8ffc8'),
    'blue':    M('06173a', '0f3570', '1b56b0', '3584ea', '7ab8ff', 'd2ecff'),
    'purple':  M('1f0736', '45157a', '6c26b4', '9b4ff0', 'c98cff', 'f0dcff'),
    'cyan':    M('03222b', '085060', '0d8aa2', '22c8e0', '7af0ff', 'e0ffff'),
    'magenta': M('2b0326', '640d58', '9e1c88', 'd640bc', 'ff86e8', 'ffd6f6'),
    'paper':   M('35260f', '7d6740', 'a68d5f', 'cdb684', 'eadaa8', 'fff6d6'),
    'leather': M('241206', '4f2a0e', '74401a', '9a5d2a', 'c08042', 'e2ac70'),
    'bone':    M('2a2620', '5e584c', '8a8272', 'b7ae9a', 'ddd5c0', 'fbf6ea'),
    'white':   M('2a2a33', '6a6c78', '9fa2ae', 'cfd2dc', 'eef0f6', 'ffffff'),
    'dark':    M('0c0a08', '1a1612', '27211b', '352d25', '463c32', '5a4e42'),
    'skin':    M('3a2014', '7a4a30', 'a8694a', 'd09070', 'e8b090', 'f8d0b0'),
    'orange':  M('3a1404', '7a3008', 'b8500e', 'f07a18', 'ffa84a', 'ffe0b0'),
    'navy':    M('080c1a', '121a33', '1c2747', '28385e', '3b5080', '5a74a8'),
    'black':   M('050505', '101012', '1b1b20', '2a2a31', '3d3d46', '5a5a66'),
    'lime':    M('0a2a04', '1e5a0a', '3a8a14', '62c222', '9cf04a', 'dcffb0'),
}

# caratteri globali: lettera minuscola = materiale con ombreggiatura automatica,
# maiuscola = stesso materiale tono "luce" fisso.
CHARMAP = {
    'g': ('gold', None), 'G': ('gold', 5), 'y': ('gold', 4), 'o': ('gold', 1), 'O': ('gold', 0),
    'i': ('iron', None), 'I': ('iron', 5), 'j': ('iron', 1), 'J': ('iron', 0),
    'a': ('steel', None),
    's': ('stone', None), 'S': ('stone', 5),
    'w': ('wood', None), 'W': ('white', 5),
    'r': ('red', None), 'R': ('red', 5), 'q': ('red', 1),
    'e': ('green', None), 'E': ('green', 5),
    'b': ('blue', None), 'B': ('blue', 5),
    'p': ('purple', None), 'P': ('purple', 5),
    'c': ('cyan', None), 'C': ('cyan', 5),
    'm': ('magenta', None), 'M': ('magenta', 5),
    'n': ('paper', None), 'N': ('paper', 5),
    'l': ('leather', None), 'L': ('leather', 5),
    'h': ('bone', None), 'H': ('bone', 5),
    'u': ('white', None),
    'd': ('dark', None), 'D': ('dark', 0),
    'k': ('black', 0), 'K': ('black', 2),
    'f': ('skin', None),
    'z': ('orange', None), 'Z': ('orange', 5),
    'v': ('navy', None),
    't': ('lime', None), 'T': ('lime', 5),
}


def parse(rows, extra=None):
    """Converte righe ASCII in una griglia di (materiale, tono_fisso)."""
    cmap = dict(CHARMAP)
    if extra:
        cmap.update(extra)
    h = len(rows)
    w = max(len(r) for r in rows)
    grid = [[None] * w for _ in range(h)]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in '. ':
                continue
            grid[y][x] = cmap[ch]
    return grid


def render(grid, outline=True, light=(-1, -1), volume=0.55, rim=1.0, outline_diag=False, out_mat=None, sel_out=False):
    """Ombreggiatura: bordo alto/sinistro chiaro, basso/destro scuro, gradiente volumetrico diagonale."""
    h = len(grid)
    w = len(grid[0])
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))

    def mat(x, y):
        if 0 <= x < w and 0 <= y < h and grid[y][x] is not None:
            return grid[y][x][0]
        return None

    # estensione di ciascun materiale lungo la diagonale (per il gradiente)
    ext = {}
    for y in range(h):
        for x in range(w):
            if grid[y][x]:
                m = grid[y][x][0]
                s = x * -light[0] + y * -light[1]
                lo, hi = ext.get(m, (s, s))
                ext[m] = (min(lo, s), max(hi, s))
    for y in range(h):
        for x in range(w):
            cell = grid[y][x]
            if not cell:
                continue
            m, tone = cell
            pal = MATS[m.split('#')[0]] if isinstance(m, str) else m
            if tone is not None:
                img.putpixel((x, y), pal[1 + min(4, tone)] if isinstance(tone, int) else tone)
                continue
            up = mat(x, y - 1) != m
            lf = mat(x - 1, y) != m
            dn = mat(x, y + 1) != m
            rt = mat(x + 1, y) != m
            score = (up + lf) - (dn + rt)
            lo, hi = ext[m]
            s = x * -light[0] + y * -light[1]
            t = 0.5 if hi == lo else (s - lo) / (hi - lo)
            v = 2.5 + rim * score * 0.75 + volume * (0.5 - t) * 2
            idx = int(max(0, min(4, round(v - 0.5))))
            if score >= 2 and up and lf:
                idx = 4 if rim > 0 else idx
            img.putpixel((x, y), pal[1 + idx])
    if outline:
        add_outline(img, grid, outline_diag, out_mat, sel_out)
    return img


def add_outline(img, grid, diag=False, out_mat=None, sel_out=False):
    w, h = img.size
    px = img.load()
    nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    if diag:
        nb += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
    todo = []
    for y in range(h):
        for x in range(w):
            if px[x, y][3] != 0:
                continue
            for ox, oy in nb:
                xx, yy = x + ox, y + oy
                if 0 <= xx < w and 0 <= yy < h and px[xx, yy][3] != 0 and grid[yy][xx] is not None:
                    m = out_mat or grid[yy][xx][0]
                    pal = MATS[m.split('#')[0]] if isinstance(m, str) else m
                    todo.append(((x, y), pal[0]))
                    break
    for p, c in todo:
        px[p] = c


def sprite(rows, extra=None, **kw):
    return render(parse(rows, extra), **kw)


def mirror(rows):
    return [r[::-1] for r in rows]


def grid_from_fn(w, h, fn):
    rows = []
    for y in range(h):
        rows.append(''.join(fn(x, y) or '.' for x in range(w)))
    return rows


def overlay(rows, top):
    """Sovrappone righe ASCII (i '.' di top lasciano passare quelle sotto)."""
    out = []
    for a, b in zip(rows, top):
        out.append(''.join(bb if bb not in '. ' else aa for aa, bb in zip(a.ljust(len(b), '.'), b)))
    return out


def scale_up(img, k):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)
