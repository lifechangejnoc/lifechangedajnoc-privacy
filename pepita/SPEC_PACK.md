# Resource pack Pepita v2 — specifica (contratto con il plugin)

Minecraft Java **26.2** (pack.mcmeta: `"min_format": 88, "max_format": 99`). Namespace custom: `pepita`.
Il plugin usa SOLO `custom_model_data` stringa (`CustomModelData.strings[0]`) sugli oggetti vanilla elencati qui sotto:
le definizioni in `assets/minecraft/items/<item>.json` devono essere `minecraft:select` su `minecraft:custom_model_data`
(index 0) con `fallback` al modello vanilla (così senza pack si vede l'oggetto normale, mai il cubo viola).

Generatore esistente: `/home/claude/pepita/resourcepack/gen.py` (NON rompere quello che già fa: menu, gemma, chiavi,
bombe, pepita_oro, 11 skin 2D `piccone_*`, 8 armature vecchie, font `pepita:logo` ). Aggiungi un modulo
`gen2.py` chiamato da `gen.py` prima dello zip (o integra in gen.py) e unisci correttamente i case degli items.json
(un solo file per item vanilla con TUTTI i case).

Sorgenti già pronte:
- `/home/claude/pepita/assets/skinpack/assets/pepita/...` — 10 set dell'utente (aureo, smeraldo, glaciale, inferno,
  ametista, abisso, faraone, sakura, tempesta, eclisse): modelli 3D picconi `models/item/<set>.json` (texture
  `pepita:item/<set>`), modelli 3D pezzi armatura `models/item/<set>_{helmet,chestplate,leggings,boots}.json`,
  texture `textures/item/<set>.png` (64x64) e `textures/entity/equipment/{humanoid,humanoid_leggings}/<set>.png`
  (128x64), `equipment/<set>.json`. Copiali (puoi minificare i JSON). Ignora `assets/pepita/items/*` (non servono).
