# Pepita — contesto completo del lavoro (passaggio di consegne)

Documento per continuare il lavoro in un'altra sessione di Claude. Scritto il 7 ottobre 2026.
Rispondi all'utente **in italiano**. L'utente vuole che si lavori in autonomia e che gli si spieghi
in modo semplice cosa deve fare lui sul pannello del server.

---

## 1. Chi è l'utente e cosa vuole

- Gestisce un server Minecraft **prison** italiano chiamato **Pepita** (IP mostrato: `pepitamc.it`).
- Hosting: **Falix**, piano gratuito (circa 2,5 GB di RAM condivisi: attenzione alle prestazioni).
- Server: **Paper 26.2** (build 132, `paper-api 26.2.build.132-stable`), **Java 25**.
  Plugin di contorno: **ViaVersion 5.12.0** e **ViaBackwards 5.12.0**.
- Tutto il gameplay è in un unico plugin scritto da noi: **PepitaCore**.
- Il resource pack è già fatto dall'utente (contratto in `pepita/SPEC_PACK.md`) ed è caricato su
  mc-packs.net (link in `config.yml` → `resource-pack.url`). Lo zip del pack non è nel repo.
- Le istruzioni originali erano in un file `ISTRUZIONI_PER_IA.md` caricato in chat (non è nel repo).
  In sintesi chiedevano: 5 mondi separati, costruzioni procedurali professionali, tier del piccone,
  skin oggetto, valuta Quantum solo nella miniera PvP, celle nel colosseo, lucky block, battle pass,
  traguardi, keyall, TAB, cartellone, NPC tutorial, GUI fantasy, architettura economica documentata.

## 2. Repository e branch

- GitHub: `lifechangejnoc/lifechangedajnoc-privacy`. Tutto il progetto sta nella cartella **`pepita/`**.
  Il `README.md` nella radice è un'altra cosa (privacy policy di un'app Etsy/Pinterest): non toccarlo.
