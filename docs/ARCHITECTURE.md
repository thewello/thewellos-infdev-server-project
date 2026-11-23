# Architecture

> The client mod and the server bridge of each build depend on the obfuscated names of that build's original jar. The
> supported builds are listed in [MULTIVERSION.md](MULTIVERSION.md); other builds are not supported.

## Server

- The server loads the original Infdev classes of the selected build with a custom class loader and inserts small hook
  calls with ASM (`GameClassLoader`, `HookTransformer`). Every block change is reported to the server.
- Each build has a **bridge** (`GameBridge` implementation) between the server and the game classes of that build.
  The game classes are reached by reflection where a class name collides with a package name.
- **Build `inf-20100630-1340`:** world generation, the save format, water and lava flow, falling blocks, random ticks and
  mobs are the original code. Because the game is single-player by design, mobs look at a "player in the world" field;
  before every entity update the server sets it to the nearest player's proxy (`TWServerHook`). Players have invisible
  proxies in the game world so mobs can target them (`TWProxy`).
- **Build 227:** the bridge loads and saves the chunks of the game's own world and uses its terrain generator for new ones.
- **Builds 313, 316, 320, 321, 325-1640, 327, 330-1611 and 413-1953:** the bridge replaces the game's chunk provider (a `java.lang.reflect.Proxy` over the game's
  interface), because the game keeps only a ring of chunks and never saves. The server keeps, saves and streams every
  chunk and uses the game's own terrain generator for new ones. Light and block metadata are the game's own. In 320 the bridge also populates new chunks (trees) and saves the
  tile entities of chests and furnaces.
- Chunks are sent to each client on demand; the server sends block, metadata, light and height data.
- Client actions (block changes, items, attacks, health reports, inventory summaries) are validated on the server before
  they are applied and shared with other players.

## Client

- The patched client receives chunks and entities from the server and does not generate its own world.
- Builds 313, 316, 320, 321, 325-1640, 327, 330-1611 and 413-1953: the client replaces the world's chunk provider by one that serves only the chunks received from
  the server (build 227 fills the game's own world with the received chunks). Other players are real game entities that are not in the world's entity list; the mod moves, animates and
  draws them (and, in 316, follows arrows that cross them).
- Build 630: mobs and arrows are created as real game objects so their models, sounds and death animations come from the
  game. Their AI is disabled; their position comes from the server. Chests and furnaces use a lock: only one player can open
  a container at a time, which prevents item duplication.
- Mod code cannot name the game classes in source (some builds have a class `net/minecraft/a/a.class` next to the
  package `net/minecraft/a/a/`), so builds 227, 313, 316, 320, 321, 325-1640, 327, 330-1611 and 413-1953 reach every game member by reflection.

## Protocol

Packets are `[byte id][body]`, written with `DataOutputStream`. The definitions are in
`common/src/net/thewellodev/protocol/Protocol.java`. The protocol number of the build is checked on connect.

## Security measures

- Accounts are verified with Mojang's session server (`online-mode=true`). The access token goes only to Mojang and Microsoft.
- Connection limits per IP and in total, a handshake timeout, and a per-connection queue limit.
- Rate limits per player for blocks, items, movement, digging and chat.
- Optional checks (movement, reach, attack rate, health, inventory, chat) live in the anti-cheat plugin, not in the core.
- See [SECURITY.md](../SECURITY.md) for the known limits.

## Plugins

Plugin jars are loaded with one class loader per plugin. Plugins receive events through `@EventHandler` methods and talk to
the server through the API of their build. The server implements the API of the shared base (`net.thewellodev.api`,
build `inf-20100630-1340`) in `server/src/net/thewellodev/server/`; builds 227, 313, 316, 320, 321, 325-1640, 327, 330-1611 and 413-1953 have their own API
(`api227`, `api313`, `api316`, `api320`, `api321`, `api325`, `api327`, `api330`, `api413`) as a view of the same server objects, with an event translator. See
[plugin-api/README.md](plugin-api/README.md).
