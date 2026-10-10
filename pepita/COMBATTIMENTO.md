# Combattimento 1.8.9 su Pepita (plugin PepitaCombat)

Il server resta in 26.2, ma i colpi diventano quelli della 1.8.9:

| Cosa | 26.2 normale | Con PepitaCombat |
|---|---|---|
| Tempo di ricarica dell'attacco | sì (barra sotto il mirino) | **no**: si colpisce a ogni click |
| Knockback | formula 1.9+ | **formula 1.8** (profilo regolabile) |
| Invulnerabilità tra i colpi | 10 tick | regolabile per le combo (profilo) |
| Colpo a spazzata della spada | sì | **no** (niente danno, spinta, effetto e suono) |
| Critici | solo da fermi o camminando | **anche correndo**, come in 1.8 |
| Danni delle armi | 1.9+ (asce fortissime) | **1.8** (spade le più forti, asce deboli) |
| Affilatezza | +0,5 per livello +0,5 | **+1,25 per livello** |
| Parata | scudo | **tasto destro con la spada** (danno dimezzato), scudi disattivati |
| Canna da pesca | tira i giocatori | **li spinge** e conta per le combo, come in 1.8 |
| Suoni dei colpi | colpo forte/debole, spazzata, critico… | **spariti** (resta solo il suono di chi viene colpito, come in 1.8) |

## Dove metterlo

1. **`PepitaCombat.jar`** → cartella `plugins/` del server **Prison** (dove c'è il PvP). Nella Lobby non serve.
   Riavvia il server.
2. **`PepitaCombat-pack.zip`** (i suoni della 1.8) → caricalo su https://mc-packs.net e copia il **link diretto**.
   Incollalo in `plugins/PepitaCombat/config.yml`:
   ```yaml
   resource-pack:
     url: "<il link copiato>"
     sha1: ""
   ```
   Poi `/pepitacombat reload`. Il pack si **aggiunge** a quello di Pepita (non lo sostituisce) e arriva ai
   giocatori quando entrano nel Prison.

## Comandi (staff, permesso `pepita.admin`)

- `/pepitacombat`: profilo attivo e valori del knockback
- `/pepitacombat profilo <pepita|1.8|combo>`: cambia profilo al volo
- `/pepitacombat reload`: ricarica `config.yml`

## I profili

| Profilo | Per cosa | Orizzontale | Verticale | Sprint | Invulnerabilità |
|---|---|---|---|---|---|
| `pepita` (predefinito) | bilanciato: combo possibili ma non infinite | 0,38 | 0,36 | 0,46 | 19 tick |
| `1.8` | identico alla 1.8.9 vanilla | 0,40 | 0,40 | 0,50 | 20 tick |
| `combo` | combo lunghe, stile server "combo" | 0,34 | 0,31 | 0,42 | 16 tick |

Tutti i valori sono in `config.yml` (`profili:`) e si possono creare profili nuovi copiando un blocco.

## Da sapere

- I suoni nuovi degli attacchi li riproduce il **client**: senza il pack li sente ancora. Per questo il pack serve.
- Tutto il resto (danni, knockback, niente ricarica) vale anche per chi non ha il pack.
- Nel Prison si combatte soprattutto col **piccone**: con i danni 1.8 conta il materiale del tier
  (diamante 6, netherite 7). Si possono cambiare in `danni-1.8.armi`.
- Se vuoi far entrare anche chi gioca **con il client 1.8.9** (Lunar, Badlion…), sul proxy serve in più
  **ViaRewind** insieme a ViaVersion e ViaBackwards. Attenzione però: i client 1.8 non vedono le skin, i menu
  decorati e il logo del resource pack di Pepita.
