# Changelog

## 2.2.0

### Added

- **Support for `inf-20100321`** (protocol 15): server bridge, client mod, plugin API (`api321`), the four bundled plugins, the
  BetaCraft files and the world takeover from 320, 316, 313 and 227. The world code of this build is identical to that of 20100320
  (checked class by class), so every feature of 320 (shared items, mobs, chests, furnaces and explosions) works the same, and a
  world of 320 is taken over without any change in the terrain. The client patcher switches off the build's **session check**: the
  game polls a server of the year 2010 and opens the screen "Session conflict!" when its check fails; the poll is removed (the game
  contacts nothing) and the check always passes.
- **Support for `inf-20100325-1640`** (protocol 16): server bridge, client mod, plugin API (`api325`), the four bundled plugins, the
  BetaCraft files and the world takeover from 321, 320, 316, 313 and 227. (The build 20100325-1545 between 321 and this one is lost:
  no copy exists. Everything it added is in 1640, and was checked there.) The patcher passes the player's session id to the mod (the
  game no longer keeps it), so online-mode servers still work.
- **Block ticks** (new in the game of 325-1640): leaves decay, grass spreads and dies under cover, fire spreads, flowers die in the
  dark and saplings grow. The server runs them and sends what changes; the client's own block ticks are cut out of its world tick.
  Items that blocks drop during a tick (a sapling from a decaying leaf) are shared items of the server. Caves and ore blobs come from
  the game's own terrain generator, which the server runs.
- **Furnaces smelt on the server** (build 325-1640): the server ticks the furnaces it holds, so they go on when nobody is near and
  every player sees the block change to the burning furnace. The furnace that a player has open is ticked by that player's game (and
  not by the server) until the window is closed, so the arrow and the flame move in the window as before.
- **Game modes** (builds 316 and later): creative (`/gamemode 1`) breaks a block with one click, and nothing hurts a player of the
  modes 1 and 2 (drowning, explosions, fire, falls: the damage methods of the game are cut out in that game, so a big hit cannot kill
  in one tick). New: **spectator** (`/gamemode 2`, also `spectator`): invisible to the other players, flies through walls (the game's
  own no-clip flag), cannot break, place, pick up, open chests, hit or be hit. Creative and spectator are for operators only.
  `Player.getGameMode()` can now return 2.
- **Primed TNT and arrows in flight are shown to the other players** (builds 316 and later): a lit block of TNT (kind 6) and each
  arrow (kind 7) live in the game that made them; they are now reported like mobs and drawn, with the flashing fuse, in the other
  games. They are not handed over when their owner leaves. `MobSpawnEvent.PRIMED_TNT` and `MobSpawnEvent.ARROW` let a plugin stop them.
- **A player who joins sees the items that already lie on the ground** (builds 316 and later): the server asks the games to announce
  their items again (`DROP_RESYNC`, 0x40) when somebody joins and once a minute, and shows the items it made itself to players who
  come near them. The block ticks, the explosions and the light were compared between the server and two games, block by block:
  they agree.
- **Optional: taken-over land can get the caves of 325-1640** (`upgrade-old-terrain=true`, off by default): the populate step of the
  new build then runs once on each chunk of an older build's world (caves, ore blobs, more trees); `/convertworld` populates and
  saves each chunk the same way, a few in each tick. Without it the old chunks stay exactly as they were made.
- **Plugin API (all builds, nothing removed):** `Player.getEyeLocation`, `getTargetBlock`, `getLoginTime`; `World.getNearbyPlayers`,
  `fill`, `getDayLength`, `isDay`, `isNight`, `getBlockLightAt`, `getSkyLightAt`; `Server.getOperators`, `getBannedPlayers`,
  `getWhitelistedPlayers`, `broadcastMessage(message, permission)`, `matchPlayers`, `getUptimeMillis`.
- **New events in builds 316, 320, 321 and 325-1640:** `ItemSpawnEvent`, `PlayerPickupItemEvent` and `MobSpawnEvent`; in 320 and
  later also `ExplosionEvent`. All can be cancelled (see the plugin API pages).
- The server's message about a slow server says which part of the tick was slow (logins, packets, world, chunks).

### Fixed

- **Light on the server (builds 313, 316, 320, 321 and 325-1640):** the game queues its light updates and works one off in every
  frame of a client; the server has no frames and never did, so the light that it stored and sent to joining players was wrong
  after block changes and in new trees: no sideways spread of sky light under a roof, no light from torches (a torch lit nothing
  for a player who joined later), and the queue grew without limit. The server now works the queue off every tick and completely
  before it sends a chunk. The light of every client and of the server is identical (checked cell by cell). Chunks that an older
  version saved after block changes keep their wrong light until blocks next to them change again.
- **Making chunks no longer stalls the server.** The chunks that a player needs were made as fast as the server could, in one tick:
  with the terrain of the newest build that took up to several seconds and the tick rate fell ("Server is behind"). A tick now
  spends at most about 20 ms on chunks (one chunk always gets through), the players take turns, and a tick that was slower is
  paid back with ticks that make no chunks.
- The first start of a new world in 325-1640 could take 8 seconds or more, and the server then wrote "The server seems to be
  frozen". It was not frozen: the terrain of this build (caves) is slow to make, and the chunks that one chunk's populate step
  reaches into were all populated at once, one after the other. They are now only generated and are populated when something
  asks for them, so a spawn area needs about half the chunks; the server shows its progress while it prepares the spawn area, and
  the freeze watch no longer counts it.
- A plugin command of a build with its own API got the server's player object, so `sender instanceof Player3xx` was always false
  (the example plugin's `/top` said "Players only" in game). It now gets the build's own player type.
- A world taken over twice in a row (for example 316, then 320, then 321) lost the chunks that nobody had visited in the middle
  build: the remaining chunks of the older world are copied first.
- The bridges of builds 313 to 321 mistook the player positions that the server gives them (x and z pairs) for x, y and z
  triples. When the server held more than 3000 chunks, chunks near the players could be dropped and read again from the files.
- The scripts `patch-client.bat` and `patch-client.sh` listed only the first four builds.

### Known limits

- The limits of 2.1.0 apply, except that items on the ground before a player joined are now shown to him.
- The furnaces of the builds before 325-1640 still smelt only in the game of a player who has the chunk loaded.
- A world taken over into 325-1640 keeps its old chunks exactly as they were made; the edge between the old land (no caves) and the
  new land is visible. With `upgrade-old-terrain=true` the old chunks get the caves, ore blobs and extra trees too (the old land
  changes; a building can be cut into by a cave).
- A server holds the random block ticks to the chunks it has loaded (a square of 129 blocks around each player).
- A cancelled `ItemSpawnEvent` leaves the item in the game of its owner only; a cancelled `ExplosionEvent` does not stop the
  explosion in the game of the player (the game runs it).

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
