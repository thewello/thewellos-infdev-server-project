package net.thewellodev.server.api316;

import net.thewellodev.api.World;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api316.Block316;
import net.thewellodev.api316.Location316;
import net.thewellodev.api316.Player316;
import net.thewellodev.api316.World316;

import java.util.ArrayList;
import java.util.List;

/** The world as the inf-20100316 API shows it. */
final class World316Impl implements World316 {

    private final Api316 api;
    final World handle;

    World316Impl(Api316 api, World handle) {
        this.api = api;
        this.handle = handle;
    }

    public String getName() {
        return handle.getName();
    }

    public long getSeed() {
        return handle.getSeed();
    }

    public Block316 getBlockAt(int x, int y, int z) {
        return api.block(handle.getBlockAt(x, y, z));
    }

    public Block316 getBlockAt(Location316 loc) {
        return getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int getBlockTypeIdAt(int x, int y, int z) {
        return handle.getBlockTypeIdAt(x, y, z);
    }

    public boolean setBlockTypeId(int x, int y, int z, int typeId) {
        return handle.setBlockTypeId(x, y, z, typeId);
    }

    public int getBlockDataAt(int x, int y, int z) {
        return handle.getBlockDataAt(x, y, z);
    }

    public boolean setBlockTypeIdAndData(int x, int y, int z, int typeId, int data) {
        return handle.setBlockTypeIdAndData(x, y, z, typeId, data);
    }

    public long getTime() {
        return handle.getTime();
    }

    public void setTime(long time) {
        handle.setTime(time);
    }

    public int getHighestBlockYAt(int x, int z) {
        return handle.getHighestBlockYAt(x, z);
    }

    public Location316 getSpawnLocation() {
        return api.location(handle.getSpawnLocation());
    }

    public void setSpawnLocation(int x, int y, int z) {
        handle.setSpawnLocation(x, y, z);
    }

    public List<? extends Player316> getPlayers() {
        List<Player316> list = new ArrayList<Player316>();
        for (Player p : handle.getPlayers()) {
            list.add(api.player(p));
        }
        return list;
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return handle.isChunkLoaded(chunkX, chunkZ);
    }

    public boolean isValidBlock(int typeId) {
        return typeId >= 0 && typeId <= 51;
    }

    public void save() {
        handle.save();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof World316Impl && ((World316Impl) o).handle == handle;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(handle);
    }

    public List<? extends Player316> getNearbyPlayers(Location316 center, double radius) {
        List<Player316> list = new ArrayList<Player316>();
        for (Player p : handle.getNearbyPlayers(api.toServer(center), radius)) {
            list.add(api.player(p));
        }
        return list;
    }

    public int fill(int x1, int y1, int z1, int x2, int y2, int z2, int typeId, int data) {
        if (!isValidBlock(typeId)) {
            throw new IllegalArgumentException("Block " + typeId + " does not exist in this build");
        }
        return handle.fill(x1, y1, z1, x2, y2, z2, typeId, data);
    }

    public int getDayLength() {
        return handle.getDayLength();
    }

    public boolean isDay() {
        return handle.isDay();
    }

    public boolean isNight() {
        return handle.isNight();
    }

    public int getBlockLightAt(int x, int y, int z) {
        return handle.getBlockLightAt(x, y, z);
    }

    public int getSkyLightAt(int x, int y, int z) {
        return handle.getSkyLightAt(x, y, z);
    }
}
