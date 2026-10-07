# Economia di Pepita

Questo documento spiega come girano i soldi, le pepite, le gemme e i Quantum in PepitaCore v2,
con le formule usate dal codice e le tabelle di riferimento calcolate con la `config.yml` di default.
Tutti i parametri citati si cambiano in `plugins/PepitaCore/config.yml` e si ricaricano con `/pa reload`.

## 1. Le quattro valute

| Valuta | Simbolo | Come si ottiene | Dove si spende | Ruolo |
|---|---|---|---|---|
| **Soldi** | `$` | Scavo nelle miniere A–Z (vendita automatica), casse, premi | Rank, prestigio, evasione, tier del piccone, celle, arredo, tassa delle celle | Progressione principale |
| **Pepite** | `✦` | Scavo (incantesimo Cercapepite e moltiplicatori), lucky block, casse, battle pass | Incantesimi normali, tier del piccone | Potenziamento del piccone |
| **Gemme** | `◆` | Negozio (acquisto reale), poche in gioco: giornaliero, prestigio, evasione, battle pass, traguardi | VIP, chiavi, booster, skin, pass premium | Valuta premium, solo cosmetica e comodità |
| **Quantum** | `⚛` | **Solo** la Miniera PvP (Minerale e Nucleo Quantico), uccisioni in PvP, pochi premi | Incantesimi Quantum, corazza (0–25) | Valuta scarsa e contesa |

Ogni movimento passa da `Economy.give/take` con il nome della fonte o dell'uscita, così il server
sa sempre da dove arriva e dove finisce il denaro (vedi §8).

## 2. L'unità di misura: il "minuto di scavo"

Nessun premio in soldi è un numero fisso: è espresso in **minuti di scavo** del giocatore che lo riceve.
In questo modo un premio della cassa vale la stessa *fatica* a rank A e a rank Z, e i premi non
diventano mai inutili o esagerati.

```
1 minuto di scavo = valore medio di un blocco della miniera migliore accessibile
                    × moltiplicatore soldi permanente (con soft cap, senza booster temporanei)
                    × blocchi al minuto di riferimento (economia.blocchi-minuto-riferimento = 90)
```

- I booster temporanei, la Corsa all'Oro e i booster globali **non** entrano nel calcolo: altrimenti
  aprire le casse durante un booster raddoppierebbe anche i premi.
- 90 blocchi/minuto è il ritmo di un piccone senza incantesimi d'area. I giocatori con Esplosivo,
  Laser, Tunnel ecc. rompono molti più blocchi: per loro un "minuto di scavo" passa più in fretta.

Premi espressi in minuti (default):

| Premio | Minuti di scavo | Parametro |
|---|---|---|
| Ricompensa giornaliera | 20 (×2 con il VIP più alto) | `giornaliero.minuti-soldi` |
| Tutorial completato | 20 + 1.500 ✦ | `tutorial.premio-minuti`, `tutorial.premio-pepite` |
| Riscatto Pepita d'Oro | 45 | `corsa-all-oro.pepita-minuti` |
| Lucky block comune / raro / leggendario | 2–5 / 8–15 / 25–44 | codice `LuckyBlocks` |
| Traguardi dei blocchi rotti (12 livelli) | 5, 10, 15, 20, 30, 40, 60, 80, 120, 160, 240, 360 | codice `Milestones` |
| Casse e battle pass | definiti per premio, sempre in minuti | codice `CrateManager`, `BattlePass` |

## 3. Rank, miniere e tempi

- Costo del rank `r` (A=0 … Y=24): `rank.costo-base × rank.crescita^r × (1 + prestigio × prestigio.aumento-costo) × evasione.aumento-costo^evasioni`
- Costo del prestigio: costo "rank 25" × `prestigio.costo-moltiplicatore`.
- Il valore medio di un blocco cresce di circa ×1,32 per miniera, il costo del rank di ×1,5:
  ogni rank richiede quindi un po' più di tempo del precedente (da ~3 a ~80 minuti).

Tabella di riferimento (prestigio 0, nessun bonus, 90 blocchi/min):

