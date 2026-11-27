# Plugin APIs

Every game build has its own plugin API, source folder, plugins and release folder. A plugin is made for one build
(`api:` line in `plugin.yml`) and the server only loads plugins of the build it runs.

| Build | API source | Plugins | Docs | In the release bundle |
|---|---|---|---|---|
| inf-20100630-1340 | `api/inf-20100630-1340/` (`net.thewellodev.api`) | `plugins/inf-20100630-1340/` | [inf-20100630-1340.md](inf-20100630-1340.md) | `plugin-api/inf-20100630-1340/`, `server/optional-plugins/inf-20100630-1340/` |
| inf-20100227-1433 | `api/inf-20100227-1433/` (`net.thewellodev.api227`) | `plugins/inf-20100227-1433/` | [inf-20100227-1433.md](inf-20100227-1433.md) | `plugin-api/inf-20100227-1433/`, `server/optional-plugins/inf-20100227-1433/` |
| inf-20100313 | `api/inf-20100313/` (`net.thewellodev.api313`) | `plugins/inf-20100313/` | [inf-20100313.md](inf-20100313.md) | `plugin-api/inf-20100313/`, `server/optional-plugins/inf-20100313/` |
| inf-20100320 | `api/inf-20100320/` (`net.thewellodev.api320`) | `plugins/inf-20100320/` | [inf-20100320.md](inf-20100320.md) | `plugin-api/inf-20100320/`, `server/optional-plugins/inf-20100320/` |
| inf-20100321 | `api/inf-20100321/` (`net.thewellodev.api321`) | `plugins/inf-20100321/` | [inf-20100321.md](inf-20100321.md) | `plugin-api/inf-20100321/`, `server/optional-plugins/inf-20100321/` |
| inf-20100413-1953 | `api/inf-20100413-1953/` (`net.thewellodev.api413`) | `plugins/inf-20100413-1953/` | [inf-20100413-1953.md](inf-20100413-1953.md) | `plugin-api/inf-20100413-1953/`, `server/optional-plugins/inf-20100413-1953/` |
| inf-20100414 | `api/inf-20100414/` (`net.thewellodev.api414`) | `plugins/inf-20100414/` | [inf-20100414.md](inf-20100414.md) | `plugin-api/inf-20100414/`, `server/optional-plugins/inf-20100414/` |
| inf-20100415 | `api/inf-20100415/` (`net.thewellodev.api415`) | `plugins/inf-20100415/` | [inf-20100415.md](inf-20100415.md) | `plugin-api/inf-20100415/`, `server/optional-plugins/inf-20100415/` |
| inf-20100420 | `api/inf-20100420/` (`net.thewellodev.api420`) | `plugins/inf-20100420/` | [inf-20100420.md](inf-20100420.md) | `plugin-api/inf-20100420/`, `server/optional-plugins/inf-20100420/` |
| inf-20100607 | `api/inf-20100607/` (`net.thewellodev.api607`) | `plugins/inf-20100607/` | [inf-20100607.md](inf-20100607.md) | `plugin-api/inf-20100607/`, `server/optional-plugins/inf-20100607/` |
| inf-20100608 | `api/inf-20100608/` (`net.thewellodev.api608`) | `plugins/inf-20100608/` | [inf-20100608.md](inf-20100608.md) | `plugin-api/inf-20100608/`, `server/optional-plugins/inf-20100608/` |
| inf-20100330-1611 | `api/inf-20100330-1611/` (`net.thewellodev.api330`) | `plugins/inf-20100330-1611/` | [inf-20100330-1611.md](inf-20100330-1611.md) | `plugin-api/inf-20100330-1611/`, `server/optional-plugins/inf-20100330-1611/` |
| inf-20100327 | `api/inf-20100327/` (`net.thewellodev.api327`) | `plugins/inf-20100327/` | [inf-20100327.md](inf-20100327.md) | `plugin-api/inf-20100327/`, `server/optional-plugins/inf-20100327/` |
| inf-20100325-1640 | `api/inf-20100325-1640/` (`net.thewellodev.api325`) | `plugins/inf-20100325-1640/` | [inf-20100325-1640.md](inf-20100325-1640.md) | `plugin-api/inf-20100325-1640/`, `server/optional-plugins/inf-20100325-1640/` |
| inf-20100316 | `api/inf-20100316/` (`net.thewellodev.api316`) | `plugins/inf-20100316/` | [inf-20100316.md](inf-20100316.md) | `plugin-api/inf-20100316/`, `server/optional-plugins/inf-20100316/` |

The APIs of `inf-20100227-1433`, `inf-20100313`, `inf-20100316`, `inf-20100320`, `inf-20100321`, `inf-20100325-1640`, `inf-20100327`, `inf-20100330-1611`, `inf-20100413-1953`, `inf-20100414`, `inf-20100415`, `inf-20100420`, `inf-20100607` and `inf-20100608` are views of the same server objects (blocks, players,
chat, commands, health, PvP, game mode, teleporting); the later builds add block data and the world time. The API of
`inf-20100630-1340` also has items, inventories, mobs and entities.

## Adding the API of a new build

1. `api/<build>/src/net/thewellodev/api<xxx>/`: the interfaces and events that build has (copy a neighbour and remove what
   the build does not have). Version-neutral classes (plugin framework, commands, config, scheduler, event base classes)
   stay in `api/inf-20100630-1340/src` and are shared.
2. `server/src/net/thewellodev/server/api<xxx>/`: wrappers around the server's objects and an event translator (see
   `api227` for the pattern), switched on in `TWServer` for that build.
3. `plugins/<build>/<plugin>/src` with `api: <build>` in each `plugin.yml`; `docs/plugin-api/<build>.md`.
4. `build.sh` builds `dist/TheWellosInfdevServer-API-<build>.jar` and the plugins of `plugins/<build>/` (add the build to the two
   `for v in` lists).
