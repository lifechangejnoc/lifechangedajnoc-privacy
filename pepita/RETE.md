# La rete di Pepita: proxy + Lobby + Prison

Dalla rete in poi Pepita sono **tre server** che lavorano insieme, come i server grandi:

```
                 pepitamc.it (porta 25565)
                          │
                ┌─────────▼─────────┐
                │   PROXY Velocity   │  ViaVersion, ViaBackwards
                └───────┬─────┬──────┘
          si entra qui  │     │  /prigione, bussola, NPC
                ┌───────▼─┐ ┌─▼────────┐
                │  LOBBY  │ │  PRISON  │
                │ Paper   │ │ Paper    │
                │ Pepita- │ │ Pepita-  │
                │ Lobby   │ │ Core     │
                └─────────┘ └──────────┘
                     ▲ /lobby, /hub ┘
```

- I giocatori si collegano **solo al proxy**: il proxy li manda nella **Lobby**.
- Nella Lobby hanno solo la bussola "Modalità". Con la bussola, con l'NPC "PRISON" o con `/prigione`
  passano al server **Prison**. Dal Prison tornano con `/lobby` o `/hub`.
- Ogni server ha la sua chat, il suo TAB e il suo inventario: nella Lobby non c'è niente del Prison.
- I dati del Prison (soldi, rank, skin, celle…) restano sul server Prison.

## 1. I tre server da creare sull'hosting

| Server | Software | Java | RAM consigliata | Plugin |
|---|---|---|---|---|
| **Proxy** | Velocity **3.6.0** (o più recente) | 21 o più | 512 MB – 1 GB | ViaVersion 5.12.0, ViaBackwards 5.12.0 |
| **Lobby** | Paper **26.2** | 25 | 1,5 – 2 GB | PepitaLobby.jar |
| **Prison** | Paper **26.2** | 25 | 4 GB o più | PepitaCore.jar |

Velocity si scarica da https://papermc.io/downloads/velocity (la 3.6.0 è la prima che supporta i client 26.2).
Il dominio `pepitamc.it` deve puntare al **proxy** (record A sull'IP del proxy; se la porta non è 25565
serve anche un record SRV `_minecraft._tcp`).

## 2. Proxy (Velocity)

