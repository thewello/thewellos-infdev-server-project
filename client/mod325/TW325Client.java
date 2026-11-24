import net.thewellodev.protocol.GameVersion;
import net.thewellodev.protocol.Protocol;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Multiplayer for inf-20100325-1640 (client side).
 *
 * Hooks (inserted by the patcher, see ClientPatcher.createTransformer316):
 *   tick(mc)       start of the game tick (net/minecraft/client/d.f)
 *   onBlockSet     end of the world's set-block method (net/minecraft/a/a/e.b(IIII))
 *   onMetaSet      end of the world's set-metadata method (net/minecraft/a/a/e.a(IIII))
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
public final class TW325Client {

    private static final double EYE_HEIGHT = 1.62;
    /** -Dthewellodev.debug=true prints the block hook calls. */
    private static final boolean DEBUG = Boolean.getBoolean("thewellodev.debug");

    private static TWConnection conn;
    private static boolean ready;
    private static boolean failed;
    private static volatile boolean blankTerrain;
    private static volatile boolean applyingRemote;
    private static long testCountsDone, testHashesDone;
    /** Test aid: blocks that are set one in each tick (see thewellodev.testplace). */
    private static final java.util.ArrayDeque<int[]> testQueue = new java.util.ArrayDeque<int[]>();
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
    private static Field worldTiles;
    private static Field worldPlayer;
    private static Field worldProvider;
    private static Field worldTime;
    private static Method getMeta, setMeta, listenerDirty;
    private static Field worldListeners;
    private static boolean dirtyLogged;
    private static Class<?> providerClass, chunkClass;
    private static Field chunkMeta, chunkSky, chunkBlockLight, chunkHeight, chunkLowest, nibbleBytes;
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