- `/home/claude/pepita/assets/drive/logo_pepita.png` — logo ufficiale 1254x1254 (pepita d'oro + scritta PEPITA).
- `/home/claude/pepita/assets/drive/screen_armor_coral.png` — esempio di stile tooltip/GUI di un server prison italiano.
- `/home/claude/pepita/assets/drive/collezione-armature.png` — anteprima dei 10 set.
- Texture vanilla: `/home/claude/mcref/client262.jar` (zip).

## 1. Picconi (skin)
Item vanilla con gli stessi case: `wooden_pickaxe, stone_pickaxe, iron_pickaxe, golden_pickaxe, diamond_pickaxe,
netherite_pickaxe` **e anche `paper`** (gli oggetti-skin staccabili sono carta con lo stesso id).
ID (custom_model_data):
- vecchie 2D (già nel gen.py): `piccone_pepita, piccone_pizza, piccone_forchettone, piccone_arcobaleno, piccone_lava,
  piccone_ghiaccio, piccone_neon, piccone_galassia, piccone_drago, piccone_caramella, piccone_pane`
- nuove 3D: `piccone_aureo, piccone_smeraldo, piccone_glaciale, piccone_inferno, piccone_ametista, piccone_abisso,
  piccone_faraone, piccone_sakura, piccone_tempesta, piccone_eclisse` → modello `pepita:item/<set>` dell'utente.
- aspetto dei tier senza skin: `tier_pepita` (piccone d'oro con pepite incastonate, 2D 16x16 o 32x32) e
  `tier_quantum` (piccone cristallo ciano/viola energetico, animato), stile coerente con gli altri.

## 2. Armature
Equipment asset per i 10 set nuovi (`assets/pepita/equipment/<set>.json`, layer humanoid + humanoid_leggings).
Item: `leather_helmet, leather_chestplate, leather_leggings, leather_boots` con case `<set>_helmet`,
`<set>_chestplate`, `<set>_leggings`, `<set>_boots` → modelli 3D dell'utente. (Le 8 armature vecchie restano solo
equipment, nessun modello oggetto.)

## 3. Icone GUI (item `paper`)
`gui_indietro, gui_chiudi, gui_avanti, gui_conferma, gui_annulla, gui_info, gui_lucchetto, gui_soldi, gui_pepite,
gui_gemme, gui_quantum, gui_battlepass, gui_premium, gui_cella, gui_unisci, gui_traguardo, gui_hub, gui_prigione,
gui_miniere, gui_pvp, gui_tutorial, gui_selettore, gui_armatura, gui_skin, gui_lucky, gui_keyall, gui_classifica,
gui_impostazioni, gui_negozio, gui_casse, gui_incantesimi, gui_rankup, gui_prestigio, gui_giornaliero, gui_gang`
+ `quantum` (cristallo quantico, valuta: cristallo ciano-magenta luminoso, animato) e `lucky` (lucky block in icona).
Stile: fantasy/prison, pixel art 16x16 (o 32x32) pulita, contorno scuro, palette calda oro/ferro/pietra + accenti
del colore del tema. Devono leggersi bene a 16px.
Anche `compass` con case `selettore` (bussola dorata a forma di pepita) e `nether_star` mantiene `menu`.

## 4. Riempitivi dei menu (item `black_stained_glass_pane`, `gray_stained_glass_pane`, `paper`)
- `riempi`: tessera di pietra scura scolpita che, in GUI, **copre tutto lo slot 18x18 senza giunture** (modello item
  con `display.gui.scale` 1.125 e texture 16x16 seamless; affiancando più riempitivi deve sembrare un pannello unico).
- `riempi_oro`: come sopra ma con filo d'oro (per bordi/cornici).
- `vuoto`: modello senza elementi (invisibile).

## 5. Container vanilla ritexturizzati (stile fantasy-prigione)
`assets/minecraft/textures/gui/container/generic_54.png, inventory.png, hopper.png, dispenser.png` e
`assets/minecraft/textures/gui/sprites/container/slot.png` (18x18). **Geometria identica al vanilla** (stesse
posizioni di slot, bordi, riquadro del giocatore, pulsante ricette): cambia solo palette/materiale.
Look: pietra lavorata calda (grigio-beige medio, NON troppo scura perché il testo vanilla "Inventario" è grigio
#404040), cornice in ferro scuro con rivetti, angoli in oro; slot incassati in ferro scuro. Nel generic_54 la fascia
del titolo (y 4..15) deve essere una targa scura (i nostri titoli sono colorati chiari).

## 6. Font
### `assets/pepita/font/gui.json`
- provider `space`: ``..`` = -1..-8, ``=-16, ``=-32, ``=-64, ``=-128,
  ``=-256; ``..`` = +1..+8, ``=+16, ``=+32, ``=+64, ``=+128.
- cornici per i menu a N righe: ``..`` (N=1..6). Immagine 176 x (N*18+17), `height` = N*18+17,
  `ascent` = 13 (così il bordo superiore coincide con quello della GUI). **Disegna SOLO fuori dalla griglia degli
  slot** (la griglia è x 7..168, y 17..17+N*18): ornamenti nei margini laterali (catene, borchie, filigrana d'oro),
  angoli decorati, targa dietro il titolo (y 3..15). Tutto il resto trasparente.
- emblemi 12x12 (`height` 12, `ascent` 11), uno per menu, in questo ordine da ``:
  `main, miniere, incantesimi, negozio, skin, casse, armatura, battlepass, traguardi, celle, selettore, conferma,
  classifiche, impostazioni, quantum, tutorial, lucky`.
### `assets/pepita/font/icons.json` (8x8, `height` 8, `ascent` 7)
`` soldi (moneta verde $), `` pepite (pepita d'oro), `` gemme (gemma viola), `` quantum
(cristallo ciano), `` chiave, `` lucky block, `` battle pass, `` spada (pvp),
`` piccone, `` stella.
### `assets/pepita/font/logo.json`
Mantieni `` (logo piccolo esistente) e aggiungi `` = logo ufficiale (da logo_pepita.png) alto 40px
(`height` 40, `ascent` 34) per l'intestazione della TAB.

## 7. Tooltip style
`assets/pepita/textures/gui/sprites/tooltip/<stile>_background.png` e `<stile>_frame.png` (100x100, con .mcmeta
nine_slice identici a quelli vanilla `tooltip/background` e `tooltip/frame`). Stili: `oro` (cornice oro su fondo
marrone scuro), `quantum` (viola/ciano come nello screenshot, con angoli luminosi), `leggendario` (oro + viola).

## 8. Blocchi
- `sponge` → **Lucky Block** (blocco dorato con punto interrogativo, bordi lavorati, animato con brillio: .mcmeta).
- `budding_amethyst` → **Minerale Quantico** (roccia scura con cristalli ciano/magenta pulsanti, animato).
- `lodestone_top` / `lodestone_side` → **Nucleo Quantico** (blocco tecnologico-arcano, nucleo ciano luminoso).

## 9. NPC
`assets/pepita/textures/entity/npc/guardiano.png` — skin giocatore 64x64 (formato classico a braccia larghe) di
"Beppe il Secondino": divisa da guardia carceraria blu notte, berretto con visiera e stemma d'oro, baffoni, mazzo di
chiavi alla cintura, stivali neri.

## 10. Logo animato (cartellone)
`paper` case `logo` → modello item `pepita:item/logo` generato (item/generated) con texture animata
`pepita:item/logo` ricavata da logo_pepita.png (192x192 per frame, ~12 frame: riflesso di luce che attraversa
oro e scritta + scintille che brillano; .mcmeta frametime 2-3, interpolate false). `display.fixed` e `display.none`
scala 1. Anche `logo_statico` (frame singolo).

## 11. Verifiche obbligatorie
Script che: valida tutti i JSON, controlla che ogni texture/modello referenziato esista, controlla le dimensioni
(container 256x256 con geometria invariata, glyph come da specifica), genera `preview2.png` con tutte le nuove
icone/GUI/tooltip per controllo visivo, e produce `PepitaResourcePack.zip` + sha1. Guarda le anteprime e migliora
finché sono belle.