- Branch di lavoro: **`claude/gallant-einstein-5vfyov`**. Nessuna pull request è stata aperta (non
  aprirla se l'utente non la chiede).
- Nei messaggi di commit non vanno messi identificativi di modelli.
- Commit fatti, dal più vecchio:
  1. `fcf3f87` PepitaCore v2: mondi separati, quantum, celle, battle pass, tier e skin oggetto, economia
  2. `cfa725e` Costruzioni v2: miniere a 8 temi, miniera PvP, colosseo delle celle, nuova prigione
  3. `3e7fb96` Consegna: ECONOMIA.md, LEGGIMI.txt, jar 2.0.0, drop dei blocchi solo nelle celle
  4. `a2d21a0` Skin add-on: la corazza ha una skin per pezzo che potenzia il pezzo
  5. `71349b2` Lobby separata dal Prison e supporto alle mappe esterne per la lobby
  6. `2822f65` Lobby: installazione della mappa più tollerante e con ripristino
  7. `CONTESTO.md` e poi la rete Velocity (PepitaLobby, modalità rete di PepitaCore, `rete/`, `RETE.md`): vedi `git log`

### Struttura di `pepita/`

| Percorso | Cosa contiene |
|---|---|
| `plugin/` | Il plugin Maven (`pom.xml`, sorgenti in `src/main/java/it/pepita/core`, `config.yml`, `plugin.yml`) |
| `lobby/` | Il plugin PepitaLobby per il server Lobby della rete (`it.pepita.lobby`) |
| `rete/` | Modelli di configurazione della rete: `proxy/velocity.toml`, `lobby/`, `prison/` |
| `RETE.md` | Guida per l'utente: installazione della rete proxy + Lobby + Prison |
| `consegna/PepitaCore.jar` | Ultimo jar compilato, quello dato all'utente |
| `LEGGIMI.txt` | Istruzioni per l'utente: installazione su Falix, lobby, comandi giocatori e staff |
| `ECONOMIA.md` | Architettura economica con formule e tabelle |
| `SPEC_PACK.md` | Contratto con il resource pack (id dei modelli, font, glifi, tooltip) |
| `SPEC_BUILD.md` | Specifiche delle costruzioni |
| `tools/` | Strumenti di anteprima e validazione delle costruzioni (`anteprima.sh`, `src/PlanTool.java`, `data/`) |
| `resourcepack/` | Generatore Python del resource pack (non modificato da noi) |
| `assets/skinpack/` | I 10 set di skin dell'utente (modelli 3D piccone e corazza) già integrati nel pack |

### Pacchetti del plugin (`it.pepita.core`)

`PepitaCore` (main), `armor` (corazza Quantum), `cell` (celle), `command` (tutti i comandi),
`cosmetics` (PickaxeSkin, ArmorSkin), `crate` (casse), `data` (PlayerData, DataManager: YAML in
`plugins/PepitaCore/giocatori/`), `display` (Sidebar: scoreboard e TAB), `economy` (Economy),
`event` (KeyAll, GoldRush, BoosterManager), `gang`, `gui` (Menu, Menus, Gui), `listener`
(PlayerListener, ProtectionListener, ResourcePackService), `lobby` (Lobby), `lucky`, `mine`,
`pass` (BattlePass, Milestones), `pickaxe` (PickaxeManager, MiningService, Enchant, PickTier),
`quantum`, `rank`, `tutorial`, `util` (Txt, ItemBuilder, Keys, Fmt), `world` (WorldService,
Layout, LobbyMap, BuildPlan, Holograms, Billboard, VoidGenerator, CellGeometry, `build/*` con
HubBuild, PrisonBuild, MineBuild, MineThemes, PvpBuild, Colosseum, ColosseumBuild).

## 3. Come si compila

- Su una macchina normale con Java 25 e accesso a `repo.papermc.io`: `cd pepita/plugin && mvn package`,
  da cui esce `target/PepitaCore.jar`.
- Nell'ambiente cloud usato finora `repo.papermc.io` era bloccato e c'era solo il JDK 21. Per questo
  `paper-api` è stata compilata dai sorgenti GitHub di PaperMC/Paper (branch `ver/26.2`) e installata in
  `~/.m2`, e il plugin si compilava con `mvn -q package -Dmaven.compiler.release=21`. Nel `pom.xml`
  c'è `<release>${maven.compiler.release}</release>` con default 25. Il bytecode Java 21 gira su Java 25.
- Anteprime delle costruzioni: `cd pepita/tools && ./anteprima.sh <hub|prigione|pvp|colosseo|mine:<tema>> 4 views=se,nw`
  (servono il plugin compilato e la variabile `PAPER_API_CP`). Il tool valida tutti gli stati dei
  blocchi contro la 26.2 (`tools/data/blocks_26.2.json`) e controlla i contratti (portali, punti
  interattivi, spazi liberi, blocchi riservati). Con `file:<percorso>` legge un elenco di blocchi
  `x y z blockdata` (usato per l'anteprima della mappa della lobby).
- **Niente è mai stato provato su un server vero**: c'è stata solo la compilazione, più test isolati
  (estrazione della mappa, parsing degli id).

## 4. Contratti da non rompere

- Gli id del resource pack in `SPEC_PACK.md` (custom_model_data stringa, font `pepita:gui`, `pepita:icons`,
  `pepita:logo`, tooltip `pepita:quantum`, ecc.).
- Le coordinate di `world/Layout.java` e la funzione `CellGeometry.classify`.
- I campi YAML dei giocatori esistenti: si aggiungono campi nuovi, non si rinominano quelli vecchi.
- Nelle costruzioni non usare **sponge, budding_amethyst e lodestone**: col pack hanno texture di gameplay.
- In 26.2 la catena si chiama `iron_chain` (non `chain`).

## 5. Cosa fa il plugin oggi

### Mondi
Cinque mondi vuoti, costruiti dal plugin al primo avvio (flag `mondi.costruiti.<mondo>` in config,
`/pa ricostruisci <mondo>` per rifarli):
- `pepita_hub`: la **lobby**, separata dal Prison (vedi §7). Può essere sostituita da una mappa esterna.
- `pepita`: cortile della prigione (casse, classifiche, incantesimi, negozio, corazza, battle pass,
  portale per le miniere), con **Beppe il Secondino** (NPC del tutorial) in `Layout.PRISON_NPC`.
- `pepita_miniere`: miniere A–Z 29x29 profonde 30, su isole a 8 temi, 128 blocchi l'una dall'altra.
- `pepita_pvp`: la miniera PvP dei Quantum, con una zona sicura al centro.
- `pepita_celle`: colosseo delle celle (10 piani x 32 settori, porte di ferro, ascensori).

### Sistemi di gioco
Rank A–Z, prestigio, evasione (rebirth), incantesimi (anche 6 incantesimi Quantum), 8 tier del piccone,
Quantum solo nella miniera PvP (blocchi duri, "in tasca" finché non si torna nella zona sicura, metà
all'uccisore), corazza Quantum 4 pezzi livelli 0–25, celle (`/cella`, fidati, unisci/dividi, reset,
tassa giornaliera), lucky block, battle pass (50 livelli, missioni, traccia premium a 1200 gemme),
traguardi dei blocchi rotti, keyall casuale ogni 35–45 minuti con bossbar, Corsa all'Oro, casse, gang,
negozio gemme, booster, TAB con logo e incantesimi attivi, cartellone/logo animato, GUI fantasy.

### Economia (dettagli in `ECONOMIA.md`)
I premi in soldi sono in **"minuti di scavo"**: valore medio del blocco della miniera migliore ×
moltiplicatore permanente × 90 blocchi al minuto. Soft cap del moltiplicatore: `1 + 25·(1 − e^(−(m−1)/25))`.
Rendimenti decrescenti dei Quantum: `D/(D+q_ultima_ora)` con D=400. Tassa del 5% su `/paga`.
Contatori per fonte, `/pa economia` e file `economia.log`. Tutti i parametri sono in `config.yml`.

### Skin add-on
- Le skin sono **oggetti separati e scambiabili**, che si montano trascinandoli sull'oggetto (o con
  tasto destro) e lo **potenziano**. Si smontano da `/skin` e tornano oggetti.
- **Piccone**: oggetti carta con `piccone_<id>`. Bonus = base della rarità × moltiplicatore del tier del
  piccone (da ×1 a ×3).
- **Corazza**: un add-on per ogni pezzo (elmo, corpetto, gambali, stivali), id `skin_corazza:<set>:<pezzo>`,
  oggetto di cuoio non indossabile col modello `<set>_<pezzo>`. Ogni add-on potenzia la statistica
  principale del pezzo (elmo esperienza, corpetto soldi, gambali pepite, stivali Quantum):
  `bonus-rarita × peso × (1 + livello × 0,04)`, parametri in `armatura.skin`. Si possono mischiare set diversi.
- Le skin vengono dalla cartella "SKIN" su Google Drive dell'utente (10 set: aureo, smeraldo, glaciale,
  inferno, ametista, abisso, faraone, sakura, tempesta, eclisse), già integrate in `assets/skinpack`
  e nel resource pack.
