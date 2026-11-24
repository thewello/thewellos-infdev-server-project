# TheWello's Infdev Server Project

A multi-version multiplayer server and client toolkit for early Minecraft **Infdev** builds, which were released
without multiplayer. One server program can run any of the supported builds. The operator selects one build
(`game-version` in `server.properties`), and the server then runs that build's own game code, with its own
blocks, world format and features. Only players with the matching client can join.

The project provides:

- a dedicated **server** that runs the original game's world code headless,
- a **client patcher** that adds a multiplayer menu and networking to your own copy of each supported game jar,
- a **plugin API** for each build, and a set of optional plugins,
- a **world takeover** from an older build to a newer one (see below).

> Not affiliated with Mojang AB, Microsoft or Markus "Notch" Persson. Minecraft is a trademark of Mojang AB.
> This repository contains **no game code**. You need your own legal copy of each Infdev jar you want to use.

## Supported builds

| Build | Protocol | World and game features | Plugin API | World takeover from |
|---|---|---|---|---|
| `inf-20100227-1433` | 10 | Blocks only. No light, metadata, mobs, items or day and night. | `net.thewellodev.api227` | none |
| `inf-20100313` | 12 | Adds sky and block light, block metadata, day and night, a very large world. | `net.thewellodev.api313` | `inf-20100227-1433` |
| `inf-20100316` | 13 | As 20100313 (24000 tick day), plus items that fall and can be picked up. | `net.thewellodev.api316` | `inf-20100313`, `inf-20100227-1433` |
| `inf-20100320` | 14 | As 20100316, plus trees and ores, chests and furnaces, explosions, fire that can be punched out. | `net.thewellodev.api320` | `inf-20100316`, `inf-20100313`, `inf-20100227-1433` |
| `inf-20100321` | 15 | The world of 20100320 unchanged; the client has a session check that the patcher switches off. | `net.thewellodev.api321` | `inf-20100320`, `inf-20100316`, `inf-20100313`, `inf-20100227-1433` |
| `inf-20100325-1640` | 16 | As 20100321, plus caves, ore blobs and block ticks (leaves decay, grass and fire spread, flowers die in the dark); the server ticks the blocks. | `net.thewellodev.api325` | `inf-20100321`, `inf-20100320`, `inf-20100316`, `inf-20100313`, `inf-20100227-1433` |
| `inf-20100327` | 17 | As 20100325-1640, but the game saves its worlds in the Alpha level format (the server writes the same files), with a new terrain generator (sea level 64, no caves yet), signed chunk numbers and 4 second furnaces. | `net.thewellodev.api327` | `inf-20100325-1640`, `inf-20100321`, `inf-20100320`, `inf-20100316`, `inf-20100313`, `inf-20100227-1433` |
| `inf-20100330-1611` | 18 | As 20100327, plus the tag TerrainPopulated in the chunk files (trees and ores are not made again), 8 second furnaces, reliable containers and better light. Same Alpha level format. | `net.thewellodev.api330` | `inf-20100327`, `inf-20100325-1640`, `inf-20100321`, `inf-20100320`, `inf-20100316`, `inf-20100313`, `inf-20100227-1433` |
| `inf-20100413-1953` | 19 | As 20100330-1611, plus beaches, erosion and big trees in the terrain; leaves no longer decay; the mobs of the earlier builds cannot be made (the classes have no constructor). Same Alpha level format. | `net.thewellodev.api413` | `inf-20100330-1611`, `inf-20100327`, `inf-20100325-1640`, `inf-20100321`, `inf-20100320`, `inf-20100316`, `inf-20100313`, `inf-20100227-1433` |
| `inf-20100414` | 20 | As 20100413-1953; the code that uses an item on a block no longer crashes when a torch, a sapling or a flower is placed. Same Alpha level format. | `net.thewellodev.api414` | `inf-20100413-1953`, `inf-20100330-1611`, `inf-20100327`, `inf-20100325-1640`, `inf-20100321`, `inf-20100320`, `inf-20100316`, `inf-20100313`, `inf-20100227-1433` |
| `inf-20100415` | 21 | As 20100414; mobs spawn on their own again and the lighting code was rewritten (lighting updates properly and uses less memory). Same Alpha level format. | `net.thewellodev.api415` | `inf-20100414`, `inf-20100413-1953`, `inf-20100330-1611`, `inf-20100327`, `inf-20100325-1640`, `inf-20100321`, `inf-20100320`, `inf-20100316`, `inf-20100313`, `inf-20100227-1433` |
| `inf-20100630-1340` | 11 | Mobs, items, minecarts, signs, chests and furnaces, health, inventory. | `net.thewellodev.api` | none |

Builds are independent: nothing is carried over from a newer build to an older one, and every build keeps its own
worlds in `worlds/<game-version>/<level-name>/`. More builds can be added; see
[docs/MULTIVERSION.md](docs/MULTIVERSION.md).

## Features

**Server**
- Runs the original world code of the selected build, including its terrain generator (the same seed gives the same land).
- Plugin system with a Bukkit-style API, one API for each build ([docs/plugin-api/](docs/plugin-api/README.md)).
- Control panel with console, memory and TPS readings, and a per-player inventory window for operators.
- Account verification with Mojang's session server (`online-mode=true`).
- Operators, whitelist, bans, optional automatic port mapping (UPnP), rate limits and input checks.
- Worlds are taken over lazily from an older build: the new world starts at once and each chunk is converted the first
  time a player needs it. The old world is only read, and is deleted when every chunk has been taken over (`delete-old-world`). The worlds of `inf-20100327`, `inf-20100330-1611`, `inf-20100413-1953`, `inf-20100414` and `inf-20100415` are in the Alpha level format: the
  game's own single player can open it.
