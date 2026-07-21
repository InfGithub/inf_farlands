# Inf's Farlands

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.234-blue)](https://neoforged.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://minecraft.net)

[English](README_EN_US.md)

在 1.21.1 NeoForge 中重现边境之地。

## 依赖

- Minecraft 1.21.1
- NeoForge 21.1.234+
- Java 21

## 构建

```bash
./gradlew build
```

产物：`build/libs/inf_farlands-1.x.x.jar`

## 特性

- 将游戏在 x/z 轴上的边界推至 ±2,147,483,647 格。

- 尽量地修复了地物、实体、光照在高坐标下的行为。

- 尽量地移植了 Beta 1.7.3 的噪声，配置项可开关。

---

添加的指令：

- `/farlands tp`：传送到指定坐标。
- `/farlands border`：修改世界边界大小。

配置文件路径：`config/inf_farlands-common.toml`。

## 警告

本模组是一个实验性的模组，为修改 Minecraft，使用了大量 Mixin，**兼容性极差，不建议在生存模式使用**。

已知不兼容的模组：

- **C2ME**

模组内有一个非核心功能的 Java Agent，对结构生成有一定帮助。若您想要启用它，请增加一个 JVM 参数： `-javaagent:inf_farlands-1.x.x.jar`。

---

如果您发现了漏洞，或想提出建议等，请尽情创建 Issue 和 Pull Request。
