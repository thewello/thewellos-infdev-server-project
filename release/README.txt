TheWello's Infdev Server Project
================================

A multiplayer server, a client patcher and plugin APIs for early Minecraft Infdev builds.

Supported builds:  inf-20100227-1433   inf-20100313   inf-20100316   inf-20100320   inf-20100630-1340

One server runs one build, chosen in server/server.properties (game-version). Only players with a client of the same
build can join. This package contains no Minecraft code: you patch your own copy of the game jar.

Contents
  client/       the client patcher, patch-client.bat / patch-client.sh, and the BetaCraft files (betacraft/)
  server/       the server jar, start scripts, server.properties and the optional plugins (per build)
  plugin-api/   for each build: API jar, API documentation, example plugin

Requirements: Java 8 (the server and the patcher), and your own original Infdev jar of each build you use.

1. Client
   a) Run  client\patch-client.bat <path to the original Infdev jar>  (on other systems: client/patch-client.sh).
      The build is detected from the jar. The patched jar is written to client\patched\ (for example
      inf-20100316-mp.jar). The patched jar contains Minecraft code: keep it for your own use, do not share it.
   b) BetaCraft: the script offers to install the patched client. By hand: copy the jar to
        %APPDATA%\.betacraft\versions
      and the matching .json and .info files from client\betacraft\ to
        %APPDATA%\.betacraft\versions\jsons
      Restart BetaCraft and pick "TheWello's Infdev Multiplayer (Infdev <build>)".
   c) Official Minecraft Launcher: create %APPDATA%\.minecraft\versions\<build>-mp\, put the patched jar and the
      matching .json file in it (rename them to <build>-mp.jar and <build>-mp.json), and make a launcher profile
      that uses that version.
   In the game click "Multiplayer..." on the title screen and type the server address. Builds 20100227, 20100313,
   20100316 and 20100320: press T to chat or type a command such as /help (Enter sends, Esc cancels). The pause menu has a
   Disconnect button; saving and loading levels are off online.

2. Server
   a) Put the original jar of the build you want to run into server/ (for example inf-20100316.jar; the patched
      client jar of the same build also works). The server recognises a jar by its contents, not by its name.
   b) Open server.properties and set game-version to inf-20100630-1340, inf-20100227-1433, inf-20100313,
      inf-20100316 or inf-20100320.
   c) Run start-server.bat (or start-server.sh). Allow the Windows firewall when asked.
      Worlds are saved in worlds/<game-version>/<level-name>/.
   d) Moving a world to a newer build: set game-version to the newer build and start the server. If the level
      (level-name) has no world for that build yet but has one for an older build, the server takes it over
      (227 -> 313 -> 316 -> 320; later builds are added as they come). It starts at once, and each chunk is converted the
      first time a player needs it. The old world is only read, never changed, so the older build still opens it.
      Type /convertworld to convert the chunks nobody has visited yet (a batch every tick; the server keeps
      running); the console says when the old folder is not needed any more. Until then do not delete it: the
      chunks that were not converted yet would be lost. Spawn point, player positions and saved inventories come
      along. Converting back to an older build is not possible.

3. Plugins
   Copy a jar from server/optional-plugins/<your build>/ into server/plugins/ and restart. Every build has its own
   API and plugins; the server loads only the plugins of the build it runs (and says so in the console). See
   plugin-api/<your build>/PLUGIN-API.md to write your own plugins.

Operators: type /op <name> in the server console, or add the name to ops.txt. Type /help in the console or in chat;
it lists only the commands that exist for the running build.

Notes on the builds
  inf-20100227-1433  Blocks only. It has no mobs, items, health, light, day and night or inventory, so those commands
                     and features are not available. The game makes its terrain from unseeded random numbers; the
                     server seeds them, so level-seed works and the same seed gives the same terrain. A world that
                     was saved by the unmodified game has no known seed: terrain made later does not fit its saved part.
  inf-20100313       Adds sky and block light, block metadata (torch direction, cloth colours), the day and night
                     cycle (12000 ticks, the server keeps the time) and a very large world. No mobs, items or health.
                     The game never saves chunks; the server saves them.
  inf-20100316       As 20100313 with a 24000 tick day. The game now has item entities: blocks drop what they give, items
                     fall and can be picked up. Each player's game runs its items; the server passes every new item and
                     every pickup on, so all players see the same items (items that lay on the ground before a player
                     joined are not shown to him). An arrow that hits a block spawns a mob; every player sees it and can hurt it
                     (it lives in the game of the player who shot the arrow, the others see a copy; when that player
                     leaves, the nearest other player takes the mob over). Only one player can pick up a shared item. Arrows hurt
                     other players (4 damage). Fire cannot be punched out and creepers do not explode: the game has
                     no code for either.
  inf-20100320       As 20100316 with trees and ores in the terrain, chests and furnaces that keep their items,
                     explosions (creepers and TNT) and fire that can be punched out. Chests and furnaces are shared:
                     only one player can have one open (the server locks it) and the contents are saved by the server.
                     A world of an older build is taken over as before; its chunks keep what they have, new land has the
                     new trees and ores, so the edge of the converted part is visible. Starting inventory: 64 of each item.
                     Explosions hurt other players too, and the many blocks they destroy are accepted by the server
                     in one burst. Mobs that arrows make and the items on the ground are shared as in 316.
  inf-20100630-1340  The full game: mobs, items, minecarts, signs, chests and furnaces, health and inventory.

Inventories (inf-20100227-1433, inf-20100313, inf-20100316 and inf-20100320)
  These builds keep the inventory on the client, and every build starts a player with its own default one. The server
  saves what the client reports (worlds/<build>/<level>/players/<name>.inv) and sends it back at the next login, also
  when the server moves to a newer build. A player who was never saved starts with the default of the build he joins.

Freezes
  If the server hangs (one tick takes more than 8 seconds) it writes where it is stuck to logs/freeze-<time>.txt.
  If the game window of build 227, 313, 316 or 320 hangs while connected, it writes
  %USERPROFILE%\.thewellodev\freeze-<time>.txt. Please attach these files to a bug report.

Notes
  - online-mode=true requires original (paid) Minecraft accounts.
  - The server and the client must be the same version of this project and the same Infdev build.
  - Third-party licenses: THIRD-PARTY-LICENSES.txt.

BetaCraft proxy
  The BetaCraft launch profiles set BetaCraft's HTTP proxy (betacraft.uk:11702). It enables skin fixes when the game is
  started through the BetaCraft launcher. Skins may not load without it.

License and source code
  This program is licensed under the GNU General Public License v3.0 (see LICENSE).
  The source code is in the project repository:
  https://github.com/thewello/thewellos-infdev-server-project
