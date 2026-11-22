# Multi-version support

One server program runs one Infdev build at a time. This page describes the model, how a build is selected and
recognised, how worlds move between builds, and how a new build is added. The notes of each build are in
[versions/](versions/).

## The model

- **One server, one build at a time.** The operator picks the build with `game-version` in `server.properties`. The
  server then runs exactly that build's game code: its blocks, items, mobs, world generation and file formats.
- **Builds are independent.** Nothing is transferred from a newer build to an older one. A server that runs
  `inf-20100227-1433` has that build's content only (for example, no mobs, because that build has none).
- **Worlds are per build.** Each build keeps its worlds in `worlds/<game-version>/<level-name>/`.
- **Clients match the server.** A player needs the client patched from the same build. A client of another build is
  refused at login with a message that names the build the server runs. The protocol number of each build is part of
  the handshake (see the table below).
- **Code per build.** Each build has its own bridge (`server/bridge<build>/`), client mod (`client/mod<build>/`) and
  plugin API (`api/<build>/`). The game classes are obfuscated and differ between builds, so a shared code path with a
  table of names was not used; the duplication is deliberate.
- **The multiplayer menu** is the only feature that is added from outside the original game: no original Infdev build has one.

## Supported builds

| Build | Protocol | SHA-256 of the original jar |
|---|---|---|
| `inf-20100227-1433` | 10 | `507cecfad21caa2e137a00c57d63ac6ebc299cb1191e703f1c49274d53e7fef3` |
| `inf-20100313` | 12 | `4974d31eb0af16ddbaaa2d81e88943baeaf8bbc0106a8db20cda19f4a824152d` |
| `inf-20100316` | 13 | `a363943f5e2c147ab0515930317dd535e9a2229fa0eb952c73fb6f078cfbda20` |
| `inf-20100320` | 14 | `7e6b3048a97384397e1adfb4458eafd3a0aae52845b816cfc6f138902da5e92a` |
| `inf-20100321` | 15 | `9dd5ed05f557d508766d8b9d4fd3f77adc69c1943fd47f51058191ab81fc55bf` |
| `inf-20100325-1640` | 16 | `1942c49c85167375182b4d181e71f2a199f223ae8f442332c4dbfaa5596c20a0` |
| `inf-20100630-1340` | 11 | `92e4969e76cd242a53ae9ca10206ece9e6596b441ff9896fd9a34b9b61ad072e` |

The registry is `common/src/net/thewellodev/protocol/GameVersion.java`.

## Selection and checks

- **Startup.** The server looks for the game jar in its folder. A jar is identified by the manifest attribute that the
  patcher writes (`TheWelloDev-Game-Version`) or, for an original jar, by its SHA-256. If no jar of the chosen build is
  found, the server does not start and names what it expected. Jars of other builds in the folder are skipped.
- **Class loader.** `GameClassLoader` loads the game classes of the chosen build and applies the build's ASM hooks.
  For builds 227, 313, 316, 320, 321 and 325-1640 it also replaces the unseeded `new Random()` calls of the terrain generators with a seeded
  generator (`TWSeed`), so `level-seed` works and the same seed gives the same land.
- **Login.** The client sends the protocol number of its build. A different number is refused.
- **Commands.** Built-in commands that make no sense for a build (for example `/give` on a build without items) are not
  registered; `/help` lists only what exists.

## Worlds and the takeover of older worlds

A world of an older build can be played on a newer one. The step is lazy, so large worlds start at once and a
half-finished conversion never leaves a world that cannot be opened:

1. If the target build has no world for `level-name` but an older build has one, the server creates the new world
   folder with `level.dat`, the players' positions and saved inventories, and a marker file `legacy.properties`
   (`from=<build>`, `complete=false`).
2. The bridge of the new build reads a chunk from the old world's folder the first time it is needed, converts it in
   memory and saves it in the new folder with the next save. A chunk that already exists in the new folder always wins.
3. `/convertworld` converts the chunks nobody has visited yet, a batch of files every tick while the server keeps
   running, and then sets `complete=true`. While it is `false` the console reminds at every start, and a missing old
   folder is reported.
4. The old folder is only read, never changed. Do not delete it before the conversion is complete.

Steps that exist: 227 to 313, 313 to 316, 316 to 320, 320 to 321, 321 to 325-1640, and from every older one of these builds to the newer ones. The generator of 325-1640 makes caves and ore blobs, so land made after a takeover into it differs from the converted land at its edges. 320 to 321 changes nothing in the terrain (the world code of the two builds is identical). The chunk files of these builds use the same container and
tags; the light of a 227 chunk (which has none) is worked out when the chunk is read. The terrain generators of 313 and
316 are the same code, so land made after a 313 to 316 takeover fits the converted land (apart from the random scatter
of flowers). The generator of 320 adds trees and ores, so land made after a takeover into 320 differs from the converted
land at its edge (the converted chunks keep what they have). Into 325-1640 the converted chunks stay as they were made; with the setting
`upgrade-old-terrain=true` they get the populate step of the new build once (caves, ore blobs, more trees) and the land fits. Converting back to an older build is not supported. There is no step to `inf-20100630-1340`: many builds lie
between it and the others that are not supported yet, and its world format differs.

A new build adds a case to `WorldConverter.supportsStep`, and its bridge converts the chunk it reads.

## Inventories across builds

Builds 227, 313, 316, 320, 321 and 325-1640 keep the inventory on the client, and each build starts a player with its own default one. The
client reports its inventory to the server (`INVENTORY_REPORT`, 32-bit counts); the server saves it as
`players/<name>.inv` and sends it back after login (`INVENTORY_SYNC`). A player who was saved keeps the inventory when
the server moves to a newer build; a player who was never saved starts with the default of the build he joins.

