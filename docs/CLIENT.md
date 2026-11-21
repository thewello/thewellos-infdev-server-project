# Client

## Setup

1. Patch your original game jar. In the release archive, run `client/patch-client.bat <original jar>`
   (`client/patch-client.sh` on other systems). The build is detected from the jar, and the result is written to
   `client/patched/<build>-mp.jar`, for example `inf-20100316-mp.jar`. Supported builds: `inf-20100227-1433`,
   `inf-20100313`, `inf-20100316`, `inf-20100320` and `inf-20100630-1340`.
   Without the scripts: `java -jar TheWellosInfdevClientPatcher.jar <original jar> <output folder>`.
2. Install the patched jar with one of the launchers:
   - **BetaCraft**: copy `<build>-mp.jar` to `%APPDATA%\.betacraft\versions` and `<build>-mp.json` and
     `<build>-mp.info` (from `client/betacraft/`) to `%APPDATA%\.betacraft\versions\jsons`. Select
     "TheWello's Infdev Multiplayer (Infdev ...)" in BetaCraft. `patch-client.bat` can do this for you.
   - **Minecraft Launcher**: create `%APPDATA%\.minecraft\versions\<build>-mp\`, put the patched jar and the
     `<build>-mp.json` file in it (both named `<build>-mp`), then create a profile that uses this version.
3. Start the game with a logged-in original account.

The patched jar contains Minecraft code. Keep it for your own use and do not share it.

## Joining a server

Main menu, then **Multiplayer...**, enter the address (`host` or `host:port`), then **Connect**.
The player name comes from your launcher account; it is not typed in. If the server uses `online-mode=true`, the
account is verified with Mojang. The server must run the same Infdev build as your client.

## Controls

| Key | Action |
|---|---|
| T | Chat |
| / | Command |
| Q | Drop item (build 20100630-1340) |
| Esc | Menu (does not pause the game in multiplayer) |
| Double Space (creative only) | Toggle flight; Space up, Shift down |

## Notes

- Builds 20100227, 20100313, 20100316 and 20100320 keep the inventory on the client. The server saves what the client reports and
  gives it back at the next login. In build 20100630-1340 the server checks the inventory (invalid items, stack sizes,
  sudden gains).
- Chests and furnaces (builds 20100320 and 20100630-1340) are locked while one player uses them.
- A patched client only works with a server of this project that runs the same build and the same project version.
  Protocol mismatches are reported on connect.
- Two game windows on one computer share one mouse. A window without the keyboard focus lets go of the mouse and reads
  no mouse movement (the patcher replaces the game's `Mouse.getDX()` and `Mouse.getDY()` calls in its renderer by
  `TWMouse.dx()` and `TWMouse.dy()`), so the camera of the other window does not turn. This holds for every build.
- If the game window hangs while connected (build 227, 313, 316 or 320), it writes a report to
  `%USERPROFILE%\.thewellodev\freeze-<time>.txt`.

## BetaCraft proxy and skins

The BetaCraft launch profiles (`client/betacraft/<build>-mp.info`) set
`-Dhttp.proxyHost=betacraft.uk -Dhttp.proxyPort=11702`. This is BetaCraft's proxy. It applies skin fixes for old
Minecraft versions, so player skins show up when the game is started through the BetaCraft launcher with this profile.
The client loads skins from `http://www.minecraft.net/skin/<name>.png`, the same way the original game does. Skins may
not load when the game is started without that proxy setting. The skin request is plain HTTP, so a player's name is
visible to anyone on the network path.
