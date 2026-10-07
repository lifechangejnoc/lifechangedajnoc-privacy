# Costruzioni di Pepita — specifica

Server prison italiano "Pepita" (Minecraft Java 26.2, Paper). Tutte le mappe sono generate dal plugin con codice
Java procedurale in mondi vuoti (void). L'utente vuole build **professionali, piene di dettagli, curate come quelle
dei migliori server prison**: niente forme piatte e monotone, usare palette ricche (gradienti di materiali, scale,
lastre, muretti, recinzioni, lanterne, catene, foglie, fiori, rampicanti, banner, botole come dettagli, pareti con
profondità, tetti con scale, archi, colonne, contrafforti, vetrate), coerenza di tema, illuminazione bella.

## API disponibile
- `it.pepita.core.world.BuildPlan` (leggi il file): `set(x,y,z, "minecraft:oak_stairs[facing=north,half=top]")`,
  `set(x,y,z, Material)`, `setIfAbsent`, `air`, `fill`, `get`, `has`, `remove`, `export(File)`.
  I blocchi sono stringhe; `remove` = la posizione non viene toccata; `air` = forza aria.
- `it.pepita.core.world.Layout` (leggi il file): coordinate fisse a cui il plugin aggancia portali, NPC, ologrammi,
  protezioni. **Rispettale al blocco.**
- `it.pepita.core.world.CellGeometry`: classificazione blocco-per-blocco delle celle del colosseo (usata anche dal
  plugin per protezioni e unione delle celle). **Il colosseo deve costruire le celle esattamente secondo `classify`.**
- `it.pepita.core.mine.Mine`: campi `minX..maxZ` (interno della miniera, incluso), `spawnX/Y/Z/Yaw`, `labelX/Y/Z`,
  `theme`, `icon`.
- Codice di esempio delle vecchie build (più semplici): `WorldService.java` (spawn, miniere). Da superare di molto.
- Le classi di build devono essere **pure**: nessun riferimento a `PepitaCore`, `Bukkit`, `World`, `BlockData`;
  solo `BuildPlan`, `Layout`, `CellGeometry`, `Mine`, `java.util.Random` con seed fisso (risultato deterministico).

## Classi da scrivere (package `it.pepita.core.world.build`)
1. `HubBuild.build(BuildPlan p)` — mondo `pepita_hub`. Isola-lobby spettacolare sospesa nel vuoto (raggio max
   `HUB_RADIUS`): piazza centrale, pavimento calpestabile a y=99 (si sta a y=100), spawn `HUB_SPAWN`.
   Quattro **portali** con varchi di `minecraft:nether_portal` ESATTAMENTE nei box `HUB_PORTALS` (axis=x per quelli
   sul piano z=-24, axis=z per quelli sul piano x=±24), incorniciati da archi monumentali a tema:
   prigione (pietra/ferro/sbarre), miniere (minecart, binari, legno, minerali), pvp (nether/sangue/spade),
   celle (colosseo/quarzo/archi). Spazio libero (raggio 3 blocchi) attorno a `HUB_NPC` (piedistallo ok sotto).
   Spazio libero attorno a `HUB_LOGO` (box ±10 in x/y, ±3 in z): lì il plugin mette un logo animato gigante;
   puoi costruire una cornice/torri ai lati. Etichette dei portali in `HUB_PORTAL_LABELS` (lascia aria lì).