- A watchdog writes a report to `logs/` if a server tick hangs.

**Client**
- A patcher that turns your original jar into a multiplayer-capable jar. It detects the build by itself.
- A multiplayer menu on the title screen, direct connect, chat, other players with names, and PvP.
- Block, metadata and light synchronisation, shared day and night, and (builds `inf-20100316` to `inf-20100415`) items on
  the ground and the mobs that arrows make, which every player sees; in `inf-20100320` and later also shared chests and furnaces.

**Optional plugins** (in the release bundle, `server/optional-plugins/<build>/`)
- `NameColor`: colours player names by role.
- `NoIllegals`: stops players from holding items that cannot be obtained in survival.
- `TheWelloNoCheat`: movement, reach, attack-rate and chat-spam checks.
- `TheWelloNoLagg`: limits dropped items, TNT and mob build-up.
- `TheWelloOpenInv` (build `inf-20100630-1340` only): lets operators look at and edit the inventory of an online player.

## Quick start

1. Download the release archive from the [Releases](../../releases) page and unzip it.
2. **Client:** run `client/patch-client.bat <your original Infdev jar>` (or `patch-client.sh`). The build is detected
   from the jar and the patched client is written to `client/patched/`. The script offers to install it into BetaCraft.
   Other launchers: see [docs/CLIENT.md](docs/CLIENT.md).
3. **Server:** put the original jar of the build you want to run into `server/`, set `game-version` in
   `server/server.properties`, and run `server/start-server.bat` (or `start-server.sh`). Java 8 is required.
4. Optional: copy plugins from `server/optional-plugins/<build>/` into `server/plugins/`.

The full end-user instructions are in the archive's `README.txt`.

## Documentation

- [Server setup and settings](docs/SERVER.md)
- [Client setup](docs/CLIENT.md)
- [Multi-version design, world takeover and per-build notes](docs/MULTIVERSION.md), [docs/versions/](docs/versions/)
- [Plugin APIs](docs/plugin-api/README.md) (one for each build)
- [Architecture](docs/ARCHITECTURE.md)
- [Build from source](docs/BUILD.md)
- [Changelog](CHANGELOG.md)

## Important: tied to exact game builds

The client mod and the server bridge refer to the obfuscated class, field and method names of the original jars, so
each build needs its own mapping and its own code. The project works only with the exact jars it was written for
(their SHA-256 values are in [docs/MULTIVERSION.md](docs/MULTIVERSION.md)). The server identifies a game jar by its hash,
not by its file name.

## Repository layout

| Path | Contents |
|---|---|
| `common/` | Protocol definitions, the build registry (`GameVersion`) and the ASM hook tool, shared by server and client |
| `server/src/` | Server core, control panel, world takeover |
| `server/bridge/`, `bridge227/`, `bridge313/`, `bridge316/`, `bridge320/`, `bridge321/`, `bridge325/`, `bridge327/`, `bridge330/`, `bridge413/`, `bridge414/`, `bridge415/` | Bridges between the server and the original game classes of each build |
| `client/patcher/` | Patcher that adds multiplayer support to an original client jar |
| `client/mod/`, `mod227/`, `mod313/`, `mod316/`, `mod320/`, `mod321/`, `mod325/`, `mod327/`, `mod330/`, `mod413/`, `mod414/`, `mod415/` | Client-side multiplayer code of each build, added to the patched jar |
| `api/<build>/` | The plugin API of each build |
| `plugins/<build>/` | Example and bundled plugins of each build (`src/` with a `plugin.yml`) |
| `release/` | Templates for the release bundle (end-user README, start scripts, `server.properties`, BetaCraft files) |
| `docs/` | Documentation |
| `lib/` | ASM 9.8 (BSD 3-Clause) |

## Building from source

Requires JDK 8, a Unix-style `sh` (Git Bash on Windows), an LWJGL 2 jar (compile-time only) and the original game
jars in `original/` (not included). See [docs/BUILD.md](docs/BUILD.md).

```sh
sh build.sh
```

The build writes the release bundle and its zip archive to `../TheWellosInfdevServer-Release/`. The archive contains
no Minecraft code.

## Platform and tested setup

- Developed and tested on Windows 11 with Java 8 (Temurin). Java 8 is required.
- The `.bat` scripts are for Windows. The `.sh` scripts and the server on other systems have not been tested.
- Known limitations are listed in [SECURITY.md](SECURITY.md) and in the notes of each build.

## About this project

This project was built almost entirely with **vibe coding**: the code was written by an AI assistant (Claude, by
Anthropic) from conversational instructions, then tested by running a server and playing on it. It has not been
independently audited or formally reviewed. Read the code before you run a server that is open to the internet, and
see [SECURITY.md](SECURITY.md).

## Credits

This project builds on and was inspired by other projects, including LilyBukkit, the Bukkit API, NoCheat, NoLagg
and OpenInv. See [CREDITS.md](CREDITS.md) for the full list and licenses.

## License

GNU General Public License v3.0. See [LICENSE](LICENSE). Bundled third-party code: [ASM](lib/ASM-LICENSE.txt) (BSD 3-Clause).
