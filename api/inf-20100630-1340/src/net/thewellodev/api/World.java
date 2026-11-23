package net.thewellodev.api;

import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;

import java.util.List;

/**
 * The Infdev world on the server. The Y axis runs from 0 to 127.
 * All methods must be called from the main server thread (events, commands, scheduler tasks).
 */
public interface World {

    String getName();

    long getSeed();

    Block getBlockAt(int x, int y, int z);

    Block getBlockAt(Location loc);

    int getBlockTypeIdAt(int x, int y, int z);

    int getBlockDataAt(int x, int y, int z);

    /** Changes a block, updates its neighbours and sends the change to players. */
    boolean setBlockTypeId(int x, int y, int z, int typeId);

    boolean setBlockTypeIdAndData(int x, int y, int z, int typeId, int data);

    /** One block above the highest solid block. */
    int getHighestBlockYAt(int x, int z);

    Location getSpawnLocation();

    void setSpawnLocation(int x, int y, int z);

    /** World time in ticks. 24000 ticks = 1 day. */
    long getTime();

    void setTime(long time);

    List<? extends Player> getPlayers();

    boolean isChunkLoaded(int chunkX, int chunkZ);

    /** Drops an item that all players can see. @return the network id of the item, or -1 if invalid */
    int dropItem(Location location, int typeId, int amount, int data);

    /** Number of shared items on the ground (whole world). */
    int getItemCount();

    /** Number of ground items in a chunk. */
    int getItemCount(int chunkX, int chunkZ);

    /** Removes all ground items. @return number removed */
    int clearItems();

    /**
     * Merges nearby ground items of the same type into stacks up to the stack limit, within {@code radius} blocks.
     * @return number of items that were removed by merging
     */
    int mergeItems(double radius);

    /** Number of living mobs (excluding players, arrows, TNT, paintings and so on). */
    int getMobCount();

    /** Removes all mobs. @return number removed */
    int clearMobs();

    /** Reduces the mob count to {@code max}, removing the ones farthest from players first. @return number removed */
    int trimMobs(int max);

    /** Number of burning TNT entities. */
    int getTntCount();

    /** Removes all burning TNT. @return number removed */
    int clearTnt();

    /** Maximum stack size of an item (1 for tools, weapons and armour; usually 64). */
    int getMaxStackSize(int typeId);

    /** Whether this id is a valid block or item in the game. */
    boolean isValidItem(int typeId);

    void save();

    /** The players whose position is within {@code radius} blocks of {@code center}, nearest first. */
    List<? extends Player> getNearbyPlayers(Location center, double radius);

    /**
     * Sets every block of the box between two corners (both included, in any order) to a block type and data value.
     * The box may hold at most 32768 blocks. Call it from the main thread.
     * @return how many blocks changed
     * @throws IllegalArgumentException when the box is too large or the type is not valid for the build
     */
    int fill(int x1, int y1, int z1, int x2, int y2, int z2, int typeId, int data);

    /** The number of ticks of a day in this build (12000 in inf-20100313, 24000 in the others). */
    int getDayLength();

    /** True when the time of day is before dusk (13/24 of the day). */
    boolean isDay();

    /** The opposite of {@link #isDay()}. */
    boolean isNight();

    /**
     * The stored block light (0 to 15: torches, fire, lava) of a block, or -1 when this build has no light, the block is
     * outside the world or its chunk is not in memory.
     */
    int getBlockLightAt(int x, int y, int z);

    /** The stored sky light (0 to 15) of a block, or -1 (see {@link #getBlockLightAt}). The darkness of the night is not subtracted. */
    int getSkyLightAt(int x, int y, int z);

    /** The block type at a location. */
    default int getBlockTypeIdAt(Location loc) {
        return getBlockTypeIdAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** The y of the lowest free block above the highest solid block of the column of a location. */
    default int getHighestBlockYAt(Location loc) {
        return getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
    }

    /** The block that is the lowest free one above the highest solid block of a column. */
    default Block getHighestBlockAt(int x, int z) {
        return getBlockAt(x, getHighestBlockYAt(x, z), z);
    }

    default Block getHighestBlockAt(Location loc) {
        return getHighestBlockAt(loc.getBlockX(), loc.getBlockZ());
    }

    /** The height of the world in blocks. */
    default int getMaxHeight() {
        return 128;
    }

    /** The x coordinate of the chunk that holds the block coordinate. */
    default int getChunkCoord(int blockCoord) {
        return blockCoord >> 4;
    }
}
