# Changelog

## 2.4.0

### Added

- **Support for `inf-20100415`** (protocol 21): server bridge, client mod, plugin API (`api415`), the four bundled plugins, the
  BetaCraft files and the world takeover from 414, 413-1953, 330-1611, 327, 325-1640, 321, 320, 316, 313 and 227. The build lets the
  mobs spawn again (their classes have constructors), rewrites the lighting code and moves some names (the world's time, player and
  tile list fields, the light queue and the client classes from `client/j` on). The client patcher tells the build from 414 by the
  class `client/a/l`; the mobs of every game are shared as in 330-1611.
- **Support for `inf-20100414`** (protocol 20): server bridge, client mod, plugin API (`api414`), the four bundled plugins, the
  BetaCraft files and the world takeover from 413-1953, 330-1611, 327, 325-1640, 321, 320, 316, 313 and 227. The jar is the one of
  20100413-1953 with one changed class (the item-use code, which no longer crashes when a torch, a sapling or a flower is placed), so
  everything of 413-1953 works the same. The client patcher tells the two builds apart by the checksum of that class.

### Fixed

- **A player who respawned did not move any more** (builds 20100227 to 20100414). The game flags a dead player as dead after a
  second and takes it out of its entity list, and the respawn did not undo that: the player was never ticked again (no gravity, no
  movement, a camera that did not follow) and the other players saw it hanging in the air. The respawn now clears the flag and puts
  the player back.
- **Block-breaking cracks of other players** (builds 20100227 to 20100415): a player now sees the cracks on the block that another player
  digs at (the digging progress was already relayed by the server; the clients now send and draw it).

## 2.3.0

### Added

- **Plugin APIs** (every build): the names and helpers of the Bukkit API of the time that fit the build, as `default` methods or
  small additions, so nothing that worked before changes. New shared classes `BlockFace` and `Vector`; `Location`: arithmetic,
  `getDirection()`, `equals`/`hashCode`; `Block`: `isEmpty()`, `isLiquid()`, `getRelative(face)`, `getFace(block)`, chunk
  coordinates; `World`: `getHighestBlockAt`, `getMaxHeight()` and location overloads; `Server`: `getViewDistance()`,
  `getSpawnRadius()`, `getWorlds()`, `getWorld(name)` and the Bukkit aliases; `Player`: `performCommand`, `teleport(player)`;
  `BlockPlaceEvent.getBlockPlaced()`/`getReplacedTypeId()`; `PlayerRespawnEvent.isBedSpawn()`. See "Added in 2.3.0" in each
  `docs/plugin-api/<build>.md`.
- **Support for `inf-20100413-1953`** (protocol 19): server bridge, client mod, plugin API (`api413`), the four bundled plugins, the
  BetaCraft files and the world takeover from 330-1611, 327, 325-1640, 321, 320, 316, 313 and 227. (The build 20100413-1951 between
  330-1611 and this one has no known copy; what it added is in this build: beaches, erosion and big trees, leaves that do not decay,
  arrows that make no mobs, the debug screen counts, and several fixes.) The jar moved many names: the world class is `a/a/g`, the
  living entity `a/c/f`, every field of the entity classes is one letter further, the light queue is worked off by one call, and the
  chunks hold entities. The mob classes of this build have no constructor, so there are no mobs; the game's mob spawner, which
  failed with a stack trace in every tick, is switched off by the patcher. The world of the build is in the same Alpha format.
- **Support for `inf-20100330-1611`** (protocol 18): server bridge, client mod, plugin API (`api330`), the four bundled plugins, the
  BetaCraft files and the world takeover from 327, 325-1640, 321, 320, 316, 313 and 227. (The build 20100330-1203 between 327 and this
  one has no known copy; what it added is in this build and was checked here: the tag `TerrainPopulated` in the chunk files,
  furnaces that smelt in 8 seconds again, containers that save reliably, better light for slabs and for the top of the world,
  broken blocks that do not keep their data value.) The jar has the same class names as 20100327; the patcher tells the two builds
  apart by the tag TerrainPopulated in the chunk class, the server by the checksum.
- **TerrainPopulated.** The server reads the tag from the chunk files and writes it: a chunk file with the tag 0 (saved by the game's
  single player before it was populated) is populated when it is needed; a chunk file without the tag (20100327 and older builds)
  counts as populated and is never populated again. The game's own world class reads a world made by the server and sees exactly
  the blocks of the files (the single player of 20100327 makes ores and trees again after a load).
- **Takeover from 327 into 330-1611** moves the chunk files over as the files they are (the Alpha layout is the same), a chunk at a
  time when it is needed and the rest in the background (`/convertworld`); a world of any older build is taken over as into 327.
  An older world may be in either layout. The older world is deleted when every chunk is over (`delete-old-world`).
