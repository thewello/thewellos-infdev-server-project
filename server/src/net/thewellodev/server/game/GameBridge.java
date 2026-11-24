package net.thewellodev.server.game;

import java.io.File;

/**
 * Bridge between the server and the original Infdev world code.
 * The implementation (TWBridge) runs in the same class loader as the obfuscated classes.
 * All methods must be called from the main server thread.
 */
public interface GameBridge {

    /** Listens to block changes (called for every change in the world). */
    interface BlockListener {
        void onBlockChanged(int x, int y, int z);
    }

    void setBlockListener(BlockListener listener);

    /** Loads or creates the world. A null seed means a random seed. */
    void load(File container, String levelName, Long seed);

    long getSeed();

    /** [x, y, z] */
    int[] getSpawn();

    void setSpawn(int x, int y, int z);

    long getTime();

    void setTime(long time);

    /**
     * One game tick: scheduled updates (water and lava flow), entities,
     * time, light and random ticks around the given player positions.
     *
     * @param playerBlockPositions [x0, z0, x1, z1, ...]
     */
    void tick(int[] playerBlockPositions);

    /** The time that the parts of the last tick took, for the warning of a slow tick (null if the build does not tell). */
    default String tickProfile() {
        return null;
    }

    int getBlockId(int x, int y, int z);

    int getBlockMeta(int x, int y, int z);

    boolean setBlock(int x, int y, int z, int id, int meta, boolean notify);

    /** Whether a chunk is loaded (checked without loading it). */
    boolean isChunkLoaded(int cx, int cz);

    /**
     * Prepares a chunk (generating it and populating it with its neighbours if needed)
     * and returns its raw data: blocks + metadata + sky light + block light + height map.
     */
    byte[] getChunkData(int cx, int cz);

    /** Whether the block is a full solid block that cannot be entered? */
    boolean isSolid(int x, int y, int z);

    /** Safe standing height in column x,z (no lava, water or fire); -1 if the column is not safe. */
    int safeY(int x, int z);

    /**
     * Where this build's own single player starts (x, y, z), or null when the build has no such rule. Used when the
     * server's spawn point is the world origin.
     */
    default double[] exactSpawn() {
        return null;
    }

    /** The y just above the highest solid block. */
    int getHighestY(int x, int z);

    void save(boolean full);

    int getLoadedEntityCount();

    /**
     * The folder of an older build's world whose chunks this world takes over when they are first needed (see
     * WorldConverter). Called before {@link #load}; null when there is none.
     */
    default void setLegacyWorld(File folder) {
    }

    /**
     * Takes over up to {@code max} chunks of the old world that are not in this world yet (for /convertworld).
     * @return the number taken over by this call, 0 when nothing is left, -1 when there is no old world
     */
    default int convertLegacyBatch(int max) {
        return -1;
    }

    /**
     * The stored light of a block: the sky light or the block light (0 to 15), as the chunk has it, or -1 when the build
     * has no light or the chunk is not in memory.
     */
    default int getRawLight(int x, int y, int z, boolean sky) {
        return -1;
    }

    /**
     * Builds from inf-20100325-1640 on: the bridge ticks the furnaces (they smelt on the server). The furnaces that a player has
     * open do not tick on the server: the game of that player ticks them while the window is open and hands the contents back
     * when it is closed.
     * @param xyz x, y and z of each furnace that is open
     */
    default void setLockedTiles(int[] xyz) {
    }

    /**
     * Builds from inf-20100325-1640 on: when a chunk of an older build's world is taken over, the populate step of this build
     * runs on it once (caves, ore blobs), so that the old land and the new land fit together. Called before {@link #load}.
     */
    default void setUpgradeOldChunks(boolean on) {
    }

    /** Number of chunks in memory, or -1 when the build cannot tell. */
    default int getLoadedChunkCount() {
        return -1;
    }

    /** Whether a block or item with this id exists in the game. */
    boolean isValidItem(int id);

    /** Damage dealt by mobs to players, and items created on the server (to be shared). */
    interface EntityListener {
        /** @param attackerId network id of the attacker (-1 = unknown) */
        void onPlayerDamaged(String player, int attackerId, int damage);

        void onSound(String name, double x, double y, double z, float volume, float pitch);

        void onParticle(String name, double x, double y, double z, double vx, double vy, double vz);

        /** An arrow fired by a player hit another player. */
        void onPlayerHitByPlayer(String victim, String attacker, int damage);

        /** A server-side player proxy picked up an item (e.g. an arrow stuck in the ground). */
        void onGiveItem(String player, int id, int count, int damage);

        void onItemSpawned(double x, double y, double z, double mx, double my, double mz, int id, int count,
                           int data);
    }

    void setEntityListener(EntityListener listener);

    /** @param difficulty 0 peaceful (no monsters), 1 easy, 2 normal, 3 hard */
    void setMobSettings(int difficulty, boolean monsters, boolean animals);

    /** Maximum monsters and animals per player (spawn limits). */
    void setMobCaps(int monsterCap, int animalCap);

    /**
     * Updates the proxy of a player in the server world (the position mobs see).
     * @param y feet height
     * @param targetable whether mobs may attack (alive and in survival)
     */
    void updatePlayer(String name, double x, double y, double z, float yaw, float pitch, boolean targetable);

    boolean placeMinecart(int x, int y, int z);

    /** @param netId -1 = got off */
    boolean setRider(String player, int netId);

    void setRiderInput(String player, float mx, float mz);

    boolean saddle(int netId);

    /** Health restored by the food item (0 if it is not food). */
    int foodHeal(int itemId);

    /** Sound of breaking or placing a block (null if none), and its parameters. */
    String blockSoundName(int blockId, boolean breaking);

    float blockSoundVolume(int blockId);

    float blockSoundPitch(int blockId);

    /** Fires an arrow from a player's proxy. */
    void fireArrow(String player);

    void removePlayer(String name);

    /** A player hit a mob. @return true if damage was applied */
    boolean attackMob(String attacker, int netId, int damage);

    /** Latest state of the synchronized entities (removed ones stay in the list once). */
    java.util.List<NetEntity> getNetEntities();

    /** Contents of chests, furnaces and signs in a chunk. */
    java.util.List<TileData> getTiles(int cx, int cz);

    /**
     * Validates tile entity data sent by a client and applies it.
     * @return the data that was applied (normalised), or null if it was rejected
     */
    /** Whether the block is a chest or furnace (lockable)? */
    boolean isLockableBlock(int x, int y, int z);

    /** Current data of a tile entity (a 1-byte empty tag if there is none). */
    byte[] getTileNow(int x, int y, int z);

    byte[] applyTile(int x, int y, int z, byte[] data);

    /** Creates the painting the player placed. */
    boolean placePainting(int x, int y, int z, int dir, int motive);

    /** Lights a TNT block when it is broken: the server creates a burning TNT entity. */
    void igniteTnt(int x, int y, int z);

    /** Creates a mob of the given type (index into Protocol.MOB_CLASSES; not arrows). */
    boolean spawnMob(int type, double x, double y, double z);

    /** Removes all mobs. @return number removed */
    int killAllMobs();

    /** Maximum stack size of an item (the game's own value; tools are 1, most items 64). */
    int getMaxStackSize(int id);

    /** Number of living mobs (players excluded). */
    int getMobCount();

    /** Reduces the mob count to max, removing the ones farthest from players first. */
    int trimMobs(int max);

    /** Number of burning TNT entities. */
    int getTntCount();

    /** Removes all burning TNT entities. */
    int clearTnt();
}
