import net.thewellodev.protocol.Protocol;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * The centre of the client mod. The patched Infdev classes call the static
 * methods in here (see ClientPatcher).
 */
public class TWClient {

    public static aw mc;
    public static TWConnection conn;
    public static fe mpWorld;
    public static TWChunkProvider provider;
    public static boolean creatingWorld;
    public static String serverAddress = "";

    /** Whether the player is in the middle of an action of their own (e.g. a click). */
    private static int actionDepth;
    /** True while applying a change from the server (so it is not sent back). */
    private static boolean applyingRemote;
    private static final LinkedHashSet<Long> pendingChanges = new LinkedHashSet<Long>();

    public static final Map<Integer, TWRemotePlayer> players = new HashMap<Integer, TWRemotePlayer>();
    private static final List<Protocol_SpawnInfo> pendingSpawns = new ArrayList<Protocol_SpawnInfo>();

    // Item sync
    private static final Map<Integer, gx> netItems = new HashMap<Integer, gx>();
    private static final java.util.IdentityHashMap<gx, Integer> itemIds = new java.util.IdentityHashMap<gx, Integer>();
    private static final Map<Integer, Long> pickupRequested = new HashMap<Integer, Long>();
    private static final List<TWConnection.Pkt> pendingItems = new ArrayList<TWConnection.Pkt>();
    private static boolean spawningNet;
    public static int myEntityId = -1;
    private static int lastHeld = -2;
    private static int lastHealth = -1;
    private static boolean deadReported;
    private static final Map<Integer, TWConnection.Pkt> itemPkts = new HashMap<Integer, TWConnection.Pkt>();

    // Block-breaking stages: entityId -> {x, y, z, stage, lastUpdateMs}
    private static final Map<Integer, long[]> remoteDigs = new HashMap<Integer, long[]>();
    private static int lastDigX, lastDigY, lastDigZ, lastDigStage = -1;

    // Creative flight
    private static boolean digErrorLogged;
    public static boolean flying;
    private static int lastJumpPress = -100;

    private static boolean rendererRegistered;
    private static double lastX, lastY, lastZ;
    private static float lastYaw, lastPitch;
    private static int ticksSincePos;
    private static boolean holdPosition;
    private static double holdX, holdY, holdZ;
    private static float holdYaw, holdPitch;
    private static int tickCounter;

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    public static void connect(aw m, String host, int port, String username) {
        mc = m;
        disconnectQuietly();
        serverAddress = port > 0 ? host + ":" + port : host;
        conn = new TWConnection(host, port, username);
        conn.start();
        m.a(new TWGuiConnecting(host, port));
    }

    public static boolean isConnected() {
        return conn != null;
    }

    public static void disconnectQuietly() {
        if (conn != null) {
            conn.close("Connection closed");
            conn = null;
        }
        cleanupWorldState();
    }

    private static void cleanupWorldState() {
        players.clear();
        TWMobs.clear();
        TWTiles.clear();
        lastInvSig = "";
        pendingSpawns.clear();
        pendingChanges.clear();
        mpWorld = null;
        provider = null;
        holdPosition = false;
        netItems.clear();
        itemIds.clear();
        itemPkts.clear();
        remoteDigs.clear();
        lastDigStage = -1;
        flying = false;
        pickupRequested.clear();
        pendingItems.clear();
        lastHeld = -2;
        lastHealth = -1;
        deadReported = false;
    }

    /** If the connection is lost, leave the world and show the reason. */
    private static void handleLostConnection(String reason) {
        TWConnection c = conn;
        conn = null;
        if (c != null) {
            c.close(reason);
        }
        boolean inMp = mpWorld != null;
        cleanupWorldState();
        if (inMp && mc.e != null) {
            mc.a((fe) null);
        }
        mc.a(new TWGuiDisconnected("Connection lost", reason));
    }

    // ------------------------------------------------------------------
    // Hook'lar
    // ------------------------------------------------------------------

