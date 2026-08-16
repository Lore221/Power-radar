# Power Radar

### Overview

Power Radar is a Minecraft 1.21.1 addon for Create and Create: Electro Energetics that adds configurable radar, monitoring, targeting, and base-defense systems.

Instead of treating every detector as an isolated block, Power Radar organizes devices into radar networks. Radar controllers collect targets, monitor controllers present network data, Logic Docks determine which targets matter, and Radar Links connect the system to displays and other equipment.

The mod is designed around Create machinery and uses the electrical simulation from [Create: Electro Energetics](https://github.com/george8188625/Create-Electro-Energetics), including nominal voltage, power consumption, electrical panels, and working-state diagnostics.

### Usage

A basic installation consists of a radar controller, radar panels, an overview module, a monitor controller, and one or more displays. Devices can be linked into the same network with the Linker.

Different radar controllers cover different roles:

- Base Radar provides general-purpose detection.
- Air Radar monitors elevated targets and airspace.
- Surface Radar monitors targets below and around the installation.

The Onboard Computer acts as a compact radar-network terminal for moving Aeronautics structures.

Logic Docks accept filter cards and define which detected targets are distributed through the network. Monitor controllers provide target counts, selected-target coordinates, target category, and speed to Create display links.

In-game Ponder scenes explain the main devices and network setup.

## Main content

- Base, Air, and Surface Radar Controllers
- Radar Panels and Overview Modules
- Radar Monitor Controllers and modular displays
- Logic Docks, filter cards, Radar Links, and Linkers
- Configurable radar ranges, vertical limits, and scan angles
- Mechanical sirens and electrical diagnostics
- Create display-link integration
- A dedicated advancement tree

## Optional integrations

### Create Big Cannons

When [Create Big Cannons](https://www.curseforge.com/minecraft/mc-mods/create-big-cannons) is installed, Power Radar adds:

- Target Controllers
- Shell Alarms
- Interception Controllers and interception fuzes
- Targeting and Allowlist Cards
- Detection and interception of compatible cannon projectiles

Create Big Cannons and Ritchie's Projectile Library are optional. The related content is not registered when Create Big Cannons is absent.

### Create: Aeronautics

When [Create: Aeronautics](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics) is installed, Power Radar adds onboard radar support for moving structures, powered EW systems that conceal a structure's exact silhouette, and physical mass values for its blocks. Aviator's Goggles display those properties using the native Aeronautics tooltip style.

Create: Aeronautics and Sable are optional.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.229 or newer
- Create 6.0.10 or newer within the 6.0.x series
- Architectury API 13.0.8 or newer
- Create: Electro Energetics 1.21.1-1.2.0-121 or newer

## Download

The latest release is available on the [GitHub Releases](https://github.com/Lore221/Power-radar/releases) page. CurseForge and Modrinth download links will be added here after their project pages are published.

Please report problems through the [issue tracker](https://github.com/Lore221/Power-radar/issues).

## Building from source

Power Radar requires Java 21.

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The resulting JAR is written to `build/libs`.

## License

Power Radar is available under the [MIT License](LICENSE).
