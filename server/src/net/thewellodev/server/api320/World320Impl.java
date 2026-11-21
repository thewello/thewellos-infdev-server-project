package net.thewellodev.server.api320;

import net.thewellodev.api.World;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api320.Block320;
import net.thewellodev.api320.Location320;
import net.thewellodev.api320.Player320;
import net.thewellodev.api320.World320;

import java.util.ArrayList;
import java.util.List;

/** The world as the inf-20100320 API shows it. */
final class World320Impl implements World320 {

    private final Api320 api;
    final World handle;

    World320Impl(Api320 api, World handle) {
        this.api = api;
        this.handle = handle;
    }

    public String getName() {
        return handle.getName();
    }

    public long getSeed() {
        return handle.getSeed();
    }

    public Block320 getBlockAt(int x, int y, int z) {
        return api.block(handle.getBlockAt(x, y, z));
    }

    public Block320 getBlockAt(Location320 loc) {
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

    public Location320 getSpawnLocation() {
        return api.location(handle.getSpawnLocation());
    }

    public void setSpawnLocation(int x, int y, int z) {
        handle.setSpawnLocation(x, y, z);
    }

    public List<? extends Player320> getPlayers() {
        List<Player320> list = new ArrayList<Player320>();
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
        return o instanceof World320Impl && ((World320Impl) o).handle == handle;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(handle);
    }
}
