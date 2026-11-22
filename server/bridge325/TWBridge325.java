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
 * Bridge to the original Infdev 20100325-1640 world code (see docs/versions/inf-20100325-1640.md).
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
public class TWBridge325 implements GameBridge {

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
    private Object worldProvider;
    /** Chunks that were read from a chunk file (they were populated before). */
    private final java.util.Set<Long> fromFile = new java.util.HashSet<Long>();
    private int populateDepth;
    /** Chunks that were taken over from an older build get the populate step of this build (see GameBridge). */
    private boolean upgradeOld = false;
    private boolean lastReadLegacy;

    public void setUpgradeOldChunks(boolean on) {
        upgradeOld = on;
    }
    /** Above 0 while the light queue or the block ticks run: they ask for chunks that nobody needs yet. */
    private int deferPopulate;
    /**
     * Chunks that the populate step of another chunk made (trees and caves reach into the chunks around). They are only
     * generated then; each is populated the first time something asks for it from outside a populate step. Populating them
     * at once would make every chunk next to them, and so on: with the caves of this build a spawn area needed 250 chunks
     * and several seconds instead of 100 chunks.
     */
    private final java.util.Set<Long> unpopulated = new java.util.HashSet<Long>();
    private Method getBlockMethod;
    private Method setBlockMethod;
    /** The game queues its light updates (one box per call of the drain method; the client calls it every frame). */
    private Method lightDrain;
    private Field lightQueue;
    // block ticks (see TWTick): the world's tick method, its player field, a stand-in player and the random-tick table
    private Method worldTick, blockRandomTick;
    private Field worldPlayerField, entX, entY, entZ;
    private Constructor<?> standInCtor;
    private Object standIn;
    private boolean[] randomTicks;
    private final java.util.Random tickRandom = new java.util.Random();
    private int tickTurn;
    // furnaces: the world's list of tile entities and its entity tick (it ticks the tiles in the list)
    private Field worldTiles;
    private Method worldEntityTick;
    private int[] lockedTiles = new int[0];
    private Method getMetaMethod;
    private Method setMetaMethod;
    private Field timeField;
    private Constructor<?> chunkCtor;
    private Field chunkBlocks, chunkMeta, chunkSky, chunkBlockLight, chunkHeight, chunkLowest, chunkX, chunkZ;
    private Field nibbleBytes;
    private Method chunkRecalc, generatorPopulate, chunkTileGet;
    private Field chunkTiles, chestSlots, furnaceSlots;
    private Field[] furnaceInts;
    private Class<?> chestClass, furnaceClass;
    private Constructor<?> stackCtor;
    private Field stackCount, stackId, stackDamage;
    /** Tile entities read from a chunk file, put into the game's chunk once it is registered (see chunkFor). */
    private List<Map<String, Object>> pendingTiles;
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
            Class<?> worldClass = Class.forName("net.minecraft.a.a.g", true, cl);
            Class<?> providerClass = Class.forName("net.minecraft.a.a.e.a", true, cl);
            chunkClass = Class.forName("net.minecraft.a.a.e.d", true, cl);
            Class<?> nibbleClass = Class.forName("net.minecraft.a.a.e.c", true, cl);
            Class<?> generatorClass = Class.forName("net.minecraft.a.a.a.b", true, cl);
            blockTable = (Object[]) Class.forName("net.minecraft.a.a.c.x", true, cl).getField("c").get(null);
            itemTable = (Object[]) Class.forName("net.minecraft.a.b.i", true, cl).getField("b").get(null);
            world = worldClass.getConstructor().newInstance();
            getBlockMethod = worldClass.getMethod("a", int.class, int.class, int.class);
            setBlockMethod = worldClass.getMethod("c", int.class, int.class, int.class, int.class); // set block, tells the listeners
            getMetaMethod = worldClass.getMethod("d", int.class, int.class, int.class);
            lightDrain = worldClass.getMethod("e");
            worldTick = worldClass.getMethod("f");
            worldTiles = worldClass.getField("a");
            worldEntityTick = worldClass.getMethod("b");
            worldPlayerField = field(worldClass, "b");
            Class<?> entityBase = Class.forName("net.minecraft.a.c.b", true, cl);
            entX = entityBase.getField("g");
            entY = entityBase.getField("h");
            entZ = entityBase.getField("i");
            standInCtor = Class.forName("net.minecraft.a.c.e.a", true, cl).getConstructor(worldClass);
            Class<?> blockBase = Class.forName("net.minecraft.a.a.c.x", true, cl);
            randomTicks = (boolean[]) blockBase.getField("d").get(null);
            blockRandomTick = blockBase.getMethod("a", worldClass, int.class, int.class, int.class, java.util.Random.class);
            lightQueue = worldClass.getDeclaredField("i");
            lightQueue.setAccessible(true);
            setMetaMethod = worldClass.getMethod("b", int.class, int.class, int.class, int.class);
            timeField = field(worldClass, "l");
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
            // trees are planted by the generator's populate step (it writes into the chunks around the new one)
            generatorPopulate = generatorClass.getMethod("a", providerClass, int.class, int.class);
            chunkTiles = field(chunkClass, "k");
            chunkTileGet = chunkClass.getMethod("d", int.class, int.class, int.class);
            Class<?> stackClass = Class.forName("net.minecraft.a.b.k", true, cl);
            stackCtor = stackClass.getConstructor(int.class, int.class);
            stackCount = stackClass.getField("a");
            stackId = stackClass.getField("c");
            stackDamage = stackClass.getField("d");
            Class<?> chestClass = Class.forName("net.minecraft.a.a.c.a.c", true, cl);
            Class<?> furnaceClass = Class.forName("net.minecraft.a.a.c.a.b", true, cl);
            chestSlots = field(chestClass, "e");
            furnaceSlots = field(furnaceClass, "e");
            furnaceInts = new Field[]{field(furnaceClass, "f"), field(furnaceClass, "g"), field(furnaceClass, "h")};
            this.chestClass = chestClass;
            this.furnaceClass = furnaceClass;
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
                        return null;
                    }
                    if (args.length == 3) {
                        return null; // populate(provider, x, z): the bridge calls it itself when a chunk is made
                    }
                    int gx = (Integer) args[0], gz = (Integer) args[1];
                    if (m.getReturnType() == boolean.class) {
                        return Boolean.valueOf(chunks.containsKey(mapKey(gx, gz)));
                    }
                    return chunkFor(gx, gz);
                }
            };
            Object provider = Proxy.newProxyInstance(cl, new Class<?>[]{providerClass}, handler);
            worldProvider = provider;
            field(worldClass, "u").set(world, provider);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not start the inf-20100325-1640 world code: " + ex, ex);
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
        long batchStart = System.nanoTime();
        while (done < max && !legacyPending.isEmpty()) {
            if (upgradeOld && (System.nanoTime() - batchStart > 30000000L || done >= Math.max(1, max / 20))) {
                break; // populating takes time: a few chunks in a tick
            }
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
            if (chunks.containsKey(key) && !(upgradeOld && unpopulated.contains(key))) {
                continue; // in memory: it is written with the next save
            }
            if (upgradeOld) {
                // the chunk is read, populated and saved (the older file is not copied as it is)
                try {
                    Object chunk = chunkFor(gameCoord(Integer.parseInt(m.group(1))), gameCoord(Integer.parseInt(m.group(2))));
                    writeChunk(chunk);
                } catch (Exception ex) {
                    throw new IllegalStateException("Could not take over " + f.getName() + ": " + ex, ex);
                }
                done++;
                continue;
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
        drainLight(300);
        blockTicks();
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
            // lastPlayers holds x and z of each player
            for (int i = 0; i + 1 < lastPlayers.length; i += 2) {
                int px = lastPlayers[i] >> 4, pz = lastPlayers[i + 1] >> 4;
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
        lastReadLegacy = false;
        File file = chunkFile(signedCoord(gx), signedCoord(gz));
        if (!file.isFile() && legacyDir != null) {
            lastReadLegacy = new File(legacyDir, file.getName()).isFile();
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
        Object tileList = level.get("TileEntities");
        if (tileList instanceof List && !((List<?>) tileList).isEmpty()) {
            pendingTiles = new ArrayList<Map<String, Object>>();
            for (Object t : (List<?>) tileList) {
                pendingTiles.add(castStringMap(t));
            }
        }
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
        if (unpopulated.contains(mapKey(chunkX.getInt(chunk), chunkZ.getInt(chunk)))) {
            return; // not final yet: it is made again (and populated) when somebody needs it
        }
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
        List<Object> tiles = new ArrayList<Object>();
        Map<?, ?> tileMap = (Map<?, ?>) chunkTiles.get(chunk);
        for (Object t : new ArrayList<Object>(tileMap.values())) {
            Map<String, Object> tag = tileToNbt(chunk, tileMap, t);
            if (tag != null) {
                tiles.add(tag);
            }
        }
        if (!tiles.isEmpty()) {
            level.put("TileEntities", tiles);
        }
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
            pendingTiles = null;
            chunk = readChunk(gx, gz);
            boolean fresh = chunk == null;
            // a chunk of an older build gets the populate step of this build once (it was made without the caves)
            boolean upgrade = !fresh && lastReadLegacy && upgradeOld;
            if (!fresh && !upgrade) {
                fromFile.add(key); // populated before: never again (a build next to it would only write into it)
            }
            boolean wasQuiet = TWTick.quiet;
            TWTick.quiet = true; // making a chunk is not a block change that the players have to hear of
            try {
                if (fresh) {
                    try {
                        chunk = generatorChunk.invoke(generator, gx, gz);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause() instanceof Exception ? (Exception) ex.getCause() : ex;
                    }
                }
            } finally {
                TWTick.quiet = wasQuiet;
            }
            chunks.put(key, chunk);
            if (pendingTiles != null) {
                List<Map<String, Object>> tiles = pendingTiles;
                pendingTiles = null;
                for (Map<String, Object> t : tiles) {
                    try {
                        restoreTile(t);
                    } catch (RuntimeException ex) {
                        // a damaged tile entity in a chunk file must not make the chunk unreadable
                    }
                }
            }
            if (fresh || upgrade) {
                if (populateDepth > 0 || deferPopulate > 0) {
                    unpopulated.add(key); // made by a populate step: populated when it is asked for
                } else {
                    boolean was = TWTick.quiet;
                    TWTick.quiet = true;
                    try {
                        populateAround(gx, gz);
                    } finally {
                        TWTick.quiet = was;
                    }
                }
            }
        } else if (populateDepth == 0 && deferPopulate == 0 && !unpopulated.isEmpty() && unpopulated.remove(key)) {
            boolean was = TWTick.quiet;
            TWTick.quiet = true;
            try {
                populateAround(gx, gz);
            } finally {
                TWTick.quiet = was;
            }
        }
        return chunk;
    }

    /**
     * What the game's chunk ring does after it made a chunk: the new chunk and the three chunks that lie before it
     * (x - 1, z - 1 and both) are populated, because a populate step reaches into the chunks after its own.
     */
    private void populateAround(int gx, int gz) throws Exception {
        if (populateDepth >= 6) {
            return; // trees that reach into chunks that do not exist make those chunks: do not let this run away
        }
        populateDepth++;
        try {
            populateAround0(gx, gz);
        } finally {
            populateDepth--;
        }
    }

    private void populateAround0(int gx, int gz) throws Exception {
        int[][] around = {{gx, gz}, {gx - 1, gz}, {gx - 1, gz - 1}, {gx, gz - 1}};
        for (int[] c : around) {
            int px = c[0] & 0x0FFFFFFF, pz = c[1] & 0x0FFFFFFF;
            if (chunks.containsKey(mapKey(px, pz)) && !fromFile.contains(mapKey(px, pz))) {
                try {
                    generatorPopulate.invoke(generator, worldProvider, px, pz);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause() instanceof Exception ? (Exception) ex.getCause() : ex;
                }
            }
        }
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
            // The populate step of a chunk writes into the chunks around it (trees reach over the border), and a new chunk
            // populates the three chunks before it. Every chunk that can still change this one is made first, so that
            // what is sent is final (otherwise the trees would be cut off at the borders of the chunks already sent).
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    chunkFor(gameCoord(cx + dx), gameCoord(cz + dz));
                }
            }
            Object chunk = chunkFor(gameCoord(cx), gameCoord(cz));
            drainLight(20000); // what is sent is lit completely
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

    /**
     * The world's tick of the game: the time of day and the darkness of the sky, and the block ticks that blocks have
     * scheduled (a leaf block that waits to decay, fire that waits to burn on). The sweep of random block ticks around the
     * player is cut out of the method (see GameClassLoader) and done here instead, over the chunks the server has: the
     * game would make every chunk within 128 blocks of the player for it. What changes is sent to the players.
     */
    private void blockTicks() {
        if (lastPlayers.length < 2) {
            return;
        }
        try {
            int n = lastPlayers.length / 2;
            int turn = tickTurn++ % n;
            if (standIn == null) {
                standIn = standInCtor.newInstance(world);
            }
            for (int i = 0; i < n; i++) {
                if (i == turn) {
                    entX.setDouble(standIn, lastPlayers[i * 2] + 0.5);
                    entY.setDouble(standIn, 64);
                    entZ.setDouble(standIn, lastPlayers[i * 2 + 1] + 0.5);
                }
            }
            TWTick.capturing = true;
            deferPopulate++;
            try {
                worldPlayerField.set(world, standIn);
                worldTick.invoke(world); // the time field moves by one: the bridge keeps its own time
                timeField.setInt(world, (int) time);
                syncFurnaces();
                worldEntityTick.invoke(world); // ticks the furnaces in the world's list (the world has no entities)
                for (int i = 0; i < n; i++) {
                    randomBlockTicks(lastPlayers[i * 2], lastPlayers[i * 2 + 1]);
                }
            } finally {
                worldPlayerField.set(world, null);
                TWTick.capturing = false;
                deferPopulate--;
            }
            int[] changed = TWTick.takeChanges();
            if (blockListener != null) {
                for (int i = 0; i + 2 < changed.length; i += 3) {
                    blockListener.onBlockChanged(changed[i], changed[i + 1], changed[i + 2]);
                }
            }
            if (entityListener != null) {
                for (double[] it : TWTick.takeItems()) {
                    entityListener.onItemSpawned(it[0], it[1], it[2], it[3], it[4], it[5], (int) it[6], (int) it[7],
                            (int) it[8]);
                }
            }
        } catch (Exception ex) {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("Block tick failed: " + cause, cause);
        }
    }

    public void setLockedTiles(int[] xyz) {
        lockedTiles = xyz == null ? new int[0] : xyz.clone();
    }

    /**
     * The world ticks the tile entities in its list, which the chunk puts there when it is loaded (the bridge's chunks never are
     * "loaded" in that sense): the list is made from the furnaces of the chunks the bridge holds, except the ones that a player
     * has open.
     */
    @SuppressWarnings("unchecked")
    private void syncFurnaces() throws Exception {
        List<Object> list = (List<Object>) worldTiles.get(world);
        list.clear();
        for (Object chunk : chunks.values()) {
            Map<?, ?> tiles = (Map<?, ?>) chunkTiles.get(chunk);
            if (tiles.isEmpty()) {
                continue;
            }
            int bx = signedCoord(chunkX.getInt(chunk)) << 4, bz = signedCoord(chunkZ.getInt(chunk)) << 4;
            for (Map.Entry<?, ?> e : tiles.entrySet()) {
                if (!furnaceClass.isInstance(e.getValue())) {
                    continue;
                }
                int key = (Integer) e.getKey();
                int x = bx + (key & 1023), y = (key >> 10) & 1023, z = bz + ((key >> 20) & 1023);
                boolean open = false;
                for (int i = 0; i + 2 < lockedTiles.length; i += 3) {
                    if (lockedTiles[i] == x && lockedTiles[i + 1] == y && lockedTiles[i + 2] == z) {
                        open = true;
                        break;
                    }
                }
                if (!open) {
                    list.add(e.getValue());
                }
            }
        }
    }

    /**
     * What the game does 32000 times in a tick for the volume around the player: a random block is asked to tick. The
     * same number of tries per block (one in 262 ticks) is made here over a square of 129 blocks, in the chunks that exist.
     */
    private void randomBlockTicks(int px, int pz) throws Exception {
        for (int i = 0; i < 8000; i++) {
            int x = px + tickRandom.nextInt(129) - 64, z = pz + tickRandom.nextInt(129) - 64, y = tickRandom.nextInt(128);
            Object chunk = chunks.get(mapKey(gameCoord(x >> 4), gameCoord(z >> 4)));
            if (chunk == null) {
                continue;
            }
            int id = ((byte[]) chunkBlocks.get(chunk))[((x & 15) << 11) | ((z & 15) << 7) | y] & 255;
            if (id > 0 && id < randomTicks.length && randomTicks[id] && blockTable[id] != null) {
                blockRandomTick.invoke(blockTable[id], world, x, y, z, tickRandom);
            }
        }
    }

    /**
     * Works off the light updates that the game has queued (a block change, a tree). The game does this once in each frame
     * of a client; the server has no frames, so without this the light of the server would never spread sideways or from
     * torches, and the queue would grow forever.
     */
    private void drainLight(int max) {
        deferPopulate++;
        try {
            java.util.List<?> queue = (java.util.List<?>) lightQueue.get(world);
            for (int i = 0; i < max && !queue.isEmpty(); i++) {
                lightDrain.invoke(world);
            }
        } catch (Exception ex) {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null ? ex.getCause() : ex;
            throw new IllegalStateException("Light update failed: " + cause, cause);
        } finally {
            deferPopulate--;
        }
    }

    public int getRawLight(int x, int y, int z, boolean sky) {
        if (y < 0 || y > 127) {
            return -1;
        }
        try {
            Object chunk = chunks.get(mapKey(gameCoord(x >> 4), gameCoord(z >> 4)));
            if (chunk == null) {
                return -1;
            }
            byte[] a = (byte[]) nibbleBytes.get(sky ? chunkSky.get(chunk) : chunkBlockLight.get(chunk));
            int i = ((x & 15) << 11) | ((z & 15) << 7) | y;
            return (a[i >> 1] >> ((i & 1) * 4)) & 15;
        } catch (Exception ex) {
            return -1;
        }
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

    // ----- chests and furnaces ------------------------------------------------------------------------------

    private static boolean isChestId(int id) {
        return id == 54;
    }

    private static boolean isFurnaceId(int id) {
        return id == 61 || id == 62;
    }

    /** The tile entity at a block position, or null when the block has none (the game makes it when it is missing). */
    private Object tileAt(int x, int y, int z) throws Exception {
        if (y < 0 || y >= 128) {
            return null;
        }
        int id = (Integer) getBlockMethod.invoke(world, x, y, z);
        if (!isChestId(id) && !isFurnaceId(id)) {
            return null;
        }
        chunkFor(gameCoord(x >> 4), gameCoord(z >> 4));
        Object tile = chunkTileGet.invoke(chunkAtBlock(x, z), x & 15, y, z & 15);
        return tile != null && (chestClass.isInstance(tile) || furnaceClass.isInstance(tile)) ? tile : null;
    }

    private Object chunkAtBlock(int x, int z) throws Exception {
        return chunkFor(gameCoord(x >> 4), gameCoord(z >> 4));
    }

    /** NBT form of a tile entity of a chunk (the tags of the later games: id, x, y, z, Items), or null. */
    private Map<String, Object> tileToNbt(Object chunk, Map<?, ?> tileMap, Object tile) throws Exception {
        int key = -1;
        for (Map.Entry<?, ?> e : tileMap.entrySet()) {
            if (e.getValue() == tile) {
                key = (Integer) e.getKey();
                break;
            }
        }
        if (key < 0) {
            return null;
        }
        int lx = key & 1023, y = (key >> 10) & 1023, lz = (key >> 20) & 1023;
        int x = (signedCoord(chunkX.getInt(chunk)) << 4) + lx, z = (signedCoord(chunkZ.getInt(chunk)) << 4) + lz;
        int block = y < 128 ? ((byte[]) chunkBlocks.get(chunk))[(lx << 11) | (lz << 7) | y] & 255 : 0;
        boolean chest = chestClass.isInstance(tile) && isChestId(block);
        boolean furnace = furnaceClass.isInstance(tile) && isFurnaceId(block);
        if (!chest && !furnace) {
            return null;
        }
        Map<String, Object> tag = new LinkedHashMap<String, Object>();
        tag.put("id", chest ? "Chest" : "Furnace");
        tag.put("x", x);
        tag.put("y", y);
        tag.put("z", z);
        Object[] slots = (Object[]) (chest ? chestSlots : furnaceSlots).get(tile);
        List<Object> items = new ArrayList<Object>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && stackCount.getInt(slots[i]) > 0) {
                Map<String, Object> it = new LinkedHashMap<String, Object>();
                it.put("Slot", (byte) i);
                it.put("id", (short) stackId.getInt(slots[i]));
                it.put("Count", (byte) stackCount.getInt(slots[i]));
                it.put("Damage", (short) stackDamage.getInt(slots[i]));
                items.add(it);
            }
        }
        tag.put("Items", items);
        if (furnace) {
            tag.put("BurnTime", (short) furnaceInts[0].getInt(tile));
            tag.put("CookTime", (short) furnaceInts[2].getInt(tile));
            tag.put("BurnItemTime", (short) furnaceInts[1].getInt(tile));
        }
        return tag;
    }

    /** Puts the contents of a saved tile entity into the tile entity the game makes for that block. */
    private void restoreTile(Map<String, Object> tag) throws Exception {
        int x = number(tag, "x"), y = number(tag, "y"), z = number(tag, "z");
        Object tile = tileAt(x, y, z);
        if (tile == null) {
            return;
        }
        boolean furnace = furnaceClass.isInstance(tile);
        Object[] slots = (Object[]) (furnace ? furnaceSlots : chestSlots).get(tile);
        Object items = tag.get("Items");
        if (items instanceof List) {
            for (Object o : (List<?>) items) {
                Map<String, Object> it = castStringMap(o);
                int slot = number(it, "Slot") & 255;
                int id = number(it, "id"), count = number(it, "Count");
                if (slot < slots.length && id > 0 && id < 2000 && count > 0 && isValidItem(id)) {
                    Object stack = stackCtor.newInstance(id, count);
                    stackDamage.setInt(stack, number(it, "Damage"));
                    slots[slot] = stack;
                }
            }
        }
        if (furnace) {
            furnaceInts[0].setInt(tile, tag.containsKey("BurnTime") ? number(tag, "BurnTime") : 0);
            furnaceInts[2].setInt(tile, tag.containsKey("CookTime") ? number(tag, "CookTime") : 0);
            furnaceInts[1].setInt(tile, tag.containsKey("BurnItemTime") ? number(tag, "BurnItemTime") : 0);
        }
    }

    /**
     * The data of a tile entity as the clients exchange it with the server (see Protocol.TILE_DATA): a type byte
     * (0 none, 1 chest, 2 furnace), then n times (short id, short count, short damage) for the 27 or 3 slots, and for a
     * furnace three ints (burn time, burn item time, cook time).
     */
    private byte[] tileBytes(Object tile) throws Exception {
        if (tile == null) {
            return new byte[]{0};
        }
        boolean furnace = furnaceClass.isInstance(tile);
        Object[] slots = (Object[]) (furnace ? furnaceSlots : chestSlots).get(tile);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bo);
        out.writeByte(furnace ? 2 : 1);
        for (Object st : slots) {
            boolean empty = st == null || stackCount.getInt(st) <= 0;
            out.writeShort(empty ? -1 : stackId.getInt(st));
            out.writeShort(empty ? 0 : stackCount.getInt(st));
            out.writeShort(empty ? 0 : stackDamage.getInt(st));
        }
        if (furnace) {
            out.writeInt(furnaceInts[0].getInt(tile));
            out.writeInt(furnaceInts[1].getInt(tile));
            out.writeInt(furnaceInts[2].getInt(tile));
        }
        return bo.toByteArray();
    }

    public List<TileData> getTiles(int cx, int cz) {
        List<TileData> result = new ArrayList<TileData>();
        try {
            Object chunk = chunkFor(gameCoord(cx), gameCoord(cz));
            Map<?, ?> tileMap = (Map<?, ?>) chunkTiles.get(chunk);
            for (Object t : new ArrayList<Object>(tileMap.values())) {
                Map<String, Object> tag = tileToNbt(chunk, tileMap, t);
                if (tag != null) {
                    result.add(new TileData(number(tag, "x"), number(tag, "y"), number(tag, "z"), tileBytes(t)));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read the tile entities of chunk " + cx + "," + cz + ": " + ex, ex);
        }
        return result;
    }

    public boolean isLockableBlock(int x, int y, int z) {
        if (y < 0 || y >= 128) {
            return false;
        }
        int id = getBlockId(x, y, z);
        return isChestId(id) || isFurnaceId(id);
    }

    public byte[] getTileNow(int x, int y, int z) {
        try {
            return tileBytes(tileAt(x, y, z));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read the tile entity at " + x + "," + y + "," + z + ": " + ex, ex);
        }
    }

    /** A client sends the contents it has after closing a chest or furnace. @return the data applied, or null. */
    public byte[] applyTile(int x, int y, int z, byte[] data) {
        try {
            Object tile = tileAt(x, y, z);
            if (tile == null || data == null || data.length < 2) {
                return null;
            }
            boolean furnace = furnaceClass.isInstance(tile);
            if (data[0] != (furnace ? 2 : 1)) {
                return null;
            }
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data, 1, data.length - 1));
            Object[] slots = (Object[]) (furnace ? furnaceSlots : chestSlots).get(tile);
            Object[] fresh = new Object[slots.length];
            for (int i = 0; i < slots.length; i++) {
                int id = in.readShort();
                int count = in.readShort();
                int damage = in.readShort();
                if (id < 0 || count <= 0) {
                    continue;
                }
                if (id == 0 || id >= 2000 || count > 999 || damage < 0 || damage > 5000 || !isValidItem(id)) {
                    return null;
                }
                Object stack = stackCtor.newInstance(id, count);
                stackDamage.setInt(stack, damage);
                fresh[i] = stack;
            }
            int[] ints = new int[3];
            if (furnace) {
                for (int i = 0; i < 3; i++) {
                    ints[i] = Math.max(0, Math.min(in.readInt(), 20000));
                }
            }
            System.arraycopy(fresh, 0, slots, 0, slots.length);
            if (furnace) {
                for (int i = 0; i < 3; i++) {
                    furnaceInts[i].setInt(tile, ints[i]);
                }
            }
            return tileBytes(tile);
        } catch (Exception ex) {
            return null;
        }
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
