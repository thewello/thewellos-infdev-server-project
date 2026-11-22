package net.thewellodev.server.api321;

import net.thewellodev.api.World;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api321.Block321;
import net.thewellodev.api321.Location321;
import net.thewellodev.api321.Player321;
import net.thewellodev.api321.World321;

import java.util.ArrayList;
import java.util.List;

/** The world as the inf-20100321 API shows it. */
final class World321Impl implements World321 {

    private final Api321 api;
    final World handle;

    World321Impl(Api321 api, World handle) {
        this.api = api;
        this.handle = handle;
    }

    public String getName() {
        return handle.getName();
    }

    public long getSeed() {
        return handle.getSeed();
    }

    public Block321 getBlockAt(int x, int y, int z) {
        return api.block(handle.getBlockAt(x, y, z));
    }

    public Block321 getBlockAt(Location321 loc) {
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

    public Location321 getSpawnLocation() {
        return api.location(handle.getSpawnLocation());
    }

    public void setSpawnLocation(int x, int y, int z) {
        handle.setSpawnLocation(x, y, z);
    }

    public List<? extends Player321> getPlayers() {
        List<Player321> list = new ArrayList<Player321>();
        for (Player p : handle.getPlayers()) {
            list.add(api.player(p));
        }
        return list;
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return handle.isChunkLoaded(chunkX, chunkZ);
    }

    public boolean isValidBlock(int typeId) {
        return typeId >= 0 && typeId <= 62;
    }

    public void save() {
        handle.save();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof World321Impl && ((World321Impl) o).handle == handle;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(handle);
    }

    public List<? extends Player321> getNearbyPlayers(Location321 center, double radius) {
        List<Player321> list = new ArrayList<Player321>();
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
