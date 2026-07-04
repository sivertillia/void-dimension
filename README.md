# The Void Dimension

A NeoForge mod for **Minecraft 1.21.1** (NeoForge 21.1.234) that gives every player their **own
private void dimension** — a dark, empty pocket world to build in, with an access system so you can
invite others in.

> Author: FinnK42 · Version: 1.0.0 · License: MIT
>
> 🇺🇦 Опис українською — див. [README_UA.md](README_UA.md).

## Features

- **Per-player void dimensions.** Each player gets a separate dimension (`void_dimension:<uuid>`),
  created on demand the first time they enter and saved to its own region files. Nobody shares a
  world, so builds never collide. Dimensions are created dynamically at runtime (Minecraft has no
  public API for this — the mod ships a small access transformer to make it work).
- **Void Teleporter.** Channel the item (bow-like animation, swirling void particles) to travel:
  - **Unnamed** → your own void, and back again from inside it (remembers where you came from).
  - **Renamed to a player's nick** (e.g. in an anvil) → *that* player's void, if it exists and you
    are allowed in (owner, granted access, or an operator). Otherwise you get a message explaining why.
- **Access system.** Grant and revoke who may enter your void; operators bypass the rules.
- **Void Monolith.** A tough block that lights up and force-loads its chunk while it stands inside a
  void dimension.
- **Config** (`Config.java`) — monolith light level and hardness, teleporter glow.

## Commands

All under `/void`:

| Command | Who | Effect |
|---------|-----|--------|
| `/void access <player>` | everyone | allow a player into **your** void |
| `/void revoke <player>` | everyone | remove a player's access to your void |
| `/void my list`         | everyone | list who you have granted access to your void |
| `/void tp <player>`     | operators | teleport into any existing player's void |
| `/void list`            | operators | list every void that currently exists |

Player names resolve through the server profile cache, so commands and the renamed teleporter work
for offline players too. Commands never create a void that doesn't already exist — only the owner's
own teleporter does that.

## Project layout

| Path | Contents |
|------|----------|
| `src/main/java/com/finnk42/void_dimension/` | Mod source (mod class, config, block, item, `command/`, `world/`, `init/`) |
| `src/main/java/.../world/VoidDimensions.java` | Runtime creation of per-player void levels |
| `src/main/java/.../world/VoidAccess.java` | Persistent per-owner access lists (`SavedData`) |
| `src/main/java/.../command/VoidCommand.java` | The `/void` command tree |
| `src/main/resources/META-INF/accesstransformer.cfg` | Opens the server internals needed for dynamic dimensions |
| `src/main/resources/data/void_dimension/` | Template dimension, biome, recipes, advancements |
| `src/main/resources/assets/void_dimension/` | Models, textures, blockstates, lang |

## Building

Requires **JDK 21**. Gradle pulls NeoForge and Minecraft automatically.

```bash
./gradlew build        # produces the jar in build/libs/
./gradlew runClient    # launch the game to test
```

On Windows use `gradlew.bat` instead of `./gradlew`. CI builds on every push and pull request via
`.github/workflows/build.yml`.

## Notes & limitations

- Dynamic dimensions are recreated on demand, not at server startup. Region files persist, so builds
  survive restarts; a player's void is rebuilt the next time they (or their teleporter) enter it. A
  player left standing inside their void across a restart is relocated to the overworld spawn until
  they re-enter.

## Credits & mappings

Built on the official NeoForge MDK. Method/field names use the official Mojang mappings, which are
covered by their own license — see <https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.

Community docs: <https://docs.neoforged.net/> · NeoForged Discord: <https://discord.neoforged.net/>
