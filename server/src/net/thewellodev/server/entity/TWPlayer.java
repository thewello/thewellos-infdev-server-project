package net.thewellodev.server.entity;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.ItemStack;
import net.thewellodev.api.entity.Player;
import net.thewellodev.server.TWServer;
import net.thewellodev.server.net.PlayerConnection;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

/** A player on the server. */
public class TWPlayer implements Player {

    /** Mobs sent to this player: id -> [x32, y32, z32, yaw, pitch, state, deathSent] */
    public final java.util.Map<Integer, int[]> visibleMobs = new java.util.HashMap<Integer, int[]>();
    public String lastMobAttacker;
    public long lastMobAttackAt;


    /** Infdev player eye height (difference between the entity y and the feet). */
    public static final double EYE_HEIGHT = 1.62;

    private final TWServer server;
    private final PlayerConnection connection;
    private final String name;
    private final int entityId;
    private String displayName;

    // Position: x, z block coordinates; y is the FEET level
    public double x, y, z;
    public float yaw, pitch;
    public boolean onGround;
    public boolean joined;

    /** Chunks sent to the client. */
    public final Set<Long> loadedChunks = new HashSet<Long>();
    /** Other players this player can see (entity ids). */
    public final Set<Integer> visiblePlayers = new HashSet<Integer>();

    public int health = 20;
    /** Vehicle or pig the player rides (network id of the mob), -1 = not riding */
    public int ridingNetId = -1;
    /** Whether the first health report after joining was accepted */
    public boolean healthSynced;
    /** Last food eaten: the health it gave and when (used to validate health gain). */
    public int lastFoodHeal;
    public long lastFoodTick = -1000;
    public long lastHealTick = -1000;
    /** Highest health expected after server-side damage, and the check time. */
    public int expectedMinHealth = 20;
    public long healthCheckTick = -1;
    public int trackedHits, trackedMaxDmg, windowStartHealth = 20;
    /** Inventory summary: total items and a leaky bucket. */
    public boolean dead;
    public int gameMode;
    public int heldItem = -1;
    /** Last inventory summary sent by the client: [id, amount, damage] triples, in slot order. */
    public volatile int[] invSnapshot = new int[0];
    /** Block changes the player may still send above the normal rate (after an explosion in its game), and until when. */
    /** Builds 316 and 320: the top bits of the numbers of this player's items and mobs, and where its numbers start. */
    public int dropSlot = -1;
    public int dropSeqStart;
    public int blockBurst;
    public long blockBurstUntil;
    /** Centre and radius of the explosion that the burst belongs to. */
    public double burstX, burstY, burstZ, burstRadius;
    /** The inventory saved for builds 227 and 313 (whose clients keep it): n times (id, count, damage), or null. */
    public volatile int[] savedInv;
    public TWPlayer lastAttacker;
    public long lastAttackedAt;
    public long lastAttackTick;

    /** PvP: the tick this player last took damage (damage cooldown). */
    public long lastHurtTick = -100;

    // Simple rate limiter: type -> {window start (ms), counter}
    private final java.util.Map<String, long[]> rates = new java.util.HashMap<String, long[]>();
    public int violations;
    public int chatSpamStrikes;

    /** Allows at most perSecond actions per second. @return true if allowed */
    public boolean allow(String kind, int perSecond) {
        long now = System.currentTimeMillis();
        long[] r = rates.get(kind);
        if (r == null) {
            r = new long[]{now, 0};
            rates.put(kind, r);
        }
        if (now - r[0] >= 1000) {
            r[0] = now;
            r[1] = 0;
        }
        return ++r[1] <= perSecond;
    }

    public int keepAliveToken;
    public long keepAliveSentAt;
    public int ping = -1;
    public int ticksSinceTeleport;

    public TWPlayer(TWServer server, PlayerConnection connection, String name, int entityId) {
        this.server = server;
        this.connection = connection;
        this.name = name;
        this.entityId = entityId;
        this.displayName = name;
    }

    public PlayerConnection getConnection() {
        return connection;
    }

    public static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public int getChunkX() {
        return Location.floor(x) >> 4;
    }

    public int getChunkZ() {
        return Location.floor(z) >> 4;
    }

