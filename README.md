# The Void Dimension

A NeoForge mod for **Minecraft 1.21.1** (NeoForge 21.1.234) that adds a new, dark,
and empty **void dimension** to the game. Craft the teleporter, build in the emptiness,
and use it to your advantage.

> Author: FinnK42 · Version: 1.0.0 · License: MIT
>
> 🇺🇦 Опис українською — див. [README_UA.md](README_UA.md).

## Features

- **The Void** — a flat, empty dimension with a pitch-black sky and fog (biome `the_abyss`),
  no world generation, no natural spawns.
- **Void Teleporter** (`void_teleporter`) — an item that teleports you into the void and back.
  It plays a bow-like use animation with swirling void particles, remembers where you came from
  (dimension + coordinates stored in player data) and returns you there on the next use.
  On the first trip it places an obsidian block under the arrival point so you don't fall.
- **Void Monolith** (`void_monolith`) — a tough block that lights up (light level 15) and
  force-loads its chunk while it stands inside the void dimension.
- **Config** (`Config.java`) — tweak the monolith's light level and hardness, and toggle the
  teleporter's glow.

## Project layout

| Path | Contents |
|------|----------|
| `src/main/java/com/finnk42/void_dimension/` | Mod source (`VoidDimensionMod`, `Config`, block, item, `init/ModConstants`) |
| `src/main/resources/data/void_dimension/` | Dimension, biome, recipes, advancements |
| `src/main/resources/assets/void_dimension/` | Models, textures, blockstates, lang |
| `src/main/resources/META-INF/neoforge.mods.toml` | Mod metadata |

## Building

Requires **JDK 21**. Gradle pulls NeoForge and Minecraft automatically.

```bash
./gradlew build        # produces the jar in build/libs/
./gradlew runClient    # launch the game to test
```

On Windows use `gradlew.bat` instead of `./gradlew`. CI builds on every push and pull
request via `.github/workflows/build.yml`.

## Credits & mappings

Built on the official NeoForge MDK. Method/field names use the official Mojang mappings,
which are covered by their own license — see
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.

Community docs: <https://docs.neoforged.net/> · NeoForged Discord: <https://discord.neoforged.net/>
