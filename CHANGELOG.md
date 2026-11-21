# Changelog

## 2.1.0

### Added

- **Support for `inf-20100320`** (protocol 14): server bridge, client mod, plugin API (`api320`), the four bundled plugins and the
  world takeover from 316, 313 and 227.
- **Terrain with trees and ores** (the new generator of the build): new chunks are populated like the game's own chunk ring does.
- **Shared chests and furnaces** in build 320: one player at a time (server lock), contents saved by the server and shown to the
  other players.
- **Explosions** (creepers and TNT) hurt other players too, and the blocks they destroy are shared. Fire can be punched out (a game feature).
- The **mobs that arrows make** (builds 316 and 320) are seen by every player with their head and body direction, walking, hurt and death animation; any player can hurt them (click or arrow); when the player who owns a mob leaves, the nearest other player takes it over.
- A mob goes after the player who is near it, not only after the player who made it (the server hands a mob to the nearer player).
- Shared items are picked up by one player only: the server decides who asks first.
- **Mouse with two windows**: every build ignores the mouse in a window without focus (all builds, including 630).
- Explosions in 320: the server accepts the burst of block changes; before, most destroyed blocks were put back.
- Terrain in 320: chunks are sent after the chunks around them exist, so trees are no longer cut off at chunk borders.
- `/time` and the server messages follow the build's day length; the release bundle has the BetaCraft files of the new build.

### Fixed in this version (after a review of the code)

- A chunk that was loaded from a file could be populated again (trees written into builds) when a new chunk appeared next to it.
- Items put into a chest or a furnace are checked against the game's item table; chest counts are 16-bit (stacks above 255 were cut).
- The explosion burst only accepts block removals inside the blast; a locked chest cannot be broken by somebody else; tile and item
  packets are checked for distance, finite numbers and real item ids; a player who leaves takes his mobs with him; saved
  inventories are filtered for the running build.
- The client sends its last inventory (and gives the chest lock back) before it disconnects, a bad packet no longer stops multiplayer,
  and only the player's own arrows count as his attacks.
- The patcher refuses a jar that is already patched and never leaves a half-written jar; the mouse is not grabbed again while a menu
  is open.

### Known limits

- A world taken over into 320 shows the edge between the converted land (without the new trees and ores) and the new land.
- A furnace nobody has open only smelts in the games that have its chunk loaded.
- Mobs that arrows make are copies in the other games (no AI of their own there); a hit on a copy is carried out by the game that runs the mob.
- Items on the ground before a player joined are not shown to him.

## 2.0.0

The project is now **TheWello's Infdev Server Project**. Its goal changed: instead of one server for one Infdev build
(`inf-20100630-1340`), one server program now runs any of several Infdev builds, one at a time, each with its own game
code, worlds, client and plugin API.

### Added

- **Multi-version server.** `game-version` in `server.properties` selects the build: `inf-20100630-1340`,
  `inf-20100227-1433`, `inf-20100313` or `inf-20100316`. The server identifies the game jar by its contents, keeps worlds
  per build in `worlds/<game-version>/<level-name>/`, and refuses clients of another build.
- **Three new builds**, each with a server bridge, a client mod and a plugin API: `inf-20100227-1433`, `inf-20100313`
  and `inf-20100316`.
- **World takeover** from an older build to a newer one (227 to 313 to 316): lazy, chunk by chunk, with `/convertworld`
  for the rest. The old world is never modified. Positions, spawn point and saved inventories come along.
- **Seeded terrain** for builds 227, 313 and 316: `level-seed` works and the same seed gives the same land.
- **Saved inventories** for builds 227, 313 and 316, which keep the inventory on the client.
- **Client patcher** with automatic build detection (`patch-client.bat` / `patch-client.sh`) and optional BetaCraft install.
- **Items on the ground** in build 316 are shared between players, and arrows hurt other players.
- **Freeze reports**: `logs/freeze-*.txt` on the server and `freeze-*.txt` on the clients of builds 227, 313 and 316.
- **Plugin API per build** (`api227`, `api313`, `api316` next to the original API) and the four bundled plugins for every build.
- **Release bundle without Minecraft code** and its zip archive, built by `build.sh`.

### Changed

- Project name, jar names (`TheWellosInfdevServer.jar`, `TheWellosInfdevClientPatcher.jar`), documentation and
  console messages (now English only).
- `/time` uses the day length of the running build (12000 ticks in 313, 24000 in the others).
- Two game windows on one computer no longer turn each other's camera.

### Fixed

- Walking animation of other players (they were always drawn standing).
- Knockback between two players on the same spot could kick the victim (NaN position).
- Tools and arrows were lost from the saved inventory of builds 227, 313 and 316.
- Chunks that were not redrawn when they arrived in build 316.

### Known limits

- Build 316: items on the ground before a player joins are not shown to him; an arrow that spawns a mob does it only in
  the shooter's game; the game has no explosions and fire cannot be punched out.
- Builds 227, 313 and 316 keep inventory (and, in 316, items) on the client, so the server can only limit it. See
  [SECURITY.md](SECURITY.md).
- Tested on Windows 11 with Java 8 only.

## 1.0.0

Multiplayer for `inf-20100630-1340`: dedicated server, client patcher, plugin API and optional plugins.