- **Support for `inf-20100327`** (protocol 17): server bridge, client mod, plugin API (`api327`), the four bundled plugins, the
  BetaCraft files and the world takeover from 325-1640, 321, 320, 316, 313 and 227. (The build 20100325-1545 and the builds between
  have no known copy.)
- **The Alpha level format.** This build saves its worlds in the Alpha level format, and so does the server now: `level.dat` with a
  `Data` compound (seed, spawn, time, size on disk, last played and a player entity) and one GZIP NBT file for each chunk in the
  folders `<X mod 64>/<Z mod 64>` with the names `c.<X>.<Z>.dat` (numbers in base 36). A world of the server can be opened in the
  game's own single player menu, and a world of the game can be put into the server's `worlds/inf-20100327/` folder. The game's
  own world class reads a world made by the server and sees the same blocks as the files hold (checked with a test program).
- **World takeover into the Alpha format.** The chunks of an older build's world (flat folder of `c.X.Z.dat` files with decimal
  numbers) are converted when they are first needed, and `/convertworld` converts the rest a few in each tick (the tick spends at
  most about 20 ms on it), dropping each chunk from memory after it is written: a world of any size can be converted while the
  server runs, with little memory (tested with 6561 chunks and 200 MB of heap). A chunk that needs no change is copied as the file
  it is; the chunks of 20100227, which have no light, are read, lit by the game's own code and written. Converted chunks stay
  exactly as they were made.
- The title screen's greyed "Multi player" button of this build is the multiplayer menu; the pause menu's "Quit game" and the
  death screen's "Title menu" disconnect.

- **The old world is deleted when the takeover is over.** When every chunk of an older build's world is in the new world (the chunks
  that players visited count, not only those of `/convertworld`), the console says so and the older world's folder is deleted. The check
  runs at each autosave, when the server stops and at the end of `/convertworld`. `delete-old-world=false` in `server.properties` keeps it.

### Fixed

- **Slow ticks.** In build 20100413-1953 the light queue is worked off for at most 10 ms in a tick (it was worked off completely, and
  one call of the game does up to 100000 steps). A sand block that rests on something is no longer tracked as falling. The warning
  "Server is behind" now also tells the time of the light, the falling sand and the block ticks of the build.

- **Sand and gravel fall by cells** (builds 20100325-1640 to 20100413-1953). The games of these builds move a sand or gravel block
  to the lowest free cell at once, so a block that lost its support seemed to teleport. The server now lets it fall, one step in
  each tick with gravity (0.04 per tick, 0.98 drag, as the falling sand of the Alpha server), and the players see it on its way; the
  games do not run their own fall in a multiplayer world. A block that falls out of the world is removed.
- **Chunks arrive about one and a half times as fast.** The time of a tick that may go to making chunks is 28 ms (it was 20 ms);
  the ticks that follow a long one still make none, so the tick rate holds.

- **Items that appeared twice** (builds 20100316 to 20100330-1611). A thrown or dropped item next to another player went into that
  player's inventory and also stayed on the ground. Two causes: a game asked for the item while the game could not pick it up yet
  (the pick-up delay of a thrown item is 2 seconds), so the server took the item away from the other players at once and the
  answer was used up for nothing; and the copy that the server had taken away still lay in the world until the game's next tick and
  could be picked up in that time, without asking. A game now asks only when the item can be picked up, and an item that was taken
  away cannot be picked up.
- **Items that other players did not see.** An item thrown near another player was taken away from that player by the thrower's own
  claim (see above), and an item far away or announced before a player came was shown only by chance. The server now keeps every
  shared item and its position, shows it to every player who comes within 128 blocks, and never shows an item again that was taken
  (a late announcement of the thrower's game could bring it back).
- **The first-person arm swung with other players** (builds 20100227 to 20100330-1611). The game draws the own arm with the shared
  player model in the pose it was last given, and in multiplayer that was the pose of the last other player drawn, so the own arm
  moved when someone else walked. The arm is put at rest before it is drawn.
- **A flying player fell when a window opened** (builds 20100316 to 20100330-1611, game modes 1 and 2). The flight is moved by the keys,
  and the keys are not read while the inventory, a menu or the chat is open, so the gravity took over. A flying player now hovers
  while a window is open.
- The console command `/plugins` printed its heading in Turkish ("Plugin'ler"); it says "Plugins" now.
- `/convertworld` ran the whole conversion in one server tick: the console task that queues itself again was run again at once.
  Now a task that queues itself runs on the next tick.

### Known limits

- The chunk files of this build hold no mark for "populated chunk": the game itself populates a chunk again when it loads one next to
  it in single player (ores and trees regenerate; the wiki lists it as a bug). The server never populates a chunk it read from a file.
- The generator of this build has no caves and no flowers (the build has none yet). Land of a world taken over from an older
  build meets the new land at a visible edge.

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
