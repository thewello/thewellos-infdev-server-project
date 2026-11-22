package net.thewellodev.server.api227;

import net.thewellodev.api.World;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api227.Block227;
import net.thewellodev.api227.Location227;
import net.thewellodev.api227.Player227;
import net.thewellodev.api227.World227;

import java.util.ArrayList;
import java.util.List;

/** The world as the inf-20100227-1433 API shows it. */
final class World227Impl implements World227 {

    private final Api227 api;
    final World handle;

    World227Impl(Api227 api, World handle) {
        this.api = api;
        this.handle = handle;
    }

    public String getName() {
        return handle.getName();
    }

    public long getSeed() {
        return handle.getSeed();
    }

    public Block227 getBlockAt(int x, int y, int z) {
        return api.block(handle.getBlockAt(x, y, z));
    }

    public Block227 getBlockAt(Location227 loc) {
        return getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int getBlockTypeIdAt(int x, int y, int z) {
        return handle.getBlockTypeIdAt(x, y, z);
    }

    public boolean setBlockTypeId(int x, int y, int z, int typeId) {
        return handle.setBlockTypeId(x, y, z, typeId);
    }

    public int getHighestBlockYAt(int x, int z) {
        return handle.getHighestBlockYAt(x, z);
    }

    public Location227 getSpawnLocation() {
        return api.location(handle.getSpawnLocation());
    }

    public void setSpawnLocation(int x, int y, int z) {
        handle.setSpawnLocation(x, y, z);
    }

    public List<? extends Player227> getPlayers() {
        List<Player227> list = new ArrayList<Player227>();
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
        return o instanceof World227Impl && ((World227Impl) o).handle == handle;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(handle);
    }

    public List<? extends Player227> getNearbyPlayers(Location227 center, double radius) {
        List<Player227> list = new ArrayList<Player227>();
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
