import net.thewellodev.protocol.Protocol;
import net.thewellodev.server.game.GameBridge;
import net.thewellodev.server.game.NetEntity;
import net.thewellodev.server.game.TileData;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridge to the original Infdev 20100316 world code (see docs/versions/inf-20100316.md).
 *
 * What this build has over inf-20100227-1433 (and shares with inf-20100313, whose bridge this was made from): working sky and block light (stored per chunk), block metadata, the
 * day and night cycle and a 32,000,000 block world. What it has not: anything saved to disk, mobs, tile entities.
 *
 * The game keeps its chunks in a 32 x 32 ring buffer and forgets the others (it never saves). The server needs
 * every chunk to stay as it was changed, so the game's chunk provider (world field q, interface net.minecraft.a.a.e.a)
 * is replaced by one of this bridge: it keeps the chunks in a map, loads them from the world folder, or asks the
 * game's own terrain generator (net.minecraft.a.a.e.e) for a new one. Chunks far from every player are written to
 * disk and dropped from memory.
 *
 * Every game class is reached by reflection: the jar has classes such as net/minecraft/a/a.class next to the
 * package net/minecraft/a/a/, so the compiler cannot name them. Used: the world (net.minecraft.a.a.e: block get a(III),
 * block set with light b(IIII), metadata a(IIII) and c(III), field q and field j, the time), the chunk
 * (net.minecraft.a.a.e.d: blocks b, metadata d, sky light e, block light f, height map g, lowest height h and the
 * constructor with the block array), the nibble array (net.minecraft.a.a.e.c, field a) and the terrain generator.
 *
 * The game finds the chunk of a block with an unsigned shift (x >>> 4): chunks at negative coordinates have huge
 * positive numbers (-1 is 0x0FFFFFFF). The provider is asked with those numbers; the map here uses them as well.
 * Chunk files use the signed numbers (c.X.Z.dat, one GZIP NBT file each, the tags of 20100630).
 */
public class TWBridge316 implements GameBridge {

    private static final int BLOCKS = Protocol.CHUNK_BLOCKS;
    private static final int NIBBLES = Protocol.CHUNK_NIBBLES;
    private static final Pattern CHUNK_FILE = Pattern.compile("c\\.(-?\\d+)\\.(-?\\d+)\\.dat");
    /** Chunks in memory above which far chunks are written out and dropped. */
    private static final int KEEP_CHUNKS = 3000;

    private BlockListener blockListener;
    private EntityListener entityListener;

    private Object world;
    /** The game's own tables of blocks and items: what is in them exists in this build. */
    private Object[] blockTable, itemTable;
    private Method getBlockMethod;
    private Method setBlockMethod;
    private Method getMetaMethod;
    private Method setMetaMethod;
    private Field timeField;
    private Constructor<?> chunkCtor;
    private Field chunkBlocks, chunkMeta, chunkSky, chunkBlockLight, chunkHeight, chunkLowest, chunkX, chunkZ;
    private Field nibbleBytes;
    private Method chunkRecalc;
    private Object generator;
    private Method generatorChunk;
    private Class<?> chunkClass;

    /** Chunks in memory, by (gameCoord(cx) << 32 | gameCoord(cz)). */
    private final Map<Long, Object> chunks = new HashMap<Long, Object>();

    private File levelDir;
    /** The older world whose chunks are taken over when needed (null: none). */
    private File legacyDir;
    /** The chunk files of the old world that /convertworld still has to look at (made on its first call). */
    private java.util.ArrayDeque<File> legacyPending;
    private long seed;
    private long time;
    private int[] spawn = {0, 64, 0};
    private int ticks;
    private int[] lastPlayers = new int[0];

    // ----- loading and saving -----------------------------------------------------------------------------

    public void setBlockListener(BlockListener listener) {
        this.blockListener = listener;
    }