| Rank | Valore medio blocco | 1 minuto di scavo | Costo rank successivo | Minuti di scavo |
|---|---|---|---|---|
| A | $13,3 | $1,19K | $4,00K | 3,4 |
| B | $17,5 | $1,58K | $6,00K | 3,8 |
| C | $23,1 | $2,08K | $9,00K | 4,3 |
| D | $30,5 | $2,75K | $13,50K | 4,9 |
| E | $40,3 | $3,62K | $20,25K | 5,6 |
| F | $53,1 | $4,78K | $30,38K | 6,4 |
| G | $70,2 | $6,31K | $45,56K | 7,2 |
| H | $92,6 | $8,33K | $68,34K | 8,2 |
| I | $122,2 | $11,00K | $102,52K | 9,3 |
| J | $161,4 | $14,52K | $153,77K | 10,6 |
| K | $213,0 | $19,17K | $230,66K | 12,0 |
| L | $281,1 | $25,30K | $345,99K | 13,7 |
| M | $371,1 | $33,40K | $518,99K | 15,5 |
| N | $489,9 | $44,09K | $778,48K | 17,7 |
| O | $646,6 | $58,20K | $1,17M | 20,1 |
| P | $853,5 | $76,82K | $1,75M | 22,8 |
| Q | $1.126,7 | $101,40K | $2,63M | 25,9 |
| R | $1.487,2 | $133,85K | $3,94M | 29,4 |
| S | $1.963,1 | $176,68K | $5,91M | 33,5 |
| T | $2.591,3 | $233,22K | $8,87M | 38,0 |
| U | $3.420,5 | $307,84K | $13,30M | 43,2 |
| V | $4.515,1 | $406,36K | $19,95M | 49,1 |
| W | $5.959,9 | $536,39K | $29,93M | 55,8 |
| X | $7.867,0 | $708,03K | $44,89M | 63,4 |
| Y | $10.384,5 | $934,60K | $67,34M | 72,0 |
| Z | $13.707,5 | $1,23M | prestigio $252,51M | 204,7 |

Durata di un ciclo completo A→Z + prestigio, in minuti di scavo (solo bonus di prestigio ed evasione):

| Prestigio | Evasioni | Moltiplicatore effettivo | A→Z | Prestigio | Totale |
|---|---|---|---|---|---|
| 0 | 0 | ×1,00 | 576 | 205 | ~13 ore |
| 1 | 0 | ×1,10 | 785 | 279 | ~18 ore |
| 5 | 0 | ×1,50 | 1.348 | 479 | ~30 ore |
| 10 | 0 | ×1,98 | 1.745 | 620 | ~39 ore |
| 10 | 1 | ×2,22 | 4.670 | 1.660 | ~105 ore |
| 50 | 2 | ×5,94 | 22.695 | 8.067 | ~513 ore |

Sono ore *a 90 blocchi al minuto*: con gli incantesimi d'area un giocatore attivo va 3–6 volte più veloce.
Il costo cresce più in fretta del bonus (+50% contro +10% per prestigio), quindi i cicli si allungano:
è voluto, perché il prestigio resti un traguardo e non una routine.

**Evasione** (rebirth): richiede `evasione.prestigio-richiesto` (10), azzera rank e prestigio,
moltiplica i costi per `evasione.aumento-costo` (×3) e dà +25% soldi, +50% pepite e 25 gemme.

## 4. Soft cap del moltiplicatore soldi

Tutti i bonus permanenti si sommano (prestigio, evasioni, VIP, livello del piccone, skin, corazza),
poi si moltiplicano booster personali, booster globali e Corsa all'Oro. Il totale `m` passa per un
soft cap che lascia invariato ×1 e frena solo la parte alta:

```
effettivo = 1 + cap × (1 − e^(−(m − 1) / cap))      con cap = economia.soft-cap = 25
```

| m grezzo | ×1 | ×1,5 | ×2 | ×3 | ×5 | ×8 | ×10 | ×15 | ×20 | ×30 | ×50 | ×100 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| effettivo | 1,00 | 1,50 | 1,98 | 2,92 | 4,70 | 7,11 | 8,56 | 11,72 | 14,31 | 18,16 | 22,48 | 25,52 |

Fino a ×3 il cap quasi non si sente, oltre ×20 ogni bonus in più rende sempre meno, e il
moltiplicatore non supera mai ~×26. Mettendo `soft-cap: 0` il cap si disattiva.

## 5. Uscite (sink)

