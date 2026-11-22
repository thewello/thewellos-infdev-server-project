package net.thewellodev.server.world;

import net.thewellodev.api.Location;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.server.TWServer;
import net.thewellodev.server.game.GameBridge;

import java.util.ArrayList;
import java.util.List;

/** API World implementation (the real Infdev world, through GameBridge). */
public class TWWorld implements World {

    private final TWServer server;
    private final GameBridge bridge;
    private final String name;

    public TWWorld(TWServer server, GameBridge bridge, String name) {
        this.server = server;
        this.bridge = bridge;
        this.name = name;
    }

    public GameBridge getBridge() {
        return bridge;
    }

    private void checkThread() {
        if (!server.isPrimaryThread()) {
            throw new IllegalStateException("The world can only be accessed from the main server thread (use the scheduler)");
        }
    }

    public String getName() {
        return name;
    }

    public long getSeed() {
        return bridge.getSeed();
    }

    public Block getBlockAt(int x, int y, int z) {
        return new TWBlock(this, x, y, z);
    }

    public Block getBlockAt(Location loc) {
        return getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int getBlockTypeIdAt(int x, int y, int z) {
        checkThread();
        if (y < 0 || y >= 128) {
            return 0;
        }
        return bridge.getBlockId(x, y, z);
    }

    public int getBlockDataAt(int x, int y, int z) {
        checkThread();
        if (y < 0 || y >= 128) {
            return 0;
        }
        return bridge.getBlockMeta(x, y, z);
    }

    public boolean setBlockTypeId(int x, int y, int z, int typeId) {
        return setBlockTypeIdAndData(x, y, z, typeId, 0);
    }

    public boolean setBlockTypeIdAndData(int x, int y, int z, int typeId, int data) {
        checkThread();
        if (y < 0 || y >= 128 || typeId < 0 || typeId > 255) {
            return false;
        }
        return bridge.setBlock(x, y, z, typeId, data & 15, true);
    }

    public int getHighestBlockYAt(int x, int z) {
        checkThread();
        return bridge.getHighestY(x, z);
    }

    public Location getSpawnLocation() {
        int[] s = bridge.getSpawn();
        return new Location(this, s[0] + 0.5, s[1], s[2] + 0.5);
    }

    public void setSpawnLocation(int x, int y, int z) {
        checkThread();
        bridge.setSpawn(x, y, z);
    }

    public long getTime() {
        return bridge.getTime();
    }

    public void setTime(long time) {
        checkThread();
        bridge.setTime(time);
        server.broadcastTime();
    }

    public List<? extends Player> getPlayers() {
        return new ArrayList<Player>(server.getOnlinePlayers());
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        checkThread();
        return bridge.isChunkLoaded(chunkX, chunkZ);
    }

    public int dropItem(Location location, int typeId, int amount, int data) {
        checkThread();
        if (!isValidItem(typeId) || amount <= 0) {
            return -1;
        }
        return server.spawnNetItem(location.getX(), location.getY(), location.getZ(), 0, 0.2, 0, typeId,
                Math.min(255, amount), data);
    }

    public int getItemCount() {
        checkThread();
        return server.getItemCount();
    }

    public int getItemCount(int chunkX, int chunkZ) {
        checkThread();
        return server.getItemCount(chunkX, chunkZ);
    }

    public int clearItems() {
        checkThread();
        return server.clearItems();
    }

    public int mergeItems(double radius) {
        checkThread();
        return server.mergeItems(radius);
    }

    public int getMobCount() {
        checkThread();
        return server.getLivingMobCount();
    }

    public int clearMobs() {
        checkThread();
        return server.killAllMobs();
    }

    public int trimMobs(int max) {
        checkThread();
        return server.trimMobs(max);
    }

    public int getTntCount() {
        checkThread();
        return server.getTntCount();
    }

    public int clearTnt() {
        checkThread();
        return server.clearTnt();
    }

    public int getMaxStackSize(int typeId) {
        checkThread();
        return server.getMaxStackSize(typeId);
    }

    public boolean isValidItem(int typeId) {
        return bridge.isValidItem(typeId);
    }

    public void save() {
        checkThread();
        bridge.save(true);
    }

    public List<? extends Player> getNearbyPlayers(Location center, double radius) {
        final double cx = center.getX(), cy = center.getY(), cz = center.getZ();
        double r2 = radius * radius;
        List<Player> list = new ArrayList<Player>();
        for (Player p : server.getOnlinePlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - cx, dy = l.getY() - cy, dz = l.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                list.add(p);
            }
        }
        java.util.Collections.sort(list, new java.util.Comparator<Player>() {
            public int compare(Player a, Player b) {
                Location la = a.getLocation(), lb = b.getLocation();
                double da = (la.getX() - cx) * (la.getX() - cx) + (la.getY() - cy) * (la.getY() - cy)
                        + (la.getZ() - cz) * (la.getZ() - cz);
                double db = (lb.getX() - cx) * (lb.getX() - cx) + (lb.getY() - cy) * (lb.getY() - cy)
                        + (lb.getZ() - cz) * (lb.getZ() - cz);
                return Double.compare(da, db);
            }
        });
        return list;
    }

    public int fill(int x1, int y1, int z1, int x2, int y2, int z2, int typeId, int data) {
        checkThread();
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), ay = Math.max(0, Math.min(y1, y2)),
                by = Math.min(127, Math.max(y1, y2)), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        long volume = (long) (bx - ax + 1) * (by - ay + 1) * (bz - az + 1);
        if (volume > 32768L) {
            throw new IllegalArgumentException("The box has " + volume + " blocks; the limit is 32768");
        }
        int changed = 0;
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                for (int y = ay; y <= by; y++) {
                    if (getBlockTypeIdAt(x, y, z) == typeId && getBlockDataAt(x, y, z) == data) {
                        continue;
                    }
                    if (setBlockTypeIdAndData(x, y, z, typeId, data)) {
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    public int getDayLength() {
        return "inf-20100313".equals(server.getGameVersion()) ? 12000 : 24000;
    }

    public boolean isDay() {
        long day = getDayLength();
        long t = getTime() % day;
        if (t < 0) {
            t += day;
        }
        return t < day * 13 / 24;
    }

    public boolean isNight() {
        return !isDay();
    }

    public int getBlockLightAt(int x, int y, int z) {
        checkThread();
        return bridge.getRawLight(x, y, z, false);
    }

    public int getSkyLightAt(int x, int y, int z) {
        checkThread();
        return bridge.getRawLight(x, y, z, true);
    }
}