    public void load(File container, String levelName, Long seedArg) {
        levelDir = new File(container, levelName);
        if (!levelDir.isDirectory() && !levelDir.mkdirs()) {
            throw new IllegalStateException("Could not create " + levelDir);
        }
        boolean existing = new File(levelDir, "level.dat").isFile();
        try {
            loadLevelFile();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read the world in " + levelDir + ": " + ex, ex);
        }
        if (!existing) {
            // a new world: the seed is the level-seed, or a random one; it is saved with the world
            seed = seedArg != null ? seedArg : new java.util.Random().nextLong();
        }
        TWSeed.reset(seed); // the terrain generators take their random numbers from it (see TWSeed)
        try {
            ClassLoader cl = getClass().getClassLoader();
            Class<?> worldClass = Class.forName("net.minecraft.a.a.e", true, cl);
            Class<?> providerClass = Class.forName("net.minecraft.a.a.e.a", true, cl);
            chunkClass = Class.forName("net.minecraft.a.a.e.d", true, cl);
            Class<?> nibbleClass = Class.forName("net.minecraft.a.a.e.c", true, cl);
            Class<?> generatorClass = Class.forName("net.minecraft.a.a.e.e", true, cl);
            blockTable = (Object[]) Class.forName("net.minecraft.a.a.b.x", true, cl).getField("c").get(null);
            itemTable = (Object[]) Class.forName("net.minecraft.a.b.i", true, cl).getField("b").get(null);
            world = worldClass.getConstructor().newInstance();
            getBlockMethod = worldClass.getMethod("a", int.class, int.class, int.class);
            setBlockMethod = worldClass.getMethod("b", int.class, int.class, int.class, int.class);
            getMetaMethod = worldClass.getMethod("c", int.class, int.class, int.class);
            setMetaMethod = worldClass.getMethod("a", int.class, int.class, int.class, int.class);
            timeField = field(worldClass, "j");
            chunkCtor = chunkClass.getConstructor(worldClass, byte[].class, int.class, int.class);
            chunkBlocks = field(chunkClass, "b");
            chunkMeta = field(chunkClass, "d");
            chunkSky = field(chunkClass, "e");
            chunkBlockLight = field(chunkClass, "f");
            chunkHeight = field(chunkClass, "g");
            chunkLowest = field(chunkClass, "h");
            chunkX = field(chunkClass, "i");
            chunkZ = field(chunkClass, "j");
            nibbleBytes = field(nibbleClass, "a");
            // (20100313 had a method here that only marked the chunk for redraw: not needed on a server)
            chunkRecalc = chunkClass.getMethod("a");
            generator = generatorClass.getConstructor(worldClass).newInstance(world);
            generatorChunk = generatorClass.getMethod("b", int.class, int.class);
            // the game's own provider keeps a ring buffer and forgets: replace it
            final Method providerHas = providerClass.getMethod("a", int.class, int.class);
            InvocationHandler handler = new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                    if (m.getDeclaringClass() == Object.class) {
                        return m.getName().equals("hashCode") ? Integer.valueOf(System.identityHashCode(proxy))
                                : m.getName().equals("equals") ? Boolean.valueOf(proxy == args[0])
                                : "chunk provider of the server";
                    }
                    if (args == null || args.length == 0) {
                        refreshLight(); // the sky brightness changed
                        return null;
                    }
                    int gx = (Integer) args[0], gz = (Integer) args[1];
                    if (m.getReturnType() == boolean.class) {
                        return Boolean.valueOf(chunks.containsKey(mapKey(gx, gz)));
                    }
                    return chunkFor(gx, gz);
                }
            };
            Object provider = Proxy.newProxyInstance(cl, new Class<?>[]{providerClass}, handler);
            field(worldClass, "q").set(world, provider);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not start the inf-20100316 world code: " + ex, ex);
        }
        try {
            timeField.setInt(world, (int) time);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        if (!existing) {
            spawn = new int[]{0, getHighestY(0, 0), 0};
            try {
                writeLevelFile(); // keeps the seed even if the server never saves
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Could not save the world in " + levelDir + ": " + ex, ex);
            }
        }
    }

    public void setLegacyWorld(File folder) {
        legacyDir = folder;
        legacyPending = null;
    }

    public int convertLegacyBatch(int max) {
        if (legacyDir == null) {
            return -1;
        }
        if (legacyPending == null) {
            legacyPending = new java.util.ArrayDeque<File>();
            File[] files = legacyDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (CHUNK_FILE.matcher(f.getName()).matches()) {
                        legacyPending.add(f);
                    }
                }
            }
        }
        int done = 0;
        while (done < max && !legacyPending.isEmpty()) {
            File f = legacyPending.poll();
            File mine = new File(levelDir, f.getName());
            if (mine.isFile()) {
                continue; // visited and saved already
            }
            Matcher m = CHUNK_FILE.matcher(f.getName());
            if (!m.matches()) {
                continue;
            }
            long key = mapKey(gameCoord(Integer.parseInt(m.group(1))), gameCoord(Integer.parseInt(m.group(2))));
            if (chunks.containsKey(key)) {
                continue; // in memory: it is written with the next save
            }
            try {
                net.thewellodev.server.world.WorldConverter.copyFile(f, mine);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Could not take over " + f.getName() + ": " + ex, ex);
            }
            done++;
        }
        if (done == 0 && legacyPending.isEmpty()) {
            legacyDir = null; // everything is taken over: the old folder is not read any more
        }
        return done;
    }

    public long getSeed() {
        return seed;
    }

    public int[] getSpawn() {
        return spawn.clone();
    }

    public void setSpawn(int x, int y, int z) {
        spawn = new int[]{x, y, z};
    }

    public long getTime() {
        return time;
    }

    public void setTime(long t) {
        time = t;
        try {
            timeField.setInt(world, (int) t);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Called once per server tick with the block positions of the players: the world keeps no entities or scheduled
     * updates here, so the only work is to drop far chunks when many are in memory.
     */
    public void tick(int[] playerBlockPositions) {
        if (playerBlockPositions != null) {
            lastPlayers = playerBlockPositions.clone();
        }
        time++; // the day and night cycle runs on the server; the clients get the time now and then
        try {
            timeField.setInt(world, (int) time);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        if (++ticks % 200 == 0 && chunks.size() > KEEP_CHUNKS) {
            try {
                dropFarChunks();
            } catch (Exception ex) {
                throw new IllegalStateException("Could not free chunks: " + ex, ex);
            }
        }
    }

    private void dropFarChunks() throws Exception {
        Iterator<Map.Entry<Long, Object>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Object> en = it.next();
            Object chunk = en.getValue();
            int cx = signedCoord(chunkX.getInt(chunk)), cz = signedCoord(chunkZ.getInt(chunk));
            boolean near = false;
            // lastPlayers holds x, y, z of each player
            for (int i = 0; i + 2 < lastPlayers.length; i += 3) {
                int px = lastPlayers[i] >> 4, pz = lastPlayers[i + 2] >> 4;
                if (Math.abs(px - cx) <= 16 && Math.abs(pz - cz) <= 16) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                writeChunk(chunk);
                it.remove();
            }
        }
    }

    public void save(boolean full) {
        if (world == null) {
            return;
        }
        try {
            if (!levelDir.isDirectory() && !levelDir.mkdirs()) {
                throw new java.io.IOException("Could not create " + levelDir);
            }
            for (Object chunk : new ArrayList<Object>(chunks.values())) {
                writeChunk(chunk);
            }
            writeLevelFile();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save the world in " + levelDir + ": " + ex, ex);
        }
    }

    private void loadLevelFile() throws java.io.IOException {
        File file = new File(levelDir, "level.dat");
        if (!file.isFile()) {
            return;
        }
        Map<String, Object> data = castStringMap(TW227Nbt.readFile(file).get("Data"));
        spawn = new int[]{number(data, "SpawnX"), number(data, "SpawnY"), number(data, "SpawnZ")};
        time = number(data, "Time");
        seed = ((Number) data.get("RandomSeed")).longValue();
    }

    private void writeLevelFile() throws java.io.IOException {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("RandomSeed", seed);
        data.put("SpawnX", spawn[0]);
        data.put("SpawnY", spawn[1]);
        data.put("SpawnZ", spawn[2]);
        data.put("Time", time);
        data.put("LastPlayed", System.currentTimeMillis());
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("Data", data);
        TW227Nbt.writeFile(new File(levelDir, "level.dat"), root);
    }

    private File chunkFile(int cx, int cz) {
        return new File(levelDir, "c." + cx + "." + cz + ".dat");
    }

    /** Reads a chunk file into a game chunk, or null when there is none. */
    private Object readChunk(int gx, int gz) throws Exception {
        File file = chunkFile(signedCoord(gx), signedCoord(gz));
        if (!file.isFile() && legacyDir != null) {
            // not in this world yet: take it over from the older world (inf-20100227-1433 files have the same tags
            // and block numbers; the full-light sky light of such a chunk is replaced by the game's own below)
            file = new File(legacyDir, file.getName());
        }
        if (!file.isFile()) {
            return null;
        }
        Map<String, Object> level = castStringMap(TW227Nbt.readFile(file).get("Level"));
        byte[] blocks = (byte[]) level.get("Blocks");
        if (blocks == null || blocks.length != BLOCKS) {
            throw new java.io.IOException("Bad chunk " + file.getName());
        }
        Object chunk = chunkCtor.newInstance(world, blocks, gx, gz);
        copyNibbles(level.get("Data"), chunkMeta.get(chunk), 0);
        copyNibbles(level.get("SkyLight"), chunkSky.get(chunk), 15);
        copyNibbles(level.get("BlockLight"), chunkBlockLight.get(chunk), 0);
        byte[] height = (byte[]) level.get("HeightMap");
        if (height == null || height.length != 256) {
            height = new byte[256];
        }
        chunkHeight.set(chunk, height);
        chunkLowest.setInt(chunk, lowest(height));
        if (allFull((byte[]) nibbleBytes.get(chunkSky.get(chunk)))) {
            // a chunk saved by inf-20100227-1433 (that build has no light: its files say "full light everywhere"):
            // the game works out the height map and the sky light itself
            chunkRecalc.invoke(chunk);
        }
        return chunk;
    }

    private void copyNibbles(Object from, Object nibbleArray, int fill) throws Exception {
        byte[] target = (byte[]) nibbleBytes.get(nibbleArray);
        if (from instanceof byte[] && ((byte[]) from).length == target.length) {
            System.arraycopy((byte[]) from, 0, target, 0, target.length);
        } else {
            java.util.Arrays.fill(target, (byte) (fill | (fill << 4)));
        }
    }

    private static boolean allFull(byte[] nibbles) {
        for (byte b : nibbles) {
            if (b != (byte) 0xFF) {
                return false;
            }
        }
        return true;
    }

    private static int lowest(byte[] height) {
        int min = 127;
        for (int i = 0; i < 256; i++) {
            min = Math.min(min, height[i] & 255);
        }
        return min;
    }

    private void writeChunk(Object chunk) throws Exception {
        int cx = signedCoord(chunkX.getInt(chunk)), cz = signedCoord(chunkZ.getInt(chunk));
        Map<String, Object> level = new LinkedHashMap<String, Object>();
        level.put("xPos", cx);
        level.put("zPos", cz);
        level.put("LastUpdate", 0L);
        level.put("Blocks", ((byte[]) chunkBlocks.get(chunk)).clone());
        level.put("Data", ((byte[]) nibbleBytes.get(chunkMeta.get(chunk))).clone());
        level.put("SkyLight", ((byte[]) nibbleBytes.get(chunkSky.get(chunk))).clone());
        level.put("BlockLight", ((byte[]) nibbleBytes.get(chunkBlockLight.get(chunk))).clone());
        level.put("HeightMap", ((byte[]) chunkHeight.get(chunk)).clone());
        level.put("TerrainPopulated", (byte) 1);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("Level", level);
        TW227Nbt.writeFile(chunkFile(cx, cz), root);
    }

    // ----- chunks -----------------------------------------------------------------------------------------

    private static long mapKey(int gx, int gz) {
        return ((long) gx << 32) | (gz & 0xFFFFFFFFL);
    }

    /** The game's number of a chunk coordinate (unsigned shift: -1 is 0x0FFFFFFF). */
    private static int gameCoord(int chunkCoord) {
        return chunkCoord & 0x0FFFFFFF;
    }

    private static int signedCoord(int gameCoord) {
        return (gameCoord << 4) >> 4;
    }

    /** The chunk with the game's numbers: from memory, from the world folder, or newly generated. */
    private Object chunkFor(int gx, int gz) throws Exception {
        long key = mapKey(gx, gz);
        Object chunk = chunks.get(key);
        if (chunk == null) {
            chunk = readChunk(gx, gz);
            if (chunk == null) {
                try {
                    chunk = generatorChunk.invoke(generator, gx, gz);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause() instanceof Exception ? (Exception) ex.getCause() : ex;
                }
            }
            chunks.put(key, chunk);
        }
        return chunk;
    }

    /** The sky brightness changed: every chunk recalculates the light that depends on it. */
    private void refreshLight() throws Exception {
        // nothing to do: the light that depends on the sky brightness is worked out when it is asked for
    }

    public boolean isChunkLoaded(int cx, int cz) {
        return chunks.containsKey(mapKey(gameCoord(cx), gameCoord(cz)));
    }

    /** Loads or generates the chunk and returns its raw data (see Protocol.CHUNK_RAW_SIZE). */
    public byte[] getChunkData(int cx, int cz) {
        try {
            Object chunk = chunkFor(gameCoord(cx), gameCoord(cz));
            byte[] raw = new byte[Protocol.CHUNK_RAW_SIZE];
            System.arraycopy((byte[]) chunkBlocks.get(chunk), 0, raw, 0, BLOCKS);
            int off = BLOCKS;
            System.arraycopy((byte[]) nibbleBytes.get(chunkMeta.get(chunk)), 0, raw, off, NIBBLES);
            off += NIBBLES;
            System.arraycopy((byte[]) nibbleBytes.get(chunkSky.get(chunk)), 0, raw, off, NIBBLES);
            off += NIBBLES;
            System.arraycopy((byte[]) nibbleBytes.get(chunkBlockLight.get(chunk)), 0, raw, off, NIBBLES);
            off += NIBBLES;
            System.arraycopy((byte[]) chunkHeight.get(chunk), 0, raw, off, 256);
            return raw;
        } catch (Exception ex) {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("Could not read chunk " + cx + "," + cz + ": " + cause, cause);
        }
    }

    // ----- blocks -----------------------------------------------------------------------------------------

    public int getBlockId(int x, int y, int z) {
        try {
            return (Integer) getBlockMethod.invoke(world, x, y, z);
        } catch (Exception ex) {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("Block read failed at " + x + "," + y + "," + z + ": " + cause, cause);
        }
    }

    public int getBlockMeta(int x, int y, int z) {
        try {
            return (Integer) getMetaMethod.invoke(world, x, y, z);
        } catch (Exception ex) {
            return 0;
        }
    }

    public boolean setBlock(int x, int y, int z, int id, int meta, boolean notify) {
        boolean changed;
        boolean metaChanged = false;
        try {
            changed = (Boolean) setBlockMethod.invoke(world, x, y, z, id); // also updates the light
            int oldMeta = changed ? 0 : (Integer) getMetaMethod.invoke(world, x, y, z);
            if (changed || oldMeta != (meta & 15)) {
                setMetaMethod.invoke(world, x, y, z, meta & 15);
                metaChanged = !changed;
            }
        } catch (Exception ex) {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("Block write failed at " + x + "," + y + "," + z + ": " + cause, cause);
        }
        if ((changed || metaChanged) && notify && blockListener != null) {
            blockListener.onBlockChanged(x, y, z);
        }
        return changed || metaChanged;
    }

    // ----- block rules ------------------------------------------------------------------------------------

    /**
     * Solid block (the player cannot walk through it). Not solid: air (0), sapling (6), water and lava (8-11),
     * flowers and mushrooms (37-40), torch (50), fire (51), the plants and wires with ids 55 and 59.
     */
    private static boolean isSolidId(int id) {
        if (id == 0 || id == 6 || (id >= 8 && id <= 11) || (id >= 37 && id <= 40) || id == 50 || id == 51
                || id == 55 || id == 59) {
            return false;
        }
        return true;
    }

    public boolean isSolid(int x, int y, int z) {
        return y >= 0 && y < 128 && isSolidId(getBlockId(x, y, z));
    }

    /** Standing height in the column: the air above the highest block, or -1 if that block is water, lava or fire. */
    public int safeY(int x, int z) {
        for (int y = 127; y > 0; y--) {
            int id = getBlockId(x, y, z);
            if (id == 0 || id == 18) {
                continue; // air and leaves: keep looking for the ground
            }
            if (id == 8 || id == 9 || id == 10 || id == 11 || id == 51) {
                return -1;
            }
            return y + 1;
        }
        return -1;
    }

    /**
     * The single player of this build is created at (0, 64, 0), moved up one block at a time while it is inside a
     * block, and then falls to the ground. The same is worked out here, so a multiplayer player starts where a
     * single player does.
     */
    public double[] exactSpawn() {
        int feet = 64;
        while (feet < 126 && (columnsSolid(feet) || columnsSolid(feet + 1))) {
            feet++;
        }
        // falls to the ground, but stays on the surface of water (a player who sinks to the bottom would drown)
        while (feet > 1 && !columnsSolid(feet - 1) && !columnsLiquid(feet - 1)) {
            feet--;
        }
        return new double[]{0.0, feet + 0.05, 0.0};
    }

    private boolean columnsLiquid(int y) {
        return isLiquid(0, y, 0) || isLiquid(-1, y, 0) || isLiquid(0, y, -1) || isLiquid(-1, y, -1);
    }

    private boolean isLiquid(int x, int y, int z) {
        int id = getBlockId(x, y, z);
        return id >= 8 && id <= 11;
    }

    private boolean columnsSolid(int y) {
        return isSolid(0, y, 0) || isSolid(-1, y, 0) || isSolid(0, y, -1) || isSolid(-1, y, -1);
    }

    public int getHighestY(int x, int z) {
        for (int y = 127; y >= 0; y--) {
            if (isSolid(x, y, z)) {
                return y + 1;
            }
        }
        return 0;
    }

    // ----- what this build does not have ------------------------------------------------------------------

    public int getLoadedEntityCount() {
        return 0;
    }

    public int getLoadedChunkCount() {
        return chunks.size();
    }

    /** Whether the game has a block or an item with this id (its own tables are looked at). */
    public boolean isValidItem(int id) {
        return id > 0 && ((id < blockTable.length && blockTable[id] != null) || (id < itemTable.length && itemTable[id] != null));
    }

    public void setEntityListener(EntityListener listener) {
        this.entityListener = listener;
    }

    public void setMobSettings(int difficulty, boolean monsters, boolean animals) {
    }

    public void setMobCaps(int monsterCap, int animalCap) {
    }

    public void updatePlayer(String name, double x, double y, double z, float yaw, float pitch, boolean targetable) {
    }

    public boolean placeMinecart(int x, int y, int z) {
        return false;
    }

    public boolean setRider(String player, int netId) {
        return false;
    }

    public void setRiderInput(String player, float mx, float mz) {
    }

    public boolean saddle(int netId) {
        return false;
    }

    public int foodHeal(int itemId) {
        return 0;
    }

    public String blockSoundName(int blockId, boolean breaking) {
        return null;
    }

    public float blockSoundVolume(int blockId) {
        return 1f;
    }

    public float blockSoundPitch(int blockId) {
        return 1f;
    }

    public void fireArrow(String player) {
    }

    public void removePlayer(String name) {
    }

    public boolean attackMob(String attacker, int netId, int damage) {
        return false;
    }

    public List<NetEntity> getNetEntities() {
        return new ArrayList<NetEntity>();
    }

    public List<TileData> getTiles(int cx, int cz) {
        return new ArrayList<TileData>();
    }

    public boolean isLockableBlock(int x, int y, int z) {
        return false;
    }

    /** A 1-byte empty tag: there are no tile entities in this build. */
    public byte[] getTileNow(int x, int y, int z) {
        return new byte[]{0};
    }

    public byte[] applyTile(int x, int y, int z, byte[] data) {
        return null;
    }

    public boolean placePainting(int x, int y, int z, int dir, int motive) {
        return false;
    }

    public void igniteTnt(int x, int y, int z) {
    }

    public boolean spawnMob(int type, double x, double y, double z) {
        return false;
    }

    public int killAllMobs() {
        return 0;
    }

    /** The game's stack sizes are not extracted for this build yet: 64 for everything. */
    public int getMaxStackSize(int id) {
        return 64;
    }

    public int getMobCount() {
        return 0;
    }

    public int trimMobs(int max) {
        return 0;
    }

    public int getTntCount() {
        return 0;
    }

    public int clearTnt() {
        return 0;
    }

    // ----- helpers ----------------------------------------------------------------------------------------

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static int number(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringMap(Object o) {
        return (Map<String, Object>) o;
    }
}
