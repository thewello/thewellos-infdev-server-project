import net.thewellodev.protocol.Protocol;
import net.thewellodev.server.game.GameBridge;
import net.thewellodev.server.game.NetEntity;
import net.thewellodev.server.game.TileData;

import net.thewellodev.server.world.WorldConverter;

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
 * Bridge to the original Infdev 20100608 world code (see docs/versions/inf-20100608.md). It is the bridge of
 * inf-20100330-1611 with the names of this build: the world class is net.minecraft.a.a.g (members moved by one letter: player d, time c,
 * provider w, light queue m and its drain method h(), tick method i(), tile list b), and the fields of the entity classes moved
 * by one letter (a stand-in player has its position in h, i and j). The chunk is as in that build (it has a "populated" flag) with the tag
 * TerrainPopulated, which the game writes into every chunk file from this build on: a chunk with the tag 0 is populated when it
 * is needed, a chunk without the tag (made by 20100327 or by an older build) counts as populated, as before.
 *
 * This build saves its worlds in the Alpha level format (the same as 20100327): a level.dat (GZIP NBT, a compound "Data" with RandomSeed, SpawnX,
 * SpawnY, SpawnZ, Time, SizeOnDisk, LastPlayed and Player) and one GZIP NBT file for each chunk, named c.X.Z.dat with X and Z
 * in base 36 and kept in the folders X mod 64 and Z mod 64 (also in base 36), for example 1r/0/c.-1.0.dat. The bridge writes
 * and reads exactly these files, so a world of this server can be opened in the game's single player menu (copy the folder
 * into the saves folder) and a world of the game can be put into the server's worlds folder.
 *
 * The game's own chunk provider (world field v, interface net.minecraft.a.a.e.a) keeps a ring buffer of chunks and writes the
 * changed ones when the world saves. The server needs every chunk to stay as it was changed and decides itself when to
 * populate a chunk, so the provider is replaced by one of this bridge: it keeps the chunks in a map, loads them from the
 * world folder, or asks the game's own terrain generator (net.minecraft.a.a.c.a) for a new one. Chunks far from every
 * player are written to disk and dropped from memory. The game's own saving (the private method a(boolean) of the world)
 * is cut out of the class when it loads (see GameClassLoader): the world of the bridge never writes anything itself.
 *
 * Every game class is reached by reflection: the jar has classes such as net/minecraft/a/a.class next to the
 * package net/minecraft/a/a/, so the compiler cannot name them. Used: the world (net.minecraft.a.a.f: block get a(III),
 * block set with light c(IIII), metadata b(IIII) and d(III), the light queue k and its drain method g(), the tick method
 * h(), the time b, the provider v), the chunk (net.minecraft.a.a.e.d: blocks e, metadata g, sky light h, block light i, height
 * map j, lowest height k, position b and c, tile entities l and the constructor with the block array), the nibble array
 * (net.minecraft.a.a.e.b, field a) and the terrain generator.
 *
 * The block coordinates of the world are signed in this build (x >> 4): the chunk numbers of the game are the real ones.
 */
public class TWBridge608 implements GameBridge {

