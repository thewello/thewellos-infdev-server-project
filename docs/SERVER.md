# Server

## Starting

Run `start-server.bat` (Windows), `start-server.sh`, or:

```sh
java -Xmx1G -jar TheWellosInfdevServer.jar          # with the control panel
java -Xmx1G -jar TheWellosInfdevServer.jar nogui    # console only
```

The server needs the game jar of the build it runs in its folder: the original jar (for example `inf-20100316.jar`)
or the patched client jar of the same build (`inf-20100316-mp.jar`). It loads the game from there. A jar is recognised
by its contents, not by its name; with several jars in the folder the one that belongs to `game-version` is used.

Java 8 is required. Use `-Xmx2G` for more than about 20 players.

## server.properties

| Key | Default | Description |
|---|---|---|
| `server-port` | 25565 | TCP port |
| `server-ip` | (empty) | Bind to a specific address |
| `motd` | `&eA TheWello's Infdev server` | Message shown in the server list (`&` colour codes) |
| `max-players` | 20 | Player limit |
| `upnp` | true | Open the port on the router automatically |
| `online-mode` | true | Verify accounts with Mojang. Set to false only for offline testing |
| `white-list` | false | Only players in `white-list.txt` may join |
| `level-name` | world | World folder |
| `level-seed` | (empty) | Seed; empty means random |
| `view-distance` | 6 | Chunk radius sent to players (2-15) |
| `spawn-protection` | 0 | Radius around spawn where non-operators cannot change blocks |
| `pvp` | true | Players can damage each other |
| `difficulty` | 2 | 0 peaceful, 1 easy, 2 normal, 3 hard |
| `spawn-monsters` | true | Natural monster spawning |
| `spawn-animals` | true | Natural animal spawning |
| `mob-cap-monsters` | 100 | Monsters per player (within 128 blocks) |
| `mob-cap-animals` | 20 | Animals per player (within 128 blocks) |
| `autosave-minutes` | 5 | Interval for saving the world |
| `game-version` | inf-20100630-1340 | The game build this server runs: `inf-20100630-1340`, `inf-20100227-1433`, `inf-20100313`, `inf-20100316` or `inf-20100320`. Only that build's content is used, and only clients of that build can join. See [MULTIVERSION.md](MULTIVERSION.md) |
| `game-jar` | auto | Game jar to load. `auto` picks the first `inf-*.jar` in the server folder that belongs to `game-version`. An explicit name must belong to it too |

Worlds are stored in `worlds/<game-version>/<level-name>/`, so two builds never share a world folder. A world
from an early release of this project (folder `<level-name>` next to `server.properties`) is moved there once on the
first start with `inf-20100630-1340`.

### Moving a world to a newer build

Set `game-version` to the newer build and start the server. If `level-name` has no world for that build yet but has one
for an older build (`inf-20100227-1433` to `inf-20100313` to `inf-20100316` to `inf-20100320`), the server takes it over: it creates the new
world at once and converts each chunk the first time a player needs it. The old world is only read. `/convertworld`
converts the chunks nobody has visited yet. Do not delete the old world before the console says it is complete.
See [MULTIVERSION.md](MULTIVERSION.md).

Builds `inf-20100227-1433`, `inf-20100313`, `inf-20100316` and `inf-20100320` have no mobs or animals: `difficulty`, `spawn-monsters`,
`spawn-animals` and the mob caps have no effect there, and the commands that need them are not registered.

## Operators and permissions

- Operators are listed in `ops.txt`, or set with `/op <name>` in the console.
- `permissions.txt` gives non-operators extra permissions: `player: perm.one, perm.two`, or `*: perm` for everyone.
- Plugin permissions that end in `.use` or `.default` are open to everyone; others need operator status or an entry in `permissions.txt`.

## Commands

Type `/help` in the console or in chat. `/help <word>` searches commands; `/help <command>` shows one command.

## Plugins

Put plugin jars in the `plugins/` folder and restart (or use `/reload`). Each build has its own API and plugins, and a
plugin only loads on the build named in its `plugin.yml` (`api:` line): see [plugin-api/](plugin-api/README.md).

## Files

| File | Purpose |
|---|---|
| `worlds/<build>/<level>/` | World data, player positions and (builds 227, 313, 316, 320) saved inventories |
| `ops.txt`, `white-list.txt`, `banned-players.txt` | Access lists |
| `permissions.txt` | Extra permissions |
| `logs/latest.log` | Log of the current run |
| `logs/freeze-*.txt` | Written when one server tick takes more than 8 seconds (where the server is stuck) |
| `plugins/` | Plugin jars and their configuration folders |
