# Inf's Farlands

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.234-blue)](https://neoforged.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://minecraft.net)

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.234+
- Java 21

## Build

```bash
./gradlew build
```

Output: `build/libs/inf_farlands-1.x.x.jar`

## Features

- Pushes the world border on the X/Y/Z axes to ±2,147,483,647 blocks.

- Highest usable coordinates: ±2,147,483,632 blocks.

- Fixes features, entities, and lighting at extreme coordinates.

- Ports Beta 1.7.3 noise; toggleable via config option.

- Includes a custom light engine designed for Y coordinates up to ±2.14 billion.

- **Far Lands** appear as a natural consequence of Beta 1.7.3 noise.

## Note

A full-Y-axis terrain generation pipeline is not implemented yet, but it is planned.

---

Commands modified:

- `/tp ~ ~ ~` — now supports teleporting to extreme coordinates.

Config file: `config/inf_farlands-common.toml`.

## Warning

This mod is experimental.

It relies on heavy Mixin usage to modify the game, including but not limited to extensive @Overwrite and reflection.

Some versions contain many bugs that may cause game process CTD, OOM, or freezing.

**Poorly compatible — not recommended for survival gameplay.**

Known incompatibilities:

- **C2ME**
- **ScalableLux**

Actively compatible mods:

- **Sodium**

---

The mod includes a Java Agent that fixes some structure generation
issues. To enable it, add the JVM argument:
`-javaagent:inf_farlands-1.x.x.jar`.

If you find a bug or have a suggestion, feel free to open an
Issue or Pull Request.
