#!/bin/sh
# Anteprime e validazione delle costruzioni di Pepita.
# Uso: ./anteprima.sh <build> [scala] [opzioni]   es: ./anteprima.sh hub 4 views=se,nw
#      build: hub | prigione | pvp | colosseo | mine:<tema> (pietra, ardesia, nera, prismarino, end, prestigio, vip, evasione)
# Richiede: plugin compilato (mvn package in ../plugin) e PAPER_API_CP con il classpath di paper-api.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
CLASSES="$HERE/../plugin/target/classes"
CP="$CLASSES:${PAPER_API_CP}"
OUT="$HERE/out"
mkdir -p "$HERE/bin" "$OUT"
javac -nowarn -d "$HERE/bin" -cp "$CP" "$HERE/src/PlanTool.java"
java -Djava.awt.headless=true -Dpepita.tools="$HERE" -cp "$HERE/bin:$CP" PlanTool "$1" "$OUT" "${2:-4}" $3 $4 $5
