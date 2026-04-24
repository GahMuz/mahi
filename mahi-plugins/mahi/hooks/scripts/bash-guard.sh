#!/bin/bash
set -euo pipefail

# Fail-closed: if this hook crashes, deny the action
trap 'echo "{\"hookSpecificOutput\":{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":\"Hook error - fail-closed\"}}"' ERR

# Read the tool input from stdin
INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")

if [ -z "$COMMAND" ]; then
  exit 0
fi

# Block destructive patterns
BLOCKED_PATTERNS=(
  "sudo "
  "su -"
  "doas "
  "pkexec "
  "rm -rf /"
  "rm -rf *"
  "rm -rf ."
  "chmod 777"
  "chmod -R 777"
  ":(){ :|:& };:"
  "curl.*\\|.*bash"
  "wget.*\\|.*bash"
  "eval \$"
  "dd if="
  "mkfs"
  "> /dev/sd"
)

for PATTERN in "${BLOCKED_PATTERNS[@]}"; do
  if echo "$COMMAND" | grep -qiE "$PATTERN"; then
    echo "{\"hookSpecificOutput\":{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":\"Blocked: destructive command pattern detected: $PATTERN\"}}"
    exit 0
  fi
done

# Block force push to master/main
if echo "$COMMAND" | grep -qiE "git push.*--force.*(master|main)" || \
   echo "$COMMAND" | grep -qiE "git push.*-f.*(master|main)"; then
  echo "{\"hookSpecificOutput\":{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":\"Blocked: force push to master/main\"}}"
  exit 0
fi

# Block git reset --hard
if echo "$COMMAND" | grep -qiE "git reset --hard"; then
  echo "{\"hookSpecificOutput\":{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":\"Blocked: git reset --hard is destructive\"}}"
  exit 0
fi

exit 0
