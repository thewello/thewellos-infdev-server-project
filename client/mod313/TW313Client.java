import net.thewellodev.protocol.GameVersion;
import net.thewellodev.protocol.Protocol;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Multiplayer for inf-20100313 (client side).
 *
 * Hooks (inserted by the patcher, see ClientPatcher.createTransformer313):
 *   tick(mc)       start of the game tick (net/minecraft/client/d.f)
 *   onBlockSet     end of the world's set-block method (net/minecraft/a/a/f.b(IIII))
 *   onMetaSet      end of the world's set-metadata method (net/minecraft/a/a/f.a(IIII))
 * and the menus (title screen, pause menu, death screen, HUD, renderer) as in the 20100227 client.
 *
 * The jar has classes such as net/minecraft/a/a.class next to the package net/minecraft/a/a/, so the game classes
 * cannot be named in source: every game class and member is reached by reflection (same as the server bridge).
 *
 * The world's chunk provider (world field q) is replaced by one that serves only the chunks received from the
 * server (the game's own generates terrain and forgets chunks). The server sends blocks, metadata, sky light,
 * block light and the height map of each chunk; the light changes of block edits are worked out here by the
 * game's own light engine, the same as on the server.
 */
public final class TW313Client {

    private static final double EYE_HEIGHT = 1.62;
    /** -Dthewellodev.debug=true prints the block hook calls. */
    private static final boolean DEBUG = Boolean.getBoolean("thewellodev.debug");

    private static TWConnection conn;
    private static boolean ready;
    private static boolean failed;
    private static volatile boolean blankTerrain;
    private static volatile boolean applyingRemote;
    private static boolean loggedIn;

    // spawn hold: the player stays at the spawn point until its chunk has arrived
    private static boolean hold;
    private static double spawnX, spawnY, spawnZ;
    private static float spawnYaw, spawnPitch;
    private static int spawnCx, spawnCz;

    private static int ticksSincePos;
    private static double lastX, lastY, lastZ;
    private static float lastYaw, lastPitch;

    // reflection handles
    private static Field mcWorld;
    private static Field worldPlayer;
    private static Field worldProvider;
    private static Field worldTime;
    private static Method getMeta, setMeta, dirtyRegion;
    private static Class<?> providerClass, chunkClass;
    private static Field chunkMeta, chunkSky, chunkBlockLight, chunkHeight, chunkLowest, nibbleBytes;
    private static Method chunkRelight;
    /** The chunks the world can see, by game number (chunk coordinate & 0x0FFFFFFF). */
    private static final Map<Long, Object> chunkMap = new java.util.HashMap<Long, Object>();
    /** Keys of the chunks that came from the server (the others are empty stand-ins). */
    private static final java.util.Set<Long> received = new java.util.HashSet<Long>();
    private static Field posX, posY, posZ, rotYaw, rotPitch;
    private static Method setPosRot;
    private static Method setBlock;
    private static Method getBlock;
    private static Method setWorld;
    private static Method setScreen;
    private static Class<?> worldClass;
    private static Field fontField;
    private static Field screenField;
    private static Method fontDraw;
    private static Constructor<?> scaledCtor;
    private static Method scaledHeight;
    private static Method scaledWidth;
    private static Constructor<?> chunkCtor;

    private TW313Client() {
    }

    // ----- hooks ------------------------------------------------------------------------------------------

    /** Called at the start of every game tick. */
    public static void tick(Object mc) {
        if (failed) {
            return;
        }
        TWConnection bc = conn;
        if (bc != null) {
            bc.beginBatch(); // what this tick sends goes out together (an explosion makes hundreds of block changes)
        }
        try {
            doTick(mc);
        } catch (Throwable t) {
            failed = true;
            blankTerrain = false;
            System.err.println("[Infdev MP] client error, multiplayer stopped: " + t);
            t.printStackTrace();
            if (conn != null) {
                conn.close("Client error: " + t);
            }
        } finally {
            if (bc != null) {
                bc.endBatch();
            }
            TWConnection now = conn;
            if (now != null && now != bc) {
                now.endBatch();
            }
        }
    }

    /** Called before every return of e.a(IIII): ok tells whether the block id changed. */
    public static void onBlockSet(boolean ok, int x, int y, int z, int id) {
        if (DEBUG) {
            System.out.println("[Infdev MP] block hook ok=" + ok + " remote=" + applyingRemote + " at " + x + " " + y
                    + " " + z + " id " + id);
        }
        if (!ok || applyingRemote || conn == null || !loggedIn || y < 0 || y >= 128) {
            return;
        }
        conn.sendBlockChange(x, y, z, id, currentMeta(x, y, z));
    }

    /** Called before every return of the world's set-metadata method (the block was set a moment earlier). */
    public static void onMetaSet(int x, int y, int z, int meta) {
        if (applyingRemote || conn == null || !loggedIn || y < 0 || y >= 128) {
            return;
        }
        try {
            Object world = mcWorld.get(mcRef);
            int id = (Integer) getBlock.invoke(world, x, y, z);
            conn.sendBlockChange(x, y, z, id, meta & 15);
        } catch (Exception ignored) {
        }
    }

    private static int currentMeta(int x, int y, int z) {
        try {
            return (Integer) getMeta.invoke(mcWorld.get(mcRef), x, y, z);
        } catch (Exception ex) {
            return 0;
        }
    }

    /** Called at the start of the terrain generator: a non-null array replaces the generated terrain. */
    public static byte[] blank() {
        return blankTerrain ? new byte[Protocol.CHUNK_BLOCKS] : null;
    }

    // ----- title screen -----------------------------------------------------------------------------------

    private static String lastAddress = loadLastAddress();
    private static volatile boolean askingAddress;

    private static File addressFile() {
        return new File(new File(System.getProperty("user.home"), ".thewellodev"), "last-server-313.txt");
    }

    private static String loadLastAddress() {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(addressFile()));
            try {
                String line = r.readLine();
                if (line != null && line.trim().length() > 0) {
                    return line.trim();
                }
            } finally {
                r.close();
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1:25565";
    }

    private static void saveLastAddress(String address) {
        try {
            File f = addressFile();
            f.getParentFile().mkdirs();
            java.io.FileWriter w = new java.io.FileWriter(f);
            try {
                w.write(address + "\n");
            } finally {
                w.close();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Called at the end of the title screen's init (net/minecraft/client/q.b): the disabled third button
     * ("Play tutorial level", id 3) becomes "Multiplayer...".
     */
    public static void titleInit(Object screen) {
        try {
            Class<?> base = Class.forName("net.minecraft.client.c.i", true, screen.getClass().getClassLoader());
            Field list = base.getDeclaredField("e");
            list.setAccessible(true);
            // loading a level crashes this build's title screen (saving/loading is a stub): switch it off
            for (Object other : (List<?>) list.get(screen)) {
                Object text = other.getClass().getField("a").get(other);
                if (text != null && text.toString().toLowerCase().indexOf("load") >= 0) {
                    other.getClass().getField("c").setBoolean(other, false);
                }
            }
            Object button = ((List<?>) list.get(screen)).get(2);
            button.getClass().getField("a").set(button, "Multiplayer...");
            button.getClass().getField("c").setBoolean(button, true);
        } catch (Throwable t) {
            System.err.println("[Infdev MP] could not add the multiplayer button: " + t);
        }
    }

    /**
     * Called at the start of the title screen's button handler (net/minecraft/client/q.a(button)).
     * @return true when the button was the multiplayer button (the game's own handler is skipped)
     */
    public static boolean titleAction(Object screen, Object button) {
        try {
            if (button.getClass().getField("b").getInt(button) != 3) {
                return false;
            }
            if (askingAddress) {
                return true;
            }
            askingAddress = true;
            // The dialog runs on its own thread: the game loop must not wait for it (the game's window and the
            // dialog would block each other).
            Thread t = new Thread("Infdev MP address dialog") {
                public void run() {
                    try {
                        Object answer = javax.swing.JOptionPane.showInputDialog(null,
                                "Server address (host or host:port)", "Infdev multiplayer",
                                javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, lastAddress);
                        if (answer != null && answer.toString().trim().length() > 0 && conn == null) {
                            lastAddress = answer.toString().trim();
                            saveLastAddress(lastAddress);
                            System.setProperty("thewellodev.autoconnect", lastAddress);
                        }
                    } finally {
                        askingAddress = false;
                    }
                }
            };
            t.setDaemon(true);
            t.start();
            return true;
        } catch (Throwable t) {
            System.err.println("[Infdev MP] multiplayer button failed: " + t);
            return false;
        }
    }

    // ----- pause menu and chat ----------------------------------------------------------------------------

    private static final java.util.List<Object[]> chatLines = new java.util.ArrayList<Object[]>();
    private static Object mcRef;
    private static boolean tWasDown;
    private static volatile boolean chatOpen;
    private static long loginTime;
    private static boolean testDone;
    private static double walkY = Double.NaN;
    private static double hoverX, hoverY = Double.NaN, hoverZ;
    private static float hoverYaw;
    private static int debugTicks;
    private static int movesThisTick;
    private static volatile String lastAuthError;
    private static boolean hudErrorShown;
    private static boolean hudSeen;

    private static boolean connected() {
        return conn != null && loggedIn;
    }

    /** End of the pause menu's init (net/minecraft/client/c/b.b): no saving or loading online, a disconnect button. */
    public static void pauseInit(Object screen) {
        if (!connected()) {
            return;
        }
        try {
            Class<?> base = Class.forName("net.minecraft.client.c.i", true, screen.getClass().getClassLoader());
            Field list = base.getDeclaredField("e");
            list.setAccessible(true);
            List<?> buttons = (List<?>) list.get(screen);
            // ids: 1 generate new level, 2 save level, 3 load level
            Object gen = buttons.get(1);
            gen.getClass().getField("a").set(gen, "Disconnect");
            for (int i = 2; i <= 3; i++) {
                Object b = buttons.get(i);
                b.getClass().getField("c").setBoolean(b, false);
            }
        } catch (Throwable t) {
            System.err.println("[Infdev MP] pause menu: " + t);
        }
    }

    /** Start of the pause menu's button handler: true if the click was handled here. */
    public static boolean pauseAction(Object screen, Object button) {
        if (!connected()) {
            return false;
        }
        try {
            int id = button.getClass().getField("b").getInt(button);
            if (id == 1) {
                disconnect();
                return true;
            }
            return id == 2 || id == 3; // save and load do nothing online
        } catch (Throwable t) {
            return false;
        }
    }

    /** The player chose Disconnect in the pause menu. */
    private static void disconnect() throws Exception {
        leave("Disconnected", mcRef);
    }

    /**
     * Ends the connection (for whatever reason), goes back to the title screen and, for a failure, tells the
     * player why in a message box (on its own thread: the game loop must not wait for it).
     */
    /** Before the connection is closed: what the server has not got yet (the inventory, an open chest). */
    private static void flushOnLeave(TWConnection c, Object mc) throws Exception {
        Object world = mcWorld.get(mc);
        Object player = world == null ? null : worldPlayer.get(world);
        if (player == null) {
            return;
        }
        if (invSynced) {
            String sig = inventorySignature(player);
            if (!sig.equals(invSignature)) {
                invSignature = sig;
                c.sendInventoryReport(inventoryTriples(player));
            }
        }
    }

    private static void leave(String reason, Object mc) {
        TWConnection c = conn;
        if (c != null && loggedIn) {
            try {
                flushOnLeave(c, mc);
            } catch (Throwable ignored) {
                // the connection may be gone already
            }
        }
        if (c != null) {
            c.close(reason);
        }
        conn = null;
        loggedIn = false;
        blankTerrain = false;
        hold = false;
        chatOpen = false;
        chatScreen = null;
        synchronized (chatLines) {
            chatLines.clear();
        }
        synchronized (remotes) {
            remotes.clear();
        }
        try {
            if (mcWorld.get(mc) != null) {
                mcWorld.set(mc, null);
                mc.getClass().getField("f").set(mc, null); // the player
                Object title = Class.forName("net.minecraft.client.q", true, mc.getClass().getClassLoader())
                        .getConstructor().newInstance();
                setScreen.invoke(mc, title);
            }
        } catch (Exception e) {
            System.err.println("[Infdev MP] could not return to the title screen: " + e);
        }
        System.out.println("[Infdev MP] disconnected: " + reason);
        String shown = reason;
        if (lastAuthError != null) {
            // the server closes the connection after a failed account check: show the real reason
            shown = lastAuthError;
        } else if (shown != null && shown.endsWith(": null")) {
            shown = shown.substring(0, shown.length() - 6);
        }
        lastAuthError = null;
        if (shown != null && !"Disconnected".equals(shown)) {
            notifyUser(shown);
        }
    }

    private static void notifyUser(final String message) {
        Thread t = new Thread("Infdev MP message") {
            public void run() {
                javax.swing.JOptionPane.showMessageDialog(null, message, "Infdev multiplayer",
                        javax.swing.JOptionPane.WARNING_MESSAGE);
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /** Packets that can arrive before the login packet. */
    private static void handleBeforeLogin(TWConnection c, TWConnection.Pkt p) {
        switch (p.id) {
            case Protocol.AUTH_REQUEST:
                startAuth(c, p.s);
                break;
            case Protocol.KEEP_ALIVE:
                c.sendKeepAlive(p.i1);
                break;
            case Protocol.DISCONNECT:
                c.close(p.s);
                break;
            default:
                break;
        }
    }

    private static void addChat(String text) {
        synchronized (chatLines) {
            chatLines.add(new Object[]{text, System.currentTimeMillis()});
            while (chatLines.size() > 50) {
                chatLines.remove(0);
            }
        }
    }

    /** End of the in-game HUD render (net/minecraft/client/c/l.a(float)): draws the last chat lines. */
    public static void drawHud(Object hud) {
        if (!connected() || mcRef == null || fontDraw == null) {
            return;
        }
        try {
            Object font = fontField.get(mcRef);
            int w = mcRef.getClass().getField("b").getInt(mcRef);
            int h = mcRef.getClass().getField("c").getInt(mcRef);
            Object scaled = scaledCtor.newInstance(w, h);
            int sh = (Integer) scaledHeight.invoke(scaled);
            int sw = (Integer) scaledWidth.invoke(scaled);
            hudFrames++;
            if (DEBUG && (hudFrames == 300 || hudFrames == 301)) {
                hudTagPrints = 0;
                synchronized (remotes) {
                    for (Remote r : remotes.values()) {
                        System.out.println("[Infdev MP] frame " + hudFrames + " " + r.name + " visible " + r.tagVisible
                                + " screen " + (int) r.tagX + "," + (int) r.tagY);
                    }
                }
            } else {
                hudTagPrints = -1;
            }
            drawNameTags(font, w, h, sw, sh);
            long now = System.currentTimeMillis();
            if (DEBUG && !hudSeen) {
                hudSeen = true;
                System.out.println("[Infdev MP] hud drawn: screen " + w + "x" + h + ", scaled height " + sh
                        + ", chat lines " + chatLines.size());
            }
            if (chatOpen) {
                String cursor = (now / 500) % 2 == 0 ? "_" : "";
                fontDraw.invoke(font, "> " + chatInput + cursor, 2, sh - 40, 0xFFFFFF);
            }
            synchronized (chatLines) {
                int row = 0;
                for (int i = chatLines.size() - 1; i >= 0 && row < 8; i--) {
                    Object[] l = chatLines.get(i);
                    if (!chatOpen && now - (Long) l[1] > 12000) {
                        break;
                    }
                    fontDraw.invoke(font, (String) l[0], 2, sh - 52 - row * 10, 0xFFFFFF);
                    row++;
                }
            }
        } catch (Throwable t) {
            // a HUD error must not break the frame; it is reported once
            if (!hudErrorShown) {
                hudErrorShown = true;
                System.err.println("[Infdev MP] chat display: " + t);
                t.printStackTrace();
            }
        }
    }

    private static final StringBuilder chatInput = new StringBuilder();
    private static Object chatScreen;

    /** Called every tick while logged in: the chat key, typing into the chat line and the test aids. */
    private static void chatTick(Object mc) throws Exception {
        if (chatOpen) {
            if (screenField.get(mc) != chatScreen) {
                // another screen took over (death screen, pause menu...): drop the chat line
                chatOpen = false;
                chatScreen = null;
            } else {
                // The game has a screen open (movement and mouse look are off, as in a real chat screen).
                // The keys are read here, before the game sees them.
                while (org.lwjgl.input.Keyboard.next()) {
                    if (!org.lwjgl.input.Keyboard.getEventKeyState()) {
                        continue;
                    }
                    int key = org.lwjgl.input.Keyboard.getEventKey();
                    char ch = org.lwjgl.input.Keyboard.getEventCharacter();
                    if (key == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
                        chatInput.setLength(0);
                        closeChat(mc);
                        break;
                    } else if (key == org.lwjgl.input.Keyboard.KEY_RETURN
                            || key == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
                        String text = chatInput.toString().trim();
                        chatInput.setLength(0);
                        closeChat(mc);
                        if (text.length() > 0 && conn != null) {
                            conn.sendChat(text);
                        }
                        break;
                    } else if (key == org.lwjgl.input.Keyboard.KEY_BACK) {
                        if (chatInput.length() > 0) {
                            chatInput.setLength(chatInput.length() - 1);
                        }
                    } else if (ch >= 32 && ch != 127 && chatInput.length() < 100) {
                        chatInput.append(ch);
                    }
                }
            }
        } else {
            boolean t = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_T);
            if (t && !tWasDown && screenField.get(mc) == null) {
                openChat(mc);
            }
            tWasDown = t;
        }
        // test aids (not for players): -Dthewellodev.testchat=text (sends), -Dthewellodev.testchatinput=text
        // (opens the chat line with this text), -Dthewellodev.testdisconnect=seconds
        if (!testDone) {
            long age = System.currentTimeMillis() - loginTime;
            String tc = System.getProperty("thewellodev.testchat");
            if (tc != null && age > 3000) {
                conn.sendChat(tc);
                System.clearProperty("thewellodev.testchat");
            }
            String ti = System.getProperty("thewellodev.testchatinput");
            if (ti != null && age > 4000 && !chatOpen) {
                System.clearProperty("thewellodev.testchatinput");
                openChat(mc);
                chatInput.append(ti);
            }
            String td = System.getProperty("thewellodev.testdisconnect");
            if (td != null && age > Long.parseLong(td) * 1000L) {
                testDone = true;
                disconnect();
            }
        }
    }

    /** Opens the chat line: an empty game screen takes the keyboard and frees the mouse. */
    private static void openChat(Object mc) throws Exception {
        while (org.lwjgl.input.Keyboard.next()) {
            // drop the key events that are already queued (the key that opened the chat)
        }
        chatInput.setLength(0);
        chatScreen = Class.forName("net.minecraft.client.c.i", true, mc.getClass().getClassLoader())
                .getConstructor().newInstance();
        chatOpen = true;
        setScreen.invoke(mc, chatScreen);
    }

    private static void closeChat(Object mc) throws Exception {
        chatOpen = false;
        if (screenField.get(mc) == chatScreen) {
            setScreen.invoke(mc, new Object[]{null});
        }
        chatScreen = null;
    }

    // ----- other players -----------------------------------------------------------------------------------

    /**
     * A player shown in the world: the game's own player class, moved by the server's packets. Its positions are
     * kept with their arrival time and it is shown a short moment in the past, between two received positions,
     * so it moves smoothly even when the packets arrive unevenly.
     */
    private static final class Remote {
        Object entity;
        String name;
        volatile boolean tagVisible;
        volatile double tagX, tagY, tagDist;
        /** {arrival time ms, x, y (eye), z, yaw, pitch} */
        final java.util.ArrayList<double[]> snaps = new java.util.ArrayList<double[]>();
    }

    /** How far in the past the other players are shown (ms). */
    private static final long INTERP_DELAY = 150;

    private static final java.util.Map<Integer, Remote> remotes = new java.util.HashMap<Integer, Remote>();
    private static int myEntityId = -1;
    private static int lastHeldSent = Integer.MIN_VALUE;
    private static Constructor<?> playerCtor;
    private static Field nameField, ent_u;
    private static Field ent_A, ent_B, ent_C, ent_o, ent_p, ent_T, ent_U, ent_af, ent_ag, ent_ah;
    private static Field invField, invItems, invSelected, stackId;
    private static Constructor<?> stackCtor;
    private static Method renderEntity, managerSetup;
    private static Field mcTextures;
    private static Field renderManager, camX, camY, camZ, camYaw;
    private static Method fontWidth, fontPlain;

    private static void spawnRemote(Object world, TWConnection.Pkt p) throws Exception {
        if (p.i1 == myEntityId) {
            return;
        }
        // The game's player class writes itself into the world's player field (e.b) when it is created; the world
        // ticks only that field. Put the own player back, or the world would move this entity with the own
        // player's physics and controls and stop updating the own player.
        Object own = worldPlayer.get(world);
        Object e = playerCtor.newInstance(world);
        worldPlayer.set(world, own);
        setPosRot.invoke(e, p.x, p.y - EYE_HEIGHT, p.z, p.yaw, p.pitch);
        try {
            nameField.set(e, p.s);
        } catch (Exception ignored) {
        }
        setHeld(e, -1); // empty hand until the server says what it holds
        ent_T.setFloat(e, p.yaw);
        ent_U.setFloat(e, p.yaw);
        Remote r = new Remote();
        r.entity = e;
        r.name = p.s;
        r.snaps.add(new double[]{System.currentTimeMillis() - INTERP_DELAY, p.x, p.y, p.z, p.yaw, p.pitch});
        synchronized (remotes) {
            remotes.put(p.i1, r);
        }
        if (DEBUG) {
            System.out.println("[Infdev MP] player shown: " + p.s + " (id " + p.i1 + ") at " + (int) p.x + " "
                    + (int) p.y + " " + (int) p.z);
        }
    }

    private static void moveRemote(TWConnection.Pkt p) {
        movesThisTick++;
        synchronized (remotes) {
            Remote r = remotes.get(p.i1);
            if (r != null) {
                // Positions are sent once per tick (50 ms). Packets that arrive together get their own 50 ms
                // slots, so a bunch of them does not show up as a jump.
                long now = System.currentTimeMillis();
                double last = r.snaps.isEmpty() ? 0 : r.snaps.get(r.snaps.size() - 1)[0];
                double ts = Math.max(now, last + 50);
                if (ts > now + 200) {
                    ts = now;
                }
                r.snaps.add(new double[]{ts, p.x, p.y, p.z, p.yaw, p.pitch});
                while (r.snaps.size() > 40) {
                    r.snaps.remove(0);
                }
            }
        }
    }

    /** Puts the item into the shown player's hand (-1: empty). */
    private static void setHeld(Object entity, int itemId) throws Exception {
        Object inv = invField.get(entity);
        Object[] items = (Object[]) invItems.get(inv);
        java.util.Arrays.fill(items, null);
        invSelected.setInt(inv, 0);
        items[0] = itemId > 0 ? stackCtor.newInstance(itemId) : null;
    }

    private static double wrapDegrees(double d) {
        while (d < -180) {
            d += 360;
        }
        while (d >= 180) {
            d -= 360;
        }
        return d;
    }

    /** Once per game tick: places every shown player at its position of INTERP_DELAY ms ago. */
    private static void tickRemotes() throws Exception {
        long renderTime = System.currentTimeMillis() - INTERP_DELAY;
        synchronized (remotes) {
            if (DEBUG && !remotes.isEmpty() && debugTicks < 80) {
                debugTicks++;
                for (Remote r : remotes.values()) {
                    System.out.println(String.format("[Infdev MP] tick %d moves %d pos %.3f %.3f %.3f",
                            debugTicks, movesThisTick, posX.getDouble(r.entity), posY.getDouble(r.entity),
                            posZ.getDouble(r.entity)));
                }
            }
            movesThisTick = 0;
            for (Remote r : remotes.values()) {
                Object e = r.entity;
                // the shown players are not in the world's entity list, so nothing counts their hurt flash down
                int hurt = hurtTime.getInt(e);
                if (hurt > 0) {
                    hurtTime.setInt(e, hurt - 1);
                }
                // keep one position older than the render time (the start of the current segment)
                while (r.snaps.size() > 2 && r.snaps.get(1)[0] <= renderTime) {
                    r.snaps.remove(0);
                }
                double[] a = r.snaps.get(0);
                double[] b = r.snaps.size() > 1 ? r.snaps.get(1) : a;
                double t = b[0] > a[0] ? (renderTime - a[0]) / (b[0] - a[0]) : 1;
                t = Math.max(0, Math.min(1, t));
                double tx = a[1] + (b[1] - a[1]) * t;
                // the entity's y is its eye height (feet + yOffset); the renderer draws it yOffset lower
                double ty = a[2] + (b[2] - a[2]) * t - EYE_HEIGHT + ent_u.getFloat(e);
                double tz = a[3] + (b[3] - a[3]) * t;
                float tyaw = (float) (a[4] + wrapDegrees(b[4] - a[4]) * t);
                float tpitch = (float) (a[5] + (b[5] - a[5]) * t);

                double x = posX.getDouble(e), y = posY.getDouble(e), z = posZ.getDouble(e);
                // previous values: the renderer draws between the previous and the current tick
                ent_A.setDouble(e, x);
                ent_B.setDouble(e, y);
                ent_C.setDouble(e, z);
                float yaw = rotYaw.getFloat(e);
                ent_o.setFloat(e, yaw);
                ent_p.setFloat(e, rotPitch.getFloat(e));
                ent_U.setFloat(e, ent_T.getFloat(e));
                float limb = ent_ag.getFloat(e);
                ent_af.setFloat(e, limb);
                // current values (yaw kept continuous, so the renderer does not spin through 360 degrees)
                posX.setDouble(e, tx);
                posY.setDouble(e, ty);
                posZ.setDouble(e, tz);
                float newYaw = yaw + (float) wrapDegrees(tyaw - yaw);
                rotYaw.setFloat(e, newYaw);
                rotPitch.setFloat(e, tpitch);
                ent_T.setFloat(e, newYaw);
                // walking animation from the horizontal distance moved
                double dist = Math.sqrt((tx - x) * (tx - x) + (tz - z) * (tz - z));
                float target = (float) Math.min(1.0, dist * 4.0);
                Object w = mcRef == null ? null : mcWorld.get(mcRef);
                if (w != null && ((Integer) blockAt.invoke(w, (int) Math.floor(tx), (int) Math.floor(ty - ent_u.getFloat(e) - 0.2),
                        (int) Math.floor(tz))) == 0) {
                    target = 0; // in the air (jumping, falling, flying): no walking
                }
                limb += (target - limb) * 0.4f;
                ent_ag.setFloat(e, limb);
                ent_ah.setFloat(e, ent_ah.getFloat(e) + limb);
            }
        }
    }

    /** Sends the own held item when it changes (the others see it in this player's hand). */
    private static void sendHeldItem(Object world, TWConnection c) throws Exception {
        Object player = worldPlayer.get(world);
        if (player == null || !invField.getDeclaringClass().isInstance(player)) {
            return;
        }
        Object inv = invField.get(player);
        Object[] items = (Object[]) invItems.get(inv);
        int sel = invSelected.getInt(inv);
        Object stack = sel >= 0 && sel < items.length ? items[sel] : null;
        int id = stack == null ? -1 : stackId.getInt(stack);
        if (id != lastHeldSent) {
            lastHeldSent = id;
            c.sendShort(Protocol.HELD_ITEM, id);
        }
    }

    /** End of the renderer's entity pass (net/minecraft/client/a/b.a(camera, frustum, partialTicks)). */
    public static void renderPlayers(float partial) {
        if (!connected() || renderEntity == null) {
            return;
        }
        try {
            Object rm = renderManager.get(null);
            if (DEBUG && !remotes.isEmpty() && !playerRenderSeen) {
                playerRenderSeen = true;
                System.out.println("[Infdev MP] drawing " + remotes.size() + " player(s)");
                Class<?> rmClass = rm.getClass();
                System.out.println("[Infdev MP] render manager textures " + access(rmClass, "b").get(rm) + " world "
                        + access(rmClass, "c").get(rm));
            }
            // this build draws entity shadows with the render manager's texture manager and world, and the manager's
            // texture field is empty by the end of the entity pass: set the manager up again for this frame
            Object world = mcWorld.get(mcRef);
            Object own = world == null ? null : worldPlayer.get(world);
            if (own == null) {
                return;
            }
            managerSetup.invoke(rm, world, mcTextures.get(mcRef), own, partial);
            if (DEBUG && renderDebugs++ < 3) {
                java.nio.FloatBuffer mv = org.lwjgl.BufferUtils.createFloatBuffer(16);
                org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, mv);
                StringBuilder sb = new StringBuilder("[Infdev MP] modelview at the player pass:");
                for (int i = 0; i < 16; i++) {
                    sb.append(String.format(" %.2f", mv.get(i)));
                }
                System.out.println(sb);
                System.out.println("[Infdev MP] camera of the manager " + camX.getDouble(rm) + " " + camY.getDouble(rm) + " "
                        + camZ.getDouble(rm) + " own " + posX.getDouble(own) + " " + posY.getDouble(own) + " "
                        + posZ.getDouble(own));
            }
            synchronized (remotes) {
                // This build draws the world relative to the camera (the matrix has the camera's rotation only), and its
                // entity renderer takes world coordinates: move the camera's position into the matrix for the players.
                org.lwjgl.opengl.GL11.glPushMatrix();
                org.lwjgl.opengl.GL11.glTranslated(-camX.getDouble(rm), -camY.getDouble(rm), -camZ.getDouble(rm));
                try {
                    for (Remote r : remotes.values()) {
                        renderEntity.invoke(rm, r.entity, partial);
                    }
                } finally {
                    org.lwjgl.opengl.GL11.glPopMatrix();
                }
                for (Remote r : remotes.values()) {
                    projectNameTag(rm, r, partial);
                }
            }
        } catch (Throwable t) {
            if (!playerRenderErrorShown) {
                playerRenderErrorShown = true;
                System.err.println("[Infdev MP] player display: " + t);
                t.printStackTrace();
            }
        }
    }

    // Created on first use: this class is also loaded by the server (the terrain hook), which has no LWJGL.
    private static java.nio.FloatBuffer MODELVIEW;
    private static java.nio.FloatBuffer PROJECTION;
    private static java.nio.IntBuffer VIEWPORT;
    private static boolean matricesCaptured;

    /** Start of the renderer's entity pass: keeps this frame's camera matrices for the name tags. */
    public static void captureMatrices() {
        if (!connected()) {
            return;
        }
        if (MODELVIEW == null) {
            MODELVIEW = org.lwjgl.BufferUtils.createFloatBuffer(16);
            PROJECTION = org.lwjgl.BufferUtils.createFloatBuffer(16);
            VIEWPORT = org.lwjgl.BufferUtils.createIntBuffer(16);
        }
        MODELVIEW.clear();
        PROJECTION.clear();
        VIEWPORT.clear();
        org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX, PROJECTION);
        org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_VIEWPORT, VIEWPORT);
        matricesCaptured = true;
    }

    /**
     * Works out where the point above the player's head is on the screen (with the 3D matrices of this frame).
     * The name itself is drawn by drawHud, with the game's own text drawing.
     */
    private static void projectNameTag(Object rm, Remote r, float partial) throws Exception {
        Object e = r.entity;
        double x = ent_A.getDouble(e) + (posX.getDouble(e) - ent_A.getDouble(e)) * partial - camX.getDouble(rm);
        double y = ent_B.getDouble(e) + (posY.getDouble(e) - ent_B.getDouble(e)) * partial - camY.getDouble(rm) + 2.15 - ent_u.getFloat(e);
        double z = ent_C.getDouble(e) + (posZ.getDouble(e) - ent_C.getDouble(e)) * partial - camZ.getDouble(rm);
        r.tagVisible = false;
        r.tagDist = Math.sqrt(x * x + y * y + z * z);
        if (r.name == null || x * x + y * y + z * z > 48 * 48) {
            return;
        }
        if (!matricesCaptured) {
            return;
        }
        // matrices captured at the start of the entity pass; they work in world coordinates, so the absolute
        // position is used (not the render manager's camera, which is updated later in the pass)
        double ax = ent_A.getDouble(e) + (posX.getDouble(e) - ent_A.getDouble(e)) * partial;
        double ay = ent_B.getDouble(e) + (posY.getDouble(e) - ent_B.getDouble(e)) * partial + 2.15 - ent_u.getFloat(e);
        double az = ent_C.getDouble(e) + (posZ.getDouble(e) - ent_C.getDouble(e)) * partial;
        double[] v = {x, y, z, 1}; // from the camera (the matrix is the camera's rotation only in this build)
        double[] eye = new double[4];
        double[] clip = new double[4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                eye[i] += MODELVIEW.get(j * 4 + i) * v[j];
            }
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                clip[i] += PROJECTION.get(j * 4 + i) * eye[j];
            }
        }
        if (DEBUG && tagPrints < 8) {
            tagPrints++;
            System.out.println(String.format("[Infdev MP] tag %s rel %.2f %.2f %.2f eye %.2f %.2f %.2f clip %.2f %.2f %.2f %.2f vp %d %d %d %d",
                    r.name, x, y, z, eye[0], eye[1], eye[2], clip[0], clip[1], clip[2], clip[3], VIEWPORT.get(0),
                    VIEWPORT.get(1), VIEWPORT.get(2), VIEWPORT.get(3)));
        }
        if (clip[3] <= 0.05) {
            return; // behind the camera
        }
        double nx = clip[0] / clip[3], ny = clip[1] / clip[3];
        r.tagX = VIEWPORT.get(0) + (nx + 1) / 2 * VIEWPORT.get(2);
        r.tagY = VIEWPORT.get(1) + (ny + 1) / 2 * VIEWPORT.get(3);
        r.tagVisible = true;
    }

    /** Draws the names of the shown players (called from drawHud, in screen space). */
    private static void drawNameTags(Object font, int screenW, int screenH, int scaledW, int scaledH) throws Exception {
        synchronized (remotes) {
            for (Remote r : remotes.values()) {
                if (!r.tagVisible) {
                    continue;
                }
                int gx = (int) (r.tagX * scaledW / screenW);
                int gy = (int) ((screenH - r.tagY) * scaledH / screenH);
                int w = (Integer) fontWidth.invoke(font, r.name);
                if (DEBUG && hudTagPrints >= 0) {
                    System.out.println("[Infdev MP] hud tag " + r.name + " at " + gx + "," + gy + " (screen "
                            + (int) r.tagX + "," + (int) r.tagY + ")");
                }
                // the name shrinks with the distance, as the game's own name tags do
                float scale = (float) Math.max(0.45, Math.min(1.2, 5.0 / Math.max(1.0, r.tagDist)));
                org.lwjgl.opengl.GL11.glPushMatrix();
                org.lwjgl.opengl.GL11.glTranslatef(gx, gy, 0f);
                org.lwjgl.opengl.GL11.glScalef(scale, scale, 1f);
                fontDraw.invoke(font, r.name, -w / 2, -8, 0xFFFFFF);
                org.lwjgl.opengl.GL11.glPopMatrix();
            }
        }
    }

    private static boolean playerRenderErrorShown;
    private static boolean playerRenderSeen;
    private static int tagPrints;
    private static int hudTagPrints = -1;
    private static int hudFrames;

    // ----- fighting, health, game mode ----------------------------------------------------------------------

    private static Field healthField, hurtTime, hurtTimeMax, motionY, itemTable;
    private static Method hurtMethod, respawnMethod, itemDamage;
    private static int gameMode;
    private static int lastHealthSent = -1;
    private static boolean deadReported;
    private static boolean mouseWasDown;
    private static long lastAttackMs;
    private static int mouseFlush;
    private static int renderDebugs;
    private static int posTicks;
    private static int probeTicks;
    private static Field pausedField, fallField, velX, velZ, onGround;
    private static Method blockAt;
    private static Class<?> playerClassForFire;
    private static boolean flying, spaceWasDown;
    private static long lastSpaceMs;

    private static int health(Object player) throws Exception {
        return healthField.getInt(player);
    }

    private static int heldItemId(Object player) throws Exception {
        Object inv = invField.get(player);
        Object[] items = (Object[]) invItems.get(inv);
        int sel = invSelected.getInt(inv);
        Object stack = sel >= 0 && sel < items.length ? items[sel] : null;
        return stack == null ? -1 : stackId.getInt(stack);
    }

    /** What the held item does to an entity (1 with an empty hand), as the game's own attack works it out. */
    private static int attackDamage(Object player) throws Exception {
        int id = heldItemId(player);
        Object[] table = (Object[]) itemTable.get(null);
        if (id <= 0 || id >= table.length || table[id] == null) {
            return 1;
        }
        return Math.max(1, (Integer) itemDamage.invoke(table[id]));
    }

    /** Once per tick: reports health and death, creative flying, and hitting other players. */
    private static void combatTick(Object mc, TWConnection c, Object player) throws Exception {
        int hp = health(player);
        if (DEBUG && ++posTicks % 10 == 0 && posTicks < 400) {
            System.out.println(String.format("[Infdev MP] pos %.2f %.2f %.2f hp %d", posX.getDouble(player),
                    posY.getDouble(player), posZ.getDouble(player), hp));
        }
        if (gameMode == 1 && hp > 0 && hp < 20) {
            healthField.setInt(player, 20); // creative: nothing hurts
            hp = 20;
        }
        if (lastHealthSent < 0 || hp < lastHealthSent) {
            c.sendShort(Protocol.HEALTH, Math.max(0, hp));
        }
        lastHealthSent = hp;
        digTick(mc, c, hp);
        if (hp <= 0) {
            if (!deadReported) {
                deadReported = true;
                c.sendSimple(Protocol.DEATH);
            }
            return;
        }
        boolean free = screenField.get(mc) == null && !chatOpen;
        if (gameMode == 1) {
            fallField.setFloat(player, 0f); // creative: no fall damage
        }
        if (gameMode == 1 && free) {
            // double tap on space switches flying on and off; space goes up, shift goes down
            boolean space = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_SPACE);
            if (space && !spaceWasDown) {
                long now = System.currentTimeMillis();
                if (now - lastSpaceMs < 350) {
                    flying = !flying;
                    lastSpaceMs = 0;
                } else {
                    lastSpaceMs = now;
                }
            }
            spaceWasDown = space;
            if (flying) {
                double vy = 0;
                if (space) {
                    vy = 0.45;
                } else if (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)) {
                    vy = -0.45;
                }
                if (vy < 0 && onGround.getBoolean(player)) {
                    flying = false; // landed
                } else {
                    motionY.setDouble(player, vy);
                    int fwd = (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_W) ? 1 : 0)
                            - (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_S) ? 1 : 0);
                    int side = (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_A) ? 1 : 0)
                            - (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_D) ? 1 : 0);
                    if (fwd != 0 || side != 0) {
                        double len = Math.sqrt(fwd * fwd + side * side);
                        double yaw = Math.toRadians(rotYaw.getFloat(player));
                        double speed = 0.55;
                        velX.setDouble(player, (side * Math.cos(yaw) - fwd * Math.sin(yaw)) / len * speed);
                        velZ.setDouble(player, (fwd * Math.cos(yaw) + side * Math.sin(yaw)) / len * speed);
                    } else {
                        velX.setDouble(player, velX.getDouble(player) * 0.4);
                        velZ.setDouble(player, velZ.getDouble(player) * 0.4);
                    }
                }
            }
        }
        boolean down = free && org.lwjgl.input.Mouse.isButtonDown(0);
        if (DEBUG && org.lwjgl.input.Mouse.isButtonDown(0) && !mouseWasDown) {
            System.out.println("[Infdev MP] click: free " + free + " grabbed " + org.lwjgl.input.Mouse.isGrabbed());
        }
        if (down && !mouseWasDown) {
            attackAim(c, player);
        }
        mouseWasDown = down;
    }

    /** A click: hits the other player the crosshair points at (reach 3.5 blocks). */
    private static void attackAim(TWConnection c, Object player) throws Exception {
        long now = System.currentTimeMillis();
        if (now - lastAttackMs < 500) {
            return;
        }
        double yaw = Math.toRadians(rotYaw.getFloat(player)), pitch = Math.toRadians(rotPitch.getFloat(player));
        double dx = -Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
        double ox = posX.getDouble(player), oy = posY.getDouble(player), oz = posZ.getDouble(player);
        int bestId = Integer.MIN_VALUE;
        double best = 3.5;
        synchronized (remotes) {
            for (java.util.Map.Entry<Integer, Remote> en : remotes.entrySet()) {
                Object e = en.getValue().entity;
                double feet = posY.getDouble(e) - ent_u.getFloat(e);
                double[] lo = {posX.getDouble(e) - 0.4, feet - 0.1, posZ.getDouble(e) - 0.4};
                double[] hi = {posX.getDouble(e) + 0.4, feet + 1.9, posZ.getDouble(e) + 0.4};
                double[] o = {ox, oy, oz}, d = {dx, dy, dz};
                double tmin = 0, tmax = best;
                boolean hit = true;
                for (int i = 0; i < 3 && hit; i++) {
                    if (Math.abs(d[i]) < 1e-9) {
                        hit = o[i] >= lo[i] && o[i] <= hi[i];
                    } else {
                        double t1 = (lo[i] - o[i]) / d[i], t2 = (hi[i] - o[i]) / d[i];
                        tmin = Math.max(tmin, Math.min(t1, t2));
                        tmax = Math.min(tmax, Math.max(t1, t2));
                        hit = tmin <= tmax;
                    }
                }
                if (hit && tmin < best) {
                    best = tmin;
                    bestId = en.getKey();
                }
            }
        }
        if (DEBUG) {
            System.out.println("[Infdev MP] attack aim: remotes " + remotes.size() + " target " + bestId);
        }
        if (bestId != Integer.MIN_VALUE) {
            lastAttackMs = now;
            c.sendAttack(bestId, attackDamage(player));
        }
    }

    /** The server says this player was hurt by another one. */
    private static void onDamage(Object world, TWConnection.Pkt p) throws Exception {
        Object player = worldPlayer.get(world);
        if (DEBUG) {
            System.out.println("[Infdev MP] damage " + p.i2 + " from " + p.i1);
        }
        if (player == null || gameMode == 1 || health(player) <= 0) {
            return;
        }
        Object attacker = null;
        synchronized (remotes) {
            Remote r = remotes.get(p.i1);
            if (r != null) {
                attacker = r.entity;
            }
        }
        hurtMethod.invoke(player, attacker, p.i2);
        // two players on the very same spot make the knockback a division by zero: no NaN in the motion
        for (Field mf : new Field[]{velX, motionY, velZ}) {
            if (Double.isNaN(mf.getDouble(player)) || Double.isInfinite(mf.getDouble(player))) {
                mf.setDouble(player, 0);
            }
        }
    }

    private static void onHealth(Object world, int hp) throws Exception {
        Object player = worldPlayer.get(world);
        if (player == null) {
            return;
        }
        hp = Math.max(0, Math.min(20, hp));
        if (hp == 0) {
            if (health(player) > 0) {
                hurtMethod.invoke(player, null, 1000);
                if (health(player) > 0) {
                    healthField.setInt(player, 0);
                }
            }
        } else {
            healthField.setInt(player, hp);
        }
        lastHealthSent = health(player);
    }

    private static void onHurtAnimation(int id) throws Exception {
        synchronized (remotes) {
            Remote r = remotes.get(id);
            if (r != null) {
                hurtTime.setInt(r.entity, 10);
                hurtTimeMax.setInt(r.entity, 10);
            }
        }
    }

    private static void onGameMode(int mode) {
        gameMode = mode;
        flying = false;
        addChat(mode == 1 ? "&eGame mode: &fCreative (fly with space / shift)" : "&eGame mode: &fSurvival");
    }

    /** End of the death screen's init: the first button respawns, loading does not exist online. */
    public static void deathInit(Object screen) {
        if (!connected()) {
            return;
        }
        try {
            Class<?> base = Class.forName("net.minecraft.client.c.i", true, screen.getClass().getClassLoader());
            Field list = base.getDeclaredField("e");
            list.setAccessible(true);
            List<?> buttons = (List<?>) list.get(screen);
            Object first = buttons.get(0);
            first.getClass().getField("a").set(first, "Respawn");
            Object second = buttons.get(1);
            second.getClass().getField("c").setBoolean(second, false);
        } catch (Throwable t) {
            System.err.println("[Infdev MP] death screen: " + t);
        }
    }

    /** Start of the death screen's button handler: true if the click was handled here. */
    public static boolean deathAction(Object screen, Object button) {
        if (!connected()) {
            return false;
        }
        try {
            int id = button.getClass().getField("b").getInt(button);
            if (id == 1) {
                respawn();
                return true;
            }
            return id == 2;
        } catch (Throwable t) {
            System.err.println("[Infdev MP] respawn: " + t);
            return false;
        }
    }

    // ----- block-breaking progress (the cracks of the other players) -----------------------------------------

    /** What other players dig at: entity id -> {x, y, z, stage 0-9, time of the last message}. */
    private static final java.util.Map<Integer, long[]> remoteDigs = new java.util.HashMap<Integer, long[]>();
    private static int lastDigX, lastDigY, lastDigZ, lastDigStage = -1;
    private static Class<?> digControllerC;
    private static Field digProgress, digX, digY, digZ;
    private static Method crackMethod;
    private static Constructor<?> hitCtor, vecCtor;
    private static Field crackLevel;
    private static boolean inCrack, digFailed;

    /** Once per tick: tells the server which block the own player digs at and how far it is (so that the others see the cracks). */
    private static void digTick(Object mc, TWConnection c, int hp) {
        try {
            int stage = -1;
            int dx = lastDigX, dy = lastDigY, dz = lastDigZ;
            Object ctl = mc.getClass().getField("a").get(mc);
            if (ctl != null && hp > 0) {
                if (digControllerC == null) {
                    digControllerC = Class.forName("net.minecraft.client.d.b", true, mc.getClass().getClassLoader());
                    digProgress = digControllerC.getDeclaredField("f");
                    digX = digControllerC.getDeclaredField("c");
                    digY = digControllerC.getDeclaredField("d");
                    digZ = digControllerC.getDeclaredField("e");
                    digProgress.setAccessible(true);
                    digX.setAccessible(true);
                    digY.setAccessible(true);
                    digZ.setAccessible(true);
                }
                if (digControllerC.isInstance(ctl)) {
                    float f = digProgress.getFloat(ctl);
                    if (f > 0.0F) {
                        stage = Math.min(9, (int) (f * 10.0F));
                        dx = digX.getInt(ctl);
                        dy = digY.getInt(ctl);
                        dz = digZ.getInt(ctl);
                    }
                }
            }
            if (stage != lastDigStage || (stage >= 0 && (dx != lastDigX || dy != lastDigY || dz != lastDigZ))) {
                if (stage < 0) {
                    c.sendDig(lastDigX, lastDigY, lastDigZ, -1);
                } else {
                    c.sendDig(dx, dy, dz, stage);
                }
                lastDigStage = stage;
                lastDigX = dx;
                lastDigY = dy;
                lastDigZ = dz;
            }
        } catch (Throwable t) {
            if (!digFailed) {
                digFailed = true;
                System.err.println("[Infdev MP] block-breaking progress: " + t);
            }
        }
    }

    /**
     * Hook at the start of the renderer's method that draws the crack of the block the own player digs at: draws the cracks of
     * the blocks that other players dig at, by calling that method for their blocks (with their progress in the renderer's field).
     */
    public static void renderDigs(Object renderer, Object viewer, float partial) {
        if (inCrack || remoteDigs.isEmpty() || renderer == null || mcRef == null) {
            return;
        }
        inCrack = true;
        float saved = 0;
        boolean haveSaved = false;
        try {
            Object world = mcWorld.get(mcRef);
            if (world == null) {
                return;
            }
            if (crackMethod == null) {
                for (Method m : renderer.getClass().getMethods()) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (m.getName().equals("a") && m.getReturnType() == void.class && pt.length > 0
                            && ((pt.length == 5 && pt[1].getName().equals("net.minecraft.a.d.c") && pt[3].getName().equals("net.minecraft.a.b.k"))
                            || (pt.length == 3 && pt[0].getName().equals("net.minecraft.a.d.c")
                            && pt[2].getName().equals("net.minecraft.a.b.k")))) {
                        crackMethod = m;
                    }
                }
                Class<?> hitC = Class.forName("net.minecraft.a.d.c", true, renderer.getClass().getClassLoader());
                hitCtor = hitC.getConstructor(int.class, int.class, int.class, int.class, Class.forName("net.minecraft.a.d.a", true,
                        renderer.getClass().getClassLoader()));
                vecCtor = hitCtor.getParameterTypes()[4].getConstructor(double.class, double.class, double.class);
                crackLevel = renderer.getClass().getField("a");
                if (crackMethod == null) {
                    throw new NoSuchMethodException("the crack method of the renderer");
                }
            }
            saved = crackLevel.getFloat(renderer);
            haveSaved = true;
            long now = System.currentTimeMillis();
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
            synchronized (remoteDigs) {
                java.util.Iterator<long[]> it = remoteDigs.values().iterator();
                while (it.hasNext()) {
                    long[] d = it.next();
                    if (now - d[4] > 3000) {
                        it.remove();
                        continue;
                    }
                    int x = (int) d[0], y = (int) d[1], z = (int) d[2];
                    if ((Integer) getBlock.invoke(world, x, y, z) == 0) {
                        continue;
                    }
                    crackLevel.setFloat(renderer, (d[3] + 1) / 10.0F - 0.01F);
                    Object hit = hitCtor.newInstance(x, y, z, 0, vecCtor.newInstance(x + 0.5, y + 0.5, z + 0.5));
                    if (crackMethod.getParameterTypes().length == 5) {
                        crackMethod.invoke(renderer, viewer, hit, 0, null, partial);
                    } else {
                        crackMethod.invoke(renderer, hit, 0, null);
                    }
                }
            }
        } catch (Throwable t) {
            synchronized (remoteDigs) {
                remoteDigs.clear();
            }
            if (!digFailed) {
                digFailed = true;
                System.err.println("[Infdev MP] could not draw the cracks of other players: " + t);
                t.printStackTrace();
            }
        } finally {
            if (haveSaved) {
                try {
                    crackLevel.setFloat(renderer, saved);
                } catch (Throwable ignored) {
                    // the renderer is gone
                }
            }
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
            inCrack = false;
        }
    }

    /**
     * The player that died is flagged as dead after a second and the world may take it out of its entity list: it would never be
     * ticked again (no gravity, no movement, a camera that does not follow). A respawn clears the flag and puts the player back.
     */
    private static void revive(Object world, Object player) {
        try {
            Class<?> base = player.getClass();
            while (base.getSuperclass() != null && base.getSuperclass() != Object.class) {
                base = base.getSuperclass();
            }
            base.getField("t").setBoolean(player, false); // the dead flag of the entity class
            boolean listed = false;
            for (Field f : world.getClass().getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    List<?> l = (List<?>) f.get(world);
                    if (l != null && l.contains(player)) {
                        listed = true;
                        break;
                    }
                }
            }
            if (!listed) {
                for (Method me : world.getClass().getMethods()) {
                    if (me.getName().equals("a") && me.getParameterTypes().length == 1 && me.getReturnType() == void.class
                            && me.getParameterTypes()[0].isAssignableFrom(player.getClass())
                            && me.getParameterTypes()[0] == base) {
                        me.invoke(world, player);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[Infdev MP] revive: " + t);
        }
    }

    private static void respawn() throws Exception {
        Object mc = mcRef;
        TWConnection c = conn;
        Object world = mcWorld.get(mc);
        Object player = world == null ? null : worldPlayer.get(world);
        if (c == null || player == null) {
            return;
        }
        flying = false;
        mouseFlush = 10;
        playerClassForFire.getField("I").setInt(player, 0); // not burning any more
        respawnMethod.invoke(player); // the game's own reset: full health, normal size, free of blocks
        revive(world, player);
        deadReported = false;
        lastHealthSent = 20;
        c.sendSimple(Protocol.RESPAWN);
        setScreen.invoke(mc, new Object[]{null});
    }

    // ----- inventory that survives a change of build ----------------------------------------------------------

    private static boolean invSynced;
    private static String invSignature = "";
    private static int invTicks;
    private static Constructor<?> stackCtor2;
    private static Field stackCount, stackDamage;

    /** The server sends what it saved for this player right after the login (an empty list: nothing saved). */
    private static void onInventorySync(Object world, int[] triples) throws Exception {
        Object player = worldPlayer.get(world);
        if (player != null && triples != null && triples.length >= 3) {
            Object[] items = (Object[]) invItems.get(invField.get(player));
            for (int i = 0; i < items.length && i * 3 + 2 < triples.length; i++) {
                int id = triples[i * 3], count = triples[i * 3 + 1];
                if (id < 0 || count <= 0) {
                    items[i] = null;
                } else {
                    Object stack = stackCtor2.newInstance(id, count);
                    stackDamage.setInt(stack, triples[i * 3 + 2]);
                    items[i] = stack;
                }
            }
            invSignature = inventorySignature(player);
            if (DEBUG) {
                System.out.println("[Infdev MP] inventory restored from the server: " + invSignature);
            }
        } else if (DEBUG) {
            System.out.println("[Infdev MP] the server has no saved inventory: the game's own is kept");
        }
        invSynced = true;
    }

    private static int[] inventoryTriples(Object player) throws Exception {
        Object[] items = (Object[]) invItems.get(invField.get(player));
        int[] t = new int[items.length * 3];
        for (int i = 0; i < items.length; i++) {
            Object s = items[i];
            boolean empty = s == null || stackCount.getInt(s) <= 0;
            t[i * 3] = empty ? -1 : stackId.getInt(s);
            t[i * 3 + 1] = empty ? 0 : stackCount.getInt(s);
            t[i * 3 + 2] = empty ? 0 : stackDamage.getInt(s);
        }
        return t;
    }

    private static String inventorySignature(Object player) throws Exception {
        return java.util.Arrays.toString(inventoryTriples(player));
    }

    /** About once a second: tells the server when the inventory changed (it saves it for the next login). */
    private static void reportInventory(TWConnection c, Object player) throws Exception {
        if (!invSynced || ++invTicks % 20 != 0) {
            return;
        }
        String sig = inventorySignature(player);
        if (!sig.equals(invSignature)) {
            invSignature = sig;
            c.sendInventoryReport(inventoryTriples(player));
        }
    }

    // ----- tick -------------------------------------------------------------------------------------------

    // ----- freeze watch ------------------------------------------------------------------------------------

    private static volatile long lastTickMs;
    private static Thread gameThread;
    private static boolean watchStarted;

    /**
     * If the game stops ticking for more than 8 seconds while connected (a freeze), this thread writes what the game
     * thread is doing to ~/.thewellodev/freeze-*.txt (once for each freeze), so the cause can be found.
     */
    private static void startFreezeWatch() {
        if (watchStarted) {
            return;
        }
        watchStarted = true;
        gameThread = Thread.currentThread();
        Thread t = new Thread("Infdev MP freeze watch") {
            public void run() {
                long reported = 0;
                while (true) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    long last = lastTickMs;
                    if (conn != null && last != 0 && System.currentTimeMillis() - last > 8000 && reported != last) {
                        reported = last;
                        StringBuilder sb = new StringBuilder("The game has not ticked for "
                                + (System.currentTimeMillis() - last) / 1000 + " s. The game thread is here:\n");
                        for (StackTraceElement e : gameThread.getStackTrace()) {
                            sb.append("    at ").append(e).append('\n');
                        }
                        try {
                            File dir = new File(System.getProperty("user.home"), ".thewellodev");
                            dir.mkdirs();
                            File f = new File(dir, "freeze-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                                    .format(new java.util.Date()) + ".txt");
                            java.io.FileWriter w = new java.io.FileWriter(f);
                            w.write(sb.toString());
                            w.close();
                            System.err.println("[Infdev MP] the game froze; details in " + f.getPath());
                        } catch (Exception ex) {
                            // nothing more to do
                        }
                    }
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    private static boolean releasedForFocus, lastActive = true;

    /**
     * Two game windows on one computer both read the same mouse: the one without focus must not turn its camera. While
     * the window is not active the mouse is released (and its movement thrown away); it is grabbed again on return.
     */
    private static int trimTicks;

    /**
     * The game asks the provider for every chunk it looks at; the ones that did not arrive from the server get an empty
     * stand-in (about 80 KB). Stand-ins far from the player are dropped, so that a long walk does not fill the memory.
     */
    private static void trimStandIns(Object player) throws Exception {
        if (++trimTicks % 100 != 0) {
            return;
        }
        int pcx = (int) Math.floor(posX.getDouble(player)) >> 4, pcz = (int) Math.floor(posZ.getDouble(player)) >> 4;
        for (java.util.Iterator<java.util.Map.Entry<Long, Object>> it = chunkMap.entrySet().iterator(); it.hasNext();) {
            java.util.Map.Entry<Long, Object> en = it.next();
            if (received.contains(en.getKey())) {
                continue;
            }
            int cx = ((int) (en.getKey() >> 32) << 4) >> 4, cz = ((int) (long) en.getKey() << 4) >> 4;
            if (Math.abs(cx - pcx) > 14 || Math.abs(cz - pcz) > 14) {
                it.remove();
            }
        }
    }

    private static void focusGuard() {
        try {
            TWMouse.guard(screenField.get(mcRef) == null && !chatOpen);
        } catch (Exception ignored) {
        }
    }

    private static void doTick(Object mc) throws Exception {
        if (!ready) {
            init(mc);
            ready = true;
            startFreezeWatch();
        }
        lastTickMs = System.currentTimeMillis();
        mcRef = mc;
        String auto = System.getProperty("thewellodev.autoconnect");
        if (auto != null && conn == null) {
            System.clearProperty("thewellodev.autoconnect");
            int colon = auto.lastIndexOf(':');
            String host = colon > 0 ? auto.substring(0, colon) : auto;
            int port = colon > 0 ? Integer.parseInt(auto.substring(colon + 1)) : Protocol.DEFAULT_PORT;
            // The account name and session come from the game's own settings object (set by the launcher)
            Object settings = mc.getClass().getField("h").get(mc);
            String user = System.getProperty("thewellodev.user");
            if (user == null) {
                user = String.valueOf(settings.getClass().getField("b").get(settings));
            }
            if (System.getProperty("thewellodev.token") == null) {
                Object session = settings.getClass().getField("c").get(settings);
                if (session != null) {
                    System.setProperty("thewellodev.token", String.valueOf(session));
                }
            }
            System.out.println("[Infdev MP] connecting to " + host + ":" + port + " as " + user);
            conn = new TWConnection(host, port, user, GameVersion.INF_20100313.protocol);
            blankTerrain = true; // from now on the terrain comes from the server only
            conn.start();
            return;
        }
        TWConnection c = conn;
        if (c == null) {
            return;
        }
        if (c.isClosed()) {
            leave(c.getCloseReason(), mc);
            return;
        }
        Object world = mcWorld.get(mc);
        TWConnection.Pkt first;
        // The game starts at its title screen and has no world yet. Until the login packet arrives, only the
        // packets of the login itself (account check, keep-alive, kick) are handled. When the server accepts the
        // login, an empty world is created the way the game does and the menu is closed.
        while (world == null && conn == c && (first = c.poll()) != null) {
            if (first.id != Protocol.LOGIN) {
                handleBeforeLogin(c, first);
                continue;
            }
            // The game's own set-world method only keeps the world after an old online check (it needs the
            // site on the applet's document base), so the field is set here first.
            Object fresh = worldClass.getConstructor().newInstance();
            installProvider(fresh);
            mcWorld.set(mc, fresh);
            setWorld.invoke(mc, fresh);
            setScreen.invoke(mc, new Object[]{null});
            world = mcWorld.get(mc);
            if (world == null) {
                return;
            }
            handle(c, world, first);
        }
        if (world == null || conn != c) {
            return;
        }
        int processed = 0;
        TWConnection.Pkt p;
        while (conn == c && (p = c.poll()) != null) {
            try {
                handle(c, world, p);
            } catch (Throwable t) {
                System.err.println("[Infdev MP] packet " + p.id + " could not be handled: " + t);
            }
            if (++processed > 40 && p.id == Protocol.CHUNK) {
                break; // the rest on the next tick
            }
        }
        if (conn != c || !loggedIn) {
            return;
        }
        chatTick(mc);
        if (conn != c) {
            return;
        }
        pausedField.setBoolean(mc, false); // the pause menu does not stop a multiplayer game
        tickRemotes();
        sendHeldItem(world, c);
        Object player = worldPlayer.get(world);
        if (player == null) {
            return;
        }
        focusGuard();
        trimStandIns(player);
        if (mouseFlush > 0) {
            // the game grabs the mouse when the world opens: the jump of the cursor to the window centre must not
            // turn the camera
            mouseFlush--;
            org.lwjgl.input.Mouse.getDX();
            org.lwjgl.input.Mouse.getDY();
        }
        if (DEBUG && !hold && ++probeTicks == 120) {
            int[][] pts = {{-20, 5}, {20, 5}, {5, -20}, {5, 20}, {-20, -20}, {20, 20}, {-20, 20}, {20, -20}};
            for (int[] q : pts) {
                int top = -1;
                for (int y = 127; y >= 0 && top < 0; y--) {
                    if ((Integer) blockAt.invoke(world, q[0], y, q[1]) != 0) {
                        top = y;
                    }
                }
                System.out.println("[Infdev MP] probe " + q[0] + "," + q[1] + " top " + top);
            }
        }
        if (!hold) {
            combatTick(mc, c, player);
            reportInventory(c, player);
        }
        if (hold) {
            // keep the player at the spawn point until the chunk below it has arrived
            boolean around = true; // the player stands on the corner of up to four chunks: wait for all nine
            for (int dx = -1; dx <= 1 && around; dx++) {
                for (int dz = -1; dz <= 1 && around; dz++) {
                    around = hasChunk(world, spawnCx + dx, spawnCz + dz);
                }
            }
            if (around) {
                hold = false;
                fallField.setFloat(player, 0f);
                mouseFlush = 30;
            } else {
                setPosRot.invoke(player, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
                // the world below is not there yet: no falling (the fall distance would kill the player on landing)
                fallField.setFloat(player, 0f);
                motionY.setDouble(player, 0);
                return;
            }
        }
        if (System.getProperty("thewellodev.testhover") != null && System.getProperty("thewellodev.testmove") == null) {
            // test aid: keeps the own player where it is, looking straight ahead (for screenshots)
            if (Double.isNaN(hoverY)) {
                hoverX = posX.getDouble(player);
                hoverY = posY.getDouble(player) - ent_u.getFloat(player);
                hoverZ = posZ.getDouble(player);
                hoverYaw = rotYaw.getFloat(player);
            }
            setPosRot.invoke(player, hoverX, hoverY, hoverZ, hoverYaw, 0f);
        }
        if (System.getProperty("thewellodev.testwalk") != null) {
            // test aid: walks 0.2 blocks per tick along +x at a fixed height (another client watches it)
            if (Double.isNaN(walkY)) {
                walkY = posY.getDouble(player) - ent_u.getFloat(player);
            }
            setPosRot.invoke(player, posX.getDouble(player) + 0.2, walkY, posZ.getDouble(player),
                    rotYaw.getFloat(player), rotPitch.getFloat(player));
        }
        String testYaw = System.getProperty("thewellodev.testyaw");
        if (testYaw != null) {
            // test aid (yaw,pitch): looks in a direction once, for screenshots
            System.clearProperty("thewellodev.testyaw");
            String[] v = testYaw.split(",");
            setPosRot.invoke(player, posX.getDouble(player), posY.getDouble(player) - ent_u.getFloat(player),
                    posZ.getDouble(player), Float.parseFloat(v[0]), Float.parseFloat(v[1]));
        }
        String testMove = System.getProperty("thewellodev.testmove");
        if (testMove != null) {
            // test aid (dx,dz): moves the own player once, so that another client can see it
            System.clearProperty("thewellodev.testmove");
            String[] v = testMove.split(",");
            double dy = v.length > 2 ? Double.parseDouble(v[2].trim()) : 0;
            setPosRot.invoke(player, posX.getDouble(player) + Double.parseDouble(v[0].trim()),
                    posY.getDouble(player) - ent_u.getFloat(player) + dy,
                    posZ.getDouble(player) + Double.parseDouble(v[1].trim()), rotYaw.getFloat(player) + 180f, 0f);
        }
        if (System.getProperty("thewellodev.testghost") != null) {
            // test aid: shows four players 3 blocks around the own player (checks the drawing without a second client)
            System.clearProperty("thewellodev.testghost");
            double[][] offsets = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
            for (int i = 0; i < offsets.length; i++) {
                TWConnection.Pkt g = new TWConnection.Pkt();
                g.i1 = -999 - i;
                g.s = "Ghost" + i;
                g.x = posX.getDouble(player) + offsets[i][0];
                g.y = posY.getDouble(player) - ent_u.getFloat(player) + EYE_HEIGHT;
                g.z = posZ.getDouble(player) + offsets[i][1];
                g.yaw = 0f;
                spawnRemote(world, g);
            }
        }

        String testPlace = System.getProperty("thewellodev.testplace");
        if (testPlace != null) {
            // test aid (dx,dy,dz,id): sets one block near the player through the game's own set-block method,
            // like a player placing it (the server only accepts blocks within the player's reach)
            System.clearProperty("thewellodev.testplace");
            String[] v = testPlace.split(",");
            int bx = (int) Math.floor(posX.getDouble(player)) + Integer.parseInt(v[0].trim());
            int by = (int) Math.floor(posY.getDouble(player)) + Integer.parseInt(v[1].trim());
            int bz = (int) Math.floor(posZ.getDouble(player)) + Integer.parseInt(v[2].trim());
            int bid = Integer.parseInt(v[3].trim());
            System.out.println("[Infdev MP] test block " + bid + " at " + bx + " " + by + " " + bz);
            setBlock.invoke(world, bx, by, bz, bid);
            if (v.length > 4) {
                setMeta.invoke(world, bx, by, bz, Integer.parseInt(v[4].trim())); // optional metadata
            }
        }
        double x = posX.getDouble(player), y = posY.getDouble(player), z = posZ.getDouble(player);
        float yaw = rotYaw.getFloat(player), pitch = rotPitch.getFloat(player);
        ticksSincePos++;
        if (ticksSincePos >= 20 || Math.abs(x - lastX) > 0.01 || Math.abs(y - lastY) > 0.01
                || Math.abs(z - lastZ) > 0.01 || Math.abs(yaw - lastYaw) > 0.5 || Math.abs(pitch - lastPitch) > 0.5) {
            ticksSincePos = 0;
            lastX = x;
            lastY = y;
            lastZ = z;
            lastYaw = yaw;
            lastPitch = pitch;
            c.sendPosition(x, y, z, yaw, pitch, true);
        }
    }

    private static void handle(TWConnection c, Object world, TWConnection.Pkt p) throws Exception {
        switch (p.id) {
            case Protocol.LOGIN:
                login(world, p);
                break;
            case Protocol.CHUNK:
                receiveChunk(world, p.i1, p.i2, Protocol.inflate(p.data, Protocol.CHUNK_RAW_SIZE));
                break;
            case Protocol.UNLOAD_CHUNK:
                forgetChunk(p.i1, p.i2);
                break;
            case Protocol.BLOCK_CHANGE:
                applyingRemote = true;
                try {
                    setBlock.invoke(world, p.i1, p.i2, p.i3, p.i4 & 255);
                    setMeta.invoke(world, p.i1, p.i2, p.i3, p.i5 & 15);
                } finally {
                    applyingRemote = false;
                }
                break;
            case Protocol.KEEP_ALIVE:
                c.sendKeepAlive(p.i1);
                break;
            case Protocol.SPAWN_PLAYER:
                spawnRemote(world, p);
                break;
            case Protocol.MOVE_PLAYER:
                moveRemote(p);
                break;
            case Protocol.HELD_ITEM:
                synchronized (remotes) {
                    Remote held = remotes.get(p.i1);
                    if (held != null) {
                        setHeld(held.entity, p.i2);
                    }
                }
                break;
            case Protocol.REMOVE_PLAYER:
                synchronized (remoteDigs) {
                    remoteDigs.remove(p.i1);
                }
                synchronized (remotes) {
                    remotes.remove(p.i1);
                }
                break;
            case Protocol.DIG_PROGRESS:
                synchronized (remoteDigs) {
                    if (p.i5 < 0) {
                        remoteDigs.remove(p.i1);
                    } else if (remoteDigs.size() < 64) {
                        remoteDigs.put(p.i1, new long[]{p.i2, p.i3, p.i4, p.i5, System.currentTimeMillis()});
                    }
                }
                break;
            case Protocol.TELEPORT: {
                Object player = worldPlayer.get(world);
                setPosRot.invoke(player, p.x, p.y - EYE_HEIGHT, p.z, p.yaw, p.pitch);
                break;
            }
            case Protocol.TIME:
                worldTime.setInt(world, (int) p.l);
                break;
            case Protocol.INVENTORY_SYNC:
                onInventorySync(world, p.inv);
                break;
            case Protocol.DAMAGE:
                onDamage(world, p);
                break;
            case Protocol.HEALTH:
                onHealth(world, p.i1);
                break;
            case Protocol.HURT_ANIMATION:
                onHurtAnimation(p.i1);
                break;
            case Protocol.GAME_MODE:
                onGameMode(p.i1);
                break;
            case Protocol.AUTH_REQUEST:
                startAuth(c, p.s);
                break;
            case Protocol.CHAT:
                addChat(p.s);
                break;
            case Protocol.DISCONNECT:
                c.close(p.s);
                break;
            default:
                break; // other players, mobs, items and so on do not exist in this build (or are not shown yet)
        }
    }

    private static void login(Object world, TWConnection.Pkt p) throws Exception {
        loggedIn = true;
        blankTerrain = true;
        myEntityId = p.i1;
        worldTime.setInt(world, (int) p.l);
        gameMode = 0;
        flying = false;
        invSynced = false;
        invSignature = "";
        invTicks = 0;
        lastHealthSent = -1;
        deadReported = false;
        lastHeldSent = Integer.MIN_VALUE;
        synchronized (remotes) {
            remotes.clear();
        }
        chunkMap.clear();
        received.clear();
        spawnX = p.x;
        spawnY = p.y - EYE_HEIGHT;
        spawnZ = p.z;
        spawnYaw = p.yaw;
        spawnPitch = p.pitch;
        spawnCx = (int) Math.floor(p.x) >> 4;
        spawnCz = (int) Math.floor(p.z) >> 4;
        hold = true;
        loginTime = System.currentTimeMillis();
        addChat("&7Press T to chat. Commands start with / (try /help).");
        Object player = worldPlayer.get(world);
        setPosRot.invoke(player, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
        System.out.println("[Infdev MP] logged in, spawn " + (int) p.x + " " + (int) p.y + " " + (int) p.z);
    }

    private static void startAuth(final TWConnection c, final String serverId) {
        final String token = System.getProperty("thewellodev.token");
        if (serverId == null || !serverId.matches("[A-Za-z0-9-]{1,40}")) {
            c.sendAuthResponse(false, "Invalid verification request");
            return;
        }
        Thread t = new Thread("Infdev MP auth") {
            public void run() {
                try {
                    String name = TWAuth.join(token, serverId);
                    System.out.println("[Infdev MP] account verified: " + name);
                    c.sendAuthResponse(true, "");
                } catch (Exception e) {
                    System.out.println("[Infdev MP] account check failed: " + e.getMessage());
                    lastAuthError = "Account check failed: " + e.getMessage();
                    c.sendAuthResponse(false, e.getMessage());
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    // ----- chunks -----------------------------------------------------------------------------------------

    private static long mapKey(int cx, int cz) {
        return ((long) (cx & 0x0FFFFFFF) << 32) | ((cz & 0x0FFFFFFF) & 0xFFFFFFFFL);
    }

    /** Replaces the world's chunk provider by one that serves the chunks from the server. */
    private static void installProvider(Object world) throws Exception {
        chunkMap.clear();
        received.clear();
        final Object w = world;
        java.lang.reflect.InvocationHandler handler = new java.lang.reflect.InvocationHandler() {
            public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                if (m.getDeclaringClass() == Object.class) {
                    return m.getName().equals("hashCode") ? Integer.valueOf(System.identityHashCode(proxy))
                            : m.getName().equals("equals") ? Boolean.valueOf(proxy == args[0])
                            : "chunk provider of the server";
                }
                if (args == null || args.length == 0) {
                    // the sky brightness changed: the chunks recalculate the light that depends on it
                    for (Object chunk : chunkMap.values().toArray()) {
                        chunkRelight.invoke(chunk);
                    }
                    return null;
                }
                int gx = (Integer) args[0], gz = (Integer) args[1];
                long key = ((long) gx << 32) | (gz & 0xFFFFFFFFL);
                if (m.getReturnType() == boolean.class) {
                    return Boolean.valueOf(chunkMap.containsKey(key));
                }
                Object chunk = chunkMap.get(key);
                if (chunk == null) {
                    // not received yet: an empty chunk (air, full sky light) until the server's arrives
                    byte[] blocks = new byte[Protocol.CHUNK_BLOCKS];
                    chunk = chunkCtor.newInstance(w, blocks, gx, gz);
                    java.util.Arrays.fill((byte[]) nibbleBytes.get(chunkSky.get(chunk)), (byte) 0xFF);
                    chunkMap.put(key, chunk);
                }
                return chunk;
            }
        };
        Object provider = java.lang.reflect.Proxy.newProxyInstance(providerClass.getClassLoader(),
                new Class<?>[]{providerClass}, handler);
        worldProvider.set(world, provider);
    }

    private static void receiveChunk(Object world, int cx, int cz, byte[] raw) throws Exception {
        byte[] blocks = new byte[Protocol.CHUNK_BLOCKS];
        System.arraycopy(raw, 0, blocks, 0, Protocol.CHUNK_BLOCKS);
        int gx = cx & 0x0FFFFFFF, gz = cz & 0x0FFFFFFF;
        Object chunk = chunkCtor.newInstance(world, blocks, gx, gz);
        int off = Protocol.CHUNK_BLOCKS;
        int n = Protocol.CHUNK_NIBBLES;
        System.arraycopy(raw, off, (byte[]) nibbleBytes.get(chunkMeta.get(chunk)), 0, n);
        off += n;
        System.arraycopy(raw, off, (byte[]) nibbleBytes.get(chunkSky.get(chunk)), 0, n);
        off += n;
        System.arraycopy(raw, off, (byte[]) nibbleBytes.get(chunkBlockLight.get(chunk)), 0, n);
        off += n;
        byte[] height = new byte[256];
        System.arraycopy(raw, off, height, 0, 256);
        chunkHeight.set(chunk, height);
        int min = 127;
        for (int i = 0; i < 256; i++) {
            min = Math.min(min, height[i] & 255);
        }
        chunkLowest.setInt(chunk, min);
        long key = ((long) gx << 32) | (gz & 0xFFFFFFFFL);
        chunkMap.put(key, chunk);
        received.add(key);
        if (DEBUG) {
            System.out.println("[Infdev MP] chunk " + cx + "," + cz);
        }
        // tell the renderer that this area changed
        dirtyRegion.invoke(world, cx * 16, 0, cz * 16, cx * 16 + 15, 127, cz * 16 + 15);
    }

    private static void forgetChunk(int cx, int cz) {
        long key = ((long) (cx & 0x0FFFFFFF) << 32) | ((cz & 0x0FFFFFFF) & 0xFFFFFFFFL);
        chunkMap.remove(key);
        received.remove(key);
    }

    private static boolean hasChunk(Object world, int cx, int cz) throws Exception {
        return received.contains(mapKey(cx, cz));
    }

    // ----- reflection setup -------------------------------------------------------------------------------

    private static void init(Object mc) throws Exception {
        ClassLoader cl = mc.getClass().getClassLoader();
        Class<?> worldC = Class.forName("net.minecraft.a.a.f", true, cl);
        Class<?> playerC = Class.forName("net.minecraft.a.c.b", true, cl);
        providerClass = Class.forName("net.minecraft.a.a.e.a", true, cl);
        chunkClass = Class.forName("net.minecraft.a.a.e.d", true, cl);
        Class<?> nibbleC = Class.forName("net.minecraft.a.a.e.c", true, cl);
        worldClass = worldC;
        Class<?> fontC = Class.forName("net.minecraft.client.c.j", true, cl);
        fontField = mc.getClass().getField("n");
        fontDraw = fontC.getMethod("a", String.class, int.class, int.class, int.class);
        screenField = mc.getClass().getField("o");
        Class<?> scaledC = Class.forName("net.minecraft.client.c.h", true, cl);
        scaledCtor = scaledC.getDeclaredConstructor(int.class, int.class);
        scaledCtor.setAccessible(true);
        scaledHeight = scaledC.getDeclaredMethod("b");
        scaledHeight.setAccessible(true);
        scaledWidth = scaledC.getDeclaredMethod("a");
        scaledWidth.setAccessible(true);
        Class<?> screenC = Class.forName("net.minecraft.client.c.i", true, cl);
        setWorld = mc.getClass().getMethod("a", worldC);
        setScreen = mc.getClass().getMethod("a", screenC);
        mcWorld = mc.getClass().getField("d");
        worldPlayer = worldC.getField("b");
        worldProvider = access(worldC, "q");
        worldTime = access(worldC, "j");
        getBlock = worldC.getMethod("a", int.class, int.class, int.class);
        getMeta = worldC.getMethod("c", int.class, int.class, int.class);
        setMeta = worldC.getMethod("a", int.class, int.class, int.class, int.class);
        dirtyRegion = worldC.getMethod("a", int.class, int.class, int.class, int.class, int.class, int.class);
        chunkCtor = chunkClass.getConstructor(worldC, byte[].class, int.class, int.class);
        chunkMeta = access(chunkClass, "c");
        chunkSky = access(chunkClass, "d");
        chunkBlockLight = access(chunkClass, "e");
        chunkHeight = access(chunkClass, "f");
        chunkLowest = access(chunkClass, "g");
        nibbleBytes = access(nibbleC, "a");
        chunkRelight = chunkClass.getMethod("b");
        posX = playerC.getField("g");
        posY = playerC.getField("h");
        posZ = playerC.getField("i");
        rotYaw = playerC.getField("m");
        rotPitch = playerC.getField("n");
        setPosRot = playerC.getMethod("b", double.class, double.class, double.class, float.class, float.class);
        setBlock = worldC.getMethod("b", int.class, int.class, int.class, int.class);
        Class<?> livingC = Class.forName("net.minecraft.a.c.e", true, cl);
        Class<?> humanC = Class.forName("net.minecraft.a.c.e.a", true, cl);
        playerCtor = humanC.getConstructor(worldC);
        nameField = access(humanC, "R");
        ent_u = playerC.getField("u");
        ent_A = playerC.getField("A");
        ent_B = playerC.getField("B");
        ent_C = playerC.getField("C");
        ent_o = playerC.getField("o");
        ent_p = playerC.getField("p");
        ent_T = livingC.getField("T");
        ent_U = livingC.getField("U");
        ent_af = livingC.getField("af");
        ent_ag = livingC.getField("ag");
        ent_ah = livingC.getField("ah");
        Class<?> rmC = Class.forName("net.minecraft.client.a.a.i", true, cl);
        renderManager = rmC.getField("a");
        renderEntity = rmC.getMethod("a", playerC, float.class);
        Class<?> texturesC = Class.forName("net.minecraft.client.a.i", true, cl);
        managerSetup = rmC.getMethod("a", worldC, texturesC, humanC, float.class);
        mcTextures = access(mc.getClass(), "m");
        camX = access(rmC, "f");
        camY = access(rmC, "g");
        camZ = access(rmC, "h");
        camYaw = rmC.getField("d");
        fontWidth = fontC.getMethod("a", String.class);
        fontPlain = fontC.getMethod("b", String.class, int.class, int.class, int.class);
        invField = humanC.getField("b");
        Class<?> invC = Class.forName("net.minecraft.a.c.e.b", true, cl);
        invItems = invC.getField("a");
        invSelected = invC.getField("c");
        Class<?> stackC = Class.forName("net.minecraft.a.b.k", true, cl);
        stackCtor = stackC.getConstructor(int.class);
        stackId = stackC.getField("c");
        stackCtor2 = stackC.getConstructor(int.class, int.class);
        stackCount = stackC.getField("a");
        stackDamage = stackC.getField("d");
        healthField = livingC.getField("W");
        hurtTime = livingC.getField("Y");
        hurtTimeMax = livingC.getField("Z");
        hurtMethod = humanC.getMethod("a", playerC, int.class);
        respawnMethod = humanC.getMethod("i");
        motionY = playerC.getField("k");
        Class<?> itemC = Class.forName("net.minecraft.a.b.i", true, cl);
        itemTable = itemC.getField("b");
        itemDamage = itemC.getMethod("a");
        pausedField = mc.getClass().getDeclaredField("l");
        pausedField.setAccessible(true);
        fallField = playerC.getDeclaredField("b");
        fallField.setAccessible(true);
        velX = playerC.getField("j");
        velZ = playerC.getField("l");
        onGround = playerC.getField("r");
        blockAt = worldC.getMethod("a", int.class, int.class, int.class);
        playerClassForFire = playerC;
    }

    private static Field access(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
