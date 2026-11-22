# Tofe's TechnoGrapple

A grappling hook mod for Minecraft on Forge and NeoForge.

| Minecraft | Mod loader | Source | Download |
| --- | --- | --- | --- |
| 1.20.1 | Forge | [mc/1.20.1-fml](https://github.com/tofemaster/TofesTechnoGrapple/tree/mc/1.20.1-fml) | [0.5.0 beta JAR](https://github.com/tofemaster/TofesTechnoGrapple/raw/refs/heads/mc/1.20.1-fml/tofestechnograpple-0.5.0-mc1.20.1.jar) |
| 26.1 | NeoForge | [mc/26.1](https://github.com/tofemaster/TofesTechnoGrapple/tree/mc/26.1) | [0.5.0 beta](https://github.com/tofemaster/TofesTechnoGrapple/releases/tag/v0.5.0-mc26.1) |
| 26.1.1 | NeoForge | [mc/26.1.1](https://github.com/tofemaster/TofesTechnoGrapple/tree/mc/26.1.1) | [0.5.0 beta](https://github.com/tofemaster/TofesTechnoGrapple/releases/tag/v0.5.0-mc26.1.1) |
| 26.1.2 | NeoForge | [mc/26.1.2](https://github.com/tofemaster/TofesTechnoGrapple/tree/mc/26.1.2) | [0.5.0 beta](https://github.com/tofemaster/TofesTechnoGrapple/releases/tag/v0.5.0-mc26.1.2) |
| 26.2 | NeoForge | [mc/26.2](https://github.com/tofemaster/TofesTechnoGrapple/tree/mc/26.2) | [0.5.0 beta](https://github.com/tofemaster/TofesTechnoGrapple/releases/tag/v0.5.0-mc26.2) |
| 26.3 | NeoForge | [mc/26.3](https://github.com/tofemaster/TofesTechnoGrapple/tree/mc/26.3) | [0.5.0](https://github.com/tofemaster/TofesTechnoGrapple/releases/tag/v0.5.0-mc26.3) |

Download the JAR for your Minecraft version and mod loader, then place it in the instance's `mods` folder.

Source is available under the MIT License.

## Build from source

Clone the branch for your Minecraft version.

**Forge 1.20.1** uses JDK 17. Run `./gradlew build` on macOS or Linux, or `gradlew.bat build` on Windows. The JAR is written to `build/libs/`.

**NeoForge 26.x** uses JDK 25. Pass the path to the Faktocraft 1.0.1 JAR to Gradle.

```sh
gradle build -PfaktocraftJar=/path/to/faktocraft-26.3-1.0.1.jar
```

Faktocraft is optional when running the mod.
