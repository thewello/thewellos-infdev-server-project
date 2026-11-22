package net.thewellodev.api313;

import java.util.List;

/**
 * The inf-20100313 world on the server. The Y axis runs from 0 to 127.
 * All methods must be called from the main server thread (events, commands, scheduler tasks).
 *
 * This build has block metadata (torch direction, cloth colour) and a day and night cycle; it has no items on the ground and
 * no mobs: this API has none of those.
 */
public interface World313 {

    String getName();

    /**
     * The seed of the world. The terrain is made from it (the same seed gives the same terrain); see the server's
     * level-seed setting.
     */
    long getSeed();

    Block313 getBlockAt(int x, int y, int z);

    Block313 getBlockAt(Location313 loc);

    /** The block id at a position (0 = air). Chunks are loaded or generated when needed. */
    int getBlockTypeIdAt(int x, int y, int z);

    /** Changes a block and sends the change to the players. @return true if the block changed */
    boolean setBlockTypeId(int x, int y, int z, int typeId);

    /** The block metadata (0 to 15) at a position. */
    int getBlockDataAt(int x, int y, int z);

    /** Changes a block and its metadata (0 to 15) and sends the change to the players. @return true if it changed */
    boolean setBlockTypeIdAndData(int x, int y, int z, int typeId, int data);

    /**
     * The world time in ticks (20 per second). A day is 12000 ticks in this build: 0 is sunrise, about 6500 is dusk; the server
     * keeps counting (use {@code getTime() % 12000} for the time of day).
     */
    long getTime();

    /** Sets the world time and sends it to the players. */
    void setTime(long time);

    /** One block above the highest solid block. */
    int getHighestBlockYAt(int x, int z);

    Location313 getSpawnLocation();

    void setSpawnLocation(int x, int y, int z);

    List<? extends Player313> getPlayers();

    boolean isChunkLoaded(int chunkX, int chunkZ);

    /** Whether this id is a block of this build (1 to 255 exist in the item list; blocks are 1 to 51). */
    boolean isValidBlock(int typeId);

    void save();

    /** The players whose position is within {@code radius} blocks of {@code center}, nearest first. */
    List<? extends Player313> getNearbyPlayers(Location313 center, double radius);

    /**
     * Sets every block of the box between two corners (both included, in any order) to a block type and data value.
     * The box may hold at most 32768 blocks. Call it from the main thread.
     * @return how many blocks changed
     * @throws IllegalArgumentException when the box is too large or the type is not valid for this build
     */
    int fill(int x1, int y1, int z1, int x2, int y2, int z2, int typeId, int data);

    /** The number of ticks of a day in this build (12000 in inf-20100313, 24000 in the others). */
    int getDayLength();

    /** True when the time of day is before dusk (13/24 of the day). */
    boolean isDay();

    /** The opposite of {@link #isDay()}. */
    boolean isNight();

    /**
     * The stored block light (0 to 15: torches, fire, lava) of a block, or -1 when the block is outside the world or its
     * chunk is not in memory.
     */
    int getBlockLightAt(int x, int y, int z);

    /** The stored sky light (0 to 15) of a block, or -1 (see {@link #getBlockLightAt}). The darkness of the night is not subtracted. */
    int getSkyLightAt(int x, int y, int z);
}