1. Installa Velocity e avvialo **una volta**: crea `velocity.toml` e `forwarding.secret`. Poi fermalo.
2. Sostituisci `velocity.toml` con quello di `rete/proxy/velocity.toml` e cambia solo:
   - `bind`: la porta del proxy che ti dà l'hosting (`0.0.0.0:PORTA`);
   - nella sezione `[servers]`: `lobby = "IP:PORTA"` e `prison = "IP:PORTA"` dei due server Paper
     (indirizzi **interni** se l'hosting li dà, altrimenti quelli pubblici).
   - I nomi `lobby` e `prison` non vanno cambiati: li usano i plugin.
3. Metti `ViaVersion-5.12.0.jar` e `ViaBackwards-5.12.0.jar` nella cartella `plugins/` del proxy
   (sono gli stessi file che usavi su Paper) e **toglili** da Lobby e Prison.
4. Apri `forwarding.secret` e copia il testo: serve ai due server Paper.
5. Avvia il proxy.

## 3. Server Lobby

1. A server **spento**, in `server.properties` cambia le righe di `rete/lobby/server.properties-da-cambiare.txt`
   (soprattutto `online-mode=false`). In `bukkit.yml` metti `allow-end: false`.
2. In `config/paper-global.yml` sostituisci la sezione `proxies:` con `rete/lobby/paper-global-proxies.yml`
   e incolla il segreto di `forwarding.secret`.
   (Se `config/paper-global.yml` non c'è ancora, avvia e ferma il server una volta.)
3. **Mappa**: cancella le cartelle `world`, `world_nether` e `world_the_end` se ci sono, carica
   `lobby-mondo.zip` nella cartella principale del server ed estrailo: deve comparire la cartella `world`
   con dentro `level.dat` e `region`. Al primo avvio Paper la aggiorna dalla 1.21.8 (in console compare
   "World storage migration is required during startup... Continuing in 30 seconds": è normale, aspetta).
4. Metti `PepitaLobby.jar` in `plugins/` e avvia.
5. Console: `op <tuo nome>`. Entra (sempre dal proxy) e controlla spawn e NPC. Per spostarli: mettiti nel
   punto giusto, guarda nella direzione giusta e scrivi `/pepitalobby spawn` oppure `/pepitalobby npc prison`.
   Lo staff in creativa (`/gamemode creative`) può costruire e toccare tutto.

## 4. Server Prison

1. A server spento cambia `server.properties` con `rete/prison/server.properties-da-cambiare.txt`
   e `config/paper-global.yml` con `rete/prison/paper-global-proxies.yml` (stesso segreto).
2. **Se arrivi dal server Falix**: copia dal vecchio server, a server spento, la cartella `world` (in Paper
   26.2 dentro `world/dimensions/` ci sono anche i mondi di Pepita), le eventuali cartelle che iniziano con
   `pepita` e la cartella `plugins/PepitaCore` (dati dei giocatori, celle, gang, config). Così non si perde
   niente. Le cartelle `pepita_hub_backup_...` non servono.
3. Metti `PepitaCore.jar` in `plugins/` e in `plugins/PepitaCore/config.yml` imposta:
   ```yaml
   rete:
     attiva: true
     server-lobby: "lobby"
   ```
   Con `rete.attiva: true` il Prison non crea più il mondo della lobby, all'ingresso porta al cortile
   (al primo ingresso parte il tutorial di Beppe) e `/lobby` e `/hub` mandano al server della lobby.
4. Avvia e fai `op <tuo nome>` anche qui: **op e permessi sono separati per ogni server**.

## 5. Sicurezza (importante)

- Con `online-mode=false` i server Paper si fidano del proxy: per questo `proxies.velocity.enabled: true`
  con il segreto giusto è **obbligatorio**. Così chi prova a entrare direttamente nella Lobby o nel Prison
  viene buttato fuori ("This server requires you to connect with Velocity"). Non spegnere mai la sezione
  velocity lasciando `online-mode=false`: chiunque potrebbe entrare con il nome di uno staff.
- Non condividere `forwarding.secret`. Se l'hosting permette di non esporre le porte di Lobby e Prison
  su internet, meglio ancora.

## 6. Aggiungere una modalità (in futuro)

1. Crea il server (Paper con i suoi plugin) e configuralo come la Lobby/Prison (online-mode=false + velocity).
2. Aggiungilo in `velocity.toml` → `[servers]`, per esempio `skyblock = "IP:PORTA"`.
3. In `plugins/PepitaLobby/config.yml` → `modalita:` copia il blocco `prison`, chiamalo `skyblock`,
   metti `server: "skyblock"`, `slot`, nome, icona e descrizione (con `pronta: false` appare "in arrivo").
4. `/pepitalobby reload`.

## 7. Problemi comuni

| Cosa vedi | Perché | Cosa fare |
|---|---|---|
| "This server requires you to connect with Velocity" | Stai entrando direttamente in Lobby o Prison | Entra dall'indirizzo del proxy |
| Il proxy dice "Unable to connect you to lobby" | IP/porta sbagliati in `[servers]` o Lobby spenta | Controlla `velocity.toml` e che la Lobby sia accesa |
| Login rifiutato con un errore di "forwarding" | Il segreto non è uguale ovunque | Ricopia `forwarding.secret` nei due `paper-global.yml` e riavvia |
| Nella Lobby i giocatori in gioco sono "?" | Il proxy non risponde ai messaggi | In `velocity.toml` deve esserci `bungee-plugin-message-channel = true` |
| `/lobby` dal Prison non fa niente | `rete.attiva` non è `true` sul Prison | Imposta `rete.attiva: true` e riavvia il Prison |
| UUID diversi / dati persi entrando | `player-info-forwarding-mode` non è `MODERN` | Mettilo `MODERN` nel proxy e attiva velocity sui server |

## 8. Cosa resta da fare più avanti (non urgente)

- **Gradi condivisi tra i server** (VIP visibile anche in Lobby): LuckPerms su proxy e server con un database
  MySQL comune, se l'hosting lo offre.
- **Chat globale** tra i server: un plugin per Velocity, se la si vuole.
- La valuta premium (gemme) per ora vive solo nel Prison: se arriveranno altre modalità andrà spostata in un
  database comune.