- Migrazione: il set di corazza indossato va su tutti i pezzi; gli altri set posseduti diventano 4
  add-on ciascuno (deposito di `/skin` se l'inventario è pieno). Campi YAML: `armatura.skin-pezzi`,
  `armatura.skin-migrate`.

## 6. Comandi principali
- Giocatori: `/menu`, `/miniere`, `/miniera`, `/rankup`, `/rankupmax`, `/prestigio`, `/evasione`,
  `/incantesimi`, `/tier`, `/skin`, `/corazza`, `/quantum`, `/casse`, `/negozio`, `/giornaliero`,
  `/battlepass`, `/traguardi`, `/cella …`, `/gang …`, `/paga`, `/top`, `/fly`, `/lobby` e `/hub`
  (vanno alla lobby), `/spawn` (nel Prison torna al cortile), `/prigione`, `/pvp`, `/selettore`.
- Staff (`/pa`, permesso `pepita.admin`): `dai`/`togli`, `chiave`, `vip`, `rank`, `prestigio`,
  `incantesimo`, `skin <g> <id> [piccone|corazza|elmo|corpetto|gambali|stivali]`, `oggetto`, `booster`,
  `corsa`, `reset`, `ricostruisci`, `economia`, `keyall`, `tier`, `corazza`, `battlepass`, `tutorial`,
  `cella`, `ologrammi`, `salva`, `render`, `info`, `reload`, **`lobby <spawn|npc|info>`**.
  L'elenco completo è in `LEGGIMI.txt`.

## 7. Lobby separata dal Prison (ultima richiesta dell'utente)

L'utente vuole una lobby "come i server grandi": entri, sei in una lobby **completamente staccata**
dalla modalità, con solo una **bussola** per scegliere la modalità (per ora solo il Prison).
Con un solo server Falix l'ho fatta come **mondo separato che si comporta come un server a parte**.
Un server separato vero (Velocity più 2 server) è un'opzione futura e non è stato fatto.

Implementazione (`lobby/Lobby.java`):
- All'ingresso si va nella lobby. L'inventario contiene solo la bussola `modalita` (tasto destro apre il
  menu "Scegli la modalità" con la carta PRISON). C'è anche un NPC "PRISON" (Mannequin con la skin del
  guardiano) nel punto `lobby.npc` (o `Layout.HUB_NPC`).