    private static final int BLOCKS = Protocol.CHUNK_BLOCKS;
    private static final int NIBBLES = Protocol.CHUNK_NIBBLES;
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
    private Field chunkPopulated;
    /** What the tag TerrainPopulated of the chunk file read last said: true, false, or null when the file has no such tag. */
    private Boolean lastReadPopulatedTag;
    private Field chunkBlocks, chunkMeta, chunkSky, chunkBlockLight, chunkHeight, chunkLowest, chunkX, chunkZ;
    private Field nibbleBytes;
    private Method chunkRecalc, generatorPopulate, chunkTileGet;
    private Field chunkTiles, chestSlots, furnaceSlots;
    private Field[] furnaceInts;
    private Class<?> chestClass, furnaceClass, signClass;
    /** The four lines of a sign (new in this build). */
    private Field signText;
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
            Class<?> nibbleClass = Class.forName("net.minecraft.a.a.e.b", true, cl);
            Class<?> generatorClass = Class.forName("net.minecraft.a.a.c.a", true, cl);
            blockTable = (Object[]) Class.forName("net.minecraft.a.a.b.y", true, cl).getField("c").get(null);
            itemTable = (Object[]) Class.forName("net.minecraft.a.b.i", true, cl).getField("b").get(null);
            // the world wants a folder (it reads and writes level.dat there): an empty scratch folder, removed again
            // at once. The saving of the world is cut out of the class, so nothing is written to it.
            File scratch = java.nio.file.Files.createTempDirectory("thewellodev-world").toFile();
            try {
                world = worldClass.getConstructor(File.class, String.class).newInstance(scratch, "world");
            } finally {
                new File(scratch, "world").delete();
                scratch.delete();
            }
            getBlockMethod = worldClass.getMethod("a", int.class, int.class, int.class);
            setBlockMethod = worldClass.getMethod("c", int.class, int.class, int.class, int.class); // set block, tells the listeners
            getMetaMethod = worldClass.getMethod("d", int.class, int.class, int.class);
            lightDrain = worldClass.getMethod("h"); // works off the queue (up to 100000 steps) and says whether more is left
            worldTick = worldClass.getMethod("i");
            worldTiles = worldClass.getField("a");
            worldEntityTick = worldClass.getMethod("c");
            worldPlayerField = field(worldClass, "c");
            TWTick.fieldShift = 1;
            Class<?> entityBase = Class.forName("net.minecraft.a.c.b", true, cl);
            entX = entityBase.getField("h");
            entY = entityBase.getField("i");
            entZ = entityBase.getField("j");
            standInCtor = Class.forName("net.minecraft.a.c.e.a", true, cl).getConstructor(worldClass);
            Class<?> blockBase = Class.forName("net.minecraft.a.a.b.y", true, cl);
            randomTicks = (boolean[]) blockBase.getField("d").get(null);
            blockRandomTick = blockBase.getMethod("a", worldClass, int.class, int.class, int.class, java.util.Random.class);
            lightQueue = worldClass.getDeclaredField("l");
            lightQueue.setAccessible(true);
            setMetaMethod = worldClass.getMethod("b", int.class, int.class, int.class, int.class);
            timeField = field(worldClass, "b"); // a long in this build
            chunkCtor = chunkClass.getConstructor(worldClass, byte[].class, int.class, int.class);
            chunkBlocks = field(chunkClass, "f");
            chunkMeta = field(chunkClass, "i");
            chunkSky = field(chunkClass, "j");
            chunkBlockLight = field(chunkClass, "k");
            chunkHeight = field(chunkClass, "l");
            chunkLowest = field(chunkClass, "m");
            chunkX = field(chunkClass, "b");
            chunkZ = field(chunkClass, "c");
            chunkPopulated = field(chunkClass, "d"); // the TerrainPopulated flag (field e is the "changed" flag now)
            nibbleBytes = field(nibbleClass, "a");
            // (20100313 had a method here that only marked the chunk for redraw: not needed on a server)
            chunkRecalc = chunkClass.getMethod("a");
            generator = generatorClass.getConstructor(worldClass, long.class).newInstance(world, seed);
            generatorChunk = generatorClass.getMethod("b", int.class, int.class);
            // trees are planted by the generator's populate step (it writes into the chunks around the new one)
            generatorPopulate = generatorClass.getMethod("a", providerClass, int.class, int.class);
            chunkTiles = field(chunkClass, "n");
            chunkTileGet = chunkClass.getMethod("d", int.class, int.class, int.class);
            Class<?> stackClass = Class.forName("net.minecraft.a.b.m", true, cl);
            stackCtor = stackClass.getConstructor(int.class, int.class);
            stackCount = stackClass.getField("a");
            stackId = stackClass.getField("c");
            stackDamage = stackClass.getField("d");
            Class<?> chestClass = Class.forName("net.minecraft.a.a.b.a.c", true, cl);
            Class<?> furnaceClass = Class.forName("net.minecraft.a.a.b.a.b", true, cl);
            chestSlots = field(chestClass, "e");
            furnaceSlots = field(furnaceClass, "e");
            furnaceInts = new Field[]{field(furnaceClass, "f"), field(furnaceClass, "g"), field(furnaceClass, "h")};
            this.chestClass = chestClass;
            signClass = Class.forName("net.minecraft.a.a.b.a.d", true, cl);
            signText = field(signClass, "e");
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
                        // the game's "work off the queued saves" (true: more is left): there is nothing to save here
                        return m.getReturnType() == boolean.class ? Boolean.FALSE : null;
                    }
                    if (args.length == 1) {
                        return null; // save(boolean): the bridge writes its chunks itself
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
            field(worldClass, "w").set(world, provider);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not start the inf-20100608 world code: " + ex, ex);
        }
        try {
            timeField.setLong(world, time);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        if (!existing) {
            spawn = findSpawn();
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
            // the older world has the flat folder of the builds before 327, or the Alpha layout (327 itself)
            legacyPending.addAll(WorldConverter.chunkFiles(legacyDir));
        }
        int done = 0;
        long batchStart = System.nanoTime();
        while (done < max && !legacyPending.isEmpty()) {
            if (done > 0 && System.nanoTime() - batchStart > 20000000L) {
                break; // a tick spends at most about 20 ms on this: a big world is converted a few chunks at a time
            }
            File f = legacyPending.poll();
            int[] xz = WorldConverter.chunkCoords(legacyDir, f);
            if (xz == null) {
                continue;
            }
            int cx = xz[0], cz = xz[1];
            if (chunkFile(cx, cz).isFile()) {
                continue; // visited and saved already
            }
            long key = mapKey(gameCoord(cx), gameCoord(cz));
            boolean inMemory = chunks.containsKey(key);
            if (inMemory && !(upgradeOld && unpopulated.contains(key))) {
                continue; // in memory: it is written with the next save
            }
            // A chunk that has its light and is not to be populated is copied as the file it is (the tags are those of the new
            // layout). Else the chunk is read (its light is worked out where the older build had none), populated if asked, and
            // written; a chunk that was not in memory is dropped again, so a big world takes no memory.
            if (!upgradeOld && !inMemory && copyLegacyFile(f, cx, cz)) {
                done++;
                continue;
            }
            try {
                Object chunk = chunkFor(gameCoord(cx), gameCoord(cz));
                writeChunk(chunk);
                if (!inMemory) {
                    chunks.remove(key);
                    fromFile.remove(key);
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Could not take over " + f.getName() + ": " + ex, ex);
            }
            done++;
        }
        if (done == 0 && legacyPending.isEmpty()) {
            legacyDir = null; // everything is taken over: the old folder is not read any more
        }
        return done;
    }

    /** Copies a chunk file of an older build into the new layout when it needs no change; false when it does. */
    private boolean copyLegacyFile(File f, int cx, int cz) {
        try {
            Map<String, Object> level = castStringMap(TW227Nbt.readFile(f).get("Level"));
            Object blocks = level.get("Blocks"), sky = level.get("SkyLight"), meta = level.get("Data");
            if (!(blocks instanceof byte[]) || ((byte[]) blocks).length != BLOCKS || !(sky instanceof byte[])
                    || ((byte[]) sky).length != NIBBLES || allFull((byte[]) sky) || !(meta instanceof byte[])
                    || level.get("BlockLight") == null || !(level.get("HeightMap") instanceof byte[])) {
                return false; // no light (inf-20100227-1433) or damaged: the game's own code works it out
            }
            File out = chunkFile(cx, cz);
            File dir = out.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            net.thewellodev.server.world.WorldConverter.copyFile(f, out);
            return true;
        } catch (Exception ex) {
            return false;
        }
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
            timeField.setLong(world, t);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Called once per server tick with the block positions of the players: the world keeps no entities or scheduled
     * updates here, so the only work is to drop far chunks when many are in memory.
     */
    private String profile;

    public String tickProfile() {
        return profile;
    }

    private final TWTick.Host sandHost = new TWTick.Host() {
        public int block(int x, int y, int z) {
            return getBlockId(x, y, z);
        }

        public void set(int x, int y, int z, int id) {
            setBlock(x, y, z, id, 0, true);
        }
    };

    public void tick(int[] playerBlockPositions) {
        long t0 = System.nanoTime();
        drainLight(300, t0 + 10000000L); // the queue is worked off for at most 10 ms in a tick (one call can do 100000 steps)
        long t1 = System.nanoTime();
        TWTick.sandTick(sandHost); // sand and gravel fall by cells
        long t2 = System.nanoTime();
        blockTicks();
        long t3 = System.nanoTime();
        profile = "light " + (t1 - t0) / 1000000L + " ms, sand " + (t2 - t1) / 1000000L + " ms (" + TWTick.fallingCount()
                + " falling), block ticks " + (t3 - t2) / 1000000L + " ms";
        if (playerBlockPositions != null) {
            lastPlayers = playerBlockPositions.clone();
        }
        time++; // the day and night cycle runs on the server; the clients get the time now and then
        try {
            timeField.setLong(world, time);
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
        data.put("SizeOnDisk", sizeOnDisk());
        data.put("LastPlayed", System.currentTimeMillis());
        // The single player game puts its player into the world at load: a player at the spawn point lets it open the world
        data.put("Player", playerTag());
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("Data", data);
        TW227Nbt.writeFile(new File(levelDir, "level.dat"), root);
    }

    /** The tags of the game's player entity (see the entity's save method): at the spawn point, with an empty inventory. */
    private Map<String, Object> playerTag() {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("Pos", new ArrayList<Object>(java.util.Arrays.asList(spawn[0] + 0.5, spawn[1] + 1.62, spawn[2] + 0.5)));
        p.put("Motion", new ArrayList<Object>(java.util.Arrays.asList(0.0, 0.0, 0.0)));
        p.put("Rotation", new ArrayList<Object>(java.util.Arrays.asList(0f, 0f)));
        p.put("FallDistance", 0f);
        p.put("Fire", (short) -20);
        p.put("Air", (short) 300);
        p.put("Health", (short) 20);
        p.put("HurtTime", (short) 0);
        p.put("DeathTime", (short) 0);
        p.put("AttackTime", (short) 0);
        p.put("Inventory", new ArrayList<Object>());
        return p;
    }

    /** The size of the chunk files (the game shows it in its world list). */
    private long sizeOnDisk() {
        long sum = 0;
        File[] dirs = levelDir.listFiles();
        if (dirs == null) {
            return 0;
        }
        for (File a : dirs) {
            File[] bs = a.isDirectory() ? a.listFiles() : null;
            if (bs == null) {
                continue;
            }
            for (File b : bs) {
                File[] fs = b.isDirectory() ? b.listFiles() : null;
                if (fs == null) {
                    continue;
                }
                for (File f : fs) {
                    sum += f.length();
                }
            }
        }
        return sum;
    }

    /** The file of a chunk in the Alpha layout: X mod 64 and Z mod 64 (base 36) are folders, the name has X and Z in base 36. */
    private File chunkFile(int cx, int cz) {
        File dir = new File(new File(levelDir, Integer.toString(cx & 63, 36)), Integer.toString(cz & 63, 36));
        return new File(dir, "c." + Integer.toString(cx, 36) + "." + Integer.toString(cz, 36) + ".dat");
    }

    /** The chunk file of an older world (in either layout), or a file that does not exist. */
    private static File legacyChunkFile(File dir, int cx, int cz) {
        File found = WorldConverter.findChunkFile(dir, cx, cz);
        return found != null ? found : new File(dir, "c." + cx + "." + cz + ".dat");
    }

    /** Reads a chunk file into a game chunk, or null when there is none. */
    private Object readChunk(int gx, int gz) throws Exception {
        lastReadLegacy = false;
        lastReadPopulatedTag = null;
        File file = chunkFile(gx, gz);
        if (!file.isFile() && legacyDir != null) {
            // not in this world yet: take it over from the older world (its files have the same tags and block numbers;
            // the full-light sky light of a chunk of inf-20100227-1433 is replaced by the game's own below)
            file = legacyChunkFile(legacyDir, gx, gz);
            lastReadLegacy = file.isFile();
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
        Object populatedTag = level.get("TerrainPopulated");
        lastReadPopulatedTag = populatedTag instanceof Number ? Boolean.valueOf(((Number) populatedTag).intValue() != 0) : null;
        // a chunk that the game saved before it was populated says 0 and is populated here when it is needed; a chunk without
        // the tag is one of an older build or of 20100327: it counts as populated (it is never populated again)
        chunkPopulated.setBoolean(chunk, lastReadPopulatedTag == null || lastReadPopulatedTag.booleanValue());
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
        level.put("TerrainPopulated", (byte) 1); // the bridge writes only chunks that are final (populated)
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
        File out = chunkFile(cx, cz);
        File dir = out.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new java.io.IOException("Could not create " + dir);
        }
        TW227Nbt.writeFile(out, root);
    }

    // ----- chunks -----------------------------------------------------------------------------------------

    private static long mapKey(int gx, int gz) {
        return ((long) gx << 32) | (gz & 0xFFFFFFFFL);
    }

    /** The game's number of a chunk coordinate (in this build the game uses the real, signed numbers). */
    private static int gameCoord(int chunkCoord) {
        return chunkCoord;
    }

    private static int signedCoord(int gameCoord) {
        return gameCoord;
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
            boolean upgrade = !fresh && ((lastReadLegacy && upgradeOld)
                    || (lastReadPopulatedTag != null && !lastReadPopulatedTag.booleanValue()));
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
            int px = c[0], pz = c[1];
            if (chunks.containsKey(mapKey(px, pz)) && !fromFile.contains(mapKey(px, pz))) {
                try {
                    generatorPopulate.invoke(generator, worldProvider, px, pz);
                    chunkPopulated.setBoolean(chunks.get(mapKey(px, pz)), true);
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
                || id == 55 || id == 59 || id == 65) {
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

    /**
     * The spawn point of a new world, as the game's own single player makes it: x 0 and z 0, starting at height 64 and moved up
     * until there is free space (no solid block at the feet or at the head). Water is free space, so a world with sea at the
     * origin starts the player in the water, as in the single player.
     */
    private int[] findSpawn() {
        int y = 64;
        while (y < 126 && (isSolid(0, y, 0) || isSolid(0, y + 1, 0))) {
            y++;
        }
        return new int[]{0, y, 0};
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
                timeField.setLong(world, time);
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
        drainLight(max, Long.MAX_VALUE);
    }

    private void drainLight(int max, long deadline) {
        deferPopulate++;
        try {
            java.util.List<?> queue = (java.util.List<?>) lightQueue.get(world);
            for (int i = 0; i < max && !queue.isEmpty() && System.nanoTime() < deadline; i++) {
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
        if (!isChestId(id) && !isFurnaceId(id) && !isSignId(id)) {
            return null;
        }
        chunkFor(gameCoord(x >> 4), gameCoord(z >> 4));
        Object tile = chunkTileGet.invoke(chunkAtBlock(x, z), x & 15, y, z & 15);
        return tile != null && (chestClass.isInstance(tile) || furnaceClass.isInstance(tile) || signClass.isInstance(tile))
                ? tile : null;
    }

    /** The sign (it stands on the ground: this build has no signs on walls). */
    private static boolean isSignId(int id) {
        return id == 63;
    }

    /** One line of a sign as the server keeps it: at most 15 characters, no control characters. */
    private static String signLine(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 15; i++) {
            if (s.charAt(i) >= 32 && s.charAt(i) != 127) {
                b.append(s.charAt(i));
            }
        }
        return b.toString();
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
        if (signClass.isInstance(tile) && isSignId(block)) {
            Map<String, Object> sign = new LinkedHashMap<String, Object>();
            sign.put("id", "Sign");
            sign.put("x", x);
            sign.put("y", y);
            sign.put("z", z);
            String[] lines = (String[]) signText.get(tile);
            for (int i = 0; i < 4; i++) {
                sign.put("Text" + (i + 1), signLine(lines[i]));
            }
            return sign;
        }
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
        if (signClass.isInstance(tile)) {
            String[] lines = (String[]) signText.get(tile);
            for (int i = 0; i < 4; i++) {
                lines[i] = signLine(tag.get("Text" + (i + 1)));
            }
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
        if (signClass.isInstance(tile)) {
            java.io.ByteArrayOutputStream so = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream signOut = new java.io.DataOutputStream(so);
            signOut.writeByte(3);
            String[] lines = (String[]) signText.get(tile);
            for (int i = 0; i < 4; i++) {
                signOut.writeUTF(signLine(lines[i]));
            }
            return so.toByteArray();
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
            if (signClass.isInstance(tile)) {
                if (data[0] != 3) {
                    return null;
                }
                java.io.DataInputStream signIn = new java.io.DataInputStream(
                        new java.io.ByteArrayInputStream(data, 1, data.length - 1));
                String[] fresh = new String[4];
                for (int i = 0; i < 4; i++) {
                    fresh[i] = signLine(signIn.readUTF());
                }
                System.arraycopy(fresh, 0, (String[]) signText.get(tile), 0, 4);
                return tileBytes(tile);
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