| Uscita | Valuta | Chiave nel log | Note |
|---|---|---|---|
| Rankup / prestigio / evasione | Soldi | `rankup`, `prestigio`, `evasione` | Il prestigio e l'evasione azzerano anche i soldi rimasti |
| Tier del piccone | Soldi + Pepite | `tier` | Vedi §6 |
| Incantesimi normali | Pepite | `incantesimi` | `costo(l) = (base + inc·l) × crescita^l` |
| Incantesimi Quantum, corazza | Quantum | `incantesimi`, `corazza` | Vedi §7 |
| Acquisto cella | Soldi | `celle` | `45 + 15 × piano` minuti di scavo |
| Tassa giornaliera delle celle | Soldi | `tassa-celle` | 3 minuti al giorno per cella, pagata quando sei online |
| Arredamento della cella | Soldi | `arredo` | 0,5 minuti ogni 8 blocchi (× prezzo del blocco) |
| Tassa su `/paga` e `/pepite paga` | Soldi, Pepite | `tassa-paga` | 5%: esce dall'economia, chi riceve ottiene il netto |
| Negozio | Gemme | `negozio` | VIP, chiavi, booster, skin, starter pack, pass premium |
| Morte in PvP | Quantum | — | I Quantum in tasca si perdono: metà va all'uccisore, metà sparisce (non erano ancora contati come entrata: lo diventano solo quando vengono messi al sicuro, chiave `pvp`) |

Le celle abbandonate (`celle.giorni-abbandono`, 14 giorni) tornano libere: nessuno accumula celle
senza giocare.

## 6. Tier del piccone

Otto tier (Legno → Pietra → Ferro → Oro → Diamante → Netherite → Pepita → Quantum). Ogni tier
moltiplica i bonus della skin applicata (`bonus = base della skin × moltiplicatore del tier`).

| Passaggio | Minuti di scavo | Pepite |
|---|---|---|
| Legno → Pietra | 8 | 300 |
| Pietra → Ferro | 20 | 2.000 |
| Ferro → Oro | 45 | 10.000 |
| Oro → Diamante | 90 | 40.000 |
| Diamante → Netherite | 180 | 150.000 |
| Netherite → Pepita | 360 | 600.000 |
| Pepita → Quantum | 720 | 2.500.000 |

Parametri: `tier.costi-minuti`, `tier.costi-pepite`. Le skin sono oggetti staccabili: si applicano
al piccone dal menu `/skin` e si possono riprendere senza perderle.

## 7. Quantum

I Quantum esistono **solo** nella Miniera PvP (`/pvp`), su due blocchi duri:

| Blocco | Quantum base | Tempo di rottura |
|---|---|---|
| Minerale Quantico | `quantum.minerale` = 1 | ~2,5 s (`quantum.secondi-minerale`) |
| Nucleo Quantico | `quantum.nucleo` = 4 | ~6 s (circa 2,3 volte tanto) |

Il tempo di rottura è imposto da un modificatore temporaneo dell'attributo `block_break_speed`,
quindi Efficienza e Rapidità non lo accorciano: solo l'incantesimo Frantumazione.

**In tasca:** i Quantum scavati restano "in tasca" finché non torni nella zona sicura o esci dal mondo
PvP. Se muori li perdi: `quantum.bottino-uccisione` (50%) va a chi ti ha ucciso, il resto sparisce.
Chi esce dal server entro 15 secondi da un colpo subito li perde allo stesso modo (metà a chi lo stava attaccando).

**Rendimenti decrescenti** per giocatore, calcolati sui Quantum raccolti nell'ultima ora:

```
resa = D / (D + quantum dell'ultima ora)        con D = quantum.dimezzamento-ora = 400
```

| Quantum nell'ultima ora | 0 | 100 | 200 | 400 | 800 | 1.600 |
|---|---|---|---|---|---|---|
| Resa | 100% | 80% | 67% | 50% | 33% | 20% |

Su un'ora intera: 200 Quantum grezzi diventano ~166, 400 → ~293, 800 → ~494. Raddoppiare le ore
passate a farmare non raddoppia il guadagno, e chi gioca poco non resta troppo indietro.

**Incantesimi Quantum** (costo `(base + inc·l) × crescita^l` in Quantum):

| Incantesimo | Livelli | Primo livello | Ultimo livello | Totale |
|---|---|---|---|---|
| Frantumazione | 25 | 30 | 2.641 | 17.783 |
| Superposizione | 50 | 40 | 46.103 | 413.696 |
| Osservatore | 50 | 35 | 35.111 | 316.091 |
| Risonanza | 20 | 60 | 5.788 | 30.864 |
| Tunnel | 30 | 50 | 9.095 | 64.442 |
| Collasso | 10 | 400 | 19.362 | 58.227 |

