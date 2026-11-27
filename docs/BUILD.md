# Building TheWello's Infdev Server Project

## Requirements

- JDK 8 (the game and the client code target Java 8).
- `sh`: on Windows use Git Bash (it comes with Git for Windows). `build.bat` is a small wrapper that calls `build.sh`.
- The original Infdev jars, which are **not** included. Put them into `original/`:

  | File | Build |
  |---|---|
  | `original/inf-20100630-1340.jar` | `inf-20100630-1340` (required: the shared API and the server core are compiled against it) |
  | `original/inf-20100227-1433.jar` | `inf-20100227-1433` (optional) |
  | `original/inf-20100313.jar` | `inf-20100313` (optional) |
  | `original/inf-20100316.jar` | `inf-20100316` (optional) |
  | `original/inf-20100320.jar` | `inf-20100320` (optional) |
  | `original/inf-20100321.jar` | `inf-20100321` (optional) |
  | `original/inf-20100325-1640.jar` | `inf-20100325-1640` (optional) |
  | `original/inf-20100327.jar` | `inf-20100327` (optional) |
  | `original/inf-20100330-1611.jar` | `inf-20100330-1611` (optional) |
  | `original/inf-20100413-1953.jar` | `inf-20100413-1953` (optional) |
  | `original/inf-20100414.jar` | `inf-20100414` (optional) |
  | `original/inf-20100415.jar` | `inf-20100415` (optional) |
  | `original/inf-20100420.jar` | `inf-20100420` (optional) |
  | `original/inf-20100607.jar` | `inf-20100607` (optional) |
  | `original/inf-20100608.jar` | `inf-20100608` (optional) |

  The SHA-256 of each jar is listed in [MULTIVERSION.md](MULTIVERSION.md). A build whose jar is missing is skipped.
- An LWJGL 2 jar as `lib/lwjgl.jar` (compile-time only; the jar from a BetaCraft or Minecraft launcher folder works).
  It is not included in this repository.

## Build

```sh
sh build.sh
```

Environment variables (all optional): `CLIENT_JAR`, `GAME227_JAR`, `GAME313_JAR`, `GAME316_JAR`, `GAME320_JAR`, `GAME321_JAR`, `GAME325_JAR`, `GAME327_JAR`, `GAME330_JAR`, `GAME413_JAR`, `GAME414_JAR`, `GAME415_JAR`, `GAME420_JAR`, `GAME607_JAR`, `GAME608_JAR` and `LWJGL_JAR` change the
input paths; `RELEASE_DIR` changes the output folder; `BETACRAFT_DIR` installs the patched clients into your BetaCraft
folder (`.../.betacraft`); `PATCHED_CLIENTS_DIR` keeps the patched client jars in a folder of your choice.

The build runs these steps:

1. Compile `common/` (protocol, build registry, ASM hook tool).
2. Compile the client patcher.
3. Create a public copy of the original jar for compiling the mod.
4. Compile the client mods and create `dist/TheWellosInfdevClientPatcher.jar` and the patched client of each build.
5. Build the plugin API jar of each build (and a sources jar).
6. Build the server `dist/TheWellosInfdevServer.jar` with the bridge of each build.
7. Build every plugin in `plugins/<build>/` into `dist/plugins/<build>/`.
8. Assemble the release bundle and its zip archive in `../TheWellosInfdevServer-Release/`.

## Output

- `../TheWellosInfdevServer-Release/TheWellosInfdevServer-<version>/` and `TheWellosInfdevServer-<version>.zip`: the
  bundle for other people. It contains no Minecraft code: the patcher works on the user's own jar.
- The patched client jars (`<build>-mp.jar`) are Minecraft derivatives. They are never put into the bundle. They are
  installed into `BETACRAFT_DIR` or copied to `PATCHED_CLIENTS_DIR` if you set these.
- `build/` and `dist/` are removed at the end of the build.

## Running from the build

Put the original jar of the build you want to run into the server folder, then start `TheWellosInfdevServer.jar` (see
[SERVER.md](SERVER.md)). To try a client, start the patched jar with the launcher class of its build
(`TWLauncher`, `TW227Launcher`, `TW313Launcher`, `TW316Launcher`, `TW320Launcher`, `TW321Launcher`, `TW325Launcher`, `TW327Launcher`, `TW330Launcher`, `TW413Launcher`, `TW414Launcher`, `TW415Launcher`, `TW420Launcher`, `TW607Launcher` or `TW608Launcher`; for example
`java -Djava.library.path=<natives> -cp "inf-20100316-mp.jar;lwjgl.jar;lwjgl_util.jar;jinput.jar" TW316Launcher <name> --connect 127.0.0.1:25565`).

## Test aids

The clients accept a few system properties for testing (`-Dthewellodev.debug=true`, `-Dthewellodev.testchat=...` and
others). They are not meant for players.