    /** Called at the start of aw.i() (every game tick, 20 times per second). */
    public static void preTick(aw m) {
        mc = m;
        TWMouse.guard();
        actionDepth = 0;
        if (mpWorld != null && m.e == mpWorld) {
            // In multiplayer the menu (ESC) does not pause the game; otherwise the other players freeze
            m.m = false;
        }
        flushChanges();
        if (!rendererRegistered) {
            registerRenderer();
        }
        String auto = System.getProperty("thewellodev.autoconnect");
        if (auto != null && conn == null && m.p instanceof kb) {
            System.clearProperty("thewellodev.autoconnect");
            int colon = auto.lastIndexOf(':');
            String host = colon > 0 ? auto.substring(0, colon) : auto;
            int port = colon > 0 ? Integer.parseInt(auto.substring(colon + 1)) : Protocol.DEFAULT_PORT;
            connect(m, host, port, m.i != null ? m.i.b : "Player");
            return;
        }
        TWConnection c = conn;
        if (c == null) {
            return;
        }
        if (c.isClosed()) {
            handleLostConnection(c.getCloseReason());
            return;
        }
        try {
            int processed = 0;
            TWConnection.Pkt p;
            while (conn == c && (p = c.poll()) != null) {
                handlePacket(p);
                if (++processed > 400 && p.id == Protocol.CHUNK) {
                    break; // continue on the next tick
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            handleLostConnection("Client error: " + e);
            return;
        }
        if (conn != c || mpWorld == null || m.e != mpWorld || m.g == null) {
            return;
        }
        tickCounter++;
        guardPlayerPosition(m);
        try {
            trySpawnPending();
            TWMobs.tick();
            TWTiles.tick();
            TWMobs.rideTick(m, c);
            TWMobs.lockOptions(m);
        TWTiles.lockTick();
            if (tickCounter % 5 == 0) {
                sendInventoryIfChanged(m, c);
            }
        } catch (Throwable t) {
            fault("preTick", t); // a mod error must not crash the game
        }
        dd p = m.g;
        // Report the held item, health and death state to the server
        hw cur = p.b.e();
        int held = cur == null ? -1 : cur.c;
        if (held != lastHeld) {
            lastHeld = held;
            c.sendShort(Protocol.HELD_ITEM, held);
        }
        if (p.au != lastHealth) {
            lastHealth = p.au;
            c.sendShort(Protocol.HEALTH, Math.max(0, p.au));
        }
        if (p.au <= 0 && !deadReported) {
            deadReported = true;
            c.sendSimple(Protocol.DEATH);
        }
        // Block-breaking progress (so other players see the cracks)
        int stage = -1;
        int dx = lastDigX, dy = lastDigY, dz = lastDigZ;
        if (m.b instanceof bb && p.au > 0) {
            bb ctl = (bb) m.b;
            if (ctl.f > 0.0F) {
                stage = Math.min(9, (int) (ctl.f * 10.0F));
                dx = ctl.c;
                dy = ctl.d;
                dz = ctl.e;
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
        // Creative: no damage; double-tap space to fly
        boolean creative = m.b instanceof bt;
        if (!creative) {
            flying = false;
        } else if (p.au > 0) {
            p.au = 20;
            p.F = 0.0F;
        }
        if (flying && p.au > 0) {
            boolean keys = m.p == null;
            boolean up = keys && Keyboard.isKeyDown(m.y.n.b);
            boolean down = keys && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            p.n = up ? 0.42 : down ? -0.42 : 0.0;
            p.u = !up; // behave as if on the ground: horizontal speed matches walking
            p.F = 0.0F;
        }
        // If the chunk has not arrived yet, keep the player still (so it does not fall into the void)
        int pcx = floor(p.j) >> 4;
        int pcz = floor(p.l) >> 4;
        if (provider != null && !provider.a(pcx, pcz)) {
            if (!holdPosition) {
                holdPosition = true;
                holdX = p.j;
                holdY = p.k;
                holdZ = p.l;
                holdYaw = p.p;
                holdPitch = p.q;
            }
            p.b(holdX, holdY, holdZ, p.p, p.q);
            p.m = 0;
            p.n = 0;
            p.o = 0;
            p.F = 0;
            return;
        }
        holdPosition = false;
        ticksSincePos++;
        if (ticksSincePos >= 20 || Math.abs(p.j - lastX) > 0.01 || Math.abs(p.k - lastY) > 0.01
                || Math.abs(p.l - lastZ) > 0.01 || Math.abs(p.p - lastYaw) > 0.5 || Math.abs(p.q - lastPitch) > 0.5) {
            ticksSincePos = 0;
            lastX = p.j;
            lastY = p.k;
            lastZ = p.l;
            lastYaw = p.p;
            lastPitch = p.q;
            c.sendPosition(p.j, p.k, p.l, p.p, p.q, p.u);
        }
    }

    /** Called from the keyboard loop of aw.i() on every key event while the screen is closed. */
    public static void onKey(aw m) {
        if (conn == null || mpWorld == null || m.e != mpWorld || m.p != null) {
            return;
        }
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        int key = Keyboard.getEventKey();
        if (key == m.y.n.b && m.b instanceof bt) {
            // Double space: toggle flight
            if (tickCounter - lastJumpPress <= 7) {
                flying = !flying;
                lastJumpPress = -100;
                addChat(flying ? "&7Flight on (Space: up, Shift: down)" : "&7Flight off");
            } else {
                lastJumpPress = tickCounter;
            }
            return;
        }
        if (key == m.y.q.b) {
            m.a(new TWGuiChat(""));
        } else if (key == Keyboard.KEY_SLASH) {
            m.a(new TWGuiChat("/"));
        }
    }

    public static void actionBegin(aw m, int button) {
        actionDepth++;
    }

    public static void actionEnd(aw m, int button) {
        if (actionDepth > 0) {
            actionDepth--;
        }
        if (actionDepth == 0) {
            flushChanges();
        }
    }

    public static void actionBegin(aw m, int button, boolean b) {
        actionDepth++;
    }

    public static void actionEnd(aw m, int button, boolean b) {
        actionEnd(m, button);
    }

    /** Called at the start of aw.a(fe, String): the world is changing. */
    private static double goodX = Double.NaN, goodY, goodZ;
    private static boolean nanReported;

    private static boolean bad(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) || Math.abs(v) > 3.0E7;
    }

    /**
     * If the player's position or speed becomes NaN or infinite (e.g. a mob hit from the same point = division by zero), the game screen
     * the game screen freezes or crashes while drawing. The player is returned to the last valid position.
     */
    private static void guardPlayerPosition(aw m) {
        dd p = m.g;
        if (p == null) {
            return;
        }
        if (bad(p.j) || bad(p.k) || bad(p.l) || bad(p.m) || bad(p.n) || bad(p.o)) {
            if (!nanReported) {
                nanReported = true;
                System.out.println("Infdev MP: invalid player position/speed (" + p.j + "," + p.k + "," + p.l + " speed " + p.m + ","
                        + p.n + "," + p.o + ") - returning to the last valid position");
            }
            if (!Double.isNaN(goodX)) {
                p.b(goodX, goodY, goodZ, bad(p.p) ? 0.0F : p.p, bad(p.q) ? 0.0F : p.q);
            }
            p.m = 0;
            p.n = 0;
            p.o = 0;
            return;
        }
        goodX = p.j;
        goodY = p.k;
        goodZ = p.l;
    }

    /** ka.a(eq,int,dx,dz) (knockback): if the attacker is at exactly the same point, division by zero gives a NaN position; skip it. */
    public static boolean skipZeroKnockback(ka self, eq attacker, int dmg, double dx, double dz) {
        return dx * dx + dz * dz < 1.0E-8 || bad(dx) || bad(dz);
    }

    /**
     * Chunk render order: ltValue when d1 < d2, -ltValue when d1 > d2, 0 when equal (consistent comparator).
     * The old game's comparator does not return 0 for equal values, so Java 8's "Comparison method violates its general contract"
     * error crashed the game.
     */
    public static int cmpDist(float d1, float d2, int ltValue) {
        return d1 < d2 ? ltValue : (d1 > d2 ? -ltValue : 0);
    }

    /** Start of dd.a(kd) (chest GUI): first request the lock from the server; call the real method once it is granted. */
    public static boolean openChest(dd p, kd inv) {
        return TWTiles.requestOpen(p, inv instanceof bk ? (bk) inv : null, 0);
    }

    /** Start of dd.a(el) (furnace GUI). */
    public static boolean openFurnace(dd p, el furnace) {
        return TWTiles.requestOpen(p, furnace, 1);
    }

    /** End of aw.i() (after every game tick, before the frame is drawn). */
    public static void postTick(aw m) {
        if (mpWorld != null && m.e == mpWorld) {
            guardPlayerPosition(m);
        }
    }

    private static long lastFaultAt;

    /** Unexpected error in the mod code: logged (once per second) instead of crashing the game. */
    static void fault(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastFaultAt > 5000) {
            lastFaultAt = now;
            System.out.println("Infdev MP error (" + where + "): " + t);
            t.printStackTrace(System.out);
        }
    }

    private static String lastInvSig = "";
    private static long lastInvSent;

    /** If the inventory changes, send a summary to the server (at most 4 times per second); the server catches invalid items. */
    /** Sets an inventory slot on the server's request (itemId < 0 empties it). The slot order matches the INVENTORY packet. */
    private static void setInventorySlot(int slot, int id, int count, int damage) {
        if (mc == null || mc.g == null || slot < 0) {
            return;
        }
        hw[][] groups = {mc.g.b.a, mc.g.b.b};
        for (hw[] g : groups) {
            if (slot < g.length) {
                g[slot] = id < 0 || count <= 0 || !validItem(id) ? null : new hw(id, Math.min(count, 64), damage);
                lastInvSig = ""; // the current inventory is sent to the server on the next tick
                return;
            }
            slot -= g.length;
        }
    }

    private static void sendInventoryIfChanged(aw m, TWConnection c) {
        if (m.g == null || tickCounter - lastInvSent < 5) {
            return;
        }
        hw[][] groups = {m.g.b.a, m.g.b.b};
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (hw[] g : groups) {
            n += g.length;
        }
        int[] t = new int[n * 3];
        int k = 0;
        for (hw[] g : groups) {
            for (hw s : g) {
                t[k++] = s == null ? -1 : s.c;
                t[k++] = s == null ? 0 : Math.max(0, Math.min(255, s.a));
                t[k++] = s == null ? 0 : Math.max(-32768, Math.min(32767, s.d));
            }
        }
        for (int v : t) {
            sb.append(v).append(',');
        }
        String sig = sb.toString();
        if (!sig.equals(lastInvSig)) {
            lastInvSig = sig;
            lastInvSent = tickCounter;
            c.sendInventory(t);
        }
    }

    public static void onChangeWorld(aw m, fe newWorld, String msg) {
        if (mpWorld != null && newWorld != mpWorld) {
            // The player left the menu or moved to another world
            if (conn != null) {
                conn.sendDisconnect("Left the game");
                conn.close("Left the game");
                conn = null;
            }
            if (m.e == mpWorld && mpWorld != null) {
                try {
                    mpWorld.a(true, (jd) null); // save level.dat (inventory)
                } catch (Throwable ignored) {
                }
            }
            cleanupWorldState();
        }
    }

    public static void onBlockSet(boolean ok, fe w, int x, int y, int z, int a, int b) {
        onBlockSet(ok, w, x, y, z, a);
    }

    public static void onBlockSet(boolean ok, fe w, int x, int y, int z, int a) {
        if (!ok || applyingRemote || actionDepth <= 0 || w != mpWorld || conn == null) {
            return;
        }
        if (y < 0 || y >= 128) {
            return;
        }
        pendingChanges.add(packPos(x, y, z));
    }

    private static void flushChanges() {
        if (pendingChanges.isEmpty()) {
            return;
        }
        if (conn == null || mpWorld == null) {
            pendingChanges.clear();
            return;
        }
        for (Long k : pendingChanges) {
            long v = k.longValue();
            int x = unpackX(v), y = unpackY(v), z = unpackZ(v);
            conn.sendBlockChange(x, y, z, mpWorld.a(x, y, z), mpWorld.e(x, y, z));
        }
        pendingChanges.clear();
    }

    /** Start of fe.a(File): gives the multiplayer world its own chunk provider. */
    public static ch createChunkProvider(fe w, File dir) {
        if (!creatingWorld) {
            return null;
        }
        provider = new TWChunkProvider(w);
        return provider;
    }

    public static boolean skipTickUpdates(fe w, boolean b) {
        return w == mpWorld && mpWorld != null;
    }

    /** Simple tick for the multiplayer world, used instead of fe.i(). */
    public static boolean worldTick(fe w) {
        if (w != mpWorld || mpWorld == null) {
            return false;
        }
        if (w.f != null && !w.r.contains(w.f)) {
            w.a(w.f);
        }
        int sky = w.a(1.0F);
        if (sky != w.c) {
            w.c = sky;
            for (int i = 0; i < w.A.size(); i++) {
                ((bv) w.A.get(i)).e();
            }
        }
        w.b++;
        if (w.b % 1200L == 0L) {
            try {
                w.a(false, (jd) null);
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    public static boolean skipSpawner(ck spawner, fe w) {
        return w == mpWorld && mpWorld != null;
    }

    /** fe.a(eq): in multiplayer, items dropped on the ground are not created locally; they are sent to the server. */
    public static boolean onSpawnEntity(fe w, eq e) {
        if (w == mpWorld && mpWorld != null && !TWMobs.creating && e instanceof jp) {
            if (conn != null) {
                conn.sendCart(floor(e.j), floor(e.k), floor(e.l));
            }
            return true; // the minecart is created on the server
        }
        if (w == mpWorld && mpWorld != null && e instanceof eo && mc != null && ((eo) e).ab == mc.g) {
            if (conn != null) {
                conn.sendSimple(Protocol.FIRE_ARROW); // the arrow is created on the server and seen by everyone
            }
            return true;
        }
        if (w == mpWorld && mpWorld != null && !TWMobs.creating && e instanceof cy) {
            cy pt = (cy) e;
            if (conn != null && pt.b != null) {
                conn.sendPainting(pt.Y, pt.Z, pt.aa, pt.a, pt.b.ordinal());
            }
            return true; // the game's own server creates these; they arrive here through the sync
        }
        if (w == mpWorld && mpWorld != null && !TWMobs.creating && (e instanceof db || e instanceof ij)) {
            return true; // TNT and falling sand are created on the server and synced here
        }
        if (w != mpWorld || mpWorld == null || spawningNet || !(e instanceof gx) || conn == null) {
            return false;
        }
        gx it = (gx) e;
        if (it.a != null && it.a.a > 0) {
            conn.sendDropItem(it.j, it.k, it.l, it.m, it.n, it.o, it.a.c, it.a.a, it.a.d);
        }
        return true;
    }

    /** Inside cb.b(F), after the particles: draws the block-breaking cracks of other players. */
    public static void renderDigs(m rg, gi viewer, float partial) {
        if (remoteDigs.isEmpty() || mpWorld == null || rg == null || viewer == null) {
            return;
        }
        long now = System.currentTimeMillis();
        float saved = rg.i;
        try {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            Iterator<long[]> it = remoteDigs.values().iterator();
            while (it.hasNext()) {
                long[] d = it.next();
                if (now - d[4] > 3000) {
                    it.remove();
                    continue;
                }
                int x = (int) d[0], y = (int) d[1], z = (int) d[2];
                if (mpWorld.a(x, y, z) == 0) {
                    continue;
                }
                double ddx = x + 0.5 - viewer.j, ddy = y + 0.5 - viewer.k, ddz = z + 0.5 - viewer.l;
                if (ddx * ddx + ddy * ddy + ddz * ddz > 64 * 64) {
                    continue;
                }
                rg.i = (d[3] + 1) / 10.0F - 0.01F;
                rg.a(viewer, new hb(x, y, z, 0, bo.b(x, y, z)), 0, (hw) null, partial);
            }
        } catch (Throwable t) {
            remoteDigs.clear();
            if (!digErrorLogged) {
                digErrorLogged = true;
                System.err.println("Infdev MP: could not draw the block-breaking crack");
                t.printStackTrace();
            }
        } finally {
            rg.i = saved;
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    /** Whether an item on the ground fits in the inventory (prevents the pickup loop when the inventory is full). */
    private static boolean canFit(hw s) {
        if (s == null || mc.g == null) {
            return false;
        }
        hw[] inv = mc.g.b.a;
        for (int i = 0; i < inv.length; i++) {
            hw x = inv[i];
            if (x == null) {
                return true;
            }
            if (x.c == s.c && x.d == s.d && x.a < x.c() && x.a < 64) {
                return true;
            }
        }
        return false;
    }

    /** TWChunkProvider: before a chunk is unloaded, hold the shared items in it until they are handled. */
    static void onChunkUnloaded(int cx, int cz) {
        Iterator<Map.Entry<Integer, gx>> it = netItems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, gx> e = it.next();
            gx item = e.getValue();
            if ((floor(item.j) >> 4) != cx || (floor(item.l) >> 4) != cz) {
                continue;
            }
            TWConnection.Pkt p = itemPkts.get(e.getKey());
            it.remove();
            itemIds.remove(item);
            item.l();
            if (p != null) {
                p.x = item.j;
                p.y = item.k;
                p.z = item.l;
                p.mx = p.my = p.mz = 0;
                pendingItems.add(p);
            }
        }
    }

    private static void startAuth(final String serverId) {
        final TWConnection c = conn;
        final String token = mc.i != null ? mc.i.c : null;
        if (serverId == null || !serverId.matches("[A-Za-z0-9-]{1,40}")) {
            c.sendAuthResponse(false, "Invalid verification request");
            return;
        }
        Thread t = new Thread("Infdev MP auth") {
            public void run() {
                try {
                    String name = TWAuth.join(token, serverId);
                    c.status = "Account verified: " + name;
                    c.sendAuthResponse(true, "");
                } catch (Exception e) {
                    c.status = e.getMessage();
                    c.sendAuthResponse(false, e.getMessage());
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    /** fe.a(): spawn search is not done in multiplayer (the server decides). */
    public static boolean skipSpawnSearch(fe w) {
        return w == mpWorld && mpWorld != null;
    }

    /** gx.a(gi): when the player walks into an item on the ground, first ask the server for permission. */
    public static boolean onItemCollide(gx it, gi player) {
        if (mpWorld == null || conn == null) {
            return false;
        }
        Integer id = itemIds.get(it);
        if (id == null) {
            return it.f == mpWorld;
        }
        if (player != mc.g || it.X > 0 || player.au <= 0 || !canFit(it.a)) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long t = pickupRequested.get(id);
        if (t == null || now - t > 1000) {
            pickupRequested.put(id, now);
            conn.sendSimple(Protocol.PICKUP_ITEM, id);
        }
        return true;
    }

    /** End of aw.o(): the player respawned. */
    public static void onRespawn(aw m) {
        try {
            if (conn != null && mpWorld != null && m.e == mpWorld) {
                deadReported = false;
                lastHealth = -1;
                lastHeld = -2;
                holdPosition = false;
                lastInvSig = "";
                TWMobs.onLocalRespawn();
                conn.sendSimple(Protocol.RESPAWN);
            }
        } catch (Throwable t) {
            fault("onRespawn", t);
        }
    }

    /** The local player hit a TWRemotePlayer. */
    static void sendAttack(TWRemotePlayer target, int damage) {
        if (conn != null) {
            conn.sendAttack(target.netId, damage);
        }
    }

    private static void giveLocal(int id, int count, int damage) {
        if (mc.g == null || !validItem(id)) {
            return;
        }
        hw st = new hw(id, count, damage);
        // ht.a(hw): true = fully added (the stack object may have been placed in an empty slot, its count is not reset!)
        boolean added = mc.g.b.a(st);
        if (!added && st.a > 0 && conn != null) {
            // inventory full: drop the rest on the ground
            conn.sendDropItem(mc.g.j, mc.g.k - 0.3, mc.g.l, 0, 0.1, 0, id, st.a, damage);
        }
    }

    static boolean validItem(int id) {
        if (id <= 0 || id >= 32000) {
            return false;
        }
        try {
            return new hw(id, 1, 0).a() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean spawnNetItem(TWConnection.Pkt p) {
        if (mpWorld == null || provider == null) {
            return true;
        }
        if (!provider.a(floor(p.x) >> 4, floor(p.z) >> 4)) {
            return false;
        }
        if (!validItem(p.i2)) {
            return true;
        }
        gx it = new gx(mpWorld, p.x, p.y, p.z, new hw(p.i2, p.i3, p.i4));
        it.m = p.mx;
        it.n = p.my;
        it.o = p.mz;
        it.X = 10;
        spawningNet = true;
        try {
            mpWorld.a(it);
        } finally {
            spawningNet = false;
        }
        netItems.put(p.i1, it);
        itemIds.put(it, p.i1);
        itemPkts.put(p.i1, p);
        return true;
    }

    private static void removeNetItem(int netId, int collector) {
        Iterator<TWConnection.Pkt> pit = pendingItems.iterator();
        while (pit.hasNext()) {
            if (pit.next().i1 == netId) {
                pit.remove();
            }
        }
        pickupRequested.remove(netId);
        itemPkts.remove(netId);
        gx it = netItems.remove(netId);
        if (it == null) {
            return;
        }
        itemIds.remove(it);
        if (collector == myEntityId && mc.g != null) {
            if (it.a != null) {
                mpWorld.a(it, "random.pop", 0.2F, ((it.O.nextFloat() - it.O.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                mc.g.b(it);
                giveLocal(it.a.c, it.a.a, it.a.d);
            }
        } else {
            TWRemotePlayer rp = players.get(collector);
            if (rp != null) {
                rp.b(it);
            }
        }
        it.l();
    }

    public static boolean skipSpawnerBlock(cs spawner) {
        return mpWorld != null && spawner.a == mpWorld;
    }

    public static void titleInit(kb screen) {
        if (screen.e.size() > 1) {
            iq b = (iq) screen.e.get(1);
            b.e = true;
            b.c = "Multiplayer";
        }
    }

    public static boolean titleAction(kb screen, iq button) {
        if (button.d == 2) {
            screen.b.a(new TWGuiConnect(screen));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Packet handling (main thread)
    // ------------------------------------------------------------------

    private static void handlePacket(TWConnection.Pkt p) throws IOException {
        switch (p.id) {
            case Protocol.LOGIN:
                handleLogin(p);
                break;
            case Protocol.CHAT:
                addChat(p.s);
                break;
            case Protocol.TIME:
                if (mpWorld != null) {
                    mpWorld.b = p.l;
                }
                break;
            case Protocol.CHUNK:
                if (provider != null) {
                    byte[] raw = Protocol.inflate(p.data, Protocol.CHUNK_RAW_SIZE);
                    provider.receive(p.i1, p.i2, raw);
                }
                break;
            case Protocol.UNLOAD_CHUNK:
                if (provider != null) {
                    provider.unload(p.i1, p.i2);
                }
                break;
            case Protocol.BLOCK_CHANGE:
                applyBlock(p.i1, p.i2, p.i3, p.i4 & 255, p.i5 & 15);
                break;
            case Protocol.SPAWN_PLAYER: {
                Protocol_SpawnInfo si = new Protocol_SpawnInfo();
                si.id = p.i1;
                si.name = p.s;
                si.x = p.x;
                si.y = p.y;
                si.z = p.z;
                si.yaw = p.yaw;
                si.pitch = p.pitch;
                removePlayer(si.id);
                pendingSpawns.add(si);
                trySpawnPending();
                break;
            }
            case Protocol.MOVE_PLAYER: {
                TWRemotePlayer rp = players.get(p.i1);
                if (rp != null) {
                    rp.setTarget(p.x, p.y, p.z, p.yaw, p.pitch);
                } else {
                    for (Protocol_SpawnInfo si : pendingSpawns) {
                        if (si.id == p.i1) {
                            si.x = p.x;
                            si.y = p.y;
                            si.z = p.z;
                            si.yaw = p.yaw;
                            si.pitch = p.pitch;
                        }
                    }
                }
                break;
            }
            case Protocol.REMOVE_PLAYER:
                removePlayer(p.i1);
                break;
            case Protocol.TELEPORT:
                if (mc.g != null) {
                    teleportLocal(p.x, p.y, p.z, p.yaw, p.pitch);
                }
                break;
            case Protocol.KEEP_ALIVE:
                conn.sendKeepAlive(p.i1);
                break;
            case Protocol.AUTH_REQUEST:
                startAuth(p.s);
                break;
            case Protocol.DIG_PROGRESS:
                if (p.i5 < 0) {
                    remoteDigs.remove(p.i1);
                } else {
                    remoteDigs.put(p.i1, new long[]{p.i2, p.i3, p.i4, p.i5, System.currentTimeMillis()});
                }
                break;
            case Protocol.SPAWN_ITEM:
                if (!spawnNetItem(p)) {
                    pendingItems.add(p);
                }
                break;
            case Protocol.REMOVE_ITEM:
                removeNetItem(p.i1, p.i2);
                break;
            case Protocol.DAMAGE:
                if (mc.g != null && mc.g.au > 0) {
                    eq attacker = players.get(p.i1);
                    if (attacker == null) {
                        attacker = TWMobs.entity(p.i1);
                    }
                    if (mc.e != null) {
                        mc.e.g = TWMobs.difficulty; // gi.a() scales damage by difficulty
                    }
                    mc.g.a(attacker, p.i2);
                }
                break;
            case Protocol.TILE_DATA:
                TWTiles.onData(p);
                break;
            case Protocol.MOUNT:
                TWMobs.onMount(p.i1, p.i2);
                break;
            case Protocol.SET_SLOT:
                setInventorySlot(p.i1, p.i2, p.i3, p.i4);
                break;
            case Protocol.LOCK_RESULT:
                TWTiles.onLockResult(p.i1, p.i2, p.i3, p.b);
                break;
            case Protocol.SOUND:
                if (mpWorld != null) {
                    mpWorld.a(p.x, p.y, p.z, p.s, p.yaw, p.pitch);
                }
                break;
            case Protocol.PARTICLE:
                if (mpWorld != null) {
                    mpWorld.a(p.s, p.x, p.y, p.z, p.mx, p.my, p.mz);
                }
                break;
            case Protocol.SPAWN_MOB:
                TWMobs.onSpawn(p);
                break;
            case Protocol.MOVE_MOB:
                TWMobs.onMove(p);
                break;
            case Protocol.REMOVE_MOB:
                TWMobs.onRemove(p.i1, p.b);
                break;
            case Protocol.DIFFICULTY:
                TWMobs.difficulty = p.i1;
                break;
            case Protocol.HURT_ANIMATION: {
                if (p.i1 >= Protocol.MOB_ID_BASE) {
                    TWMobs.onHurt(p.i1);
                    break;
                }
                TWRemotePlayer rp = players.get(p.i1);
                if (rp != null) {
                    rp.aw = 10;
                    rp.ax = 10;
                }
                break;
            }
            case Protocol.HEALTH:
                if (mc.g != null && mc.g.au > 0) {
                    int h = Math.max(0, Math.min(20, p.i1));
                    if (h == 0) {
                        // Kill through real damage (death screen, items scatter)
                        mc.g.U = 0;
                        mc.g.a((eq) null, 1000);
                    } else {
                        mc.g.au = h;
                    }
                    lastHealth = mc.g.au;
                }
                break;
            case Protocol.GIVE_ITEM:
                giveLocal(p.i1, p.i2, p.i3);
                break;
            case Protocol.GAME_MODE:
                if (p.i1 == 1) {
                    mc.b = new bt(mc);
                    addChat("&eGame mode: &fCreative");
                } else {
                    mc.b = new bb(mc);
                    addChat("&eGame mode: &fSurvival");
                }
                if (mc.e != null) {
                    mc.b.a(mc.e);
                }
                break;
            case Protocol.HELD_ITEM: {
                TWRemotePlayer rp = players.get(p.i1);
                if (rp != null) {
                    rp.b.a[rp.b.c] = validItem(p.i2) ? new hw(p.i2, 1, 0) : null;
                } else {
                    for (Protocol_SpawnInfo si : pendingSpawns) {
                        if (si.id == p.i1) {
                            si.held = p.i2;
                        }
                    }
                }
                break;
            }
            case Protocol.DISCONNECT:
                handleLostConnection(p.s);
                break;
            default:
                break;
        }
    }

    private static void handleLogin(TWConnection.Pkt p) throws IOException {
        TWMobs.difficulty = p.i2;
        myEntityId = p.i1;
        File saves = new File(aw.b(), "saves");
        String folder = "TheWelloDev_" + serverAddress.replaceAll("[^A-Za-z0-9._-]", "_");
        File dir = new File(saves, folder);
        dir.mkdirs();
        File levelDat = new File(dir, "level.dat");
        if (!levelDat.exists()) {
            writeLevelDat(levelDat, p.l2, floor(p.x), floor(p.y), floor(p.z), p.l);
        }
        cleanupWorldState();
        creatingWorld = true;
        fe w;
        try {
            w = new fe(saves, folder, p.l2);
        } finally {
            creatingWorld = false;
        }
        if (provider == null) {
            throw new IOException("Could not create the chunk provider");
        }
        // Our minimal level.dat has no player data: do not try to read an empty tag
        if (w.D != null && !w.D.a("Pos")) {
            w.D = null;
        }
        // If the player logged out while burning, the save keeps "Fire": do not keep burning after rejoining
        if (w.D != null) {
            if (w.D.a("Fire")) {
                w.D.a("Fire", (short) -20);
            }
            if (w.D.a("Air")) {
                w.D.a("Air", (short) 300);
            }
            if (w.D.a("FallDistance")) {
                w.D.a("FallDistance", 0.0F);
            }
        }
        // If the player logged out on the death screen, the local save has health 0: do not die instantly after rejoining
        if (w.D != null && w.D.a("Health") && w.D.c("Health") <= 0) {
            w.D.a("Health", (short) 20);
            w.D.a("DeathTime", (short) 0);
            w.D.a("HurtTime", (short) 0);
        }
        mpWorld = w;
        w.b = p.l;
        mc.a(w, "Connecting to the server...");
        mc.a((dc) null);
        teleportLocal(p.x, p.y, p.z, p.yaw, p.pitch);
        holdPosition = false;
        addChat("&eInfdev MP: &fConnected to " + serverAddress + ".");
        if (p.s != null && p.s.length() > 0) {
            addChat(p.s);
        }
    }

    private static void teleportLocal(double x, double y, double z, float yaw, float pitch) {
        dd pl = mc.g;
        if (pl == null) {
            return;
        }
        pl.b(x, y, z, yaw, pitch);
        pl.m = 0;
        pl.n = 0;
        pl.o = 0;
        pl.F = 0;
        holdX = x;
        holdY = y;
        holdZ = z;
        holdYaw = yaw;
        holdPitch = pitch;
        lastX = Double.NaN;
    }

    private static void applyBlock(int x, int y, int z, int id, int meta) {
        fe w = mpWorld;
        if (w == null || provider == null || y < 0 || y >= 128) {
            return;
        }
        if (!provider.a(x >> 4, z >> 4)) {
            return; // chunk not loaded yet; the server will send the full chunk
        }
        if (w.a(x, y, z) == id && w.e(x, y, z) == meta) {
            return;
        }
        applyingRemote = true;
        try {
            if (w.a(x, y, z, id, meta)) {
                for (int i = 0; i < w.A.size(); i++) {
                    ((bv) w.A.get(i)).a(x, y, z);
                }
            }
        } finally {
            applyingRemote = false;
        }
    }

    private static void trySpawnPending() {
        if (mpWorld == null || provider == null) {
            return;
        }
        if (!pendingItems.isEmpty() && tickCounter % 10 == 0) {
            Iterator<TWConnection.Pkt> pit = pendingItems.iterator();
            while (pit.hasNext()) {
                if (spawnNetItem(pit.next())) {
                    pit.remove();
                }
            }
        }
        if (pendingSpawns.isEmpty()) {
            return;
        }
        Iterator<Protocol_SpawnInfo> it = pendingSpawns.iterator();
        while (it.hasNext()) {
            Protocol_SpawnInfo si = it.next();
            if (!provider.a(floor(si.x) >> 4, floor(si.z) >> 4)) {
                continue;
            }
            TWRemotePlayer rp = new TWRemotePlayer(mpWorld, si.name);
            rp.netId = si.id;
            if (validItem(si.held)) {
                rp.b.a[rp.b.c] = new hw(si.held, 1, 0);
            }
            rp.b(si.x, si.y, si.z, si.yaw, si.pitch);
            rp.setTarget(si.x, si.y, si.z, si.yaw, si.pitch);
            mpWorld.a(rp);
            players.put(si.id, rp);
            it.remove();
        }
    }

    private static void removePlayer(int id) {
        remoteDigs.remove(id);
        TWRemotePlayer rp = players.remove(id);
        if (rp != null) {
            rp.l();
        }
        Iterator<Protocol_SpawnInfo> it = pendingSpawns.iterator();
        while (it.hasNext()) {
            if (it.next().id == id) {
                it.remove();
            }
        }
    }

    public static void addChat(String msg) {
        if (msg == null) {
            return;
        }
        System.out.println("[CHAT] " + msg.replaceAll("&[0-9a-fA-F]", ""));
        if (mc == null || mc.u == null) {
            return;
        }
        String[] lines = msg.split("\n");
        for (String line : lines) {
            for (String part : wrap(line, 64)) {
                mc.u.e.add(0, new ew(part));
            }
        }
        while (mc.u.e.size() > 50) {
            mc.u.e.remove(mc.u.e.size() - 1);
        }
    }

    private static List<String> wrap(String s, int max) {
        List<String> out = new ArrayList<String>();
        String color = "";
        while (s.length() > max) {
            int cut = s.lastIndexOf(' ', max);
            if (cut < max / 2) {
                cut = max;
            }
            String head = s.substring(0, cut);
            out.add(color + head);
            int ci = head.lastIndexOf('&');
            if (ci >= 0 && ci + 1 < head.length()) {
                color = head.substring(ci, ci + 2);
            }
            s = s.substring(cut).trim();
        }
        out.add(color + s);
        return out;
    }

    private static void registerRenderer() {
        try {
            if (fl.a != null) {
                TWPlayerRender r = new TWPlayerRender();
                r.a(fl.a);
                fl.a.n.put(TWRemotePlayer.class, r);
                rendererRegistered = true;
            }
        } catch (Throwable t) {
            rendererRegistered = true;
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }

    static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int unpackX(long v) {
        return (int) (v >> 38) << 6 >> 6;
    }

    static int unpackZ(long v) {
        return (int) ((v >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    static int unpackY(long v) {
        return (int) (v & 0xFFF);
    }

    /** Writes a minimal level.dat for the multiplayer world so the game does not search for a spawn. */
    private static void writeLevelDat(File f, long seed, int sx, int sy, int sz, long time) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        o.writeByte(10);
        o.writeUTF("");
        o.writeByte(10);
        o.writeUTF("Data");
        nbtLong(o, "RandomSeed", seed);
        nbtInt(o, "SpawnX", sx);
        nbtInt(o, "SpawnY", sy);
        nbtInt(o, "SpawnZ", sz);
        nbtLong(o, "Time", time);
        nbtLong(o, "SizeOnDisk", 0L);
        nbtLong(o, "LastPlayed", System.currentTimeMillis());
        o.writeByte(0); // end of the data
        o.writeByte(0); // end of the root
        o.flush();
        GZIPOutputStream gz = new GZIPOutputStream(new FileOutputStream(f));
        gz.write(bo.toByteArray());
        gz.close();
    }

    private static void nbtLong(DataOutputStream o, String n, long v) throws IOException {
        o.writeByte(4);
        o.writeUTF(n);
        o.writeLong(v);
    }

    private static void nbtInt(DataOutputStream o, String n, int v) throws IOException {
        o.writeByte(3);
        o.writeUTF(n);
        o.writeInt(v);
    }

    /** Spawn information waiting for a player whose chunk has not arrived yet. */
    static class Protocol_SpawnInfo {
        int id;
        String name;
        double x, y, z;
        float yaw, pitch;
        int held = -1;
    }
}