## Block ticks in build 325-1640

From this build the world's blocks tick (leaves decay, grass spreads and dies under cover, fire spreads, flowers die in the
dark, saplings grow). The **server** is the only one that ticks them: the client's world tick has the scheduled ticks and the
random ticks around the player cut out (they would change blocks that the server does not know of), and the server runs the
world's own tick method with a stand-in player, plus its own sweep of random ticks over the chunks it holds (8000 tries a
tick for each player in a square of 129 blocks, the same rate per block as the game's 32000 tries over its volume). Every block
that changes during a tick is sent to the players, and the items that blocks drop (a sapling from a leaf) become shared items
of the server's own slot (2047). See `TWTick` in `server/bridgeshared/`.

## Items and mobs in builds 316 and later

The game of each client of these builds runs item entities and the mobs that an arrow makes. The server numbers them with the
**slot** it gives each player at login (`SLOT`): a number is `slot << 20 | n`, where n counts up and is never 0.

- **Items.** The client reports each new item (`DROP_SPAWN`) and each item that is gone (`DROP_REMOVE`); the server passes
  them to the players near the item, whose clients show a copy. A shared item is only picked up when the server has given it
  to that game: the client asks (`DROP_CLAIM`, the pick-up method is hooked), the first one asked gets it
  (`DROP_CLAIM_RESULT`) and the other copies are removed at once. When a player joins (and once a minute) the server asks every game to announce its items again (`DROP_RESYNC`), and it shows its own items (the ones that blocks drop during a tick) to players who walk into their range, so that a player sees the items that lay there before he came.
- **Arrows.** An arrow that crosses another player's body reports a hit to the server, which applies the PvP rules.
- **Mobs.** A mob that an arrow makes (sheep, pig, skeleton, creeper, spider, zombie) lives in the game of the player who shot
  the arrow. That client reports it a few times a second with everything the renderer needs (position, head and body
  direction, walking, hurt and death time, fire, health: `MOB_STATE`, `MOB_REMOVE`); the server passes it on, and the other
  clients draw a copy that is not part of their world (no AI, no physics) and moves smoothly towards the reported state. A hit
  on a copy (a click or an arrow) goes to the server (`MOB_HIT`), which gives it to the game that runs the mob, where the
  real mob is hurt. The server remembers every mob; when its owner leaves it hands the mob to the nearest other player
  (`MOB_ADOPT`): that client replaces its copy by a real mob with the same number, and the others do not notice.
  The same hand-over happens while the owner is alive: a mob only goes after the one player of the game that runs it (within
  16 blocks), so once a second the server gives a mob whose owner is more than 20 blocks away to another player who is clearly
  nearer (`MOB_RELEASE` to the old owner, `MOB_ADOPT` to the new one, at most once in five seconds per mob).
- **Explosions (320 and later).** An explosion in a player's game destroys hundreds of blocks in one tick. The client announces it
  (`EXPLOSION`, power and centre) and the server then accepts the block removals inside the blast radius, as many as the
  explosion can destroy, in the next three seconds, above the normal rate limit.

- **Arrows.** Every arrow in flight in a game (kind number 7: a player's, a skeleton's) is reported every tick and drawn in the other games, so that they see it fly and stick. It hurts other players as before (the game that shot it tells the server); the server never hands it over.
- **Primed TNT.** A lit block of TNT is reported like a mob, with the kind number 6 (the fuse in the place of the health). The other
  games draw a copy with its flashing fuse. It explodes only in the game that made it; the server never hands it over (it
  is removed when its owner leaves), and a hit on its copy does nothing.

The creeper, zombie and skeleton of these builds do not burn in daylight: the game's own code computes the brightness and
throws it away (the mob classes call their brightness method and discard the result).

## Chests and furnaces in builds 320 and later

The tile entities are the game's own objects in each client. The server owns the contents: it sends them with each chunk
(`TILE_DATA`), grants one lock per block (`LOCK_REQUEST`, `LOCK_RESULT`, `LOCK_RELEASE`), accepts the contents from the
holder of the lock when the window is closed, and saves them in the chunk file. See [versions/inf-20100320.md](versions/inf-20100320.md).

From build 325-1640 the server also ticks the furnaces it holds (it puts them into the world's list of tile entities, which the
world's entity tick works through), so they smelt without anybody near. The furnace that a player has open is not ticked on the
server: the game of that player ticks it (its own tick of tile entities only knows the open furnace) and hands the contents,
progress included, back when the window is closed.

## Freeze reports

The server (`logs/freeze-*.txt`, a tick over 8 seconds) and the clients of builds 227, 313, 316, 320, 321 and 325-1640
(`%USERPROFILE%\.thewellodev\freeze-*.txt`, no tick for 8 seconds while connected) write the stack of the stuck thread.

## Adding a build

1. Identify the build: add it to `GameVersion` (id, protocol number, SHA-256) and to `GameVersion.chronological()` if
   worlds can be taken over into it.
2. Map the classes and members the hooks and the bridge use (compare with the nearest build; the notes of each build in
   [versions/](versions/) list what changed).
3. Server: write `server/bridge<build>/TWBridge<build>.java` (a neighbouring bridge is the best start), and add the class
   loader case in `GameClassLoader` (probe class, seeded generator classes).
4. Client: write `client/mod<build>/`, add `createTransformer<build>` to the patcher and the detection in
   `ClientPatcher.detect`.
5. Plugin API: see [plugin-api/README.md](plugin-api/README.md).
6. `build.sh`: add the build to the lists, add `release/client/<build>-mp.json` and `.info`.
7. Test: server start, new world, takeover of a world, two clients, blocks both ways, other players, plugins.
