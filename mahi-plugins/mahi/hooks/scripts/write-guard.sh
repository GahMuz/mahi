#!/bin/bash
set -euo pipefail

# Fail-closed: if this hook crashes, deny the action
trap 'echo "{\"hookSpecificOutput\":{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":\"Hook error - fail-closed\"}}"' ERR

# Read the tool input from stdin
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty' 2>/dev/null || echo "")

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

# Block writes to sensitive files
PROTECTED_PATTERNS=(
  "\.env$"
  "\.env\."
  "credentials"
  "secret"
  "\.pem$"
  "\.key$"
  "id_rsa"
  "\.ssh/"
  "\.npmrc$"
  "\.pypirc$"
  "\.aws/"
  "kubeconfig"
)

for PATTERN in "${PROTECTED_PATTERNS[@]}"; do
  if echo "$FILE_PATH" | grep -qiE "$PATTERN"; then
    echo "{\"hookSpecificOutput\":{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":\"Blocked: write to protected file matching pattern: $PATTERN\"}}"
    exit 0
  fi
done

exit 0