    public boolean hasChunk(int cx, int cz) {
        return loadedChunks.contains(chunkKey(cx, cz));
    }

    // ---------------------------------------------------------------- API

    public int getEntityId() {
        return entityId;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String name) {
        this.displayName = name == null ? this.name : name;
    }

    public Location getLocation() {
        return new Location(server.getWorld(), x, y, z, yaw, pitch);
    }

    public World getWorld() {
        return server.getWorld();
    }

    public void teleport(Location l) {
        teleport(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public void teleport(double nx, double ny, double nz) {
        teleport(nx, ny, nz, yaw, pitch);
    }

    public void teleport(double nx, double ny, double nz, float nyaw, float npitch) {
        x = nx;
        y = ny;
        z = nz;
        yaw = nyaw;
        pitch = npitch;
        ticksSinceTeleport = 0;
        connection.sendTeleport(nx, ny + EYE_HEIGHT, nz, nyaw, npitch);
        server.onPlayerMoved(this);
    }

    public void kickPlayer(String reason) {
        server.kickPlayer(this, reason);
    }

    public boolean isOnline() {
        return joined && !connection.isClosed() && server.getPlayerExact(name) == this;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public InetSocketAddress getAddress() {
        return connection.getAddress();
    }

    public int getPing() {
        return ping;
    }

    public void chat(String message) {
        server.handleChat(this, message);
    }

    public void sendBlockChange(int bx, int by, int bz, int typeId, int data) {
        connection.sendBlockChange(bx, by, bz, typeId, data);
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int h) {
        health = Math.max(0, Math.min(20, h));
        connection.sendHealth(health);
    }

    public boolean isDead() {
        return dead;
    }

    public void giveItem(int typeId, int amount, int data) {
        if (!server.getWorld().isValidItem(typeId)) {
            throw new IllegalArgumentException("Invalid item id: " + typeId);
        }
        while (amount > 0) {
            int n = Math.min(64, amount);
            connection.sendGiveItem(typeId, n, data);
            amount -= n;
        }
    }

    public ItemStack[] getInventoryContents() {
        int[] s = invSnapshot;
        ItemStack[] out = new ItemStack[s.length / 3];
        for (int i = 0; i < out.length; i++) {
            if (s[i * 3] >= 0 && s[i * 3 + 1] > 0) {
                out[i] = new ItemStack(s[i * 3], s[i * 3 + 1], s[i * 3 + 2]);
            }
        }
        return out;
    }

    /** Slots the client reports: 36 hotbar and bag slots, then 4 armour slots. */
    public static final int INVENTORY_SLOTS = 40;

    /** Empties the whole inventory on the client. Used when a player joins a world for the first time. */
    public void clearInventory() {
        int[] empty = new int[INVENTORY_SLOTS * 3];
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            empty[i * 3] = -1;
            connection.sendSetSlot(i, -1, 0, 0);
        }
        invSnapshot = empty;
    }

    public void setInventorySlot(int slot, ItemStack item) {
        int[] s = invSnapshot;
        if (slot < 0 || slot * 3 + 2 >= s.length) {
            return;
        }
        int id = item == null ? -1 : item.getTypeId();
        int amount = item == null ? 0 : Math.max(1, Math.min(64, item.getAmount()));
        int data = item == null ? 0 : item.getData();
        int[] copy = s.clone();
        copy[slot * 3] = id;
        copy[slot * 3 + 1] = amount;
        copy[slot * 3 + 2] = data;
        invSnapshot = copy;
        connection.sendSetSlot(slot, id, amount, data);
    }

    public int getItemInHand() {
        return heldItem;
    }

    public int getGameMode() {
        return gameMode;
    }

    public void setGameMode(int mode) {
        gameMode = mode == 1 ? 1 : 0;
        connection.sendGameMode(gameMode);
    }

    public void sendMessage(String message) {
        if (message == null) {
            return;
        }
        connection.sendChat(message);
    }

    public boolean isOp() {
        return server.isOp(name);
    }

    public void setOp(boolean op) {
        server.setOp(name, op);
    }

    public boolean hasPermission(String permission) {
        return server.hasPermission(this, permission);
    }

    public Server getServer() {
        return server;
    }

    @Override
    public String toString() {
        return "TWPlayer{" + name + "}";
    }
}
