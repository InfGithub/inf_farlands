# Inf's Farlands

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.234-blue)](https://neoforged.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://minecraft.net)

Restores the Far Lands in 1.21.1 NeoForge.

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

- Fixes features, entities, and lighting at extreme coordinates as much as
  possible.

- Ports Beta 1.7.3 noise as accurately as possible; toggleable via config
  option.

---

Commands added:

- `/farlands tp` — teleport to any coordinates.
- `/farlands border` — adjust world border size.

Config file: `config/inf_farlands-common.toml`.

## Warning

This mod is experimental, relies on heavy Mixin usage, and is
**extremely incompatible** with other mods. Not recommended for
survival gameplay.

Known incompatibilities:

- **C2ME**

The mod includes a non-critical Java Agent that helps with
structure generation. To enable it, add the JVM argument:
`-javaagent:inf_farlands-1.x.x.jar`.

---

If you find a bug or have a suggestion, feel free to open an
Issue or Pull Request.