- L'**inventario del Prison** viene salvato in Base64 (`ItemStack.serializeItemsAsBytes`) nei campi
  YAML `lobby.attiva` e `lobby.inventario-prison` quando si entra nella lobby, e ripristinato quando si
  rientra nel Prison. Gli oggetti ricevuti mentre si è in lobby vengono uniti, non persi. Il salvataggio
  su disco è immediato.
- Nella lobby: modalità avventura, inventario bloccato, scoreboard/TAB della lobby, chat con il tag
  [Lobby], niente bossbar (keyall, booster, Corsa all'Oro) e niente messaggi del Prison: tutti i
  `Bukkit.broadcast` del Prison passano da `Txt.broadcastPrison`. I comandi del Prison sono bloccati con
  un suggerimento (insieme `PRISON_ONLY` in `Lobby`).
- Il tutorial ora parte la prima volta che si entra nel Prison. Respawn e cadute nel vuoto restano nel
  Prison.

### Mappa esterna per la lobby (`world/LobbyMap.java`)
- L'utente ha caricato la mappa **"Meridian Base" di Kaizen87**: mondo **1.21.8** (DataVersion 4440),
  superflat di cemento bianco con il pavimento a y=−34, base fantascientifica sull'acqua tra x −97..109 e
  z −84..116, alta fino a y=18.
- Ho preparato **`lobby.zip`** (dato all'utente, **non è nel repo** per la licenza) con solo `level.dat`,
  `region/`, `entities/`, `poi/`, i file di licenza e `pepita-lobby.yml`:
  - `spawn: [25.5, -17, 24.5, 90]` (sala sotto la cupola centrale, guardando la torre al centro)
  - `npc: [21.5, -17, 24.5, -90]`
  - `vuoto-y: -60`
  - `sostituisci`: 3 lodestone (37 −32 112, 41 −32 112, 39 −32 114) → `chiseled_stone_bricks`
- Installazione: l'utente mette lo zip (o la cartella estratta) in `plugins/PepitaCore/mappe/`
  (`lobby.mappa` in config, default `lobby.zip`; se manca usa l'unica mappa presente) e riavvia.
  All'avvio, prima di creare `pepita_hub`, il plugin:
  1. prepara la mappa in una cartella temporanea;
  2. crea/carica il vecchio `pepita_hub`, lo scarica e sposta la sua cartella (`World#getWorldPath()`)
     in `pepita_hub_backup_<data>`;
  3. mette la mappa come cartella `pepita_hub` nella radice del server;
  4. a quel punto Paper, quando il mondo viene creato, fa da solo il **legacy CraftBukkit import**
     (`WorldFolderMigration` → `LegacyCraftBukkitWorldMigration`) e aggiorna i chunk dalla 1.21.8.
  Paper 26.2 tiene i mondi dei plugin come dimensioni (`world/dimensions/<ns>/<nome>`) e **rifiuta
  di importare sopra file esistenti**: per questo il vecchio mondo va spostato prima. Se qualcosa va
  storto, il plugin rimette la lobby di prima. Scrive `lobby.mappa-installata` e
  `mondi.costruiti.pepita_hub: true`; la lobby con una mappa non viene mai ricostruita da HubBuild.
- Con una mappa attiva non compaiono i portali, le scritte e il logo dell'hub del plugin: resta solo un
  benvenuto sopra l'NPC. Qualsiasi portale della mappa porta al Prison.
- **Licenza**: "All rights reserved, Unauthorized commercial use is prohibited"
  (https://www.patreon.com/posts/license-usage-70505678). Il server ha un negozio di gemme, quindi
  l'utente deve verificare i termini. Non mettere la mappa su GitHub né dentro il jar.

## 7b. Rete Velocity (dal 8 ottobre 2026)

L'utente passa a un hosting nuovo ("gamehosting") e vuole **server separati**. Fatto:
- **Proxy Velocity 3.6.0** (prima versione con i client 26.2, protocollo 776): modello in `rete/proxy/velocity.toml`
  (`player-info-forwarding-mode = "MODERN"`, server `lobby` e `prison`, `try = ["lobby"]`,
  `bungee-plugin-message-channel = true`). ViaVersion/ViaBackwards vanno sul proxy.
- **Server Lobby**: Paper 26.2 con il nuovo plugin **PepitaLobby** (`pepita/lobby/`, jar `PepitaLobby.jar`).
  La mappa Meridian è il mondo principale (`lobby-mondo.zip` → cartella `world`, aggiornata da Paper all'avvio).
  Bussola "Modalità" (menu con le modalità di `config.yml → modalita`, per ora `prison`), NPC per modalità,
  scoreboard/TAB con i giocatori per server (sottocanale `PlayerCount` del canale `BungeeCord`), chat propria,
  protezioni totali, `/lobby`, `/modalita`, `/prigione`, `/fly`, staff `/pepitalobby spawn|npc|info|reload`.
  Il passaggio a un altro server usa il sottocanale `Connect`.
- **Server Prison**: PepitaCore con **`rete.attiva: true`**: niente mondo `pepita_hub`, all'ingresso si va al cortile
  (tutorial al primo ingresso), `/lobby` e `/hub` (e la voce Lobby del selettore) mandano al server `rete.server-lobby`
  con `PepitaCore#connect`. Con `rete.attiva: false` resta tutto come prima (server unico con la lobby nel mondo hub).
- Backend: `online-mode=false` e `config/paper-global.yml → proxies.velocity.enabled: true` con il segreto di
  `forwarding.secret` (modelli in `rete/lobby` e `rete/prison`). Guida completa per l'utente: `RETE.md`.
- Ancora da fare in futuro: gradi condivisi (LuckPerms + MySQL), chat globale, gemme in un database comune.

## 8. Stato attuale e cose aperte

0. **Rete appena consegnata, non ancora installata**: seguire `RETE.md` con l'utente sull'hosting nuovo.
1. **(Solo server unico)** l'utente diceva "la lobby è rimasta la stessa" dopo aver provato a installare la
   mappa. Cause probabili: jar vecchio ancora caricato (o due jar), zip non in `plugins/PepitaCore/mappe/`,
   nome diverso, zip estratto da Falix, `/reload` invece del riavvio. Il commit `2822f65` rende il
   rilevamento tollerante e scrive sempre in console righe `[PepitaCore] Lobby: …`. **Bisogna farsi
   mandare quelle righe del log di avvio** (e l'output di `/pa lobby info`) per capire.
2. Nulla è stato provato su un server reale. Le parti più delicate da verificare in gioco sono: import
   della mappa, scambio dell'inventario lobby/Prison, trascinamento delle skin add-on, menu e NPC.
3. Possibili sviluppi futuri (solo se l'utente li chiede): server separati veri con Velocity, chat
   separata per modalità, altre modalità oltre al Prison nel menu della lobby.

## 9. Altre risposte già date
- Java 25 su Falix: pannello → Settings → scheda Environment → versione di Java.
- Sbannare un IP: `banlist ips` per vedere l'IP, poi `pardon-ip <ip>` (in console senza `/`). Se c'è anche
  il ban per nome: `pardon <nome>`. Non modificare `banned-ips.json` a server acceso.
- Mappe online: dall'ambiente cloud usato finora erano bloccati Planet Minecraft, CurseForge, Modrinth,
  BuiltByBit, mc-packs.net e falixnodes.net (proxy con errore 403). Era raggiungibile solo GitHub.
  Per questo l'utente scarica le mappe da solo e le passa in chat o su Google Drive (che è leggibile
  con il connettore).

## 10. File dati all'utente
`PepitaCore.jar` (ultimo: commit `2822f65`), `lobby.zip`, `LEGGIMI.txt`, `ECONOMIA.md`, i jar di ViaVersion
e ViaBackwards (li aveva caricati lui), anteprime PNG dell'hub e della mappa Meridian.

---

### Prompt da incollare nella nuova sessione

> Sto lavorando al mio server Minecraft prison "Pepita" (Paper 26.2, Java 25, Falix free) con il plugin
> PepitaCore. Il codice è nel repo GitHub `lifechangejnoc/lifechangedajnoc-privacy`, cartella `pepita/`,
> branch `claude/gallant-einstein-5vfyov`. Leggi prima `pepita/CONTESTO.md` (contesto completo),
> poi `pepita/LEGGIMI.txt` ed `pepita/ECONOMIA.md`. Rispondimi in italiano. Il problema aperto è che,
> dopo aver messo `lobby.zip` in `plugins/PepitaCore/mappe/`, la lobby è rimasta quella vecchia: ecco
> le righe della console che iniziano con `[PepitaCore] Lobby:` …
