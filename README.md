# Power Radar

Power Radar is a NeoForge 1.21.1 integration addon for Create, Create: Electro Energetics, and Create Big Cannons. It provides radar scanning, networked monitor displays, target control, shell alarms, and projectile interception.

## Required dependencies

- NeoForge 21.1.229 or newer for Minecraft 1.21.1
- Create 6.0.10
- Ponder, Flywheel, and Registrate
- Architectury API 13.0.8
- Create: Electro Energetics 1.21.1-1.0.0 or newer
- Create Big Cannons 5.11.6 or newer in the compatible 1.21.1 line
- Ritchie's Projectile Library 2.1.2 or newer in the compatible 1.21.1 line

Gradle resolves every required development dependency from its official Maven repository or Modrinth Maven. Local JAR files in `libs/` are not required for compilation or CI.

## Build

Power Radar requires Java 21.

Windows:

```powershell
.\gradlew.bat build
```

Linux or macOS:

```bash
bash ./gradlew build
```

The standard `check` lifecycle includes `verifyProjectStructure`, which validates Java source layout, the common/client boundary, translation parity, and required block resources.

Development run configurations are generated for the client, dedicated server, data generation, and GameTest server. GameTests are not yet included, so use the client and dedicated server configurations for runtime verification.

## Dependency sources

- Create ecosystem: Create Maven and Registrate Maven
- Architectury API: Architectury Maven
- Electro Energetics, Create Big Cannons, and RPL: immutable Modrinth Maven version IDs pinned in `gradle.properties`

Do not commit third-party mod JARs to this repository.
