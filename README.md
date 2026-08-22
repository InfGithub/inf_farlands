# Inf's Farlands

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.234-blue)](https://neoforged.net)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://minecraft.net)

[English](README_EN_US.md)

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

- 将游戏在 X/Y/Z 轴上的边界推至 ±2,147,483,647 格。

- 实际可玩最高坐标：±2,147,483,632 格。

- 修复了地物、实体、光照等在高坐标下的行为。

- 移植了 Beta 1.7.3 的噪声，配置项可开关。

- 编写了一套适配 ±21.4 亿 Y 轴坐标的光照引擎。

- **边境之地**作为 Beta 1.7.3 噪声的特性出现。

## 注意

本模组暂未实现全 Y 轴的地形生成管线，但该任务已在计划中。

---

修改的指令：

- `/tp ~ ~ ~`：现在支持传送到高坐标。

配置文件路径：`config/inf_farlands-common.toml`。

## 警告

本模组是实验性的。

模组使用了大量 Mixin 以修改游戏，包括但不限于大量 @Overwrite 和反射。

一些版本存在很多漏洞，可能导致游戏进程 CTD、OOM、冻结 等。

**兼容性差，不建议在生存模式使用**。

已知不兼容的模组：

- **C2ME**
- **ScalableLux**

主动兼容的模组：

- **Sodium**

---

模组内有一个 Java Agent，修复了结构生成时的部分问题。若您想要启用它，请增加一个 JVM 参数： `-javaagent:inf_farlands-1.x.x.jar`。

如果您发现了漏洞，或想提出建议等，请尽情创建 Issue 和 Pull Request。
