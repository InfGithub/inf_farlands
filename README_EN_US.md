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

- Pushes the x/z world border to ±2,147,483,647 blocks.

- Highest usable coordinates: ±2,147,483,632 blocks.

- Fixes features, entities, and lighting at extreme coordinates.

- Ports Beta 1.7.3 noise; toggleable via config option.

- **Far Lands** appear as a natural consequence of Beta 1.7.3 noise.

---

Commands modified:

- `/tp ~ ~ ~` — now supports teleporting to extreme coordinates.

Config file: `config/inf_farlands-common.toml`.

## Warning

This mod is experimental and relies on heavy Mixin usage, especially
in the sky light engine which uses extensive reflection and @Overwrite.
**Extremely incompatible** with other mods. Not recommended for
survival gameplay.

Known incompatibilities:

- **C2ME**

---

The mod includes a Java Agent that fixes some structure generation
issues. To enable it, add the JVM argument:
`-javaagent:inf_farlands-1.x.x.jar`.

If you find a bug or have a suggestion, feel free to open an
Issue or Pull Request.