2. `PrisonBuild.build(BuildPlan p)` — mondo `pepita`: nuova versione molto più ricca dello spawn della prigione
   (vedi `WorldService.buildSpawn` per l'idea: isola, piazza circolare, mura con torri di guardia, Pepita Gigante,
   ciliegi). Mantieni ESATTAMENTE i blocchi interattivi: casse `PRISON_CRATES` (comune=chest[facing=west],
   rara=ender_chest[facing=west], leggendaria=purple_shulker_box[facing=up], pepita=yellow_shulker_box[facing=up]),
   `PRISON_BOARDS` (blocco qualsiasi, ci vanno sopra ologrammi a y+2), `PRISON_ENCHANT`=enchanting_table,
   `PRISON_SHOP`=emerald_block, `PRISON_DAILY`=barrel[facing=up], `PRISON_ARMOR`=smithing_table,
   `PRISON_PASS`=lectern[facing=south]. Portale miniere: nether_portal[axis=x] in x -2..2, y 100..104,
   z=`PRISON_PORTAL_Z`. Spawn `PRISON_SPAWN` libero. Lascia libero un box ±9 x, ±7 y, ±3 z attorno a `PRISON_LOGO`.
   Raggio max `PRISON_RADIUS`+8.
3. `MineBuild.build(BuildPlan p, Mine m)` — mondo `pepita_miniere`: isola tematica attorno a una miniera.
   L'interno (minX..maxX, minY..maxY, minZ..maxZ) **non va toccato** (usa `remove` o non scriverci): lo riempie il
   plugin. Pareti e fondo a ±1. Bordo calpestabile a y=maxY+1 (si sta a maxY+2). Sopra la buca niente tetti
   per almeno 14 blocchi (si vola/si cade dentro). Pedana d'arrivo a sud su (spawnX, spawnY-1, spawnZ).
   Insegna a nord: il plugin mette un ologramma in (labelX, labelY, labelZ): lascia aria lì (±2), puoi fare un
   portale/arco/pilastri dietro. Raggio max `MINE_ISLAND_RADIUS` dal centro. Temi (`m.theme`):
   `pietra` (cava di montagna, legno e ponteggi, gru, carrelli), `ardesia` (miniera profonda, lanterne, sostegni),
   `nera` (fortezza di blackstone, oro), `prismarino` (rovina sottomarina, alghe, lanterne marine),
   `end` (isola dell'End, purpur, chorus, end rod), `prestigio` (tempio viola/ametista), `vip` (oro, quarzo,
   smeraldi, giardino di lusso), `evasione` (prigione in rovina, ossidiana, anime, catene).
   Le miniere sono grandi: interno 29x29, profonde 30 (vedi `Layout.MINE_*`).
4. `PvpBuild.build(BuildPlan p, Mine m)` — mondo `pepita_pvp`: la Miniera PvP dei Quantum (interno 41x41,
   profonda 24) dentro un'arena/fortezza in rovina tetra (nether, basalto, blackstone, catene, fuochi d'anima,
   cristalli di ametista), con passerelle, ripari, punti alti, ma sempre raggiungibile la buca. Arrivo sicuro
   `PVP_SPAWN` (piattaforma con muretti/cancello, raggio `PVP_SAFE_RADIUS`). Raggio max `PVP_RADIUS`.
   Etichetta della miniera come per le altre (labelX/Y/Z).