    private TW325Client() {
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
        containerFlush(world, c);
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
        if (DEBUG) {
            System.out.println("[Infdev MP] player appears: " + p.s);
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
                System.out.println("[Infdev MP] render manager textures " + access(rmClass, "e").get(rm) + " world "
                        + access(rmClass, "f").get(rm));
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
                // This build draws the world relative to the camera (the matrix has the camera's rotation only) and, unlike
                // 20100313, its render manager subtracts the camera position (static fields b, c, d) itself: the entities
                // are drawn with their world coordinates as they are.
                for (Remote r : remotes.values()) {
                    renderEntity.invoke(rm, r.entity, partial);
                }
                for (Object mob : mobCopies.values().toArray()) {
                    renderEntity.invoke(rm, mob, partial);
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
    private static Field noClipField;

    /** Hook (see ClientPatcher.addGameModeHooks): nothing hurts the player of this game in the game modes 1 and 2. */
    public static boolean noDamage(Object entity) {
        try {
            if (gameMode == 0 || mcRef == null) {
                return false;
            }
            Object w = mcWorld.get(mcRef);
            return w != null && entity == worldPlayer.get(w);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Hook: the digging controller breaks blocks at once (creative). */
    public static boolean creative() {
        return gameMode == 1;
    }

    /** Hook: the digging controller does nothing (spectator). */
    public static boolean spectator() {
        return gameMode == 2;
    }
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
        noClipField.setBoolean(player, gameMode == 2); // the spectator goes through walls
        if (gameMode != 0 && hp > 0 && hp < 20) {
            healthField.setInt(player, 20); // creative and spectator: nothing hurts (see noDamage)
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
        if (gameMode != 0) {
            fallField.setFloat(player, 0f); // creative and spectator: no fall damage
        }
        if (gameMode == 2) {
            flying = true; // the spectator always flies
        }
        if (gameMode != 0 && free) {
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
                if (vy < 0 && onGround.getBoolean(player) && gameMode == 1) {
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
        if (gameMode != 0 && flying && !free) {
            // an inventory window, a menu or the chat is open: the flying player hovers where it is (the keys are not read, so
            // without this the gravity pulled it down) and slows down
            motionY.setDouble(player, 0);
            velX.setDouble(player, velX.getDouble(player) * 0.4);
            velZ.setDouble(player, velZ.getDouble(player) * 0.4);
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
        if (!mobCopies.isEmpty()) {
            double[] mh = aimAtMobCopy(ox, oy, oz, dx, dy, dz, best);
            if (mh != null) {
                lastAttackMs = now;
                c.sendMobHit((int) mh[0], attackDamage(player));
                return;
            }
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
        if (player == null || gameMode != 0 || health(player) <= 0) {
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
        flying = mode == 2;
        addChat(mode == 1 ? "&eGame mode: &fCreative (fly with space / shift, blocks break at once, nothing hurts)"
                : mode == 2 ? "&eGame mode: &fSpectator (invisible, fly through walls with space / shift, nothing interacts)"
                : "&eGame mode: &fSurvival");
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
     * The player that died is flagged as dead after a second and the world takes it out of its entity list: it is never ticked
     * again (no gravity, no movement, a camera that does not follow). A respawn clears the flag and puts the player back.
     */
    private static void revive(Object world, Object player) throws Exception {
        entDead.setBoolean(player, false);
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
            worldAddEntity.invoke(world, player);
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

    // ----- items that fall (shared between the players) ------------------------------------------------------

    private static Class<?> itemEntityC;
    private static Constructor<?> itemEntityCtor;
    private static Method worldAddEntity;
    private static Field entDead, entMotX, entMotY, entMotZ;
    /** Item entities of this game that the server knows: the entity -> {number, 1 when it is a copy of another player's}. */
    private static final java.util.Map<Object, int[]> dropTracked = new java.util.IdentityHashMap<Object, int[]>();
    /** Items that appeared in this game and have not been announced yet (their motion is set after they are added). */
    private static final java.util.List<Object> dropPending = new java.util.ArrayList<Object>();
    /** Every shared item of this game by its number (own items and copies). */
    private static final java.util.Map<Integer, Object> dropByNumber = new java.util.HashMap<Integer, Object>();
    private static boolean spawningRemoteDrop;
    /** The slot of this game on the server (top bits of the numbers of its items and mobs) and its last own number. */
    private static int mySlot = -1, mySeq;
    /** Items for which a pick-up was asked, and when; items the server gave to this game. */
    private static final java.util.Map<Object, Long> claimSent = new java.util.IdentityHashMap<Object, Long>();
    private static final java.util.Set<Object> claimGranted =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());

    /** The next number of this game: slot and a counter that is never 0 (a lower part of 0 means "all of that player"). */
    private static int nextNumber() {
        mySeq = (mySeq + 1) & 0xFFFFF;
        if (mySeq == 0) {
            mySeq = 1;
        }
        return (mySlot << 20) | mySeq;
    }

    private static void onSlot(TWConnection.Pkt p) {
        mySlot = p.i1 & 0x7FF;
        mySeq = p.i2 & 0xFFFFF;
    }

    /**
     * Called at the start of the world's add-entity method. The game of every player has its own items (blocks drop,
     * items can be thrown); the server passes them on, so everybody sees all of them.
     */
    public static void onEntityAdd(Object entity) {
        if (entity != null && connected() && mobKind(entity.getClass()) == ARROW_KIND) {
            try {
                Object w = mcRef == null ? null : mcWorld.get(mcRef);
                if (w != null && arrowShooter.get(entity) == worldPlayer.get(w)) {
                    synchronized (ownArrows) {
                        ownArrows.add(entity); // an arrow of this player: it can hurt the others
                    }
                }
            } catch (Exception ignored) {
                // the arrow is only shown then
            }
            synchronized (mobPending) {
                mobPending.add(entity);
            }
            return;
        }
        if (entity != null && connected() && mobKind(entity.getClass()) >= 0) {
            if (adoptNumber != 0) {
                ownMobs.put(entity, new int[]{adoptNumber, mobKind(entity.getClass())}); // a mob taken over: same number
                return;
            }
            synchronized (mobPending) {
                mobPending.add(entity);
            }
            return;
        }
        if (arrowC != null && entity != null && entity.getClass() == arrowC && connected()) {
            try {
                Object w = mcRef == null ? null : mcWorld.get(mcRef);
                if (w == null || arrowShooter.get(entity) != worldPlayer.get(w)) {
                    return; // the arrow of a skeleton or another source: not an attack of this player
                }
            } catch (Exception ignored) {
                return;
            }
            synchronized (ownArrows) {
                ownArrows.add(entity);
            }
            return;
        }
        if (spawningRemoteDrop || itemEntityC == null || entity == null || entity.getClass() != itemEntityC
                || !connected()) {
            return;
        }
        synchronized (dropPending) {
            dropPending.add(entity);
        }
    }

    /**
     * Called at the start of the item entity's "touched by a player" method. A shared item is only picked up when the server
     * has given it to this game (two players who touch it at the same time must not both get it). Returns true to cancel
     * the pick-up for now.
     */
    public static boolean onItemPickup(Object item) {
        if (!connected() || conn == null) {
            return false;
        }
        try {
            if (entDead.getBoolean(item)) {
                // An item that was taken away (another player got it, or the server said so) lies in the world until the
                // game's tick removes it: it must not be picked up in the meantime (that would give the item twice).
                return true;
            }
        } catch (Exception ignored) {
            // the check is only a safety net
        }
        int[] v = dropTracked.get(item);
        if (v == null) {
            return false; // not shared (it was there before the login)
        }
        try {
            if (itemDelay.getInt(item) > 0) {
                // The game does not pick the item up yet (it was just thrown or dropped): asking now would take the item away
                // from the other players while nobody can get it, and a granted answer would be used up for nothing.
                return true;
            }
        } catch (Exception ignored) {
            // without the delay field the claim is asked at once, as before
        }
        if (claimGranted.remove(item)) {
            return false; // the server said yes: the game picks it up now
        }
        long now = System.currentTimeMillis();
        Long sent = claimSent.get(item);
        if (sent == null || now - sent > 1500) {
            claimSent.put(item, now);
            conn.sendDropClaim(v[0]);
        }
        return true;
    }

    private static void onClaimResult(int number, int code) {
        Object e = dropByNumber.get(number);
        if (e == null || code == 2) {
            return; // code 2: a plugin refused for now; the item stays and is asked for again later
        }
        if (code == 1) {
            claimGranted.add(e);
        } else {
            forgetDrop(e, number); // somebody else got it
        }
    }

    private static void forgetDrop(Object e, int number) {
        try {
            entDead.setBoolean(e, true);
        } catch (Exception ignored) {
            // the entity is gone anyway
        }
        dropTracked.remove(e);
        dropByNumber.remove(number);
        claimSent.remove(e);
        claimGranted.remove(e);
    }

    /** The server asks for the own items again (a player joined, or time passed): every item of this game is announced once more. */
    private static void onDropResync(TWConnection c) throws Exception {
        for (java.util.Map.Entry<Object, int[]> en : new java.util.ArrayList<java.util.Map.Entry<Object, int[]>>(dropTracked.entrySet())) {
            Object e = en.getKey();
            if (en.getValue()[1] != 0 || entDead.getBoolean(e)) {
                continue; // a copy of somebody else's item, or an item that is gone
            }
            Object stack = stackOfItem.get(e);
            if (stack != null) {
                c.sendDropSpawn(en.getValue()[0], stackId.getInt(stack), stackCount.getInt(stack), stackDamage.getInt(stack),
                        new double[]{posX.getDouble(e), posY.getDouble(e), posZ.getDouble(e), entMotX.getDouble(e),
                                entMotY.getDouble(e), entMotZ.getDouble(e)});
            }
        }
    }

    private static void dropTick(TWConnection c) throws Exception {
        Object[] pending;
        synchronized (dropPending) {
            pending = dropPending.toArray();
            dropPending.clear();
        }
        for (Object e : pending) {
            if (entDead.getBoolean(e) || mySlot < 0) {
                continue;
            }
            Object stack = stackOfItem.get(e);
            if (stack == null) {
                continue;
            }
            int number = nextNumber();
            dropTracked.put(e, new int[]{number, 0});
            dropByNumber.put(number, e);
            if (DEBUG) {
                System.out.println("[Infdev MP] item announced: " + stackId.getInt(stack) + " x" + stackCount.getInt(stack)
                        + " #" + number + " at " + posX.getDouble(e) + "," + posY.getDouble(e) + "," + posZ.getDouble(e));
            }
            c.sendDropSpawn(number, stackId.getInt(stack), stackCount.getInt(stack), stackDamage.getInt(stack),
                    new double[]{posX.getDouble(e), posY.getDouble(e), posZ.getDouble(e), entMotX.getDouble(e),
                            entMotY.getDouble(e), entMotZ.getDouble(e)});
        }
        if (dropTracked.isEmpty()) {
            return;
        }
        for (java.util.Iterator<java.util.Map.Entry<Object, int[]>> it = dropTracked.entrySet().iterator(); it.hasNext();) {
            java.util.Map.Entry<Object, int[]> en = it.next();
            if (entDead.getBoolean(en.getKey())) {
                int[] v = en.getValue();
                if (DEBUG) {
                    System.out.println("[Infdev MP] item gone #" + v[0] + (v[1] == 0 ? " (own)" : " (copy)"));
                }
                c.sendDropRemove(v[0], v[1] == 0); // picked up, or too old: the others drop their copy
                dropByNumber.remove(v[0]);
                claimSent.remove(en.getKey());
                claimGranted.remove(en.getKey());
                it.remove();
            }
        }
    }

    private static void onDropSpawn(Object world, TWConnection.Pkt p) throws Exception {
        if (dropByNumber.containsKey(p.i1)) {
            return;
        }
        Object stack = stackCtor2.newInstance(p.i2, p.i3);
        stackDamage.setInt(stack, p.i4);
        Object e = itemEntityCtor.newInstance(world, p.x, p.y, p.z, stack);
        entMotX.setDouble(e, p.mx);
        entMotY.setDouble(e, p.my);
        entMotZ.setDouble(e, p.mz);
        spawningRemoteDrop = true;
        try {
            worldAddEntity.invoke(world, e);
        } finally {
            spawningRemoteDrop = false;
        }
        if (DEBUG) {
            System.out.println("[Infdev MP] item of another player: " + p.i2 + " x" + p.i3 + " #" + p.i1);
        }
        dropTracked.put(e, new int[]{p.i1, 1});
        dropByNumber.put(p.i1, e);
    }

    /** An item is gone (somebody got it, or it is too old): the copy or the own item goes away here too. */
    private static void onDropRemove(TWConnection.Pkt p) throws Exception {
        Object e = dropByNumber.get(p.i1);
        if (e != null) {
            if (DEBUG) {
                System.out.println("[Infdev MP] item #" + p.i1 + " was taken by another player");
            }
            forgetDrop(e, p.i1);
        }
    }

    private static void dropReset() {
        dropTracked.clear();
        dropByNumber.clear();
        claimSent.clear();
        claimGranted.clear();
        mySlot = -1;
        synchronized (dropPending) {
            dropPending.clear();
        }
    }

    private static Field stackOfItem;
    /** The item entity's pickup delay in ticks (the game picks the item up when it is 0). */
    private static Field itemDelay;

    // ----- chests and furnaces (shared between the players) ----------------------------------------------------

    private static int chestStep;
    private static long chestStepAt;
    private static Method tileGet;
    private static Method blockUse;
    private static Field blockTable;
    private static Class<?> chestTileC, furnaceTileC;
    private static Field chestSlots, furnaceSlots;
    private static Field[] furnaceInts;
    private static Field stackFieldCount, stackFieldDamage;

    /** An open of a chest or furnace that waits for the server's locks. */
    private static final class PendingOpen {
        int x, y, z;
        int[][] positions;
        boolean[] granted;
        long since = System.currentTimeMillis();
    }

    private static PendingOpen pendingOpen;
    /** The positions this game holds locks for while a chest or furnace window is open. */
    private static int[][] heldLocks;
    private static long heldSince;
    private static boolean containerReentry;

    private static boolean isContainerId(int id) {
        return id == 54 || id == 61 || id == 62;
    }

    /**
     * Called at the start of the chest and furnace blocks' "right-clicked" method. In multiplayer the contents belong to
     * the server: the game asks for a lock first (so two players never open the same chest) and the server sends the
     * current contents; the click is repeated when the lock is granted. Returns true to cancel the click for now.
     */
    public static boolean onContainerUse(Object world, int x, int y, int z, Object player) {
        if (containerReentry || !connected() || !loggedIn || conn == null) {
            return false;
        }
        try {
            if (pendingOpen != null && System.currentTimeMillis() - pendingOpen.since < 8000) {
                return true; // still waiting for the answer of the last click
            }
            if (heldLocks != null) {
                return true; // a window of this game is still open
            }
            int id = (Integer) getBlock.invoke(world, x, y, z);
            java.util.List<int[]> list = new java.util.ArrayList<int[]>();
            list.add(new int[]{x, y, z});
            if (id == 54) {
                // chests next to each other make one large chest: all of them are locked
                int[][] around = {{x - 1, z}, {x + 1, z}, {x, z - 1}, {x, z + 1}};
                for (int[] a : around) {
                    if ((Integer) getBlock.invoke(world, a[0], y, a[1]) == 54) {
                        list.add(new int[]{a[0], y, a[1]});
                    }
                }
            }
            PendingOpen po = new PendingOpen();
            po.x = x;
            po.y = y;
            po.z = z;
            po.positions = list.toArray(new int[0][]);
            po.granted = new boolean[po.positions.length];
            pendingOpen = po;
            for (int[] pos : po.positions) {
                conn.sendXYZ(Protocol.LOCK_REQUEST, pos[0], pos[1], pos[2]);
            }
            return true;
        } catch (Throwable t) {
            pendingOpen = null;
            return false; // fall back to the game's own window
        }
    }

    private static void onLockResult(Object world, int x, int y, int z, boolean ok) throws Exception {
        PendingOpen po = pendingOpen;
        if (po == null) {
            if (ok) {
                conn.sendXYZ(Protocol.LOCK_RELEASE, x, y, z);
            }
            return;
        }
        int index = -1;
        for (int i = 0; i < po.positions.length; i++) {
            if (po.positions[i][0] == x && po.positions[i][1] == y && po.positions[i][2] == z) {
                index = i;
            }
        }
        if (index < 0) {
            return;
        }
        if (!ok) {
            // somebody else has it: give back the locks already granted (the server told the player why)
            for (int i = 0; i < po.positions.length; i++) {
                if (po.granted[i]) {
                    conn.sendXYZ(Protocol.LOCK_RELEASE, po.positions[i][0], po.positions[i][1], po.positions[i][2]);
                }
            }
            pendingOpen = null;
            return;
        }
        po.granted[index] = true;
        for (boolean g : po.granted) {
            if (!g) {
                return;
            }
        }
        pendingOpen = null;
        heldLocks = po.positions;
        heldSince = System.currentTimeMillis();
        Object player = worldPlayer.get(world);
        int id = (Integer) getBlock.invoke(world, po.x, po.y, po.z);
        if (!isContainerId(id)) {
            // the block was broken while the answer came: give the locks back
            for (int[] pos : po.positions) {
                conn.sendXYZ(Protocol.LOCK_RELEASE, pos[0], pos[1], pos[2]);
            }
            heldLocks = null;
            return;
        }
        Object block = ((Object[]) blockTable.get(null))[id];
        containerReentry = true;
        try {
            blockUse.invoke(block, world, po.x, po.y, po.z, player);
        } finally {
            containerReentry = false;
        }
    }

    /** About once a tick: when the window of a chest or furnace is closed, the contents go to the server. */
    /**
     * Called at the start of the world's entity tick, which also ticks the tile entities in the world's list. The server smelts in
     * the furnaces; this game only ticks the furnace that its player has open (the server does not tick that one meanwhile).
     */
    @SuppressWarnings("unchecked")
    public static void tiles(Object world) {
        try {
            java.util.List<Object> list = (java.util.List<Object>) worldTiles.get(world);
            list.clear();
            int[][] held = heldLocks;
            if (held != null) {
                for (int[] pos : held) {
                    Object tile = tileGet.invoke(world, pos[0], pos[1], pos[2]);
                    if (tile != null && furnaceTileC.isInstance(tile)) {
                        list.add(tile);
                    }
                }
            }
        } catch (Throwable ignored) {
            // before the mod is ready there is nothing to do
        }
    }

    private static void containerTick(Object mc, Object world, TWConnection c) throws Exception {
        if (pendingOpen != null && System.currentTimeMillis() - pendingOpen.since > 8000) {
            for (int i = 0; i < pendingOpen.positions.length; i++) {
                if (pendingOpen.granted[i]) {
                    c.sendXYZ(Protocol.LOCK_RELEASE, pendingOpen.positions[i][0], pendingOpen.positions[i][1],
                            pendingOpen.positions[i][2]);
                }
            }
            pendingOpen = null;
        }
        if (heldLocks == null || System.currentTimeMillis() - heldSince < 500) {
            return;
        }
        if (screenField.get(mc) != null) {
            return; // the window is still open
        }
        containerFlush(world, c);
    }

    /** The contents of the chests and furnaces this game holds go to the server, and the locks are given back. */
    private static void containerFlush(Object world, TWConnection c) throws Exception {
        if (heldLocks == null) {
            return;
        }
        for (int[] pos : heldLocks) {
            Object tile = tileGet.invoke(world, pos[0], pos[1], pos[2]);
            if (tile != null) {
                c.sendTile(pos[0], pos[1], pos[2], tileBytes(tile));
            }
            c.sendXYZ(Protocol.LOCK_RELEASE, pos[0], pos[1], pos[2]);
        }
        heldLocks = null;
    }

    private static void containerReset() {
        pendingOpen = null;
        heldLocks = null;
    }

    /**
     * Tile entity data (same layout as the server's bridge): a type byte (0 none, 1 chest, 2 furnace), the slots as
     * (short id, short count, short damage), and for a furnace three ints (burn time, burn item time, cook time).
     */
    private static byte[] tileBytes(Object tile) throws Exception {
        boolean furnace = furnaceTileC.isInstance(tile);
        if (!furnace && !chestTileC.isInstance(tile)) {
            return new byte[]{0};
        }
        Object[] slots = (Object[]) (furnace ? furnaceSlots : chestSlots).get(tile);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bo);
        out.writeByte(furnace ? 2 : 1);
        for (Object st : slots) {
            boolean empty = st == null || stackFieldCount.getInt(st) <= 0;
            out.writeShort(empty ? -1 : stackId.getInt(st));
            out.writeShort(empty ? 0 : stackFieldCount.getInt(st));
            out.writeShort(empty ? 0 : stackFieldDamage.getInt(st));
        }
        if (furnace) {
            for (int i = 0; i < 3; i++) {
                out.writeInt(furnaceInts[i].getInt(tile));
            }
        }
        return bo.toByteArray();
    }

    /** The server sends the contents of a chest or furnace (with the chunk, and when somebody closed it). */
    private static void onTileData(Object world, TWConnection.Pkt p) throws Exception {
        byte[] d = p.data;
        if (d == null || d.length < 2 || p.i2 < 0 || p.i2 >= 128) {
            return;
        }
        int id = (Integer) getBlock.invoke(world, p.i1, p.i2, p.i3);
        if (!isContainerId(id)) {
            return;
        }
        if (DEBUG) {
            System.out.println("[Infdev MP] tile data for " + p.i1 + "," + p.i2 + "," + p.i3 + " (" + d.length + " bytes, first item "
                    + (((d[1] & 255) << 8 | (d[2] & 255)) << 16 >> 16) + ")");
        }
        Object tile = tileGet.invoke(world, p.i1, p.i2, p.i3);
        if (tile == null) {
            return;
        }
        boolean furnace = furnaceTileC.isInstance(tile);
        if (d[0] != (furnace ? 2 : 1)) {
            return;
        }
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(d, 1, d.length - 1));
        Object[] slots = (Object[]) (furnace ? furnaceSlots : chestSlots).get(tile);
        for (int i = 0; i < slots.length; i++) {
            int sid = in.readShort();
            int count = in.readShort();
            int damage = in.readShort();
            if (sid < 0 || count <= 0) {
                slots[i] = null;
            } else {
                Object stack = stackCtor2.newInstance(sid, count);
                stackFieldDamage.setInt(stack, damage);
                slots[i] = stack;
            }
        }
        if (furnace) {
            for (int i = 0; i < 3; i++) {
                furnaceInts[i].setInt(tile, in.readInt());
            }
        }
    }

    // ----- explosions -------------------------------------------------------------------------------------

    /**
     * Called at the start of the world's explosion method (creepers and TNT explode in this build). The game hurts the
     * entities of its own world; the other players are not in it, so they are hurt here, with the game's own formula:
     * the nearer to the centre, the more damage. The server applies its PvP rules.
     */
    public static void onExplosion(Object source, double x, double y, double z, float power) {
        if (!connected() || conn == null) {
            return;
        }
        try {
            conn.sendExplosion(power, x, y, z);
            synchronized (remotes) {
                for (java.util.Map.Entry<Integer, Remote> en : remotes.entrySet()) {
                    Object e = en.getValue().entity;
                    double dx = posX.getDouble(e) - x;
                    double dy = posY.getDouble(e) - ent_u.getFloat(e) + 0.9 - y;
                    double dz = posZ.getDouble(e) - z;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / (power * 2.0);
                    if (d > 1.0) {
                        continue;
                    }
                    double exposure = 1.0 - d;
                    int damage = (int) ((exposure * exposure + exposure) / 2.0 * 8.0 * power + 1.0);
                    conn.sendAttack(en.getKey(), Math.max(1, Math.min(10, damage)));
                }
            }
        } catch (Throwable t) {
            // an explosion must never crash the game
        }
    }

    // ----- mobs that an arrow makes (shared between the players) ------------------------------------------------

    private static final String[] MOB_CLASS_NAMES = {"net.minecraft.a.c.a.f", "net.minecraft.a.c.a.c",
            "net.minecraft.a.c.a.d", "net.minecraft.a.c.a.b", "net.minecraft.a.c.b.c", "net.minecraft.a.c.b.b",
            "net.minecraft.a.c.c.a", "net.minecraft.a.c.d.a"};
    /** The kind number of the primed TNT (the other kinds are the mobs): it is shown to the other players like a mob. */
    private static final int TNT_KIND = 6;
    private static Field tntFuse;
    /** The kind number of an arrow in flight (any arrow of this game: a player's, a skeleton's): shown to the others too. */
    private static final int ARROW_KIND = 7;
    private static Field arrowShake;
    private static Class<?>[] mobClasses;
    private static Constructor<?>[] mobCtors;
    private static Method entSetPos, entHurt;
    private static Field entDeath, entFire, entBox;
    private static Field[] boxFields;
    /** The mobs of this game that the server knows: the entity -> {number, kind}. */
    private static final java.util.Map<Object, int[]> ownMobs = new java.util.IdentityHashMap<Object, int[]>();
    private static final java.util.List<Object> mobPending = new java.util.ArrayList<Object>();
    /** Copies of the mobs of other players' games (not in the world's entity list: the owner's game moves them). */
    private static final java.util.Map<Integer, Object> mobCopies = new java.util.HashMap<Integer, Object>();
    /** Where the owner's game says each copy is and how it looks: x, y, z, yaw, pitch, body yaw, limb amount, limb position, hurt, death, fire, health. */
    private static final java.util.Map<Integer, double[]> mobTargets = new java.util.HashMap<Integer, double[]>();
    private static int mobTicks;
    /** Set while a mob that the server handed over is added to the world: its number. */
    private static int adoptNumber;

    private static int mobKind(Class<?> c) {
        for (int i = 0; mobClasses != null && i < mobClasses.length; i++) {
            if (mobClasses[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private static void mobTick(TWConnection c) throws Exception {
        Object[] pending;
        synchronized (mobPending) {
            pending = mobPending.toArray();
            mobPending.clear();
        }
        for (Object e : pending) {
            if (!entDead.getBoolean(e) && ownMobs.size() < 40 && mySlot >= 0) {
                int[] v = {nextNumber(), mobKind(e.getClass())};
                ownMobs.put(e, v);
                sendMob(c, v, e);
            }
        }
        boolean update = ++mobTicks % 3 == 0;
        boolean fast = true; // an arrow flies fast: it is reported every tick
        for (java.util.Iterator<java.util.Map.Entry<Object, int[]>> it = ownMobs.entrySet().iterator(); it.hasNext();) {
            java.util.Map.Entry<Object, int[]> en = it.next();
            if (entDead.getBoolean(en.getKey())) {
                c.sendMobRemove(en.getValue()[0], true);
                it.remove();
            } else if (update || (fast && en.getValue()[1] == ARROW_KIND)) {
                sendMob(c, en.getValue(), en.getKey());
            }
        }
    }

    private static void sendMob(TWConnection c, int[] v, Object e) throws Exception {
        if (v[1] == ARROW_KIND) {
            c.sendMobState(v[0], v[1], posX.getDouble(e), posY.getDouble(e), posZ.getDouble(e), rotYaw.getFloat(e),
                    rotPitch.getFloat(e), 0f, 0f, 0f, 0, 0, Math.max(0, Math.min(1000, arrowShake.getInt(e))) << 8);
            return;
        }
        if (v[1] == TNT_KIND) {
            // a primed TNT: its place and how long the fuse still burns (in the place of the health)
            c.sendMobState(v[0], v[1], posX.getDouble(e), posY.getDouble(e), posZ.getDouble(e), 0f, 0f, 0f, 0f, 0f, 0, 0,
                    Math.max(0, Math.min(1000, tntFuse.getInt(e))) << 8);
            return;
        }
        int hurt = Math.max(0, Math.min(255, hurtTime.getInt(e)));
        int death = Math.max(0, Math.min(255, entDeath.getInt(e)));
        int health = Math.max(0, Math.min(1000, healthField.getInt(e)));
        int fire = entFire.getInt(e) > 0 ? 1 : 0;
        c.sendMobState(v[0], v[1], posX.getDouble(e), posY.getDouble(e), posZ.getDouble(e), rotYaw.getFloat(e),
                rotPitch.getFloat(e), ent_T.getFloat(e), ent_ag.getFloat(e), ent_ah.getFloat(e), hurt, death,
                fire | (health << 8));
    }

    private static double[] mobTarget(TWConnection.Pkt p) {
        return new double[]{p.x, p.y, p.z, p.yaw, p.pitch, p.mx, p.my, p.mz, p.i3, p.i4, p.i5 & 1, p.i5 >> 8};
    }

    private static void onMobState(Object world, TWConnection.Pkt p) throws Exception {
        if (p.i2 < 0 || p.i2 >= mobCtors.length) {
            return;
        }
        Object e = mobCopies.get(p.i1);
        if (e == null) {
            if (mobCopies.size() >= 80) {
                return;
            }
            e = p.i2 == TNT_KIND ? mobCtors[p.i2].newInstance(world, (float) p.x, (float) p.y, (float) p.z)
                    : p.i2 == ARROW_KIND ? mobCtors[p.i2].newInstance(world, worldPlayer.get(world))
                    : mobCtors[p.i2].newInstance(world);
            if (DEBUG) {
                System.out.println("[Infdev MP] mob of another player: kind " + p.i2 + " #" + p.i1);
            }
            mobCopies.put(p.i1, e);
            entSetPos.invoke(e, p.x, p.y, p.z);
            ent_A.setDouble(e, p.x);
            ent_B.setDouble(e, p.y);
            ent_C.setDouble(e, p.z);
            if (p.i2 == ARROW_KIND) {
                rotYaw.setFloat(e, p.yaw);
                rotPitch.setFloat(e, p.pitch);
                ent_o.setFloat(e, p.yaw);
                ent_p.setFloat(e, p.pitch);
            }
            if (p.i2 < TNT_KIND) {
                rotYaw.setFloat(e, p.yaw);
                rotPitch.setFloat(e, p.pitch);
                ent_o.setFloat(e, p.yaw);
                ent_p.setFloat(e, p.pitch);
                ent_T.setFloat(e, (float) p.mx);
                ent_U.setFloat(e, (float) p.mx);
            }
        }
        mobTargets.put(p.i1, mobTarget(p));
    }

    /**
     * Every game tick: a copy of another player's mob moves part of the way to the position the owner reported and takes
     * over its look (body and head direction, walking, hurt, death, fire). The owner reports about seven times a second;
     * the renderer needs the previous values of the tick to draw between the ticks.
     */
    private static void mobCopyTick() throws Exception {
        for (java.util.Map.Entry<Integer, Object> en : mobCopies.entrySet()) {
            double[] t = mobTargets.get(en.getKey());
            if (t == null) {
                continue;
            }
            Object e = en.getValue();
            double x = posX.getDouble(e), y = posY.getDouble(e), z = posZ.getDouble(e);
            ent_A.setDouble(e, x);
            ent_B.setDouble(e, y);
            ent_C.setDouble(e, z);
            if (mobKind(e.getClass()) == ARROW_KIND) {
                ent_o.setFloat(e, rotYaw.getFloat(e));
                ent_p.setFloat(e, rotPitch.getFloat(e));
                entSetPos.invoke(e, x + (t[0] - x) * 0.6, y + (t[1] - y) * 0.6, z + (t[2] - z) * 0.6);
                rotYaw.setFloat(e, (float) t[3]);
                rotPitch.setFloat(e, (float) t[4]);
                arrowShake.setInt(e, (int) t[11]);
                continue;
            }
            if (mobKind(e.getClass()) == TNT_KIND) {
                double tx = t[0] - x, ty = t[1] - y, tz = t[2] - z;
                double f = tx * tx + ty * ty + tz * tz > 64 ? 1.0 : 0.3;
                entSetPos.invoke(e, x + tx * f, y + ty * f, z + tz * f);
                tntFuse.setInt(e, (int) t[11]);
                continue;
            }
            float yaw = rotYaw.getFloat(e), pitch = rotPitch.getFloat(e), body = ent_T.getFloat(e);
            ent_o.setFloat(e, yaw);
            ent_p.setFloat(e, pitch);
            ent_U.setFloat(e, body);
            ent_af.setFloat(e, ent_ag.getFloat(e));
            double dx = t[0] - x, dy = t[1] - y, dz = t[2] - z;
            double k = dx * dx + dy * dy + dz * dz > 64 ? 1.0 : 0.3; // far away (teleported): at once
            entSetPos.invoke(e, x + dx * k, y + dy * k, z + dz * k);
            rotYaw.setFloat(e, yaw + (float) wrapDegrees(t[3] - yaw) * (float) k);
            rotPitch.setFloat(e, pitch + (float) (t[4] - pitch) * (float) k);
            ent_T.setFloat(e, body + (float) wrapDegrees(t[5] - body) * (float) k);
            ent_ag.setFloat(e, ent_ag.getFloat(e) + ((float) t[6] - ent_ag.getFloat(e)) * 0.4f);
            ent_ah.setFloat(e, ent_ah.getFloat(e) + ((float) t[7] - ent_ah.getFloat(e)) * 0.4f);
            hurtTime.setInt(e, (int) t[8]);
            entDeath.setInt(e, (int) t[9]);
            entFire.setInt(e, t[10] > 0 ? 300 : 0);
            healthField.setInt(e, (int) t[11]);
        }
    }

    /** The server hands a mob over (its owner left): the copy becomes the real mob of this game, under the same number. */
    private static void onMobAdopt(Object world, TWConnection.Pkt p) throws Exception {
        if (p.i2 < 0 || p.i2 >= mobCtors.length || p.i2 >= TNT_KIND) {
            return;
        }
        removeMobCopies(p.i1);
        Object mob = mobCtors[p.i2].newInstance(world);
        entSetPos.invoke(mob, p.x, p.y, p.z);
        rotYaw.setFloat(mob, p.yaw);
        rotPitch.setFloat(mob, p.pitch);
        ent_T.setFloat(mob, (float) p.mx);
        healthField.setInt(mob, Math.max(1, p.i5 >> 8));
        adoptNumber = p.i1;
        try {
            worldAddEntity.invoke(world, mob);
        } finally {
            adoptNumber = 0;
        }
        if (DEBUG) {
            System.out.println("[Infdev MP] took over the mob #" + p.i1);
        }
    }

    /** The server gives the mob to the player who is nearer to it: the real mob goes (no death), a copy comes from his states. */
    private static void onMobRelease(int number) throws Exception {
        for (java.util.Iterator<java.util.Map.Entry<Object, int[]>> it = ownMobs.entrySet().iterator(); it.hasNext();) {
            java.util.Map.Entry<Object, int[]> en = it.next();
            if (en.getValue()[0] == number) {
                Object mob = en.getKey();
                it.remove(); // before it is dead: the tick must not tell the server that the mob is gone
                entDead.setBoolean(mob, true);
                if (DEBUG) {
                    System.out.println("[Infdev MP] gave the mob #" + number + " to a nearer player");
                }
                return;
            }
        }
    }

    /** Another player hit the copy of a mob of this game: the real mob is hurt. */
    private static void onMobHit(TWConnection.Pkt p) throws Exception {
        for (java.util.Map.Entry<Object, int[]> en : ownMobs.entrySet()) {
            if (en.getValue()[0] == p.i1 && en.getValue()[1] < TNT_KIND && !entDead.getBoolean(en.getKey())) {
                Object attacker = null;
                synchronized (remotes) {
                    Remote r = remotes.get(p.i3);
                    if (r != null) {
                        attacker = r.entity;
                    }
                }
                if (DEBUG) {
                    System.out.println("[Infdev MP] the mob #" + p.i1 + " was hit for " + p.i2);
                }
                entHurt.invoke(en.getKey(), attacker, p.i2);
                return;
            }
        }
    }

    /** The first mob copy that a ray from (ox, oy, oz) in direction (dx, dy, dz) meets within max: {number, distance}, or null. */
    private static double[] aimAtMobCopy(double ox, double oy, double oz, double dx, double dy, double dz, double max)
            throws Exception {
        double[] best = null;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz};
        for (java.util.Map.Entry<Integer, Object> en : mobCopies.entrySet()) {
            double[] box = boxOf(en.getValue());
            double tmin = 0, tmax = max;
            boolean hit = true;
            for (int i = 0; i < 3 && hit; i++) {
                if (Math.abs(d[i]) < 1e-9) {
                    hit = o[i] >= box[i] && o[i] <= box[i + 3];
                } else {
                    double t1 = (box[i] - o[i]) / d[i], t2 = (box[i + 3] - o[i]) / d[i];
                    tmin = Math.max(tmin, Math.min(t1, t2));
                    tmax = Math.min(tmax, Math.max(t1, t2));
                    hit = tmin <= tmax;
                }
            }
            if (hit && (best == null || tmin < best[1])) {
                best = new double[]{en.getKey(), tmin};
            }
        }
        return best;
    }

    /** The bounding box of an entity: min x, y, z, max x, y, z. */
    private static double[] boxOf(Object e) throws Exception {
        Object b = entBox.get(e);
        return new double[]{boxFields[0].getDouble(b), boxFields[1].getDouble(b), boxFields[2].getDouble(b),
                boxFields[3].getDouble(b), boxFields[4].getDouble(b), boxFields[5].getDouble(b)};
    }

    /** A number with the lower 20 bits zero means all mobs of that player. */
    private static void removeMobCopies(int number) {
        if ((number & 0xFFFFF) != 0) {
            mobCopies.remove(number);
            mobTargets.remove(number);
            return;
        }
        for (java.util.Iterator<Integer> it = mobCopies.keySet().iterator(); it.hasNext();) {
            int key = it.next();
            if ((key >>> 20) == (number >>> 20)) {
                it.remove();
                mobTargets.remove(key);
            }
        }
    }

    private static void mobReset() {
        ownMobs.clear();
        mobCopies.clear();
        mobTargets.clear();
        synchronized (mobPending) {
            mobPending.clear();
        }
    }

    // ----- arrows ----------------------------------------------------------------------------------------------

    private static Class<?> arrowC, livingC316;
    private static Field arrowStuck, arrowShooter;
    private static final java.util.List<Object> ownArrows = new java.util.ArrayList<Object>();

    /**
     * The shown players are not in the world's entity list, so the arrows of this game fly through them. The arrows
     * shot here are followed: one that crosses another player's body hurts that player (the server applies the damage).
     */
    private static void arrowTick(TWConnection c) throws Exception {
        if (ownArrows.isEmpty()) {
            return;
        }
        for (java.util.Iterator<Object> it = ownArrows.iterator(); it.hasNext();) {
            Object a = it.next();
            if (entDead.getBoolean(a) || arrowStuck.getBoolean(a)) {
                it.remove();
                continue;
            }
            double[] o = {ent_A.getDouble(a), ent_B.getDouble(a), ent_C.getDouble(a)};
            double[] d = {posX.getDouble(a) - o[0], posY.getDouble(a) - o[1], posZ.getDouble(a) - o[2]};
            int hitId = Integer.MIN_VALUE;
            synchronized (remotes) {
                for (java.util.Map.Entry<Integer, Remote> en : remotes.entrySet()) {
                    Object e = en.getValue().entity;
                    double feet = posY.getDouble(e) - ent_u.getFloat(e);
                    double[] lo = {posX.getDouble(e) - 0.5, feet - 0.1, posZ.getDouble(e) - 0.5};
                    double[] hi = {posX.getDouble(e) + 0.5, feet + 1.9, posZ.getDouble(e) + 0.5};
                    double tmin = 0, tmax = 1;
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
                    if (hit) {
                        hitId = en.getKey();
                        break;
                    }
                }
            }
            if (hitId == Integer.MIN_VALUE && !mobCopies.isEmpty()) {
                double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                if (len > 1e-6) {
                    double[] mh = aimAtMobCopy(o[0], o[1], o[2], d[0] / len, d[1] / len, d[2] / len, len);
                    if (mh != null) {
                        c.sendMobHit((int) mh[0], 4);
                        entDead.setBoolean(a, true);
                        it.remove();
                        continue;
                    }
                }
            }
            if (hitId != Integer.MIN_VALUE) {
                if (DEBUG) {
                    System.out.println("[Infdev MP] arrow hit player " + hitId);
                }
                c.sendAttack(hitId, 4);
                entDead.setBoolean(a, true);
                it.remove();
            }
        }
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
    private static int regrabs;

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

    // ----- falling sand and gravel -------------------------------------------------------------------------

    /**
     * Hook at the start of the sand block's own fall (it moves the block to the lowest free cell at once). In a multiplayer
     * game the server lets the block fall by cells, with gravity, and the changes arrive like any other block change; the game's
     * own fall must not run in this world.
     * @return true if the fall is left to the server (the game's own code does not run)
     */
    public static boolean sandFall(Object world, int x, int y, int z) {
        return conn != null;
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
                try {
                    // this build has no session id in its settings object (the session check is gone)
                    Object session = settings.getClass().getField("c").get(settings);
                    if (session != null) {
                        System.setProperty("thewellodev.token", String.valueOf(session));
                    }
                } catch (NoSuchFieldException none) {
                    // the player has no token: only servers with online-mode=false accept him
                }
            }
            System.out.println("[Infdev MP] connecting to " + host + ":" + port + " as " + user);
            conn = new TWConnection(host, port, user, GameVersion.INF_20100325_1640.protocol);
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
            dropTick(c);
            mobTick(c);
            mobCopyTick();
            arrowTick(c);
            containerTick(mc, world, c);
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
            String wp = System.getProperty("thewellodev.testwalk");
            double step = wp.indexOf('.') > 0 ? Double.parseDouble(wp) : 0.2; // a value such as 1.2 is the speed in blocks per tick
            setPosRot.invoke(player, posX.getDouble(player) + step, walkY, posZ.getDouble(player),
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

        String testDrop = System.getProperty("thewellodev.testdrop");
        if (testDrop != null && !hold) {
            // test aid (id,count): an item falls two blocks ahead of the player, like a dropped block
            System.clearProperty("thewellodev.testdrop");
            String[] v = testDrop.split(",");
            Object st = stackCtor2.newInstance(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()));
            Object it = itemEntityCtor.newInstance(world, posX.getDouble(player), posY.getDouble(player) + 1,
                    posZ.getDouble(player) + 2, st);
            entMotY.setDouble(it, 0.2);
            worldAddEntity.invoke(world, it);
        }

        if (System.getProperty("thewellodev.testarrow") != null && !hold && !remotes.isEmpty()) {
            // test aid: shoots an arrow at the first other player (like a bow would)
            System.clearProperty("thewellodev.testarrow");
            Object target = remotes.values().iterator().next().entity;
            Object arrow = arrowC.getConstructor(worldClass, livingC316).newInstance(world, player);
            double dx = posX.getDouble(target) - posX.getDouble(player);
            double dy = posY.getDouble(target) - posY.getDouble(player);
            double dz = posZ.getDouble(target) - posZ.getDouble(player);
            arrowC.getMethod("a", double.class, double.class, double.class, float.class, float.class)
                    .invoke(arrow, dx, dy, dz, 1.5f, 1f);
            worldAddEntity.invoke(world, arrow);
        }

        String lateMove = System.getProperty("thewellodev.testlatemove");
        if (lateMove != null && !hold) {
            // test aid (seconds,dx,dz): moves the own player once, that many seconds after the login
            String[] v = lateMove.split(",");
            if (System.currentTimeMillis() - loginTime > Long.parseLong(v[0].trim()) * 1000L) {
                System.clearProperty("thewellodev.testlatemove");
                setPosRot.invoke(player, posX.getDouble(player) + Double.parseDouble(v[1].trim()),
                        posY.getDouble(player) - ent_u.getFloat(player), posZ.getDouble(player)
                        + Double.parseDouble(v[2].trim()), rotYaw.getFloat(player), 0f);
            }
        }

        String testChest = System.getProperty("thewellodev.testchest");
        if (testChest != null && !hold) {
            // test aid (dx,dy,dz,itemId,count): places a chest, opens it through the game's own click, puts items in
            // it and closes the window (the server must then keep them and show them to the other players)
            String[] v = testChest.split(",");
            int bx = (int) Math.floor(posX.getDouble(player)) + Integer.parseInt(v[0].trim());
            int by = (int) Math.floor(posY.getDouble(player)) + Integer.parseInt(v[1].trim());
            int bz = (int) Math.floor(posZ.getDouble(player)) + Integer.parseInt(v[2].trim());
            long age = System.currentTimeMillis() - loginTime;
            int cid = v.length > 5 ? Integer.parseInt(v[5].trim()) : 54; // an optional sixth value: 61 makes it a furnace
            if (chestStep == 0 && age > 4000) {
                setBlock.invoke(world, bx, by, bz, cid);
                chestStep = 1;
                chestStepAt = System.currentTimeMillis();
            } else if (chestStep == 1 && System.currentTimeMillis() - chestStepAt > 1500) {
                Object block = ((Object[]) blockTable.get(null))[cid];
                blockUse.invoke(block, world, bx, by, bz, player);
                chestStep = 2;
                chestStepAt = System.currentTimeMillis();
            } else if (chestStep == 2 && heldLocks != null && screenField.get(mc) != null
                    && System.currentTimeMillis() - chestStepAt > 1500) {
                Object tile = tileGet.invoke(world, bx, by, bz);
                Object[] slots = (Object[]) (cid == 61 ? furnaceSlots : chestSlots).get(tile);
                slots[0] = stackCtor2.newInstance(Integer.parseInt(v[3].trim()), Integer.parseInt(v[4].trim()));
                if (cid == 61) {
                    slots[1] = stackCtor2.newInstance(263, 5); // fuel: coal
                }
                System.out.println("[Infdev MP] test chest: put items in, closing the window");
                setScreen.invoke(mc, new Object[]{null});
                chestStep = 3;
                System.clearProperty("thewellodev.testchest");
            }
        }

        String testExplode = System.getProperty("thewellodev.testexplode");
        if (testExplode != null && !hold && System.currentTimeMillis() - loginTime > 6000) {
            // test aid (dx,dy,dz,power): an explosion near the player, through the game's own explosion method
            System.clearProperty("thewellodev.testexplode");
            String[] v = testExplode.split(",");
            double ex = posX.getDouble(player) + Double.parseDouble(v[0].trim());
            double ey = posY.getDouble(player) + Double.parseDouble(v[1].trim());
            double ez = posZ.getDouble(player) + Double.parseDouble(v[2].trim());
            worldClass.getMethod("a", Class.forName("net.minecraft.a.c.b", true, worldClass.getClassLoader()), double.class,
                    double.class, double.class, float.class).invoke(world, null, ex, ey, ez, Float.parseFloat(v[3].trim()));
        }

        String testMob = System.getProperty("thewellodev.testmob");
        if (testMob != null && !hold && System.currentTimeMillis() - loginTime > 6000) {
            // test aid (kind,dx,dz): a mob appears near the player, like the one an arrow makes (kind 0 to 5)
            System.clearProperty("thewellodev.testmob");
            String[] v = testMob.split(",");
            int testKind = Integer.parseInt(v[0].trim());
            Object mob = testKind == TNT_KIND ? mobCtors[testKind].newInstance(world, (float) posX.getDouble(player),
                    (float) posY.getDouble(player) + 3f, (float) posZ.getDouble(player)) : mobCtors[testKind].newInstance(world);
            setPosRot.invoke(mob, posX.getDouble(player) + Double.parseDouble(v[1].trim()), posY.getDouble(player) + (v.length > 3 ? Double.parseDouble(v[3].trim()) : 0),
                    posZ.getDouble(player) + Double.parseDouble(v[2].trim()), 0f, 0f);
            worldAddEntity.invoke(world, mob);
        }

        String testMobHit = System.getProperty("thewellodev.testmobhit");
        if (testMobHit != null && !hold && !mobCopies.isEmpty() && System.currentTimeMillis() - loginTime > 9000) {
            // test aid (damage): hits the first copy of another player's mob, like a click or an arrow would
            System.clearProperty("thewellodev.testmobhit");
            conn.sendMobHit(mobCopies.keySet().iterator().next(), Integer.parseInt(testMobHit.trim()));
        }

        String testBreak = System.getProperty("thewellodev.testbreak");
        if (testBreak != null && gameMode != -1 && System.currentTimeMillis() - loginTime > 9000) {
            // test aid (dx,dy,dz): one click on a block through the game's own digging controller (the creative mode breaks it
            // at once, the survival mode only starts digging, the spectator does nothing)
            System.clearProperty("thewellodev.testbreak");
            String[] v = testBreak.split(",");
            int bx = (int) Math.floor(posX.getDouble(player)) + Integer.parseInt(v[0].trim());
            int by = (int) Math.floor(posY.getDouble(player)) + Integer.parseInt(v[1].trim());
            int bz = (int) Math.floor(posZ.getDouble(player)) + Integer.parseInt(v[2].trim());
            Object controller = mc.getClass().getField("a").get(mc);
            int before = ((Integer) getBlock.invoke(world, bx, by, bz)).intValue();
            controller.getClass().getMethod("a", int.class, int.class, int.class).invoke(controller, bx, by, bz);
            System.out.println("[Infdev MP] test break at " + bx + " " + by + " " + bz + " (mode " + gameMode + "): block " + before
                    + " -> " + getBlock.invoke(world, bx, by, bz));
        }
        String testPlace = System.getProperty("thewellodev.testplace");
        if (testPlace != null) {
            // test aid (dx,dy,dz,id): sets one block near the player through the game's own set-block method,
            // like a player placing it (the server only accepts blocks within the player's reach)
            System.clearProperty("thewellodev.testplace");
            // several blocks can be given, separated by ";": one is set in each tick
            for (String entry : testPlace.split(";")) {
                String[] v = entry.split(",");
                testQueue.add(new int[]{(int) Math.floor(posX.getDouble(player)) + Integer.parseInt(v[0].trim()),
                        (int) Math.floor(posY.getDouble(player)) + Integer.parseInt(v[1].trim()),
                        (int) Math.floor(posZ.getDouble(player)) + Integer.parseInt(v[2].trim()),
                        Integer.parseInt(v[3].trim()), v.length > 4 ? Integer.parseInt(v[4].trim()) : -1});
            }
        }
        if (!testQueue.isEmpty()) {
            int[] q = testQueue.poll();
            System.out.println("[Infdev MP] test block " + q[3] + " at " + q[0] + " " + q[1] + " " + q[2]);
            setBlock.invoke(world, q[0], q[1], q[2], q[3]);
            if (q[4] >= 0) {
                setMeta.invoke(world, q[0], q[1], q[2], q[4]); // optional metadata
            }
        }
        String hashBox = System.getProperty("thewellodev.testhash");
        if (hashBox != null && !hold) {
            // test aid (x1,y1,z1,x2,y2,z2,first seconds): a hash of the blocks and their data in the box, every 10 seconds
            String[] v = hashBox.split(",");
            long age = System.currentTimeMillis() - loginTime;
            long firstAt = Long.parseLong(v[6].trim()) * 1000L;
            if (age >= firstAt && (age - firstAt) / 10000L >= testHashesDone) {
                testHashesDone++;
                long h = 17, lh = 17;
                for (int hx = Integer.parseInt(v[0].trim()); hx <= Integer.parseInt(v[3].trim()); hx++) {
                    for (int hz = Integer.parseInt(v[2].trim()); hz <= Integer.parseInt(v[5].trim()); hz++) {
                        Object hc = chunkMap.get(((long) ((hx >> 4) & 0x0FFFFFFF) << 32) | (((hz >> 4) & 0x0FFFFFFF) & 0xFFFFFFFFL));
                        byte[] hs = hc == null ? null : (byte[]) nibbleBytes.get(chunkSky.get(hc));
                        byte[] hb = hc == null ? null : (byte[]) nibbleBytes.get(chunkBlockLight.get(hc));
                        for (int hy = Integer.parseInt(v[1].trim()); hy <= Integer.parseInt(v[4].trim()); hy++) {
                            h = h * 31 + ((Integer) getBlock.invoke(world, hx, hy, hz)).intValue() * 16
                                    + ((Integer) getMeta.invoke(world, hx, hy, hz)).intValue();
                            if (hs != null) {
                                int hi = ((hx & 15) << 11) | ((hz & 15) << 7) | hy;
                                lh = lh * 31 + (((hs[hi >> 1] >> ((hi & 1) * 4)) & 15) * 16 + ((hb[hi >> 1] >> ((hi & 1) * 4)) & 15));
                            }
                        }
                    }
                }
                System.out.println("[Infdev MP] HASH " + h + " LIGHT " + lh);
            }
        }
        String countBlocks = System.getProperty("thewellodev.testcount");
        if (countBlocks != null && !hold) {
            // test aid (x1,y1,z1,x2,y2,z2,id,seconds): prints how many blocks of that id this game has in the box, every
            // 10 seconds after the first delay
            String[] v = countBlocks.split(",");
            long age = System.currentTimeMillis() - loginTime;
            long firstAt = Long.parseLong(v[7].trim()) * 1000L;
            if (age >= firstAt && (age - firstAt) / 10000L >= testCountsDone) {
                testCountsDone++;
                int n = 0;
                for (int cx = Integer.parseInt(v[0].trim()); cx <= Integer.parseInt(v[3].trim()); cx++) {
                    for (int cy = Integer.parseInt(v[1].trim()); cy <= Integer.parseInt(v[4].trim()); cy++) {
                        for (int cz = Integer.parseInt(v[2].trim()); cz <= Integer.parseInt(v[5].trim()); cz++) {
                            if (((Integer) getBlock.invoke(world, cx, cy, cz)).intValue() == Integer.parseInt(v[6].trim())) {
                                n++;
                            }
                        }
                    }
                }
                System.out.println("[Infdev MP] count of block " + v[6].trim() + " = " + n);
            }
        }
        String lightDump = System.getProperty("thewellodev.testlightdump");
        if (lightDump != null && !hold && System.currentTimeMillis() - loginTime > Long.parseLong(lightDump.split(",")[4].trim()) * 1000L) {
            // test aid (x1,z1,x2,z2,seconds after the login): prints the sky light and block light columns of the received chunks
            System.clearProperty("thewellodev.testlightdump");
            String[] v = lightDump.split(",");
            int lx1 = Integer.parseInt(v[0].trim()), lz1 = Integer.parseInt(v[1].trim());
            int lx2 = Integer.parseInt(v[2].trim()), lz2 = Integer.parseInt(v[3].trim());
            for (int lx = lx1; lx <= lx2; lx++) {
                for (int lz = lz1; lz <= lz2; lz++) {
                    int gx = (lx >> 4) & 0x0FFFFFFF, gz = (lz >> 4) & 0x0FFFFFFF;
                    Object ch = chunkMap.get(((long) gx << 32) | (gz & 0xFFFFFFFFL));
                    if (ch == null) {
                        continue;
                    }
                    byte[] sky = (byte[]) nibbleBytes.get(chunkSky.get(ch));
                    byte[] blk = (byte[]) nibbleBytes.get(chunkBlockLight.get(ch));
                    StringBuilder sb = new StringBuilder();
                    for (int yy = 0; yy < 128; yy++) {
                        int idx = ((lx & 15) << 11) | ((lz & 15) << 7) | yy;
                        int sv = (sky[idx >> 1] >> ((idx & 1) * 4)) & 15, bv = (blk[idx >> 1] >> ((idx & 1) * 4)) & 15;
                        sb.append(Integer.toHexString(sv)).append(Integer.toHexString(bv)).append(' ');
                    }
                    System.out.println("LIGHT " + lx + " " + lz + " " + sb);
                }
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
                if (DEBUG) {
                    System.out.println("[Infdev MP] player gone: #" + p.i1);
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
            case Protocol.DROP_SPAWN:
                onDropSpawn(world, p);
                break;
            case Protocol.TILE_DATA:
                onTileData(world, p);
                break;
            case Protocol.LOCK_RESULT:
                onLockResult(world, p.i1, p.i2, p.i3, p.b);
                break;
            case Protocol.DROP_REMOVE:
                onDropRemove(p);
                break;
            case Protocol.MOB_STATE:
                onMobState(world, p);
                break;
            case Protocol.MOB_ADOPT:
                onMobAdopt(world, p);
                break;
            case Protocol.MOB_HIT:
                onMobHit(p);
                break;
            case Protocol.MOB_RELEASE:
                onMobRelease(p.i1);
                break;
            case Protocol.DROP_RESYNC:
                onDropResync(c);
                break;
            case Protocol.DROP_CLAIM_RESULT:
                onClaimResult(p.i1, p.i2);
                break;
            case Protocol.SLOT:
                onSlot(p);
                break;
            case Protocol.MOB_REMOVE:
                removeMobCopies(p.i1);
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
        dropReset();
        mobReset();
        containerReset();
        ownArrows.clear();
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
        markDirty(world, cx * 16, 0, cz * 16, cx * 16 + 15, 127, cz * 16 + 15);
    }

    /** Tells the world's listeners (the renderer) that a region changed (this build has no world method for it). */
    private static void markDirty(Object world, int x1, int y1, int z1, int x2, int y2, int z2) throws Exception {
        if (DEBUG && !dirtyLogged) {
            dirtyLogged = true;
            System.out.println("[Infdev MP] world listeners: " + ((List<?>) worldListeners.get(world)).size());
        }
        for (Object l : ((List<?>) worldListeners.get(world)).toArray()) {
            listenerDirty.invoke(l, x1, y1, z1, x2, y2, z2);
        }
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
        Class<?> worldC = Class.forName("net.minecraft.a.a.g", true, cl);
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
        worldProvider = access(worldC, "u");
        worldTime = access(worldC, "l");
        worldTiles = worldC.getField("a");
        getBlock = worldC.getMethod("a", int.class, int.class, int.class);
        getMeta = worldC.getMethod("d", int.class, int.class, int.class);
        setMeta = worldC.getMethod("b", int.class, int.class, int.class, int.class);
        worldListeners = access(worldC, "t");
        Class<?> listenerC = Class.forName("net.minecraft.a.a.b", true, cl);
        listenerDirty = listenerC.getMethod("a", int.class, int.class, int.class, int.class, int.class, int.class);
        chunkCtor = chunkClass.getConstructor(worldC, byte[].class, int.class, int.class);
        chunkMeta = access(chunkClass, "d");
        chunkSky = access(chunkClass, "e");
        chunkBlockLight = access(chunkClass, "f");
        chunkHeight = access(chunkClass, "g");
        chunkLowest = access(chunkClass, "h");
        nibbleBytes = access(nibbleC, "a");
        posX = playerC.getField("g");
        posY = playerC.getField("h");
        posZ = playerC.getField("i");
        rotYaw = playerC.getField("m");
        rotPitch = playerC.getField("n");
        setPosRot = playerC.getMethod("b", double.class, double.class, double.class, float.class, float.class);
        setBlock = worldC.getMethod("c", int.class, int.class, int.class, int.class);
        Class<?> livingC = Class.forName("net.minecraft.a.c.e", true, cl);
        Class<?> humanC = Class.forName("net.minecraft.a.c.e.a", true, cl);
        playerCtor = humanC.getConstructor(worldC);
        nameField = access(humanC, "R");
        ent_u = playerC.getField("u");
        noClipField = playerC.getField("E");
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
        camX = access(rmC, "b"); // (static in this build)
        camY = access(rmC, "c");
        camZ = access(rmC, "d");
        camYaw = rmC.getField("g");
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
        tileGet = worldC.getMethod("h", int.class, int.class, int.class);
        Class<?> blockC = Class.forName("net.minecraft.a.a.c.x", true, cl);
        blockTable = blockC.getField("c");
        blockUse = blockC.getMethod("a", worldC, int.class, int.class, int.class, humanC);
        chestTileC = Class.forName("net.minecraft.a.a.c.a.c", true, cl);
        furnaceTileC = Class.forName("net.minecraft.a.a.c.a.b", true, cl);
        chestSlots = access(chestTileC, "e");
        furnaceSlots = access(furnaceTileC, "e");
        furnaceInts = new Field[]{access(furnaceTileC, "f"), access(furnaceTileC, "g"), access(furnaceTileC, "h")};
        stackFieldCount = stackC.getField("a");
        stackFieldDamage = stackC.getField("d");
        entSetPos = playerC.getDeclaredMethod("a", double.class, double.class, double.class);
        entSetPos.setAccessible(true);
        entHurt = playerC.getMethod("a", playerC, int.class);
        entDeath = livingC.getField("X");
        entFire = playerC.getField("I");
        entBox = playerC.getField("q");
        Class<?> boxC = Class.forName("net.minecraft.a.d.b", true, cl);
        boxFields = new Field[]{boxC.getField("a"), boxC.getField("b"), boxC.getField("c"), boxC.getField("d"),
                boxC.getField("e"), boxC.getField("f")};
        mobClasses = new Class<?>[MOB_CLASS_NAMES.length];
        mobCtors = new Constructor<?>[MOB_CLASS_NAMES.length];
        for (int i = 0; i < MOB_CLASS_NAMES.length; i++) {
            mobClasses[i] = Class.forName(MOB_CLASS_NAMES[i], true, cl);
            if (i == TNT_KIND) {
                tntFuse = mobClasses[i].getField("a");
            }
            if (i == ARROW_KIND) {
                arrowShake = mobClasses[i].getField("a");
            }
            mobCtors[i] = i == TNT_KIND ? mobClasses[i].getConstructor(worldC, float.class, float.class, float.class)
                    : i == ARROW_KIND ? mobClasses[i].getConstructor(worldC, livingC) : mobClasses[i].getConstructor(worldC);
        }
        arrowC = Class.forName("net.minecraft.a.c.d.a", true, cl);
        livingC316 = livingC;
        arrowStuck = access(arrowC, "Q");
        arrowShooter = access(arrowC, "R");
        itemEntityC = Class.forName("net.minecraft.a.c.c.b", true, cl);
        itemEntityCtor = itemEntityC.getConstructor(worldC, double.class, double.class, double.class, stackC);
        stackOfItem = itemEntityC.getField("a");
        itemDelay = itemEntityC.getField("N");
        worldAddEntity = worldC.getMethod("a", playerC);
        entDead = playerC.getField("t");
        entMotX = playerC.getField("j");
        entMotY = playerC.getField("k");
        entMotZ = playerC.getField("l");
    }

    private static Field access(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
