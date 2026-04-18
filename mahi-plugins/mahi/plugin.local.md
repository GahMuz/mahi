---
name: Mahi Local Configuration
description: Local overrides for the Mahi plugin — set the JAR path for this machine. Not committed to git.
---

# Configuration locale Mahi

## MAHI_JAR — Chemin vers le fat jar

Définir la variable d'environnement `MAHI_JAR` avec le chemin absolu vers le jar compilé :

```bash
# Unix / WSL
export MAHI_JAR=/absolute/path/to/mahi-mcp/build/libs/mahi-mcp-server.jar

# Windows (PowerShell)
$env:MAHI_JAR = "C:\path\to\mahi-mcp\build\libs\mahi-mcp-server.jar"
```

## Build du jar

```bash
cd mahi-mcp
./gradlew bootJar
# → build/libs/mahi-mcp-server.jar
```