**Corazza Quantum** (4 pezzi, livelli 0–25): costo del livello `L = armatura.costo-base × armatura.costo-crescita^L`.
Ogni pezzo spinge una statistica (elmo = esperienza, corpetto = soldi, gambali = pepite, stivali = Quantum)
di `armatura.bonus-livello.*` per livello.

| Livello | 1 | 5 | 10 | 15 | 20 | 25 |
|---|---|---|---|---|---|---|
| Costo del livello | 40 | 78 | 178 | 406 | 929 | 2.125 |
| Totale per un pezzo | 40 | 288 | 945 | 2.445 | 5.873 | 13.714 |

Corazza completa a 25 su tutti e quattro i pezzi: **54.856 Quantum**.

## 8. Gemme

- Si comprano sul negozio esterno (`negozio.link`). In gioco se ne trovano poche: giornaliero (5, il doppio con il VIP più alto),
  prestigio (3), evasione (25), traguardi e battle pass.
- Si spendono solo in cose che non rompono la progressione: VIP, chiavi, booster personali (×2 per un'ora = 200 ◆),
  booster globale (800 ◆), skin, starter pack (900 ◆), pass premium (`battlepass.prezzo-premium` = 1.200 ◆).
- Ogni acquisto viene scritto nel log del server (`[Negozio] … per N gemme`) e somma il campo `gemmeSpese` del giocatore.

## 9. Monitoraggio

- Ogni movimento è contato per valuta e per fonte, ora per ora.
- **`/pa economia`** mostra l'ora corrente, le ultime 24 ore e quanto hanno in tasca i giocatori online.
- Il file `plugins/PepitaCore/economia.log` riceve una riga per ogni ora, per esempio:

  ```
  2026-10-07 21:00 | online max 34 | soldi +812,4M (scavo 701,2M, casse 64,0M, battlepass 30,1M, traguardi 9,8M) -790,2M (rankup 610,5M, prestigio 140,0M, celle 22,3M, tassa-paga 4,1M) | quantum +9.120 (pvp 8.800, pvp-uccisioni 320) -8.400 (corazza 6.100, incantesimi 2.300)
  ```

- Cosa guardare:
  - **Soldi in entrata molto sopra le uscite per giorni**: inflazione. Alza `rank.crescita` o i costi delle celle,
    oppure abbassa `economia.soft-cap`.
  - **Una fonte che domina** (per esempio `casse` sopra `scavo`): i premi di quella fonte sono troppo alti.
  - **Quantum in uscita sempre bassi**: i giocatori li accumulano, abbassa `quantum.dimezzamento-ora`.

## 10. Parametri principali in `config.yml`

| Parametro | Default | Effetto |
|---|---|---|
| `economia.blocchi-minuto-riferimento` | 90 | Valore del "minuto di scavo" |
| `economia.soft-cap` | 25 | Tetto morbido del moltiplicatore soldi (0 = disattivato) |
| `economia.tassa-paga` | 0.05 | Tassa su `/paga` |
| `rank.costo-base`, `rank.crescita` | 4000, 1.5 | Costo dei rank |
| `prestigio.costo-moltiplicatore`, `aumento-costo`, `bonus-soldi` | 2.5, 0.5, 0.10 | Prestigio |
| `evasione.*` | 10, 3.0, 0.25, 0.50 | Evasione |
| `tier.costi-minuti`, `tier.costi-pepite` | vedi §6 | Tier del piccone |
| `quantum.minerale`, `nucleo`, `dimezzamento-ora`, `secondi-minerale`, `bottino-uccisione` | 1, 4, 400, 2.5, 0.5 | Quantum |
| `armatura.*` | 25, 40, 1.18 | Corazza |
| `celle.*` | 45, 15, 3, 14 | Prezzi e tassa delle celle |
| `lucky.ogni-blocchi`, `lucky.probabilita.*` | 4000 | Frequenza e rarità dei lucky block |
| `battlepass.*` | 50 livelli, 1.000 XP, 1.200 ◆ | Battle pass |
| `keyall.minuti-min`, `minuti-max` | 35, 45 | Intervallo del keyall casuale |
| `prezzi-blocchi` | vuoto | Prezzi personalizzati dei blocchi (sovrascrivono quelli di default) |
