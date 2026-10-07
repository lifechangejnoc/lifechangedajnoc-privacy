"""Simboli 8x8 (font icons) disegnati a mano, riusati anche dentro gli emblemi 12x12 dei menu."""
from PIL import Image
from px import hexc

# palette comune per i simboli piccoli
P = {
    # oro
    'k': '2a1603', 'W': 'fffbe6', 'Y': 'fff3b0', 'y': 'ffd64a', 'g': 'e6a425', 'o': 'b46c0e', 'O': '7a4508',
    # verde
    'K': '07260f', 'l': '8af29a', 'e': '2fb84a', 'd': '178230',
    # viola
    'V': '1f0736', 'L': 'f0dcff', 'p': 'c98cff', 'q': '9b4ff0', 'Q': '6c26b4',
    # ciano / magenta
    'C': '03222b', 'c': '7af0ff', 'x': '22c8e0', 'X': '0d8aa2', 'm': 'ff86e8', 'M': 'd640bc',
    # ferro
    'I': '16171c', 'i': 'eef0f4', 'j': 'b9bdc6', 'h': '878b96', 'H': '5c5f6a',
    # legno / cuoio
    'w': 'c28545', 'n': '9a6129', 'N': '4f2c10',
    # rosso / blu
    'r': 'ff6a5a', 'R': 'd8302f', 's': '7a0f14', 'b': '7ab8ff', 'B': '1b56b0',
    # carta
    'z': 'eadaa8', 'Z': 'a68d5f',
}


def s8(rows):
    assert len(rows) == 8 and all(len(r) == 8 for r in rows), rows
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), hexc(P[ch]))
    return img


SYM = {
    'soldi': [
        "..KKKK..",
        ".KlldlK.",
        "KlldddeK",
        "KlldeeeK",
        "KledddeK",
        "KleeedeK",
        ".KdddeK.",
        "..KKKK..",
    ],
    'pepite': [
        "...kkk..",
        "..kyyyk.",
        ".kyWyggk",
        "kyyyggok",
        "kyggggok",
        "kgggooOk",
        ".kooOOk.",
        "..kkkk..",
    ],
    'gemme': [
        "........",
        ".VVVVVV.",
        "VLLppqQV",
        "VppqqqQV",
        ".VqqqQV.",
        "..VqQV..",
        "...VV...",
        "........",
    ],
    'quantum': [
        "...CC...",
        "..CcxC..",
        "..CcxC..",
        ".CcxmXC.",
        ".CcmxXC.",
        ".CcxxXC.",
        "..CxXC..",
        "...CC...",
    ],
    'chiave': [
        "........",
        ".kkk....",
        "kyWyk...",
        "ky.ykkkk",
        "kyygyyyk",
        ".kkkogok",
        "....kkkk",
        "........",
    ],
    'lucky': [
        "kkkkkkkk",
        "kYyyyygk",
        "kyyWWygk",
        "kyyyyWgk",
        "kyyyWygk",
        "kyyyyygk",
        "kgggWook",
        "kkkkkkkk",
    ],
    'battlepass': [
        "........",
        "kkkkkkkk",
        "kYyoyyyk",
        ".kyoyqyk",
        ".kyoqqqk",
        "kyyoyqyk",
        "kkkkkkkk",
        "........",
    ],
    'spada': [
        "......II",
        ".....Iij",
        "....Iijh",
        ".k.Iijh.",
        ".kgijh..",
        "..kgh...",
        ".nkkgk..",
        "kNk..k..",
    ],
    'piccone': [
        "..IIII..",
        ".Iijjh I".replace(' ', 'I')[:8],
        "Ih.IIhhI",
        "I..InIhI",
        "..InN.II",
        ".InN....",
        "InN.....",
        "IN......",
    ],
    'stella': [
        "...kk...",
        "..kYyk..",
        "kkkyygkk",
        "kYyyygok",
        ".kyggok.",
        ".kgogok.",
        "kgok.kok",
        "kkk...kk",
    ],
    # --- simboli extra per gli emblemi
    'libro': [
        "........",
        ".VVVVVV.",
        "VppqqqzV",
        "VpqyyqzV",
        "VqqyyQzV",
        "VqqqqQzV",
        "VQQQQQzV",
        ".VVVVVV.",
    ],
    'pennello': [
        "......II",
        ".....IjI",
        "....IjhI",
        "...InhI.",
        "..knnI..",
        ".krRk...",
        "krRk....",
        "kkk.....",
    ],
    'cassa': [
        "........",
        ".kkkkkk.",
        "kwwwwwnk",
        "kyyyyyyk",
        "knnyWnNk",
        "kwwnnnNk",
        "kkkkkkkk",
        "........",
    ],
    'corazza': [
        "........",
        "II.II.II",
        "IjhyyhjI",
        "IjjhhhhI",
        ".IjhhhI.",
        ".IyyyoI.",
        ".IjhhHI.",
        "..IIII..",
    ],
    'trofeo': [
        "kkkkkkkk",
        "kYyyyyok",
        "kyyyyyok",
        ".kyyyok.",
        "..kgok..",
        "...kk...",
        "..kgok..",
        ".kkkkkk.",
    ],
    'sbarre': [
        "IIIIIIII",
        "IjIjIjhI",
        "IjIjIjhI",
        "IjIjIjhI",
        "IjIjIjhI",
        "IjIjIjhI",
        "IhIhIhHI",
        "IIIIIIII",
    ],
    'bussola': [
        "..kkkk..",
        ".kyzzgk.",
        "kyzzzRgk",
        "kgzzRzok",
        "kgzizzok",
        "kgizzzok",
        ".kozzok.",
        "..kkkk..",
    ],
    'spunta': [
        "........",
        "......KK",
        ".....KlK",
        "KK..KlK.",
        "KlKKlK..",
        ".KllK...",
        "..KK....",
        "........",
    ],
    'corona': [
        "........",
        "k..kk..k",
        "kykyykyk",
        "kyyyyyyk",
        "kyRyyByk",
        "kggggggk",
        "kkkkkkkk",
        "........",
    ],
    'ingranaggio': [
        "...II...",
        ".IIjjII.",
        ".IjhhhI.",
        "IjhIIhhI",
        "IjhIIhHI",
        ".IhhhHI.",
        ".IIHHII.",
        "...II...",
    ],
    'tomo': [
        "........",
        ".NNN.NN.",
        "NzzzNzzN",
        "NzZzNzZN",
        "NzzzNzzN",
        "NnnnNnnN",
        ".NNNNNN.",
        "........",
    ],
}
SYM['piccone'][1] = ".IijjhII"[:8]
SYM['piccone'] = [
    ".IIIII..",
    "Ijjjjhh.",
    ".II.IhhI",
    "...InIhI",
    "..InI.I.",
    ".InI....",
    "InI.....",
    "II......",
]


def sym(name):
    return s8(SYM[name])
