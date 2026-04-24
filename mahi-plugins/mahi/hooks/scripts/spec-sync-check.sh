#!/bin/bash
set -euo pipefail

# Lightweight hook: inject active item reminder on every user prompt.
# Reads .mahi/local/active.json — the single source of truth for the active item (spec or ADR).

LOCAL_ACTIVE="${CLAUDE_PROJECT_DIR:-.}/.mahi/local/active.json"

# Quick exit if no active item
[ -f "$LOCAL_ACTIVE" ] || exit 0

# Read type, id and path
ITEM_TYPE=$(grep -o '"type"[[:space:]]*:[[:space:]]*"[^"]*"' "$LOCAL_ACTIVE" | sed 's/.*"type"[[:space:]]*:[[:space:]]*"//;s/"//')
ITEM_ID=$(grep -o '"id"[[:space:]]*:[[:space:]]*"[^"]*"' "$LOCAL_ACTIVE" | sed 's/.*"id"[[:space:]]*:[[:space:]]*"//;s/"//')
ITEM_PATH=$(grep -o '"path"[[:space:]]*:[[:space:]]*"[^"]*"' "$LOCAL_ACTIVE" | sed 's/.*"path"[[:space:]]*:[[:space:]]*"//;s/"//')

[ -z "$ITEM_ID" ] && exit 0

# Read current phase from state.json
STATE_FILE="${CLAUDE_PROJECT_DIR:-.}/$ITEM_PATH/state.json"
if [ -f "$STATE_FILE" ]; then
  PHASE=$(grep -o '"currentPhase"[[:space:]]*:[[:space:]]*"[^"]*"' "$STATE_FILE" | head -1 | sed 's/.*"currentPhase"[[:space:]]*:[[:space:]]*"//;s/"//')
else
  PHASE="inconnu"
fi

if [ "$ITEM_TYPE" = "adr" ]; then
  cat <<EOF
{"systemMessage": "ADR actif : ${ITEM_ID} (phase : ${PHASE}). Si le message utilisateur contient un retour affectant le cadrage, les options ou la décision, mettre à jour les documents concernés et loguer dans log.md."}
EOF
else
  cat <<EOF
{"systemMessage": "Spec actif : ${ITEM_ID} (phase : ${PHASE}). Si le message utilisateur contient un retour affectant les exigences, la conception ou le plan, mettre à jour les documents concernés et loguer dans state.json changelog."}
EOF
fi