5. `ColosseumBuild` — mondo `pepita_celle`:
   - `build(BuildPlan p)`: un **colosseo altissimo** (fino a `COLOSSEUM_TOP`) con arena centrale (pavimento y=80,
     spawn `CELLS_SPAWN`), anelli di archi e colonne all'esterno, e le celle disposte in tondo su
     `CELL_FLOORS` piani × `CELL_SECTORS` settori secondo `CellGeometry.classify`. Ballatoio a ogni piano tra
     `BALCONY_IN` e `CELL_RIN` (pavimento allo stesso y del pavimento delle celle) con ringhiera verso l'arena.
     Coronamento sopra il tetto (y CELL_Y0+CELL_FLOORS*CELL_FH) con merli, statue, bracieri, stendardi.
     Ascensori: 4 colonne/vani in vetro e oro agli angoli `ELEVATOR_ANGLES` a raggio `ELEVATOR_R`.
   - `public static List<int[]> elevatorPads()`: per ogni ascensore e per ogni livello (arena y=81 + ogni piano)
     la posizione di un `minecraft:light_weighted_pressure_plate` {x, y, z, livello} (livello -1 = arena, 0..9 =
     piani). Le piastre devono essere piazzate da `build`. Il plugin apre il menu dell'ascensore quando ci si sale.
   - `public static void buildCell(BuildPlan p, int floor, int sector, boolean furnish)`: scrive tutti i blocchi
     della cella (pavimento SLAB, muro interno WALL_IN con porta DOOR = `minecraft:iron_door` 2 blocchi
     (lower/upper, facing verso il ballatoio, hinge coerente, open=false), muro esterno WALL_OUT con finestra
     WINDOW (sbarre di ferro o vetro), i due muri laterali (indice `sector` e `sector+1`) e l'interno INTERIOR
     (aria + se furnish arredamento da cella: branda/letto, lanterna, baule, scrivania, tappeto, quadro...
     solo dentro INTERIOR, lasciando libero il varco della porta). Usata anche dal plugin per resettare una cella.
     Per iterare i blocchi di una cella scorri il box dell'anello per quel piano e usa `classify`.
   - Il ROOF (tetto sopra l'ultimo piano) lo scrive `build`.
   Le celle devono sembrare celle di prigione vere viste dal ballatoio (sbarre, porte di ferro, numeri incisi
   o insegne colorate per piano), mentre l'esterno è un colosseo monumentale (travertino: smooth sandstone, calcite,
   quartz, mud bricks, pietra; dettagli in oro).

## Strumenti da creare (cartella `/home/claude/pepita/tools`)
- Tabella colori dei blocchi ricavata dalle texture del jar `/home/claude/mcref/client262.jar` (media colore
  top/lato per ogni blocco; per scale/lastre/muretti usa il blocco base).
- Un programma Java (compilato con javac contro `/home/claude/pepita/plugin/target/classes` + il classpath in
  `/tmp/claude-0/-home-claude/544e6682-5c31-5967-b0b0-8074f70e5ad8/scratchpad/cp.txt`) che esegue i builder,
  esporta i piani e **renderizza anteprime PNG** isometriche (da più lati) e dall'alto, con forme approssimate
  (lastre mezze, muretti/sbarre sottili, lanterne piccole...). Usalo continuamente per guardare il risultato con
  il tool Read e migliorare.
- **Validatore**: ogni stringa di blocco nei piani deve esistere in 26.2 con proprietà/valori validi (controlla
  contro `assets/minecraft/blockstates/*.json` del jar; per i blocchi multipart/varianti ricava le proprietà valide;
  eventualmente usa anche i report del server: `java -DbundlerMainClass=net.minecraft.data.Main -jar
  /home/claude/testserver/paper.jar --reports` NON va bene su paper — preferisci i blockstates del jar client).
- JDK 25: `export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH`. Un'altra sessione sta modificando in parallelo
  le ALTRE classi del plugin, quindi per le anteprime compila con `javac` solo le tue classi + quelle che ti servono
  (BuildPlan, Layout, CellGeometry, Mine, Prices...) in una cartella tua (es. `/home/claude/pepita/tools/out`),
  usando `/home/claude/pepita/plugin/target/classes` e il classpath sopra. Alla fine verifica comunque che
  `cd /home/claude/pepita/plugin && mvn -q -o package -DskipTests` (online se mancano
  plugin maven). Non modificare altre classi del plugin oltre a quelle nel package `world.build` e agli strumenti,
  tranne se strettamente necessario (in quel caso dillo).

## Qualità
Guarda le anteprime e itera finché ogni build è davvero bella e dettagliata (silhouette interessante, niente aree
vuote monotone, contrasto materiali, profondità delle facciate, vegetazione, luci). Pensa a come appare al giocatore
che arriva allo spawn: punto focale, percorsi chiari, insegne. Prestazioni: tieni il numero totale di blocchi
ragionevole (hub < 400k, prigione < 400k, ogni isola miniera < 120k esclusa la buca, PvP < 400k, colosseo < 900k).
