package net.thewellodev.api320;

import java.util.List;

/**
 * The inf-20100320 world on the server. The Y axis runs from 0 to 127.
 * All methods must be called from the main server thread (events, commands, scheduler tasks).
 *
 * This build has block metadata (torch direction, cloth colour) and a day and night cycle; it has no items on the ground and
 * no mobs: this API has none of those.
 */
public interface World320 {

    String getName();

    /**
     * The seed of the world. The terrain is made from it (the same seed gives the same terrain); see the server's
     * level-seed setting.
     */
    long getSeed();

    Block320 getBlockAt(int x, int y, int z);

    Block320 getBlockAt(Location320 loc);

    /** The block id at a position (0 = air). Chunks are loaded or generated when needed. */
    int getBlockTypeIdAt(int x, int y, int z);

    /** Changes a block and sends the change to the players. @return true if the block changed */
    boolean setBlockTypeId(int x, int y, int z, int typeId);

    /** The block metadata (0 to 15) at a position. */
    int getBlockDataAt(int x, int y, int z);

    /** Changes a block and its metadata (0 to 15) and sends the change to the players. @return true if it changed */
    boolean setBlockTypeIdAndData(int x, int y, int z, int typeId, int data);

    /**
     * The world time in ticks (20 per second). A day is 24000 ticks: 0 is sunrise, about 12000 is dusk; the server
     * keeps counting (use {@code getTime() % 24000} for the time of day).
     */
    long getTime();

    /** Sets the world time and sends it to the players. */
    void setTime(long time);

    /** One block above the highest solid block. */
    int getHighestBlockYAt(int x, int z);

    Location320 getSpawnLocation();

    void setSpawnLocation(int x, int y, int z);

    List<? extends Player320> getPlayers();

    boolean isChunkLoaded(int chunkX, int chunkZ);

    /** Whether this id is a block of this build (blocks are 1 to 62). */
    boolean isValidBlock(int typeId);

    void save();
}
