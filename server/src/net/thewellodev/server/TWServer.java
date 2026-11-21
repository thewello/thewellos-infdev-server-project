package net.thewellodev.server;

import net.thewellodev.api.ItemStack;
import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.TheWello;
import net.thewellodev.api.World;
import net.thewellodev.api.command.Command;
import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api.command.ConsoleCommandSender;
import net.thewellodev.api.command.PluginCommand;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api.event.block.BlockBreakEvent;
import net.thewellodev.api.event.block.BlockPlaceEvent;
import net.thewellodev.api.event.player.PlayerAttackEvent;
import net.thewellodev.api.event.player.PlayerChatEvent;
import net.thewellodev.api.event.player.PlayerHealthEvent;
import net.thewellodev.api.event.player.PlayerInventoryEvent;
import net.thewellodev.api.event.player.PlayerDamageByPlayerEvent;
import net.thewellodev.api.event.player.PlayerDeathEvent;
import net.thewellodev.api.event.player.PlayerDropItemEvent;
import net.thewellodev.api.event.player.PlayerPickupItemEvent;
import net.thewellodev.api.event.player.PlayerRespawnEvent;
import net.thewellodev.api.event.player.PlayerCommandPreprocessEvent;
import net.thewellodev.api.event.player.PlayerJoinEvent;
import net.thewellodev.api.event.player.PlayerKickEvent;
import net.thewellodev.api.event.player.PlayerLoginEvent;
import net.thewellodev.api.event.player.PlayerMoveEvent;
import net.thewellodev.api.event.player.PlayerQuitEvent;
import net.thewellodev.api.event.server.ServerCommandEvent;
import net.thewellodev.api.event.world.ItemSpawnEvent;
import net.thewellodev.api.plugin.PluginManager;
import net.thewellodev.api.util.ChatColor;
import net.thewellodev.protocol.GameVersion;
import net.thewellodev.protocol.Protocol;
import net.thewellodev.server.command.BuiltinCommands;
import net.thewellodev.server.command.CommandMap;
import net.thewellodev.server.command.ConsoleSender;
import net.thewellodev.server.entity.TWPlayer;
import net.thewellodev.server.game.GameBridge;
import net.thewellodev.server.game.GameClassLoader;
import net.thewellodev.server.net.PlayerConnection;
import net.thewellodev.server.plugin.SimplePluginManager;
import net.thewellodev.server.plugin.TWScheduler;
import net.thewellodev.server.world.TWBlock;
import net.thewellodev.server.world.TWWorld;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The server. */
public class TWServer implements Server {

    public static final String NAME = "TheWello's Infdev Server";
    public static final String VERSION = "2.1.0";

    private final Logger logger;
    private final File dir;
    private final ServerConfig config;
    private final ConsoleSender console = new ConsoleSender(this);
    private final CommandMap commandMap = new CommandMap();
    private final SimplePluginManager pluginManager = new SimplePluginManager(this);
    private final TWScheduler scheduler;

    private GameBridge bridge;
    private TWWorld world;
    private ServerSocket serverSocket;
    private Thread mainThread;
    private volatile boolean running;
    private volatile boolean stopped;
    private final List<Runnable> stopListeners = new CopyOnWriteArrayList<Runnable>();

    private final ConcurrentLinkedQueue<PlayerConnection> newConnections = new ConcurrentLinkedQueue<PlayerConnection>();
    private final List<PlayerConnection> pending = new ArrayList<PlayerConnection>();
    private final Map<String, TWPlayer> players = new LinkedHashMap<String, TWPlayer>();
    private volatile List<TWPlayer> playerSnapshot = Collections.emptyList();
    private final ConcurrentLinkedQueue<String[]> consoleQueue = new ConcurrentLinkedQueue<String[]>();
    /** Work to run on the main thread (for requests from other threads such as the GUI). */
    private final ConcurrentLinkedQueue<Runnable> mainTasks = new ConcurrentLinkedQueue<Runnable>();
    private final Map<Long, Set<Integer>> changedBlocks = new LinkedHashMap<Long, Set<Integer>>();
    private int nextEntityId = 1;

    /** A shared item on the ground. */
    private static final class NetItem {
        int id, type, count, data;
        double x, y, z;
        long spawnTick;
    }

    private final Map<Integer, NetItem> items = new LinkedHashMap<Integer, NetItem>();
    private int nextItemId = 1;
    private boolean pvp;
    private boolean onlineMode;
    private boolean useUpnp;
    private volatile net.thewellodev.server.net.NetworkHelper network;
    private static final long ITEM_LIFETIME_TICKS = 20L * 60 * 5;

    // Synchronized because the GUI thread reads these too
    private final Set<String> ops = Collections.synchronizedSet(new TreeSet<String>(String.CASE_INSENSITIVE_ORDER));
    private final Set<String> banned = Collections.synchronizedSet(new TreeSet<String>(String.CASE_INSENSITIVE_ORDER));
    private final Set<String> whitelist = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Set<String>> extraPermissions = new HashMap<String, Set<String>>();

    // settings
    private int port;
    private String ip;
    private String motd;
    private int maxPlayers;
    private int viewDistance;
    private boolean whitelistEnabled;
    private int difficulty;
    private boolean spawnMonsters, spawnAnimals;
    private int mobCapMonsters = 100, mobCapAnimals = 20;
    /** Players that have a proxy in the world. */
    private Set<String> proxied = new java.util.HashSet<String>();
    /** Mob network id -> type and last position (for the attack range check). */
    private final Map<Integer, net.thewellodev.server.game.NetEntity> mobs = new java.util.HashMap<Integer, net.thewellodev.server.game.NetEntity>();
    private int spawnProtection;
    private int autosaveTicks;
    private String levelName;
    /** The build this server runs (game-version in server.properties). */
    private GameVersion gameVersion;
    /** Folder that holds the worlds of the selected build. */
    private File worldRoot;

    // statistics
    private long tickCount;
    private final long startTime = System.currentTimeMillis();
    private final long[] tickTimes = new long[100];
    private volatile double tps = 20.0;
    private volatile double mspt;
    private volatile int statChunks;
    private volatile int statEntities;

    public TWServer(File dir, Logger logger) {
        this.dir = dir;
        this.logger = logger;
        this.config = new ServerConfig(new File(dir, "server.properties"));
        this.scheduler = new TWScheduler(logger);
    }

    // ================================================================ startup

    public void start() throws Exception {
        String versionId = config.getString("game-version", GameVersion.INF_20100630_1340.id);
        gameVersion = GameVersion.byId(versionId);
        if (gameVersion == null) {
            throw new IOException("Unknown game-version '" + versionId + "' in server.properties. Known builds: "
                    + GameVersion.idList());
        }
        if (!gameVersion.supported) {
            throw new IOException(gameVersion.id + " is recognised, but its server code is not finished yet. "
                    + "Set game-version in server.properties to a supported build.");
        }
        logger.info("TheWello's Infdev Server " + VERSION + " starting (" + gameVersion.id + ", Java "
                + System.getProperty("java.version") + ")");
        port = config.getInt("server-port", Protocol.DEFAULT_PORT);
        ip = config.getString("server-ip", "");
        motd = config.getString("motd", "&eA TheWello's Infdev server");
        maxPlayers = config.getInt("max-players", 20);
        viewDistance = Math.max(2, Math.min(15, config.getInt("view-distance", 6)));
        levelName = config.getString("level-name", "world");
        if (!levelName.matches("[A-Za-z0-9_ -]{1,64}")) {
            // The name is used as a folder name: no dots or path separators allowed
            logger.warning("Invalid level-name '" + levelName + "' (letters, digits, space, _ and - only, max 64). Using 'world'.");
            levelName = "world";
        }
        String seedStr = config.getString("level-seed", "");
        whitelistEnabled = config.getBoolean("white-list", false);
        difficulty = Math.max(0, Math.min(3, config.getInt("difficulty", 2)));
        spawnMonsters = config.getBoolean("spawn-monsters", true);
        spawnAnimals = config.getBoolean("spawn-animals", true);
        mobCapMonsters = config.getInt("mob-cap-monsters", 100);
        mobCapAnimals = config.getInt("mob-cap-animals", 20);
        spawnProtection = config.getInt("spawn-protection", 0);
        pvp = config.getBoolean("pvp", true);
        onlineMode = config.getBoolean("online-mode", true);
        useUpnp = config.getBoolean("upnp", true);
        autosaveTicks = Math.max(1, config.getInt("autosave-minutes", 5)) * 60 * 20;
        String gameJarName = config.getString("game-jar", "auto");
        config.saveIfDirty();

        loadList("ops.txt", ops);
        loadList("banned-players.txt", banned);
        loadList("white-list.txt", whitelist);
        loadPermissions();

        // Game jar: it must belong to the selected build
        File gameJar = findGameJar(gameJarName, gameVersion);
        if (gameJar == null) {
            throw new IOException("No game jar for " + gameVersion.id + " was found. Put the patched client jar of that build "
                    + "(or its original jar) in the server folder, or set game-jar in server.properties.");
        }
        logger.info("Loading game code: " + gameJar.getName());
        GameClassLoader gcl = new GameClassLoader(gameJar, TWServer.class.getClassLoader(), gameVersion);
        bridge = gcl.createBridge();
        bridge.setBlockListener(new GameBridge.BlockListener() {
            public void onBlockChanged(int x, int y, int z) {
                markChanged(x, y, z);
            }
        });

        Long seed = null;
        if (seedStr.length() > 0) {
            try {
                seed = Long.parseLong(seedStr);
            } catch (NumberFormatException e) {
                seed = (long) seedStr.hashCode();
            }
        }
        logger.info("Loading world: '" + levelName + "'" + (seed != null ? " (seed " + seed + ")" : ""));
        mainThread = Thread.currentThread();
        startFreezeWatch();
        worldRoot = prepareWorldRoot();
        // a world of an older build is taken over: its chunks are converted when first needed (the old world stays as it is)
        bridge.setLegacyWorld(net.thewellodev.server.world.WorldConverter.prepare(new File(dir, "worlds"), levelName,
                gameVersion, logger));
        bridge.load(worldRoot, levelName, seed);
        gcl.verifyHooks();
        bridge.setMobSettings(difficulty, spawnMonsters, spawnAnimals);
        bridge.setMobCaps(mobCapMonsters, mobCapAnimals);
        bridge.setEntityListener(new GameBridge.EntityListener() {
            public void onPlayerDamaged(String player, int attackerId, int damage) {
                handleMobDamage(player, attackerId, damage);
            }

            public void onSound(String name, double x, double y, double z, float volume, float pitch) {
                broadcastSound(name, x, y, z, volume, pitch);
            }

            public void onParticle(String name, double x, double y, double z, double vx, double vy, double vz) {
                broadcastParticle(name, x, y, z, vx, vy, vz);
            }

            public void onPlayerHitByPlayer(String victim, String attacker, int damage) {
                TWPlayer v = players.get(victim.toLowerCase(java.util.Locale.ENGLISH));
                TWPlayer a = players.get(attacker.toLowerCase(java.util.Locale.ENGLISH));
                if (v != null && a != null) {
                    applyPvp(a, v, damage);
                }
            }

            public void onGiveItem(String player, int id, int count, int damage) {
                TWPlayer p = players.get(player.toLowerCase(java.util.Locale.ENGLISH));
                if (p != null && p.joined && bridge.isValidItem(id)) {
                    p.getConnection().sendGiveItem(id, Math.min(64, count), damage);
                }
            }

            public void onItemSpawned(double x, double y, double z, double mx, double my, double mz, int id,
                                      int count, int data) {
                if (bridge.isValidItem(id) && count > 0 && items.size() < 2000) {
                    spawnNetItem(x, y, z, mx, my, mz, id, Math.min(64, count), data);
                }
            }
        });
        world = new TWWorld(this, bridge, levelName);
        int[] sp = bridge.getSpawn();
        logger.info("Seed: " + bridge.getSeed() + ", spawn: " + sp[0] + " " + sp[1] + " " + sp[2]);
        logger.info("Preparing spawn area...");
        long t0 = System.currentTimeMillis();
        int r = Math.min(viewDistance, 4);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                bridge.getChunkData((sp[0] >> 4) + dx, (sp[2] >> 4) + dz);
            }
        }
        changedBlocks.clear();
        logger.info("Spawn ready (" + (System.currentTimeMillis() - t0) + " ms)");

        TheWello.setServer(this);
        if (gameVersion == GameVersion.INF_20100227_1433) {
            // this build has its own plugin API (net.thewellodev.api227): a view of this server and its events
            net.thewellodev.server.api227.Server227Impl view = new net.thewellodev.server.api227.Server227Impl(this);
            net.thewellodev.api227.TheWello227.setServer(view);
            pluginManager.setTranslator(new net.thewellodev.server.api227.Events227(pluginManager, view.api()));
        }
        if (gameVersion == GameVersion.INF_20100313) {
            // this build has its own plugin API too (net.thewellodev.api313)
            net.thewellodev.server.api313.Server313Impl view = new net.thewellodev.server.api313.Server313Impl(this);
            net.thewellodev.api313.TheWello313.setServer(view);
            pluginManager.setTranslator(new net.thewellodev.server.api313.Events313(pluginManager, view.api()));
        }
        if (gameVersion == GameVersion.INF_20100320) {
            // this build has its own plugin API too (net.thewellodev.api320)
            net.thewellodev.server.api320.Server320Impl view = new net.thewellodev.server.api320.Server320Impl(this);
            net.thewellodev.api320.TheWello320.setServer(view);
            pluginManager.setTranslator(new net.thewellodev.server.api320.Events320(pluginManager, view.api()));
        }
        if (gameVersion == GameVersion.INF_20100316) {
            // this build has its own plugin API too (net.thewellodev.api316)
            net.thewellodev.server.api316.Server316Impl view = new net.thewellodev.server.api316.Server316Impl(this);
            net.thewellodev.api316.TheWello316.setServer(view);
            pluginManager.setTranslator(new net.thewellodev.server.api316.Events316(pluginManager, view.api()));
        }
        commandMap.disableBuiltins(gameVersion.unavailableCommands);
        BuiltinCommands.registerAll(this, commandMap);
        pluginManager.loadPlugins(new File(dir, "plugins"));
        pluginManager.enableAll();

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        InetSocketAddress bind = ip.length() > 0 ? new InetSocketAddress(InetAddress.getByName(ip), port)
                : new InetSocketAddress(port);
        serverSocket.bind(bind);
        Thread accept = new Thread("TW accept") {
            public void run() {
                acceptLoop();
            }
        };
        accept.setDaemon(true);
        accept.start();
        running = true;
        logger.info("Server listening on " + (ip.length() > 0 ? ip : "*") + ":" + port + ". Ready! "
                + "Type /help for commands.");
        if (!onlineMode) {
            logger.warning("online-mode=false: accounts are not verified; anyone can join with any name!");
        }
        final net.thewellodev.server.net.NetworkHelper nh = new net.thewellodev.server.net.NetworkHelper(port);
        network = nh;
        Thread nt = new Thread("TW network setup") {
            public void run() {
                nh.run(useUpnp);
                logger.info("LAN address: " + nh.lanIp + ":" + port);
                if (nh.publicIp != null) {
                    // Keep the internet IP out of the log and out of screenshots; use /ip in the console for the full address
                    logger.info("Internet address: ***.***.***.***:" + port + " (use 'Show' in the panel or /ip in the console for the full address)");
                }
                logger.info(nh.status);
            }
        };
        nt.setDaemon(true);
        nt.start();
    }

    /**
     * The jar that holds the game code of the selected build. An explicit name must identify as that build.
     * "auto" takes the first "inf-*.jar" in the server folder (or the BetaCraft versions folder) that identifies
     * as that build; jars of other builds are skipped.
     */
    private File findGameJar(String name, GameVersion want) throws IOException {
        List<File> dirs = new ArrayList<File>();
        dirs.add(dir);
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            dirs.add(new File(appdata, ".betacraft/versions"));
        }
        dirs.add(new File(System.getProperty("user.home"), "AppData/Roaming/.betacraft/versions"));
        if (!name.equalsIgnoreCase("auto")) {
            File f = new File(name);
            if (!f.isAbsolute()) {
                File found = null;
                for (File d : dirs) {
                    File c = new File(d, name);
                    if (c.isFile()) {
                        found = c;
                        break;
                    }
                }
                f = found;
            }
            if (f == null || !f.isFile()) {
                return null;
            }
            GameVersion got = GameVersion.identify(f);
            if (got != want) {
                throw new IOException(f.getName() + " is " + (got == null ? "not a known game jar" : got.id)
                        + ", but game-version is " + want.id + ".");
            }
            return f;
        }
        for (File d : dirs) {
            File[] list = d.listFiles();
            if (list == null) {
                continue;
            }
            java.util.Arrays.sort(list);
            for (File c : list) {
                if (!c.isFile() || !c.getName().startsWith("inf-") || !c.getName().endsWith(".jar")) {
                    continue;
                }
                GameVersion got;
                try {
                    got = GameVersion.identify(c);
                } catch (IOException e) {
                    continue; // not a readable jar
                }
                if (got == want) {
                    return c;
                }
                if (got != null) {
                    logger.info("Skipping " + c.getName() + " (it is " + got.id + ")");
                }
            }
        }
        return null;
    }

    /**
     * Worlds of each build are kept apart in worlds/&lt;build id&gt;/. Worlds saved by earlier versions of this
     * server (folder next to server.properties, build inf-20100630-1340) are moved there once.
     */
    private File prepareWorldRoot() {
        File root = new File(new File(dir, "worlds"), gameVersion.id);
        root.mkdirs();
        if (gameVersion == GameVersion.INF_20100630_1340) {
            File legacy = new File(dir, levelName);
            File target = new File(root, levelName);
            if (legacy.isDirectory() && !target.exists()) {
                if (legacy.renameTo(target)) {
                    logger.info("Moved the world '" + levelName + "' to " + target.getPath());
                } else {
                    logger.warning("Could not move the world '" + legacy.getPath() + "' to " + target.getPath()
                            + ". Move it by hand, or the server will create a new world.");
                }
            }
        }
        return root;
    }

    // Connection flood protection (used by the accept thread)
    private final Map<String, List<PlayerConnection>> connsByIp = new HashMap<String, List<PlayerConnection>>();
    private final Map<String, Long> lastConnectByIp = new HashMap<String, Long>();
    private final java.util.concurrent.ExecutorService authPool = java.util.concurrent.Executors.newFixedThreadPool(4,
            new java.util.concurrent.ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "TW auth");
                    t.setDaemon(true);
                    return t;
                }
            });

    /** @return whether a connection is accepted (limits per IP and in total) */
    private boolean admitConnection(String addr, PlayerConnection c) {
        // All loopback addresses (127.x.x.x) are local: tunnel programs
        // connect players from addresses such as 127.81.x.x and open their control connection first.
        boolean local;
        try {
            local = InetAddress.getByName(addr).isLoopbackAddress();
        } catch (Exception e) {
            local = false;
        }
        int total = 0;
        Iterator<Map.Entry<String, List<PlayerConnection>>> it = connsByIp.entrySet().iterator();
        while (it.hasNext()) {
            List<PlayerConnection> l = it.next().getValue();
            for (Iterator<PlayerConnection> li = l.iterator(); li.hasNext(); ) {
                if (li.next().isClosed()) {
                    li.remove();
                }
            }
            if (l.isEmpty()) {
                it.remove();
            } else {
                total += l.size();
            }
        }
        if (total >= maxPlayers + 20) {
            return false;
        }
        List<PlayerConnection> mine = connsByIp.get(addr);
        // Local address: comes from the same computer or from tunnel programs (all players show the same IP)
        if (mine != null && mine.size() >= (local ? maxPlayers + 5 : 3)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastConnectByIp.get(addr);
        if (!local && last != null && now - last < 1000) {
            return false; // more than one connection per second from the same IP
        }
        lastConnectByIp.put(addr, now);
        if (lastConnectByIp.size() > 5000) {
            lastConnectByIp.clear();
        }
        if (mine == null) {
            mine = new ArrayList<PlayerConnection>();
            connsByIp.put(addr, mine);
        }
        mine.add(c);
        return true;
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket s = serverSocket.accept();
                String addr = s.getInetAddress().getHostAddress();
                PlayerConnection c = new PlayerConnection(s, gameVersion.protocol);
                if (!admitConnection(addr, c)) {
                    s.close();
                    continue;
                }
                c.start();
                newConnections.add(c);
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    logger.warning("Could not accept a connection: " + e.getMessage());
                }
            }
        }
    }

    // ================================================================ main loop

    public void run() {
        mainThread = Thread.currentThread();
        long next = System.nanoTime();
        long lastWarn = 0;
        while (running) {
            long start = System.nanoTime();
            try {
                tick();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Unexpected error during a tick", t);
            }
            long end = System.nanoTime();
            tickTimes[(int) (tickCount % tickTimes.length)] = end - start;
            mspt = (end - start) / 1e6;
            next += 50000000L;
            long sleep = next - System.nanoTime();
            if (sleep < -2000000000L) {
                long behind = -sleep / 50000000L;
                if (System.currentTimeMillis() - lastWarn > 15000) {
                    logger.warning("Server is behind! Skipping " + behind + " ticks (" + (-sleep / 1000000L) + " ms)");
                    lastWarn = System.currentTimeMillis();
                }
                next = System.nanoTime();
            } else if (sleep > 0) {
                try {
                    Thread.sleep(sleep / 1000000L, (int) (sleep % 1000000L));
                } catch (InterruptedException ignored) {
                }
            }
        }
        doStop();
    }

    /** When the last tick began (for the freeze watch). */
    private volatile long tickStartedMs = System.currentTimeMillis();
    private Thread freezeWatch;

    /**
     * A server that hangs says nothing: this thread writes what the main thread is doing to logs/freeze-*.txt when
     * one tick takes more than 8 seconds (once for each freeze).
     */
    private void startFreezeWatch() {
        final Thread main = mainThread;
        freezeWatch = new Thread("TW freeze watch") {
            public void run() {
                long reported = 0;
                while (true) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    long started = tickStartedMs;
                    if (System.currentTimeMillis() - started > 8000 && reported != started) {
                        reported = started;
                        StringBuilder sb = new StringBuilder("The server tick has been running for "
                                + (System.currentTimeMillis() - started) / 1000 + " s. The main thread is here:\n");
                        for (StackTraceElement e : main.getStackTrace()) {
                            sb.append("    at ").append(e).append('\n');
                        }
                        File dirLogs = new File(dir, "logs");
                        dirLogs.mkdirs();
                        File f = new File(dirLogs, "freeze-" + new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                                .format(new java.util.Date()) + ".txt");
                        try {
                            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
                            w.write(sb.toString());
                            w.close();
                        } catch (IOException ex) {
                            // nothing more to do
                        }
                        logger.severe("The server seems to be frozen (a tick takes more than 8 s). Where it is stuck was "
                                + "written to " + f.getPath());
                    }
                }
            }
        };
        freezeWatch.setDaemon(true);
        freezeWatch.start();
    }

    private void tick() {
        tickStartedMs = System.currentTimeMillis();
        tickCount++;
        soundBudget = 0;
        particleBudget = 0;
        // 0) Tasks queued from other threads
        Runnable task;
        while ((task = mainTasks.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                logger.warning("Task error: " + e);
            }
        }
        // 1) console commands
        String[] cmd;
        while ((cmd = consoleQueue.poll()) != null) {
            handleConsoleLine(cmd[0]);
        }
        // 2) New connections / handshakes
        PlayerConnection nc;
        while ((nc = newConnections.poll()) != null) {
            pending.add(nc);
        }
        Iterator<PlayerConnection> pit = pending.iterator();
        while (pit.hasNext()) {
            PlayerConnection c = pit.next();
            if (c.isClosed()) {
                pit.remove();
            } else if (!c.handshakeDone) {
                if (System.currentTimeMillis() - c.getConnectedAt() > 10000) {
                    c.close("Handshake timed out");
                    pit.remove();
                }
            } else if (!onlineMode) {
                pit.remove();
                handleLogin(c, c.username);
            } else if (processAuth(c)) {
                pit.remove();
            }
        }
        // 3) Player packets
        for (TWPlayer p : new ArrayList<TWPlayer>(players.values())) {
            processPackets(p);
        }
        // 4) World
        int[] pos = new int[players.size() * 2];
        int i = 0;
        for (TWPlayer p : players.values()) {
            pos[i++] = Location.floor(p.x);
            pos[i++] = Location.floor(p.z);
        }
        try {
            syncProxies();
            bridge.tick(pos);
            trackMobs();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error in the world tick", t);
        }
        // 5) plugin scheduler
        scheduler.tick();
        if (tickCount % 20 == 0) {
            sweepLocks();
            if (sharesDrops()) {
                mobAuthorityTick();
            }
        }
        // 6) Send block changes
        flushBlockChanges();
        // 7) chunks, visibility, keepalive
        for (TWPlayer p : new ArrayList<TWPlayer>(players.values())) {
            if (p.getConnection().isClosed()) {
                removePlayer(p, p.getConnection().getCloseReason(), true);
                continue;
            }
            updateChunks(p);
            updateKeepAlive(p);
            checkHealth(p);
            p.ticksSinceTeleport++;
        }
        if (tickCount % 10 == 0) {
            updateVisibility();
        }
        if (tickCount % 20 == 0) {
            broadcastTime();
            updateTps();
            List<Integer> expired = new ArrayList<Integer>();
            for (NetItem it : items.values()) {
                if (tickCount - it.spawnTick > ITEM_LIFETIME_TICKS) {
                    expired.add(it.id);
                }
            }
            for (int id : expired) {
                removeNetItem(id, -1);
            }
            Set<Long> all = new java.util.HashSet<Long>();
            for (TWPlayer p : players.values()) {
                all.addAll(p.loadedChunks);
            }
            statChunks = all.size();
            statEntities = bridge.getLoadedEntityCount();
        }
        if (tickCount % autosaveTicks == 0) {
            logger.info("Auto-saving...");
            saveAll();
        }
    }

    private void updateTps() {
        long sum = 0;
        int n = (int) Math.min(tickCount, tickTimes.length);
        for (int i = 0; i < n; i++) {
            sum += tickTimes[i];
        }
        double avgMs = n == 0 ? 0 : sum / (double) n / 1e6;
        tps = Math.min(20.0, 1000.0 / Math.max(50.0, avgMs));
    }

    // ================================================================ join and leave

    private static boolean validName(String n) {
        return n != null && n.matches("[A-Za-z0-9_]{2,16}");
    }

    /**
     * Original-account verification steps. @return true if the connection finished without waiting (logged in or rejected)
     */
    private boolean processAuth(final PlayerConnection c) {
        long now = System.currentTimeMillis();
        if (c.authState == 0) {
            if (!validName(c.username)) {
                c.disconnect("Invalid user name");
                return true;
            }
            if (banned.contains(c.username)) {
                c.disconnect("You are banned from this server!");
                return true;
            }
            c.authServerId = net.thewellodev.server.net.SessionVerifier.newServerId();
            c.authState = 1;
            c.authStartedAt = now;
            c.sendAuthRequest(c.authServerId);
            return false;
        }
        if (now - c.authStartedAt > 30000) {
            c.disconnect("Account verification timed out");
            return true;
        }
        if (c.authState == 1) {
            PlayerConnection.InPacket in;
            while ((in = c.poll()) != null) {
                if (in.id != Protocol.AUTH_RESPONSE) {
                    continue;
                }
                if (!in.b) {
                    logger.info(c.username + " [" + c.getAddressString() + "] account verification failed: " + in.s);
                    c.disconnect("This server requires an original Minecraft account. " + (in.s == null ? "" : in.s));
                    return true;
                }
                c.authState = 2;
                final String user = c.username, sid = c.authServerId;
                // Limited pool: even in a flood of logins at most 4 requests to Mojang run at the same time
                authPool.execute(new Runnable() {
                    public void run() {
                        if (c.isClosed()) {
                            return;
                        }
                        try {
                            c.authVerifiedName = net.thewellodev.server.net.SessionVerifier.hasJoined(user, sid);
                        } catch (Exception e) {
                            c.authError = e.getMessage() == null ? e.toString() : e.getMessage();
                        }
                    }
                });
                break;
            }
            return false;
        }
        if (c.authVerifiedName != null) {
            handleLogin(c, c.authVerifiedName);
            return true;
        }
        if (c.authError != null) {
            logger.info(c.username + " [" + c.getAddressString() + "] not verified: " + c.authError);
            c.disconnect("Account not verified: " + c.authError);
            return true;
        }
        return false;
    }

    private void handleLogin(PlayerConnection c, String name) {
        String addr = c.getAddress() == null ? "?" : c.getAddress().getAddress().getHostAddress();
        if (!validName(name)) {
            c.disconnect("Invalid user name (2-16 characters: letters, digits or _)");
            return;
        }
        if (banned.contains(name)) {
            c.disconnect("You are banned from this server!");
            return;
        }
        if (whitelistEnabled && !whitelist.contains(name) && !ops.contains(name)) {
            c.disconnect("You are not on the white list!");
            return;
        }
        TWPlayer old = players.get(name.toLowerCase(java.util.Locale.ENGLISH));
        if (old != null) {
            removePlayer(old, "Logged in from another place", true);
            old.getConnection().disconnect("Logged in from another place");
        }
        if (players.size() >= maxPlayers) {
            c.disconnect("Server is full! (" + maxPlayers + ")");
            return;
        }
        TWPlayer p = new TWPlayer(this, c, name, nextEntityId++);
        PlayerLoginEvent le = pluginManager.callEvent(new PlayerLoginEvent(p, addr));
        if (!le.isAllowed()) {
            c.disconnect(le.getKickMessage() == null || le.getKickMessage().length() == 0 ? "Login refused"
                    : le.getKickMessage());
            return;
        }
        // position
        // The client keeps its own inventory per server address, so a first visit to this world may still carry items from another world
        boolean firstVisit = !playerFile(name).exists();
        loadInventoryFile(p);
        if (!loadPlayerData(p)) {
            int[] sp = bridge.getSpawn();
            double[] safe = safeSpawn(sp[0], sp[2]);
            p.x = safe[0];
            p.y = safe[1];
            p.z = safe[2];
        } else {
            // If the saved position is inside a block (it may have been filled in meanwhile), move up so the player does not suffocate
            int bx = Location.floor(p.x), bz = Location.floor(p.z);
            int by = Location.floor(p.y + 0.05); // floating-point margin: a player standing exactly on a block top is not treated as buried
            int feet = bridge.getBlockId(bx, by, bz), below = bridge.getBlockId(bx, by - 1, bz);
            boolean hot = feet == 10 || feet == 11 || feet == 51 || below == 10 || below == 11 || below == 51;
            // Body width 0.6: all four corner columns are checked, so the player is not buried in a neighbouring block.
            boolean stuck = by < 1;
            for (double ox : new double[]{-0.299, 0.299}) {
                for (double oz : new double[]{-0.299, 0.299}) {
                    int cx = Location.floor(p.x + ox), cz = Location.floor(p.z + oz);
                    if (bridge.isSolid(cx, Location.floor(p.y + 0.3), cz)
                            && bridge.isSolid(cx, Location.floor(p.y + 1.0), cz)) {
                        stuck = true;
                    }
                }
            }
            if (stuck || hot) {
                // First look for free space straight up the same column (do not leave the position); otherwise use the nearest safe spot
                boolean found = false;
                if (!hot) {
                    for (int y = Math.max(1, by); y < 125; y++) {
                        if (!bridge.isSolid(bx, y, bz) && !bridge.isSolid(bx, y + 1, bz) && bridge.isSolid(bx, y - 1, bz)) {
                            p.y = y + 0.1;
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    double[] safe = safeSpawn(bx, bz);
                    p.x = safe[0];
                    p.y = safe[1];
                    p.z = safe[2];
                }
            }
        }
        if (p.gameMode != 0 && !isOp(name)) {
            p.gameMode = 0; // creative mode is for operators only
        }
        players.put(name.toLowerCase(java.util.Locale.ENGLISH), p);
        rebuildSnapshot();
        p.joined = true;
        c.sendLogin(p.getEntityId(), bridge.getSeed(), p.x, p.y + TWPlayer.EYE_HEIGHT, p.z, p.yaw, p.pitch,
                bridge.getTime(), difficulty, motd);
        if (firstVisit) {
            p.clearInventory();
            savePlayerData(p);
            logger.info(name + " joined this world for the first time: inventory cleared.");
        }
        updateChunks(p);
        for (NetItem it : items.values()) {
            c.sendSpawnItem(it.id, it.x, it.y, it.z, 0, 0, 0, it.type, it.count, it.data);
        }
        if (p.gameMode != 0) {
            c.sendGameMode(p.gameMode);
        }
        if (sharesDrops()) {
            p.dropSlot = allocateSlot(p);
            p.dropSeqStart = 1 + new java.util.Random().nextInt(0xFFFF00);
            c.sendSlot(p.dropSlot, p.dropSeqStart);
        }
        if (gameVersion != null && gameVersion.protocol != Protocol.VERSION) {
            int[] saved = p.savedInv;
            c.sendInventorySync(saved == null ? new int[0] : filterInventory(saved));
        }
        logger.info(name + " [" + c.getAddressString() + "] joined (id " + p.getEntityId() + ") at "
                + String.format("%.1f %.1f %.1f", p.x, p.y, p.z));
        PlayerJoinEvent je = pluginManager.callEvent(new PlayerJoinEvent(p, "&e" + name + " joined the game."));
        if (je.getJoinMessage() != null) {
            broadcastMessage(je.getJoinMessage());
        }
        updateVisibility();
    }

    /** Removes the player from the list (does not close the connection). */
    private void removePlayer(TWPlayer p, String reason, boolean announce) {
        if (players.get(p.getName().toLowerCase(java.util.Locale.ENGLISH)) != p) {
            return;
        }
        players.remove(p.getName().toLowerCase(java.util.Locale.ENGLISH));
        releaseAllLocks(p);
        rebuildSnapshot();
        savePlayerData(p);
        for (TWPlayer o : players.values()) {
            if (o.visiblePlayers.remove(p.getEntityId())) {
                o.getConnection().sendRemovePlayer(p.getEntityId());
            }
        }
        logger.info(p.getName() + " left: " + reason);
        if (sharesDrops() && p.joined) {
            transferMobs(p);
        }
        if (announce) {
            PlayerQuitEvent qe = pluginManager.callEvent(new PlayerQuitEvent(p, "&e" + p.getName() + " left the game."));
            if (qe.getQuitMessage() != null) {
                broadcastMessage(qe.getQuitMessage());
            }
        }
        p.joined = false;
    }

    public void kickPlayer(TWPlayer p, String reason) {
        checkPrimary();
        PlayerKickEvent ke = pluginManager.callEvent(new PlayerKickEvent(p, reason, "&e" + p.getName() + " was kicked from the game."));
        if (ke.isCancelled()) {
            return;
        }
        p.getConnection().disconnect(ke.getReason());
        if (players.get(p.getName().toLowerCase(java.util.Locale.ENGLISH)) == p) {
            players.remove(p.getName().toLowerCase(java.util.Locale.ENGLISH));
            rebuildSnapshot();
            savePlayerData(p);
            for (TWPlayer o : players.values()) {
                if (o.visiblePlayers.remove(p.getEntityId())) {
                    o.getConnection().sendRemovePlayer(p.getEntityId());
                }
            }
            logger.info(p.getName() + " was kicked: " + ke.getReason());
            if (ke.getLeaveMessage() != null) {
                broadcastMessage(ke.getLeaveMessage());
            }
            p.joined = false;
        }
    }

    private void rebuildSnapshot() {
        playerSnapshot = Collections.unmodifiableList(new ArrayList<TWPlayer>(players.values()));
    }

    // ================================================================ packets

    private void processPackets(TWPlayer p) {
        PlayerConnection c = p.getConnection();
        PlayerConnection.InPacket in;
        int n = 0;
        while (n++ < 1000 && (in = c.poll()) != null) {
            if (players.get(p.getName().toLowerCase(java.util.Locale.ENGLISH)) != p) {
                return;
            }
            switch (in.id) {
                case Protocol.POSITION:
                    handleMove(p, in);
                    break;
                case Protocol.BLOCK_CHANGE:
                    handleBlockChange(p, in.i1, in.i2, in.i3, in.i4, in.i5);
                    break;
                case Protocol.CHAT:
                    if (!p.allow("chat", 3)) {
                        if (++p.chatSpamStrikes >= 15) {
                            p.kickPlayer("Stop spamming!");
                            return;
                        }
                        p.sendMessage("&cYou are typing too fast; your message was not sent.");
                        break;
                    }
                    handleChat(p, in.s);
                    break;
                case Protocol.KEEP_ALIVE:
                    if (in.i1 == p.keepAliveToken && p.keepAliveSentAt > 0) {
                        int ms = (int) (System.currentTimeMillis() - p.keepAliveSentAt);
                        p.ping = p.ping < 0 ? ms : (p.ping * 3 + ms) / 4;
                    }
                    break;
                case Protocol.DISCONNECT:
                    c.close(in.s == null || in.s.length() == 0 ? "Left the game" : in.s);
                    removePlayer(p, c.getCloseReason(), true);
                    return;
                case Protocol.DROP_ITEM:
                    handleDrop(p, in);
                    break;
                case Protocol.PICKUP_ITEM:
                    handlePickup(p, in.i1);
                    break;
                case Protocol.ATTACK:
                    handleAttack(p, in.i1, in.i2);
                    break;
                case Protocol.HEALTH:
                    handleHealthReport(p, Math.max(0, Math.min(20, in.i1)));
                    break;
                case Protocol.INVENTORY:
                    handleInventory(p, in.inv);
                    break;
                case Protocol.INVENTORY_REPORT:
                    handleInventoryReport(p, in.inv);
                    break;
                case Protocol.DROP_SPAWN:
                    handleDropSpawn(p, in);
                    break;
                case Protocol.DROP_REMOVE:
                    handleDropRemove(p, in);
                    break;
                case Protocol.EXPLOSION:
                    handleExplosion(p, in.yaw, in.x, in.y, in.z);
                    break;
                case Protocol.MOB_STATE:
                    handleMobState(p, in);
                    break;
                case Protocol.MOB_REMOVE:
                    handleMobRemove(p, in);
                    break;
                case Protocol.MOB_HIT:
                    handleMobHit(p, in);
                    break;
                case Protocol.DROP_CLAIM:
                    handleDropClaim(p, in.i1);
                    break;
                case Protocol.DEATH:
                    handleDeath(p);
                    break;
                case Protocol.RESPAWN:
                    handleRespawn(p);
                    break;
                case Protocol.PLACE_CART:
                    if (p.allow("cart", 4) && !p.dead && near(p, in.i1, in.i2, in.i3, 8) && !spawnProtected(p, in.i1, in.i3)) {
                        bridge.placeMinecart(in.i1, in.i2, in.i3);
                    }
                    break;
                case Protocol.RIDE:
                    if (p.allow("ride", 10) && !p.dead) {
                        net.thewellodev.server.game.NetEntity ne = mobs.get(in.i1);
                        if (in.i1 < 0 || (ne != null && near(p, ne.x, ne.y, ne.z, 8))) {
                            if (bridge.setRider(p.getName(), in.i1)) {
                                p.ridingNetId = in.i1 < 0 ? -1 : in.i1;
                                for (TWPlayer o : players.values()) {
                                    if (o != p && o.joined && o.visiblePlayers.contains(p.getEntityId())) {
                                        o.getConnection().sendMount(p.getEntityId(), p.ridingNetId);
                                    }
                                }
                            }
                        }
                    }
                    break;
                case Protocol.RIDE_INPUT:
                    bridge.setRiderInput(p.getName(), (float) in.x, (float) in.z);
                    break;
                case Protocol.SADDLE: {
                    net.thewellodev.server.game.NetEntity ne = mobs.get(in.i1);
                    if (p.allow("saddle", 4) && !p.dead && ne != null && near(p, ne.x, ne.y, ne.z, 6)) {
                        bridge.saddle(in.i1);
                    }
                    break;
                }
                case Protocol.FIRE_ARROW:
                    if (p.allow("arrow", 4) && !p.dead && p.heldItem == 261) {
                        bridge.fireArrow(p.getName());
                    }
                    break;
                case Protocol.TILE_DATA:
                    handleTile(p, in);
                    break;
                case Protocol.LOCK_REQUEST:
                    handleLockRequest(p, in.i1, in.i2, in.i3);
                    break;
                case Protocol.LOCK_RELEASE:
                    releaseLock(p, in.i1, in.i2, in.i3, true);
                    break;
                case Protocol.PLACE_PAINTING:
                    handlePainting(p, in);
                    break;
                case Protocol.DIG_PROGRESS:
                    handleDig(p, in.i1, in.i2, in.i3, in.i4);
                    break;
                case Protocol.HELD_ITEM:
                    if (!p.allow("held", 20)) {
                        break;
                    }
                    if (p.heldItem > 0 && bridge.foodHeal(p.heldItem) > 0 && in.i1 != p.heldItem) {
                        // The player may have eaten and dropped the item: remember it briefly to validate the health gain
                        p.lastFoodHeal = bridge.foodHeal(p.heldItem);
                        p.lastFoodTick = tickCount;
                    }
                    p.heldItem = in.i1 > 0 && bridge.isValidItem(in.i1) ? in.i1 : -1;
                    for (TWPlayer o : players.values()) {
                        if (o != p && o.visiblePlayers.contains(p.getEntityId())) {
                            o.getConnection().sendHeldItem(p.getEntityId(), p.heldItem);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void handleMove(TWPlayer p, PlayerConnection.InPacket in) {
        double nx = in.x, ny = in.y - TWPlayer.EYE_HEIGHT, nz = in.z;
        if (Double.isNaN(nx) || Double.isNaN(ny) || Double.isNaN(nz) || Double.isInfinite(nx) || Double.isInfinite(nz)
                || Math.abs(nx) > 3.2E7 || Math.abs(nz) > 3.2E7) {
            p.kickPlayer("Invalid position");
            return;
        }
        if (!p.allow("move", 40) || p.dead) {
            return;
        }
        double mdx = nx - p.x, mdz = nz - p.z;
        // Ignore stale position packets right after a teleport
        if (p.ticksSinceTeleport < 10) {
            if (mdx * mdx + mdz * mdz > 16) {
                return;
            }
        } else if (ny < -64 || ny > 512) {
            p.teleport(p.x, p.y, p.z, p.yaw, p.pitch);
            return;
        }
        Location from = p.getLocation();
        Location to = new Location(world, nx, ny, nz, in.yaw, in.pitch);
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()
                || from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()) {
            PlayerMoveEvent me = pluginManager.callEvent(new PlayerMoveEvent(p, from, to));
            if (me.isCancelled()) {
                p.teleport(from);
                return;
            }
            Location fin = me.getTo();
            if (fin.getX() != to.getX() || fin.getY() != to.getY() || fin.getZ() != to.getZ()) {
                p.teleport(fin);
                return;
            }
        }
        p.x = nx;
        p.y = ny;
        p.z = nz;
        p.yaw = in.yaw;
        p.pitch = in.pitch;
        p.onGround = in.b;
        onPlayerMoved(p);
    }

    /** The player moved: tell the players who can see them. */
    public void onPlayerMoved(TWPlayer p) {
        for (TWPlayer o : players.values()) {
            if (o != p && o.visiblePlayers.contains(p.getEntityId())) {
                o.getConnection().sendMovePlayer(p.getEntityId(), p.x, p.y + TWPlayer.EYE_HEIGHT, p.z, p.yaw, p.pitch);
            }
        }
    }

    /** Blocks that non-operators cannot place (they cannot be obtained in normal survival). */
    private static boolean restrictedPlace(int id) {
        return id == 7 /* bedrock */ || id == 52 /* mob spawner */;
    }

    private void handleBlockChange(TWPlayer p, int x, int y, int z, int id, int meta) {
        if (y < 0 || y >= 128) {
            return;
        }
        int cx = x >> 4, cz = z >> 4;
        if (!p.hasChunk(cx, cz)) {
            return;
        }
        meta &= 15;
        int oldId = bridge.getBlockId(x, y, z);
        int oldMeta = bridge.getBlockMeta(x, y, z);
        if (oldId == id && oldMeta == meta) {
            return;
        }
        // An invalid block id can crash other clients; the rate limit prevents world-edit spam
        boolean invalid = id != 0 && (id >= 256 || !bridge.isValidItem(id));
        boolean forbidden = !p.isOp() && (restrictedPlace(id) || (oldId == 7 && p.gameMode == 0));
        if (invalid || forbidden || !(takeBurst(p, x, y, z, id) || p.allow("block", 40)) || p.dead) {
            p.getConnection().sendBlockChange(x, y, z, oldId, oldMeta);
            if (invalid) {
                flagViolation(p, "invalid block id " + id);
            }
            return;
        }
        boolean protectedSpawn = false;
        if (spawnProtection > 0 && !p.isOp()) {
            int[] sp = bridge.getSpawn();
            protectedSpawn = Math.max(Math.abs(x - sp[0]), Math.abs(z - sp[2])) <= spawnProtection;
        }
        boolean cancel = protectedSpawn;
        if (!cancel && bridge.isLockableBlock(x, y, z)) {
            String holder = tileLocks.get(lockKey(x, y, z));
            TWPlayer h = holder == null ? null : players.get(holder.toLowerCase(java.util.Locale.ENGLISH));
            if (holder != null && !holder.equalsIgnoreCase(p.getName()) && h != null && h.joined) {
                p.sendMessage("&cThis chest/furnace is in use by another player (" + holder + ").");
                cancel = true; // the player gets the block back below
            }
        }
        if (!cancel) {
            TWBlock b = new TWBlock(world, x, y, z);
            if (id == 0) {
                cancel = pluginManager.callEvent(new BlockBreakEvent(b, p)).isCancelled();
            } else {
                cancel = pluginManager.callEvent(new BlockPlaceEvent(b, p, id, meta)).isCancelled();
            }
        }
        if (cancel) {
            p.getConnection().sendBlockChange(x, y, z, bridge.getBlockId(x, y, z), bridge.getBlockMeta(x, y, z));
            if (protectedSpawn) {
                p.sendMessage("&cSpawn protection: you cannot change blocks here.");
            }
            return;
        }
        if (id == 0 && oldId == 46) {
            bridge.igniteTnt(x, y, z); // TNT catches fire when broken (as in the original game)
        }
        boolean applied = bridge.setBlock(x, y, z, id, meta, true);
        // Other players hear the sound of this change (break: old block, place: new block)
        int soundBlock = id == 0 ? oldId : id;
        String sn = soundBlock > 0 ? bridge.blockSoundName(soundBlock, id == 0) : null;
        if (sn != null) {
            broadcastSoundExcept(p, sn, x + 0.5, y + 0.5, z + 0.5, bridge.blockSoundVolume(soundBlock),
                    bridge.blockSoundPitch(soundBlock));
        }
    }

    // ================================================================ items

    /** Creates a shared item and sends it to everyone. @return network id */
    public int spawnNetItem(double x, double y, double z, double mx, double my, double mz, int type, int count,
                            int data) {
        if (world != null) {
            ItemSpawnEvent ev = pluginManager.callEvent(new ItemSpawnEvent(new Location(world, x, y, z), type, count, data));
            if (ev.isCancelled() || ev.getAmount() <= 0) {
                return -1;
            }
            count = Math.min(64, ev.getAmount());
        }
        return placeNetItem(x, y, z, mx, my, mz, type, count, data);
    }

    /** Creates a shared item on the server without firing an event (for internal actions). */
    private int placeNetItem(double x, double y, double z, double mx, double my, double mz, int type, int count,
                             int data) {
        NetItem it = new NetItem();
        it.id = nextItemId++;
        it.type = type;
        it.count = count;
        it.data = data;
        it.x = x;
        it.y = y;
        it.z = z;
        it.spawnTick = tickCount;
        items.put(it.id, it);
        for (TWPlayer o : players.values()) {
            o.getConnection().sendSpawnItem(it.id, x, y, z, mx, my, mz, type, count, data);
        }
        return it.id;
    }

    public int getItemCount() {
        return items.size();
    }

    public int getItemCount(int chunkX, int chunkZ) {
        int n = 0;
        for (NetItem it : items.values()) {
            if (((int) Math.floor(it.x) >> 4) == chunkX && ((int) Math.floor(it.z) >> 4) == chunkZ) {
                n++;
            }
        }
        return n;
    }

    public int clearItems() {
        List<Integer> ids = new ArrayList<Integer>(items.keySet());
        for (int id : ids) {
            removeNetItem(id, -1);
        }
        return ids.size();
    }

    /** Item types that can be stacked (no damage, up to 64). */
    private boolean stackable(NetItem it) {
        if (it.data != 0) {
            return false;
        }
        if (it.type < 256) {
            return true;
        }
        switch (it.type) {
            case 260: case 262: case 263: case 264: case 265: case 266: case 280: case 287: case 288: case 289:
            case 295: case 296: case 297: case 318: case 319: case 320:
                return true;
            default:
                return false;
        }
    }

    public int mergeItems(double radius) {
        double r2 = radius * radius;
        List<NetItem> list = new ArrayList<NetItem>(items.values());
        int removed = 0;
        for (int i = 0; i < list.size(); i++) {
            NetItem a = list.get(i);
            if (a == null || !stackable(a) || a.count >= 64) {
                continue;
            }
            boolean changed = false;
            for (int j = i + 1; j < list.size() && a.count < 64; j++) {
                NetItem b = list.get(j);
                if (b == null || b.type != a.type || b.data != a.data) {
                    continue;
                }
                double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
                if (dx * dx + dy * dy + dz * dz > r2) {
                    continue;
                }
                int take = Math.min(64 - a.count, b.count);
                a.count += take;
                b.count -= take;
                changed = true;
                if (b.count <= 0) {
                    removeNetItem(b.id, -1);
                    list.set(j, null);
                    removed++;
                } else {
                    refreshNetItem(b);
                }
            }
            if (changed) {
                refreshNetItem(a);
            }
        }
        return removed;
    }

    /** If the amount changed, recreates the item on the clients (same position, same id). */
    private void refreshNetItem(NetItem it) {
        for (TWPlayer o : players.values()) {
            o.getConnection().sendInts(Protocol.REMOVE_ITEM, it.id, -1);
            o.getConnection().sendSpawnItem(it.id, it.x, it.y, it.z, 0, 0, 0, it.type, it.count, it.data);
        }
    }

    private void removeNetItem(int id, int collector) {
        if (items.remove(id) == null) {
            return;
        }
        for (TWPlayer o : players.values()) {
            o.getConnection().sendInts(Protocol.REMOVE_ITEM, id, collector);
        }
    }

    private void handleDrop(TWPlayer p, PlayerConnection.InPacket in) {
        if (in.i2 <= 0 || in.i2 > 64 || !bridge.isValidItem(in.i1) || in.i3 < 0 || in.i3 > 1000) {
            flagViolation(p, "invalid item");
            return;
        }
        // Spam and memory protection: at most 20 items per player per second, and at most 2000 items on the ground
        if (!p.allow("drop", 20) || p.dead) {
            return;
        }
        if (items.size() >= 2000) {
            Integer oldest = items.keySet().iterator().next();
            removeNetItem(oldest, -1);
        }
        double dx = in.x - p.x, dy = in.y - p.y, dz = in.z - p.z;
        if (dx * dx + dy * dy + dz * dz > 12 * 12) {
            return; // cannot create items far from the player
        }
        if (pluginManager.callEvent(new PlayerDropItemEvent(p, in.i1, in.i2, in.i3)).isCancelled()) {
            return;
        }
        double clamp = 1.5;
        spawnNetItem(in.x, in.y, in.z, Math.max(-clamp, Math.min(clamp, in.mx)), Math.max(-clamp, Math.min(clamp, in.my)),
                Math.max(-clamp, Math.min(clamp, in.mz)), in.i1, in.i2, in.i3);
    }

    private void handlePickup(TWPlayer p, int id) {
        NetItem it = items.get(id);
        if (it == null || p.dead || !p.allow("pickup", 40)) {
            return;
        }
        double dx = it.x - p.x, dz = it.z - p.z;
        if (dx * dx + dz * dz > 48 * 48) {
            return;
        }
        if (pluginManager.callEvent(new PlayerPickupItemEvent(p, id, it.type, it.count, it.data)).isCancelled()) {
            return;
        }
        removeNetItem(id, p.getEntityId());
    }

    /** Records a player who sends invalid or forged packets; kicks them if it keeps happening. */
    private void flagViolation(TWPlayer p, String what) {
        p.violations++;
        if (p.violations <= 3 || p.violations % 50 == 0) {
            logger.warning(p.getName() + " suspicious packet: " + what + " (" + p.violations + ")");
        }
        if (p.violations >= 200) {
            p.kickPlayer("Invalid packets");
        }
    }

    /** Forwards the breaking progress of a block to the other players who can see that chunk. */
    private void handleDig(TWPlayer p, int x, int y, int z, int stage) {
        if (y < 0 || y >= 128 || stage > 9 || stage < -1 || !p.allow("dig", 30)) {
            return;
        }
        if (stage >= 0) {
            double dx = x + 0.5 - p.x, dy = y + 0.5 - p.y, dz = z + 0.5 - p.z;
            if (dx * dx + dy * dy + dz * dz > 10 * 10) {
                return;
            }
        }
        long k = TWPlayer.chunkKey(x >> 4, z >> 4);
        for (TWPlayer o : players.values()) {
            if (o != p && o.loadedChunks.contains(k)) {
                o.getConnection().sendDig(p.getEntityId(), x, y, z, stage < 0 ? -1 : stage);
            }
        }
    }

    // ================================================================ PvP and death

    private TWPlayer playerById(int entityId) {
        for (TWPlayer o : players.values()) {
            if (o.getEntityId() == entityId) {
                return o;
            }
        }
        return null;
    }

    private void handleAttack(TWPlayer attacker, int targetId, int damage) {
        if (targetId >= Protocol.MOB_ID_BASE) {
            handleMobAttack(attacker, targetId, damage);
            return;
        }
        TWPlayer victim = playerById(targetId);
        if (victim == null || victim == attacker || victim.dead || attacker.dead || victim.gameMode == 1) {
            return; // creative players take no damage
        }
        if (!pvp) {
            if (tickCount - attacker.lastAttackTick > 40) {
                attacker.sendMessage("&cPvP is disabled on this server.");
            }
            attacker.lastAttackTick = tickCount;
            return;
        }
        // Victim damage cooldown: as in the game, at most one hit per 10 ticks (0.5 s)
        if (tickCount - victim.lastHurtTick < 10) {
            return;
        }
        double dx = victim.x - attacker.x, dy = victim.y - attacker.y, dz = victim.z - attacker.z;
        if (pluginManager.callEvent(new PlayerAttackEvent(attacker, victim, false, dx * dx + dy * dy + dz * dz,
                damage, attacker.heldItem)).isCancelled()) {
            return;
        }
        attacker.lastAttackTick = tickCount;
        // The strongest weapon (diamond sword) deals 10 damage; a hacked client cannot send more
        int dmg = Math.max(1, Math.min(10, damage));
        victim.lastHurtTick = tickCount;
        PlayerDamageByPlayerEvent ev = pluginManager.callEvent(new PlayerDamageByPlayerEvent(victim, attacker, dmg));
        if (ev.isCancelled() || ev.getDamage() <= 0) {
            return;
        }
        victim.lastAttacker = attacker;
        victim.lastAttackedAt = System.currentTimeMillis();
        victim.getConnection().sendDamage(attacker.getEntityId(), ev.getDamage());
        trackDamage(victim, ev.getDamage());
        for (TWPlayer o : players.values()) {
            if (o.visiblePlayers.contains(victim.getEntityId())) {
                o.getConnection().sendInts(Protocol.HURT_ANIMATION, victim.getEntityId());
            }
        }
    }

    // ================================================================ chests / furnaces / signs

    private void sendTiles(PlayerConnection c, int cx, int cz) {
        for (net.thewellodev.server.game.TileData t : bridge.getTiles(cx, cz)) {
            c.sendTile(t.x, t.y, t.z, t.data);
        }
    }

    private void handlePainting(TWPlayer p, PlayerConnection.InPacket in) {
        if (!p.allow("paint", 5) || p.dead) {
            return;
        }
        double dx = in.i1 + 0.5 - p.x, dy = in.i2 + 0.5 - p.y, dz = in.i3 + 0.5 - p.z;
        if (dx * dx + dy * dy + dz * dz > 10 * 10) {
            return;
        }
        if (!p.hasChunk(in.i1 >> 4, in.i3 >> 4) || spawnProtected(p, in.i1, in.i3)) {
            return;
        }
        bridge.placePainting(in.i1, in.i2, in.i3, in.i4, in.i5);
    }

    /** Spawn protection: non-operators cannot change blocks, tile entities or paintings in the protected area. */
    private boolean spawnProtected(TWPlayer p, int x, int z) {
        if (spawnProtection <= 0 || p.isOp()) {
            return false;
        }
        int[] sp = bridge.getSpawn();
        return Math.max(Math.abs(x - sp[0]), Math.abs(z - sp[2])) <= spawnProtection;
    }

    private static boolean near(TWPlayer p, double x, double y, double z, double r) {
        double dx = x - p.x, dy = y - p.y, dz = z - p.z;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private int soundBudget, particleBudget;

    private void broadcastSound(String name, double x, double y, double z, float vol, float pitch) {
        if (name == null || name.length() > 64 || soundBudget++ > 60) {
            return;
        }
        double range = Math.min(48.0, 16.0 * Math.max(1.0F, vol));
        for (TWPlayer p : players.values()) {
            double dx = p.x - x, dy = p.y - y, dz = p.z - z;
            if (p.joined && dx * dx + dy * dy + dz * dz <= range * range) {
                p.getConnection().sendSound(name, Protocol.toFixed(x), Protocol.toFixed(y), Protocol.toFixed(z), vol, pitch);
            }
        }
    }

    private void broadcastSoundExcept(TWPlayer except, String name, double x, double y, double z, float vol, float pitch) {
        if (soundBudget++ > 60) {
            return;
        }
        for (TWPlayer p : players.values()) {
            double dx = p.x - x, dy = p.y - y, dz = p.z - z;
            if (p != except && p.joined && dx * dx + dy * dy + dz * dz <= 16 * 16) {
                p.getConnection().sendSound(name, Protocol.toFixed(x), Protocol.toFixed(y), Protocol.toFixed(z), vol, pitch);
            }
        }
    }

    private void broadcastParticle(String name, double x, double y, double z, double vx, double vy, double vz) {
        if (name == null || name.length() > 32 || particleBudget++ > 80) {
            return;
        }
        for (TWPlayer p : players.values()) {
            double dx = p.x - x, dy = p.y - y, dz = p.z - z;
            if (p.joined && dx * dx + dy * dy + dz * dz <= 24 * 24) {
                p.getConnection().sendParticle(name, (float) x, (float) y, (float) z, (float) vx, (float) vy, (float) vz);
            }
        }
    }

    /** PvP rules: damage one player deals to another (shared by melee and arrows). */
    private void applyPvp(TWPlayer attacker, TWPlayer victim, int damage) {
        if (victim == attacker || victim.dead || attacker.dead || victim.gameMode == 1) {
            return;
        }
        if (!pvp) {
            if (tickCount - attacker.lastAttackTick > 40) {
                attacker.sendMessage("&cPvP is disabled on this server.");
            }
            attacker.lastAttackTick = tickCount;
            return;
        }
        if (tickCount - victim.lastHurtTick < 10) {
            return;
        }
        int dmg = Math.max(1, Math.min(10, damage));
        victim.lastHurtTick = tickCount;
        PlayerDamageByPlayerEvent ev = pluginManager.callEvent(new PlayerDamageByPlayerEvent(victim, attacker, dmg));
        if (ev.isCancelled() || ev.getDamage() <= 0) {
            return;
        }
        victim.lastAttacker = attacker;
        victim.lastAttackedAt = System.currentTimeMillis();
        victim.getConnection().sendDamage(attacker.getEntityId(), ev.getDamage());
        trackDamage(victim, ev.getDamage());
        for (TWPlayer o : players.values()) {
            if (o.visiblePlayers.contains(victim.getEntityId())) {
                o.getConnection().sendInts(Protocol.HURT_ANIMATION, victim.getEntityId());
            }
        }
    }

    // ---- chest/furnace locks: only one player can open one at a time (prevents item duplication)
    private final Map<Long, String> tileLocks = new java.util.HashMap<Long, String>();
    private final Map<Long, Long> tileLockTicks = new java.util.HashMap<Long, Long>();

    private static long lockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private void handleLockRequest(TWPlayer p, int x, int y, int z) {
        if (!p.allow("lock", 10) || p.dead || !near(p, x + 0.5, y + 0.5, z + 0.5, 8) || !bridge.isLockableBlock(x, y, z)) {
            p.getConnection().sendLockResult(x, y, z, false);
            return;
        }
        long k = lockKey(x, y, z);
        String holder = tileLocks.get(k);
        TWPlayer h = holder == null ? null : players.get(holder.toLowerCase(java.util.Locale.ENGLISH));
        if (holder != null && !holder.equalsIgnoreCase(p.getName()) && h != null && h.joined) {
            p.sendMessage("&cThis chest/furnace is in use by another player (" + holder + ").");
            p.getConnection().sendLockResult(x, y, z, false);
            return;
        }
        // Lock is free or its owner is gone: grant it. Send the current contents first, then allow the open.
        tileLocks.put(k, p.getName());
        tileLockTicks.put(k, tickCount);
        p.getConnection().sendTile(x, y, z, bridge.getTileNow(x, y, z));
        p.getConnection().sendLockResult(x, y, z, true);
    }

    private void releaseLock(TWPlayer p, int x, int y, int z, boolean broadcast) {
        long k = lockKey(x, y, z);
        String holder = tileLocks.get(k);
        if (holder == null || !holder.equalsIgnoreCase(p.getName())) {
            return;
        }
        tileLocks.remove(k);
        tileLockTicks.remove(k);
        if (broadcast) {
            // The contents may have changed: update the local copies of nearby players too
            byte[] data = bridge.getTileNow(x, y, z);
            for (TWPlayer o : players.values()) {
                if (o != p && o.joined && o.hasChunk(x >> 4, z >> 4)) {
                    o.getConnection().sendTile(x, y, z, data);
                }
            }
        }
    }

    private void releaseAllLocks(TWPlayer p) {
        Iterator<Map.Entry<Long, String>> it = tileLocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, String> e = it.next();
            if (e.getValue().equalsIgnoreCase(p.getName())) {
                tileLockTicks.remove(e.getKey());
                it.remove();
            }
        }
    }

    /** Clears locks whose owner left, moved away, died or held the lock too long. */
    private void sweepLocks() {
        Iterator<Map.Entry<Long, String>> it = tileLocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, String> e = it.next();
            TWPlayer h = players.get(e.getValue().toLowerCase(java.util.Locale.ENGLISH));
            long age = tickCount - tileLockTicks.get(e.getKey());
            long k = e.getKey();
            boolean drop = h == null || !h.joined || h.dead || age > 20 * 60 * 10;
            if (!drop) {
                // Location: checking the distance every tick is enough; the holder is released if it is more than 16 blocks
                // away (even if the player moved or the game was closed)
                int lx = (int) ((k >> 38) & 0x3FFFFFF), lz = (int) ((k >> 12) & 0x3FFFFFF);
                if ((lx & 0x2000000) != 0) {
                    lx |= ~0x3FFFFFF;
                }
                if ((lz & 0x2000000) != 0) {
                    lz |= ~0x3FFFFFF;
                }
                double dx = lx + 0.5 - h.x, dz = lz + 0.5 - h.z;
                drop = dx * dx + dz * dz > 16 * 16;
            }
            if (drop) {
                tileLockTicks.remove(k);
                it.remove();
            }
        }
    }

    private void handleTile(TWPlayer p, PlayerConnection.InPacket in) {
        if (!p.allow("tile", 10) || p.dead || in.i2 < 0 || in.i2 >= 128 || !p.hasChunk(in.i1 >> 4, in.i3 >> 4)
                || !near(p, in.i1 + 0.5, in.i2 + 0.5, in.i3 + 0.5, 16)) {
            return;
        }
        if (bridge.isLockableBlock(in.i1, in.i2, in.i3)) {
            String holder = tileLocks.get(lockKey(in.i1, in.i2, in.i3));
            if (holder == null || !holder.equalsIgnoreCase(p.getName())) {
                // No lock: chest/furnace contents are only accepted from the player who holds the lock
                p.getConnection().sendTile(in.i1, in.i2, in.i3, bridge.getTileNow(in.i1, in.i2, in.i3));
                return;
            }
        }
        double dx = in.i1 + 0.5 - p.x, dy = in.i2 + 0.5 - p.y, dz = in.i3 + 0.5 - p.z;
        if (dx * dx + dy * dy + dz * dz > 10 * 10) {
            return;
        }
        if (spawnProtected(p, in.i1, in.i3)) {
            p.getConnection().sendTile(in.i1, in.i2, in.i3, bridge.getTileNow(in.i1, in.i2, in.i3));
            return;
        }
        byte[] norm = bridge.applyTile(in.i1, in.i2, in.i3, in.data);
        if (norm == null) {
            return;
        }
        for (TWPlayer o : players.values()) {
            if (o != p && o.joined && o.hasChunk(in.i1 >> 4, in.i3 >> 4)) {
                o.getConnection().sendTile(in.i1, in.i2, in.i3, norm);
            }
        }
    }

    // ================================================================ mob'lar

    /** Updates player proxies (invisible entities that mobs target) in the server world. */
    private void syncProxies() {
        Set<String> now = new java.util.HashSet<String>();
        for (TWPlayer p : players.values()) {
            if (!p.joined) {
                continue;
            }
            bridge.updatePlayer(p.getName(), p.x, p.y, p.z, p.yaw, p.pitch, !p.dead && p.gameMode == 0);
            now.add(p.getName());
        }
        for (String n : proxied) {
            if (!now.contains(n)) {
                bridge.removePlayer(n);
            }
        }
        proxied = now;
    }

    /** Sends mob and arrow positions to the players in range. */
    private void trackMobs() {
        List<net.thewellodev.server.game.NetEntity> list = bridge.getNetEntities();
        for (net.thewellodev.server.game.NetEntity ne : list) {
            if (ne.removed) {
                mobs.remove(ne.id);
            } else {
                mobs.put(ne.id, ne);
            }
        }
        double range = Math.min(viewDistance * 16.0, 64.0); // mobs are sent within 64 blocks (fps)
        for (TWPlayer p : players.values()) {
            if (!p.joined) {
                continue;
            }
            PlayerConnection c = p.getConnection();
            Map<Integer, int[]> vis = p.visibleMobs;
            for (net.thewellodev.server.game.NetEntity ne : list) {
                int[] last = vis.get(ne.id);
                if (ne.removed) {
                    if (last != null) {
                        vis.remove(ne.id);
                        if (last[6] == 0) {
                            c.sendRemoveMob(ne.id, false);
                        }
                    }
                    continue;
                }
                double dx = ne.x - p.x, dz = ne.z - p.z;
                boolean near = dx * dx + dz * dz <= range * range
                        && p.hasChunk(Location.floor(ne.x) >> 4, Location.floor(ne.z) >> 4);
                int fx = Protocol.toFixed(ne.x), fy = Protocol.toFixed(ne.y), fz = Protocol.toFixed(ne.z);
                int yaw = Protocol.toAngle(ne.yaw), pitch = Protocol.toAngle(ne.pitch);
                if (last == null) {
                    if (near && !ne.dead) {
                        vis.put(ne.id, new int[]{fx, fy, fz, yaw, pitch, ne.state, 0});
                        c.sendSpawnMob(ne.id, ne.type, fx, fy, fz, yaw, pitch, ne.state);
                    }
                    continue;
                }
                if (!near) {
                    vis.remove(ne.id);
                    if (last[6] == 0) {
                        c.sendRemoveMob(ne.id, false);
                    }
                    continue;
                }
                if (last[6] != 0) {
                    continue; // death already sent; the client plays the animation and removes it
                }
                if (ne.hurt) {
                    c.sendInts(Protocol.HURT_ANIMATION, ne.id);
                }
                if (ne.dead) {
                    last[6] = 1;
                    c.sendRemoveMob(ne.id, true);
                    continue;
                }
                // Mobs every 2 ticks (the client interpolates smoothly); fast arrows every tick
                boolean moveTick = (tickCount & 1) == 0 || ne.type == 6 || ne.type >= 8;
                if (moveTick && (fx != last[0] || fy != last[1] || fz != last[2] || yaw != last[3]
                        || pitch != last[4] || ne.state != last[5])) {
                    last[0] = fx;
                    last[1] = fy;
                    last[2] = fz;
                    last[3] = yaw;
                    last[4] = pitch;
                    last[5] = ne.state;
                    c.sendMoveMob(ne.id, fx, fy, fz, yaw, pitch, ne.state);
                }
            }
        }
    }

    private void handleMobAttack(TWPlayer attacker, int id, int damage) {
        net.thewellodev.server.game.NetEntity ne = mobs.get(id);
        if (ne == null || ne.dead || attacker.dead) {
            return;
        }
        double dx = ne.x - attacker.x, dy = ne.y - attacker.y, dz = ne.z - attacker.z;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (pluginManager.callEvent(new PlayerAttackEvent(attacker, null, true, d2, damage, attacker.heldItem))
                .isCancelled()) {
            return;
        }
        attacker.lastAttackTick = tickCount;
        bridge.attackMob(attacker.getName(), id, Math.max(1, Math.min(10, damage)));
    }

    private void handleMobDamage(String name, int attackerId, int damage) {
        TWPlayer p = players.get(name.toLowerCase(java.util.Locale.ENGLISH));
        if (p == null || p.dead || p.gameMode == 1 || !p.joined) {
            return;
        }
        net.thewellodev.server.game.NetEntity ne = mobs.get(attackerId);
        p.lastMobAttacker = ne != null && ne.type >= 0 && ne.type < Protocol.MOB_NAMES.length
                ? Protocol.MOB_NAMES[ne.type == 6 ? 1 : ne.type] : "a monster";
        p.lastMobAttackAt = System.currentTimeMillis();
        p.lastHurtTick = tickCount;
        p.getConnection().sendDamage(ne != null ? attackerId : -1, damage);
        trackDamage(p, damage);
        for (TWPlayer o : players.values()) {
            if (o.visiblePlayers.contains(p.getEntityId())) {
                o.getConnection().sendInts(Protocol.HURT_ANIMATION, p.getEntityId());
            }
        }
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int d) {
        checkPrimary();
        difficulty = Math.max(0, Math.min(3, d));
        config.set("difficulty", String.valueOf(difficulty));
        config.saveIfDirty();
        bridge.setMobSettings(difficulty, spawnMonsters, spawnAnimals);
        bridge.setMobCaps(mobCapMonsters, mobCapAnimals);
        for (TWPlayer p : players.values()) {
            p.getConnection().sendDifficulty(difficulty);
        }
    }

    public int killAllMobs() {
        checkPrimary();
        return bridge.killAllMobs();
    }

    public int getMaxStackSize(int id) {
        return bridge.getMaxStackSize(id);
    }

    public int getLivingMobCount() {
        return bridge.getMobCount();
    }

    public int trimMobs(int max) {
        return bridge.trimMobs(max);
    }

    public int getTntCount() {
        return bridge.getTntCount();
    }

    public int clearTnt() {
        return bridge.clearTnt();
    }

    /** @return number spawned */
    public int spawnMob(int type, double x, double y, double z, int count) {
        checkPrimary();
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (bridge.spawnMob(type, x + (Math.random() - 0.5), y, z + (Math.random() - 0.5))) {
                n++;
            }
        }
        return n;
    }

    public int getMobCount() {
        return mobs.size();
    }

    private void handleDeath(TWPlayer p) {
        if (p.dead) {
            return;
        }
        p.dead = true;
        p.health = 0;
        p.healthCheckTick = -1; // a pending damage check must not overwrite health after respawn
        TWPlayer killer = p.lastAttacker != null && System.currentTimeMillis() - p.lastAttackedAt < 10000
                && p.lastAttacker.joined ? p.lastAttacker : null;
        String mob = p.lastMobAttacker != null && System.currentTimeMillis() - p.lastMobAttackAt < 5000
                && (killer == null || p.lastMobAttackAt > p.lastAttackedAt) ? p.lastMobAttacker : null;
        String msg = mob != null ? "&c" + p.getName() + " was killed by " + mob + "."
                : killer != null ? "&c" + p.getName() + " was killed by " + killer.getName() + "."
                : "&c" + p.getName() + " died.";
        PlayerDeathEvent de = pluginManager.callEvent(new PlayerDeathEvent(p, killer, msg));
        if (de.getDeathMessage() != null) {
            broadcastMessage(de.getDeathMessage());
        }
        for (TWPlayer o : players.values()) {
            if (o.visiblePlayers.remove(p.getEntityId())) {
                o.getConnection().sendRemovePlayer(p.getEntityId());
            }
        }
    }

    // ================================================================ health and inventory checks

    /**
     * Health is authoritative on the server: drops reported by the client (falls, lava, drowning...) are accepted; increases are
     * only from eating (the amount the food restores) or from server commands (/heal, respawn).
     */
    private void handleHealthReport(TWPlayer p, int reported) {
        if (!p.healthSynced) {
            p.healthSynced = true; // on join, the client's saved health is used as the baseline
            p.health = reported;
            return;
        }
        if (reported <= p.health) {
            p.health = reported;
            return;
        }
        int allowed = 0;
        if (tickCount - p.lastHealTick >= 10) {
            allowed = bridge.foodHeal(p.heldItem);
            if (tickCount - p.lastFoodTick <= 20) {
                allowed = Math.max(allowed, p.lastFoodHeal);
            }
        }
        PlayerHealthEvent ev = pluginManager.callEvent(new PlayerHealthEvent(p, p.health, reported, allowed, false));
        if (ev.isEnforced()) {
            p.getConnection().sendHealth(p.health); // write the server's health back to the client
            return;
        }
        if (allowed > 0 && reported - p.health <= allowed) {
            p.lastHealTick = tickCount;
            p.lastFoodTick = -1000;
        }
        p.health = reported;
    }

    /** Damage the server dealt: the client's health should drop by at least this much (armour reduces it by up to 80%). */
    private void trackDamage(TWPlayer v, int dmg) {
        if (v.healthCheckTick < 0) {
            v.windowStartHealth = v.health; // new window: checked three seconds later
            v.healthCheckTick = tickCount + 60;
            v.trackedHits = 0;
            v.trackedMaxDmg = 0;
        }
        // Some hits are legitimately ignored by the client (invulnerability after damage, armour): expect
        // half of the hits to count; a client that ignores all of them is still caught.
        v.trackedHits++;
        v.trackedMaxDmg = Math.max(v.trackedMaxDmg, dmg);
        int per = (int) Math.ceil(v.trackedMaxDmg * 0.3);
        v.expectedMinHealth = Math.max(0, v.windowStartHealth - (v.trackedHits / 2) * per);
    }

    private void checkHealth(TWPlayer p) {
        if (p.healthCheckTick > 0 && tickCount >= p.healthCheckTick) {
            p.healthCheckTick = -1;
            if (!p.dead && p.health > p.expectedMinHealth) {
                // the client may have ignored damage: if the plugin decides so, the server lowers the health
                PlayerHealthEvent ev = pluginManager.callEvent(
                        new PlayerHealthEvent(p, p.health, p.expectedMinHealth, 0, true));
                if (ev.isEnforced()) {
                    p.health = p.expectedMinHealth;
                    p.getConnection().sendHealth(p.health);
                    if (p.health <= 0) {
                        handleDeath(p);
                    }
                }
            }
        }
    }

    /** Inventory summary: flags invalid or forbidden items and sudden large increases (the server does not store the inventory). */
    /**
     * Builds 227 and 313 keep the inventory on the client, which starts from the game's own default in every build
     * (a different one in each). The server saves what the client reports and sends it back at the next login, so a
     * player keeps the inventory when the server moves to another build; players who were never saved start with the
     * build's default.
     */
    /** A slot (11 bits) that no player on the server has: the top bits of the numbers of his items and mobs. */
    private int allocateSlot(TWPlayer p) {
        boolean[] used = new boolean[2048];
        for (TWPlayer q : players.values()) {
            if (q != p && q.dropSlot >= 0) {
                used[q.dropSlot] = true;
            }
        }
        int start = new java.util.Random().nextInt(2048);
        for (int i = 0; i < 2048; i++) {
            int slot = (start + i) & 0x7FF;
            if (!used[slot]) {
                return slot;
            }
        }
        return start;
    }

    /** Builds whose clients run their own item entities and pass them on through the server. */
    private boolean sharesDrops() {
        return gameVersion == GameVersion.INF_20100316 || gameVersion == GameVersion.INF_20100320;
    }

    /** Who picked up which shared item (number to slot): the first one asked gets it. */
    private final java.util.Map<Integer, Integer> dropClaims = new java.util.LinkedHashMap<Integer, Integer>() {
        protected boolean removeEldestEntry(java.util.Map.Entry<Integer, Integer> eldest) {
            return size() > 4000;
        }
    };

    /** A mob that an arrow made, as the server knows it: who runs it and where it was last. */
    private static final class MobInfo {
        int number, kind, hurt, death, fireAndHealth;
        String owner;
        double x, y, z;
        float yaw, pitch, body, limbAmount, limbPosition;
        long handedOverAt;
    }

    private final java.util.Map<Integer, MobInfo> mobRegistry = new java.util.HashMap<Integer, MobInfo>();

    private static boolean validNumber(TWPlayer p, int number) {
        return p.dropSlot >= 0 && (number >>> 20) == p.dropSlot && (number & 0xFFFFF) != 0;
    }

    /**
     * Builds 316 and 320: the game of each player runs its own items (blocks drop, items can be thrown). The server passes
     * them on, so that every player sees the items of the others.
     */
    private void handleDropSpawn(TWPlayer p, PlayerConnection.InPacket in) {
        if (!sharesDrops() || !p.allow("dropspawn", 120) || !validNumber(p, in.i1) || in.i2 <= 0 || in.i2 > 2000
                || !bridge.isValidItem(in.i2) || in.i3 <= 0 || in.i3 > 999 || !isFinite(in.x) || !isFinite(in.y)
                || !isFinite(in.z) || !isFinite(in.mx) || !isFinite(in.my) || !isFinite(in.mz)
                || !near(p, in.x, in.y, in.z, 128) || Math.abs(in.mx) > 10 || Math.abs(in.my) > 10
                || Math.abs(in.mz) > 10) {
            return;
        }
        double[] v = {in.x, in.y, in.z, in.mx, in.my, in.mz};
        for (TWPlayer q : players.values()) {
            if (q != p && near(q, in.x, in.y, in.z, 160)) {
                q.getConnection().sendDropSpawn(in.i1, in.i2, in.i3, in.i4, v);
            }
        }
    }

    /** An item is gone: the one who dropped it, or the one who got it, says so. */
    private void handleDropRemove(TWPlayer p, PlayerConnection.InPacket in) {
        if (!sharesDrops() || !p.allow("dropremove", 120)) {
            return;
        }
        int number = in.i1;
        Integer claimer = dropClaims.get(number);
        if (!(p.dropSlot >= 0 && (number >>> 20) == p.dropSlot) && (claimer == null || claimer != p.dropSlot)) {
            return;
        }
        dropClaims.remove(number);
        for (TWPlayer q : players.values()) {
            if (q != p) {
                q.getConnection().sendDropRemove(number);
            }
        }
    }

    /** The first player who asks for an item gets it; the others lose their copy at once. */
    private void handleDropClaim(TWPlayer p, int number) {
        if (!sharesDrops() || !p.allow("dropclaim", 60) || p.dropSlot < 0) {
            return;
        }
        Integer cur = dropClaims.get(number);
        boolean ok = cur == null || cur == p.dropSlot;
        if (ok && cur == null) {
            dropClaims.put(number, p.dropSlot);
            for (TWPlayer q : players.values()) {
                if (q != p) {
                    q.getConnection().sendDropRemove(number);
                }
            }
        }
        p.getConnection().sendDropClaimResult(number, ok);
    }

    /**
     * Build 320: an explosion in the player's game destroys hundreds of blocks in one tick. The block changes arrive in a
     * burst; the player may send as many as the explosion can destroy (and only for a moment), above the normal rate.
     */
    private void handleExplosion(TWPlayer p, float power, double x, double y, double z) {
        if (gameVersion != GameVersion.INF_20100320 || !p.allow("explosion", 2) || p.dead
                || !(power > 0f) || power > 8f || !isFinite(x) || !isFinite(y) || !isFinite(z)
                || !near(p, x, y, z, 64)) {
            return;
        }
        p.blockBurst = Math.min(3000, (int) (power * power * power * 8 + 100));
        p.blockBurstUntil = tickCount + 60;
        p.burstX = x;
        p.burstY = y;
        p.burstZ = z;
        p.burstRadius = power * 2.0 + 2.0;
    }

    private static boolean isFinite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    /** A removal of a block inside the blast of the player's last explosion may exceed the normal rate. */
    private boolean takeBurst(TWPlayer p, int x, int y, int z, int id) {
        if (id != 0 || p.blockBurst <= 0 || tickCount > p.blockBurstUntil) {
            return false;
        }
        double dx = x + 0.5 - p.burstX, dy = y + 0.5 - p.burstY, dz = z + 0.5 - p.burstZ;
        if (dx * dx + dy * dy + dz * dz > p.burstRadius * p.burstRadius) {
            return false;
        }
        p.blockBurst--;
        return true;
    }

    /**
     * The mobs that an arrow makes live in the game of the player who shot it; the server passes them on to the players
     * near them, and remembers where each one is, so that another player can take it over when its owner leaves.
     */
    private void handleMobState(TWPlayer p, PlayerConnection.InPacket in) {
        if (!sharesDrops() || !p.allow("mobstate", 400) || in.i2 > 5 || !isFinite(in.x) || !isFinite(in.y)
                || !isFinite(in.z) || Math.abs(in.x) > 3.2e7 || Math.abs(in.z) > 3.2e7 || in.y < -64 || in.y > 256
                || !isFinite(in.mx) || !isFinite(in.my) || !isFinite(in.mz) || !isFinite(in.yaw) || !isFinite(in.pitch)) {
            return;
        }
        MobInfo m = mobRegistry.get(in.i1);
        if (m == null) {
            if (!validNumber(p, in.i1) || mobRegistry.size() >= 400) {
                return;
            }
            m = new MobInfo();
            m.number = in.i1;
            m.kind = in.i2;
            m.owner = p.getName();
            mobRegistry.put(m.number, m);
        } else if (!m.owner.equalsIgnoreCase(p.getName())) {
            return;
        }
        m.x = in.x;
        m.y = in.y;
        m.z = in.z;
        m.yaw = in.yaw;
        m.pitch = in.pitch;
        m.body = (float) in.mx;
        m.limbAmount = (float) in.my;
        m.limbPosition = (float) in.mz;
        m.hurt = in.i3;
        m.death = in.i4;
        m.fireAndHealth = in.i5;
        for (TWPlayer q : players.values()) {
            if (q != p && near(q, m.x, m.y, m.z, 160)) {
                sendMobTo(q, m, false);
            }
        }
    }

    private void sendMobTo(TWPlayer q, MobInfo m, boolean adopt) {
        if (adopt) {
            q.getConnection().sendMobAdopt(m.number, m.kind, m.x, m.y, m.z, m.yaw, m.pitch, m.body, m.limbAmount,
                    m.limbPosition, m.hurt, m.death, m.fireAndHealth);
        } else {
            q.getConnection().sendMobState(m.number, m.kind, m.x, m.y, m.z, m.yaw, m.pitch, m.body, m.limbAmount,
                    m.limbPosition, m.hurt, m.death, m.fireAndHealth);
        }
    }

    private void handleMobRemove(TWPlayer p, PlayerConnection.InPacket in) {
        MobInfo m = mobRegistry.get(in.i1);
        if (!sharesDrops() || !p.allow("mobremove", 80) || m == null || !m.owner.equalsIgnoreCase(p.getName())) {
            return;
        }
        mobRegistry.remove(m.number);
        for (TWPlayer q : players.values()) {
            if (q != p) {
                q.getConnection().sendMobRemove(m.number);
            }
        }
    }

    /** A player hit the copy of a mob: the game of the player who runs the mob hurts the real one. */
    private void handleMobHit(TWPlayer p, PlayerConnection.InPacket in) {
        MobInfo m = mobRegistry.get(in.i1);
        if (!sharesDrops() || !p.allow("mobhit", 30) || m == null || p.dead || in.i2 < 1 || m.owner.equalsIgnoreCase(p.getName())
                || !near(p, m.x, m.y, m.z, 64)) {
            return;
        }
        TWPlayer owner = players.get(m.owner.toLowerCase(java.util.Locale.ENGLISH));
        if (owner != null && owner.joined) {
            owner.getConnection().sendMobHit(m.number, Math.min(in.i2, 10), p.getEntityId());
        }
    }

    /**
     * A mob only goes after the player of the game that runs it (the game has one player in its world, and looks for a
     * target within 16 blocks of it). So a mob must be run by the game of the player who is near it: once a second, a mob
     * whose owner is far away and that has another player clearly nearer is handed to that player.
     */
    private void mobAuthorityTick() {
        for (MobInfo m : new java.util.ArrayList<MobInfo>(mobRegistry.values())) {
            if (tickCount - m.handedOverAt < 100) {
                continue;
            }
            TWPlayer owner = players.get(m.owner.toLowerCase(java.util.Locale.ENGLISH));
            double dOwner = owner == null || !owner.joined ? Double.MAX_VALUE : distance2(owner, m);
            TWPlayer best = null;
            double bestD = 24 * 24;
            for (TWPlayer q : players.values()) {
                if (q != owner && q.joined && q.getConnection() != null && !q.dead) {
                    double d = distance2(q, m);
                    if (d < bestD) {
                        bestD = d;
                        best = q;
                    }
                }
            }
            // the owner can still catch the mob when it is within 20 blocks; otherwise the nearer player gets it
            if (best != null && dOwner > 20 * 20 && bestD < dOwner * 0.6) {
                m.handedOverAt = tickCount;
                if (owner != null && owner.joined) {
                    owner.getConnection().sendMobRelease(m.number);
                }
                m.owner = best.getName();
                sendMobTo(best, m, true);
            }
        }
    }

    private static double distance2(TWPlayer p, MobInfo m) {
        double dx = p.x - m.x, dy = p.y - m.y, dz = p.z - m.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** The owner of some mobs left: the nearest other player takes each mob over, or the mob is gone. */
    private void transferMobs(TWPlayer leaver) {
        for (java.util.Iterator<MobInfo> it = mobRegistry.values().iterator(); it.hasNext();) {
            MobInfo m = it.next();
            if (!m.owner.equalsIgnoreCase(leaver.getName())) {
                continue;
            }
            TWPlayer best = null;
            double bestD = 200 * 200;
            for (TWPlayer q : players.values()) {
                if (q != leaver && q.joined && q.getConnection() != null) {
                    double dx = q.x - m.x, dz = q.z - m.z;
                    if (dx * dx + dz * dz < bestD) {
                        bestD = dx * dx + dz * dz;
                        best = q;
                    }
                }
            }
            if (best != null) {
                m.owner = best.getName();
                sendMobTo(best, m, true);
            } else {
                it.remove();
                for (TWPlayer q : players.values()) {
                    if (q != leaver) {
                        q.getConnection().sendMobRemove(m.number);
                    }
                }
            }
        }
    }


    /** The saved inventory may come from another build: ids that this build does not have are left out. */
    private int[] filterInventory(int[] saved) {
        int[] out = saved.clone();
        for (int i = 0; i + 2 < out.length; i += 3) {
            if (out[i] >= 0 && (!bridge.isValidItem(out[i]) || out[i + 1] > 999)) {
                out[i] = -1;
                out[i + 1] = 0;
                out[i + 2] = 0;
            }
        }
        return out;
    }

    private void handleInventoryReport(TWPlayer p, int[] inv) {
        if (inv == null || inv.length > 48 * 3 || !p.allow("invreport", 4)) {
            return;
        }
        int[] clean = new int[inv.length];
        for (int i = 0; i + 2 < inv.length; i += 3) {
            // blocks are 0 to 255, items (tools, arrows) go above that
            boolean empty = inv[i] < 0 || inv[i] > 2000 || inv[i + 1] <= 0;
            clean[i] = empty ? -1 : inv[i];
            clean[i + 1] = empty ? 0 : Math.min(inv[i + 1], 9999);
            clean[i + 2] = empty ? 0 : Math.max(0, Math.min(inv[i + 2], 5000));
        }
        p.savedInv = clean;
    }

    private void handleInventory(TWPlayer p, int[] inv) {
        if (inv == null || !p.allow("inv", 6)) {
            return;
        }
        p.invSnapshot = inv;
        int total = 0;
        for (int i = 0; i + 2 < inv.length; i += 3) {
            if (inv[i] >= 0 && inv[i + 1] > 0) {
                total += inv[i + 1];
            }
        }
        pluginManager.callEvent(new PlayerInventoryEvent(p, p.getInventoryContents(), total));
    }

    /** The nearest safe spawn point (no lava, water or fire) to {x, y, z}. */
    private double[] safeSpawn(int x, int z) {
        Set<Long> prepared = new java.util.HashSet<Long>();
        if (x == 0 && z == 0 && gameVersion != null && gameVersion.protocol != net.thewellodev.protocol.Protocol.VERSION) {
            for (int cx = -1; cx <= 0; cx++) {
                for (int cz = -1; cz <= 0; cz++) {
                    bridge.getChunkData(cx, cz);
                }
            }
            double[] exact = bridge.exactSpawn();
            if (exact != null) {
                return exact;
            }
        }
        for (int r = 0; r <= 24; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    if (prepared.add(TWPlayer.chunkKey((x + dx) >> 4, (z + dz) >> 4))) {
                        bridge.getChunkData((x + dx) >> 4, (z + dz) >> 4); // prepare the chunk
                    }
                    int y = bridge.safeY(x + dx, z + dz);
                    if (y > 0) {
                        return new double[]{x + dx + 0.5, y + 0.1, z + dz + 0.5};
                    }
                }
            }
        }
        return new double[]{x + 0.5, bridge.getHighestY(x, z) + 0.5, z + 0.5};
    }

    private void handleRespawn(TWPlayer p) {
        if (!p.dead) {
            return; // do not allow respawn-teleporting to the spawn without dying
        }
        p.dead = false;
        p.health = 20;
        p.healthCheckTick = -1;
        p.expectedMinHealth = 20;
        p.lastAttacker = null;
        int[] sp = bridge.getSpawn();
        double[] safe = safeSpawn(sp[0], sp[2]);
        Location loc = new Location(world, safe[0], safe[1], safe[2]);
        PlayerRespawnEvent re = pluginManager.callEvent(new PlayerRespawnEvent(p, loc));
        p.teleport(re.getRespawnLocation());
    }

    public boolean isPvp() {
        return pvp;
    }

    /** Removes all shared items on the ground. @return number removed */
    public int clearNetItems() {
        List<Integer> ids = new ArrayList<Integer>(items.keySet());
        for (int id : ids) {
            removeNetItem(id, -1);
        }
        return ids.size();
    }

    public void handleChat(TWPlayer p, String raw) {
        if (raw == null) {
            return;
        }
        String msg = raw.trim();
        if (msg.length() == 0) {
            return;
        }
        if (msg.length() > 100) {
            msg = msg.substring(0, 100);
        }
        for (int i = 0; i < msg.length(); i++) {
            if (msg.charAt(i) < 32) {
                p.kickPlayer("Invalid character");
                return;
            }
        }
        if (msg.startsWith("/")) {
            PlayerCommandPreprocessEvent ce = pluginManager.callEvent(new PlayerCommandPreprocessEvent(p, msg));
            if (ce.isCancelled()) {
                return;
            }
            logger.info(p.getName() + " command: " + ce.getMessage());
            String line = ce.getMessage().startsWith("/") ? ce.getMessage().substring(1) : ce.getMessage();
            if (!dispatchCommand(p, line)) {
                p.sendMessage("&cUnknown command. Type /help.");
            }
            return;
        }
        if (!p.isOp()) {
            msg = ChatColor.stripColor(msg).replace("&", "");
        }
        PlayerChatEvent ev = pluginManager.callEvent(new PlayerChatEvent(p, msg));
        if (ev.isCancelled()) {
            return;
        }
        String out;
        try {
            out = String.format(ev.getFormat(), p.getDisplayName(), ev.getMessage());
        } catch (Exception e) {
            out = "<" + p.getDisplayName() + "> " + ev.getMessage();
        }
        broadcastMessage(out);
    }

    // ================================================================ chunks and visibility

    private void updateChunks(TWPlayer p) {
        PlayerConnection c = p.getConnection();
        int pcx = p.getChunkX(), pcz = p.getChunkZ();
        int r = viewDistance;
        // Unload chunks that are now far away
        Iterator<Long> it = p.loadedChunks.iterator();
        while (it.hasNext()) {
            long k = it.next();
            int cx = (int) (k >> 32);
            int cz = (int) k;
            if (Math.abs(cx - pcx) > r + 1 || Math.abs(cz - pcz) > r + 1) {
                it.remove();
                c.sendUnloadChunk(cx, cz);
            }
        }
        // Send new chunks starting with the nearest (flow control)
        int budget = Math.max(0, 6 - c.getPendingChunks());
        if (budget == 0) {
            return;
        }
        for (int ring = 0; ring <= r && budget > 0; ring++) {
            for (int dx = -ring; dx <= ring && budget > 0; dx++) {
                for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int cx = pcx + dx, cz = pcz + dz;
                    long k = TWPlayer.chunkKey(cx, cz);
                    if (p.loadedChunks.contains(k)) {
                        continue;
                    }
                    byte[] raw = bridge.getChunkData(cx, cz);
                    p.loadedChunks.add(k);
                    c.sendChunk(cx, cz, raw);
                    sendTiles(c, cx, cz);
                    budget--;
                }
            }
        }
    }

    private void updateVisibility() {
        double range = viewDistance * 16.0;
        for (TWPlayer a : players.values()) {
            for (TWPlayer b : players.values()) {
                if (a == b) {
                    continue;
                }
                double dx = a.x - b.x, dz = a.z - b.z;
                boolean near = !b.dead && dx * dx + dz * dz <= range * range && a.hasChunk(b.getChunkX(), b.getChunkZ());
                boolean sees = a.visiblePlayers.contains(b.getEntityId());
                if (near && !sees) {
                    a.visiblePlayers.add(b.getEntityId());
                    a.getConnection().sendSpawnPlayer(b.getEntityId(), b.getName(), b.x, b.y + TWPlayer.EYE_HEIGHT, b.z,
                            b.yaw, b.pitch);
                    if (b.heldItem > 0) {
                        a.getConnection().sendHeldItem(b.getEntityId(), b.heldItem);
                    }
                    if (b.ridingNetId >= 0) {
                        a.getConnection().sendMount(b.getEntityId(), b.ridingNetId);
                    }
                } else if (!near && sees) {
                    a.visiblePlayers.remove(b.getEntityId());
                    a.getConnection().sendRemovePlayer(b.getEntityId());
                }
            }
        }
    }

    private void updateKeepAlive(TWPlayer p) {
        PlayerConnection c = p.getConnection();
        if (tickCount % 100 == p.getEntityId() % 100) {
            p.keepAliveToken = (int) (System.nanoTime() & 0x7FFFFFFF);
            p.keepAliveSentAt = System.currentTimeMillis();
            c.sendKeepAlive(p.keepAliveToken);
        }
        if (System.currentTimeMillis() - c.getLastReceived() > 30000) {
            c.close("Timed out");
            removePlayer(p, "Timed out", true);
        }
    }

    private void markChanged(int x, int y, int z) {
        long k = TWPlayer.chunkKey(x >> 4, z >> 4);
        Set<Integer> s = changedBlocks.get(k);
        if (s == null) {
            s = new LinkedHashSet<Integer>();
            changedBlocks.put(k, s);
        }
        if (s.size() <= 64) {
            s.add(((x & 15) << 12) | ((z & 15) << 8) | (y & 255));
        }
    }

    private void flushBlockChanges() {
        if (changedBlocks.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Set<Integer>> e : changedBlocks.entrySet()) {
            long k = e.getKey();
            int cx = (int) (k >> 32);
            int cz = (int) k;
            List<TWPlayer> viewers = new ArrayList<TWPlayer>();
            for (TWPlayer p : players.values()) {
                if (p.loadedChunks.contains(k)) {
                    viewers.add(p);
                }
            }
            if (viewers.isEmpty()) {
                continue;
            }
            if (e.getValue().size() > 64) {
                byte[] raw = bridge.getChunkData(cx, cz);
                for (TWPlayer p : viewers) {
                    p.getConnection().sendChunk(cx, cz, raw);
                    sendTiles(p.getConnection(), cx, cz);
                }
                continue;
            }
            for (int packed : e.getValue()) {
                int x = (cx << 4) | (packed >> 12 & 15);
                int z = (cz << 4) | (packed >> 8 & 15);
                int y = packed & 255;
                int id = bridge.getBlockId(x, y, z);
                int meta = bridge.getBlockMeta(x, y, z);
                for (TWPlayer p : viewers) {
                    p.getConnection().sendBlockChange(x, y, z, id, meta);
                }
            }
        }
        changedBlocks.clear();
    }

    public void broadcastTime() {
        long t = bridge.getTime();
        for (TWPlayer p : players.values()) {
            p.getConnection().sendTime(t);
        }
    }

    // ================================================================ console

    private boolean convertingWorld;

    /**
     * /convertworld: takes over the chunks of the older world that nobody has visited yet, a batch of files every tick
     * (the server keeps running). When all are done the world is marked complete (WorldConverter).
     */
    public void startWorldConversion(final CommandSender sender) {
        if (convertingWorld) {
            sender.sendMessage("&eThe world is being converted already.");
            return;
        }
        if (bridge.convertLegacyBatch(0) < 0) {
            sender.sendMessage("&eNothing to convert: this world has no older world left to take chunks from.");
            return;
        }
        convertingWorld = true;
        sender.sendMessage("&eConverting the rest of the world in the background (the server keeps running) ...");
        final int[] total = {0};
        mainTasks.add(new Runnable() {
            public void run() {
                int n;
                try {
                    n = bridge.convertLegacyBatch(200);
                } catch (RuntimeException ex) {
                    convertingWorld = false;
                    logger.severe("Converting the world stopped: " + ex);
                    sender.sendMessage("&cConverting stopped: " + ex.getMessage());
                    return;
                }
                if (n > 0) {
                    total[0] += n;
                    mainTasks.add(this); // the next batch on the next tick
                    return;
                }
                convertingWorld = false;
                File levelDir = new File(worldRoot, levelName);
                String from = net.thewellodev.server.world.WorldConverter.origin(levelDir);
                try {
                    net.thewellodev.server.world.WorldConverter.markComplete(levelDir);
                } catch (IOException ex) {
                    logger.warning("Could not mark the world as converted: " + ex);
                }
                bridge.save(true);
                logger.info("The world was converted: " + total[0] + " more chunks were taken over"
                        + (from == null ? "." : " from " + from + ". worlds/" + from + "/" + levelName + " is not needed any more."));
                sender.sendMessage("&aThe world is converted completely (" + total[0] + " more chunks"
                        + (from == null ? "" : "; worlds/" + from + "/" + levelName + " can be deleted now") + ").");
            }
        });
    }

    /** Runs a task on the main thread (can be called from any thread). */
    public void runOnMainThread(Runnable task) {
        mainTasks.add(task);
    }

    /**
     * Deletes an item from an operator's inventory window. If {@code typeId >= 0}, all items of that type are removed;
     * otherwise only {@code slot} is emptied. The target player is notified and the action is logged.
     */
    public void submitInventoryDelete(final String admin, final String target, final int slot, final int typeId) {
        mainTasks.add(new Runnable() {
            public void run() {
                TWPlayer p = players.get(target.toLowerCase(java.util.Locale.ENGLISH));
                if (p == null || !p.joined) {
                    return;
                }
                ItemStack[] cur = p.getInventoryContents();
                int removed = 0;
                if (typeId >= 0) {
                    for (int i = 0; i < cur.length; i++) {
                        if (cur[i] != null && cur[i].getTypeId() == typeId) {
                            p.setInventorySlot(i, null);
                            removed++;
                        }
                    }
                } else if (slot >= 0 && slot < cur.length && cur[slot] != null) {
                    p.setInventorySlot(slot, null);
                    removed = 1;
                }
                if (removed > 0) {
                    logger.warning("[GUI] " + admin + " deleted " + removed + " slot(s) from " + p.getName() + "'s inventory"
                            + (typeId >= 0 ? " (type " + typeId + ")" : " (slot " + slot + ")"));
                    p.sendMessage("&eAn operator deleted an item from your inventory.");
                }
            }
        });
    }

    /** A line entered from the GUI or the console (any thread). Starting with "/" runs a command; otherwise it is a server message. */
    public void submitConsoleInput(String line) {
        if (line != null && line.trim().length() > 0) {
            consoleQueue.add(new String[]{line.trim()});
        }
    }

    private void handleConsoleLine(String line) {
        if (line.startsWith("/")) {
            String c = line.substring(1).trim();
            if (c.length() == 0) {
                return;
            }
            ServerCommandEvent ev = pluginManager.callEvent(new ServerCommandEvent(console, c));
            if (ev.isCancelled()) {
                return;
            }
            logger.info("> /" + ev.getCommand());
            if (!dispatchCommand(console, ev.getCommand())) {
                logger.info("Unknown command. Type /help for the command list.");
            }
        } else {
            broadcastMessage("&d[Server] &f" + line);
        }
    }

    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        checkPrimary();
        String[] parts = commandLine.trim().split(" +");
        if (parts.length == 0 || parts[0].length() == 0) {
            return false;
        }
        String label = parts[0].toLowerCase(java.util.Locale.ENGLISH);
        Command cmd = commandMap.get(label);
        if (cmd == null) {
            return false;
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        try {
            cmd.execute(sender, label, args);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error in command '/" + commandLine + "'", t);
            sender.sendMessage("&cError while running the command: " + t);
        }
        return true;
    }

    // ================================================================ shutdown

    public void shutdown() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void addStopListener(Runnable r) {
        stopListeners.add(r);
    }

    private void doStop() {
        logger.info("Stopping the server...");
        try {
            pluginManager.disableAll();
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error while disabling plugins", t);
        }
        for (TWPlayer p : new ArrayList<TWPlayer>(players.values())) {
            savePlayerData(p);
            p.getConnection().disconnect("Server closed");
        }
        players.clear();
        rebuildSnapshot();
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        if (network != null) {
            network.close();
        }
        try {
            logger.info("Saving the world...");
            if (bridge != null) {
                bridge.save(true);
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Could not save the world", t);
        }
        scheduler.shutdown();
        authPool.shutdownNow();
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {
        }
        logger.info("Server stopped.");
        stopped = true;
        for (Runnable r : stopListeners) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }

    // ================================================================ files

    private void loadList(String file, Set<String> into) {
        File f = new File(dir, file);
        into.clear();
        if (!f.exists()) {
            saveList(file, into);
            return;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0 && !line.startsWith("#")) {
                    into.add(line);
                }
            }
            r.close();
        } catch (IOException e) {
            logger.warning(file + " could not be read: " + e);
        }
    }

    private void saveList(String file, Set<String> from) {
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir, file)), "UTF-8");
            synchronized (from) {
                for (String s : from) {
                    w.write(s + "\n");
                }
            }
            w.close();
        } catch (IOException e) {
            logger.warning(file + " could not be written: " + e);
        }
    }

    /**
     * permissions.txt: each line is "player: perm1, perm2"; "*: perm" gives a permission to everyone.
     */
    private void loadPermissions() {
        File f = new File(dir, "permissions.txt");
        extraPermissions.clear();
        if (!f.exists()) {
            try {
                Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
                w.write("# TheWello's Infdev Server permission file. Operators have every permission.\n");
                w.write("# Format:  playername: perm.one, perm.two\n");
                w.write("#          *: permission.for.everyone\n");
                w.write("# Example:  *: thewello.command.seed\n");
                w.close();
            } catch (IOException ignored) {
            }
            return;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                int c = line.indexOf(':');
                if (line.startsWith("#") || c <= 0) {
                    continue;
                }
                String who = line.substring(0, c).trim().toLowerCase(java.util.Locale.ENGLISH);
                Set<String> s = extraPermissions.get(who);
                if (s == null) {
                    s = new java.util.HashSet<String>();
                    extraPermissions.put(who, s);
                }
                for (String perm : line.substring(c + 1).split(",")) {
                    if (perm.trim().length() > 0) {
                        s.add(perm.trim().toLowerCase(java.util.Locale.ENGLISH));
                    }
                }
            }
            r.close();
        } catch (IOException e) {
            logger.warning("permissions.txt could not be read: " + e);
        }
    }

    public boolean hasPermission(TWPlayer p, String perm) {
        if (perm == null || perm.length() == 0 || isOp(p.getName())) {
            return true;
        }
        String lp = perm.toLowerCase(java.util.Locale.ENGLISH);
        if (BuiltinCommands.DEFAULT_PERMISSIONS.contains(lp)) {
            return true;
        }
        if (!lp.startsWith("thewello.") && pluginDefaultAllowed(lp)) {
            return true;
        }
        Set<String> all = extraPermissions.get("*");
        Set<String> mine = extraPermissions.get(p.getName().toLowerCase(java.util.Locale.ENGLISH));
        return (all != null && (all.contains(lp) || all.contains("*")))
                || (mine != null && (mine.contains(lp) || mine.contains("*")));
    }

    /** Plugin permissions: those ending in ".use" or ".default" are open to everyone. */
    private boolean pluginDefaultAllowed(String perm) {
        return perm.endsWith(".use") || perm.endsWith(".default");
    }

    private File playerFile(String name) {
        File d = new File(new File(worldRoot, levelName), "players");
        d.mkdirs();
        return new File(d, name.toLowerCase(java.util.Locale.ENGLISH) + ".txt");
    }

    private File inventoryFile(String name) {
        return new File(new File(new File(worldRoot, levelName), "players"),
                name.toLowerCase(java.util.Locale.ENGLISH) + ".inv");
    }

    /** One line for each slot: "id count damage" (id -1: empty). */
    private void saveInventoryFile(TWPlayer p) {
        int[] inv = p.savedInv;
        if (inv == null) {
            return;
        }
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(inventoryFile(p.getName())), "UTF-8");
            for (int i = 0; i + 2 < inv.length; i += 3) {
                w.write(inv[i] + " " + inv[i + 1] + " " + inv[i + 2] + "\n");
            }
            w.close();
        } catch (IOException e) {
            logger.warning(p.getName() + " inventory could not be saved: " + e);
        }
    }

    private void loadInventoryFile(TWPlayer p) {
        File f = inventoryFile(p.getName());
        if (!f.isFile()) {
            return;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            java.util.List<Integer> values = new java.util.ArrayList<Integer>();
            String line;
            while ((line = r.readLine()) != null && values.size() < 48 * 3) {
                String[] v = line.trim().split(" ");
                if (v.length == 3) {
                    values.add(Integer.parseInt(v[0]));
                    values.add(Integer.parseInt(v[1]));
                    values.add(Integer.parseInt(v[2]));
                }
            }
            r.close();
            int[] inv = new int[values.size()];
            for (int i = 0; i < inv.length; i++) {
                inv[i] = values.get(i);
            }
            p.savedInv = inv;
        } catch (Exception e) {
            logger.warning(p.getName() + " inventory could not be read: " + e);
        }
    }

    private boolean loadPlayerData(TWPlayer p) {
        File f = playerFile(p.getName());
        if (!f.exists()) {
            return false;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String[] v = r.readLine().trim().split(" ");
            r.close();
            p.x = Double.parseDouble(v[0]);
            p.y = Double.parseDouble(v[1]);
            p.z = Double.parseDouble(v[2]);
            p.yaw = Float.parseFloat(v[3]);
            p.pitch = Float.parseFloat(v[4]);
            p.gameMode = 0; // the game mode is not kept between visits: everyone joins in survival
            if (p.y < -10 || Double.isNaN(p.y)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.warning(p.getName() + " player data could not be read: " + e);
            return false;
        }
    }

    private void savePlayerData(TWPlayer p) {
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(playerFile(p.getName())), "UTF-8");
            w.write(p.x + " " + p.y + " " + p.z + " " + p.yaw + " " + p.pitch + " " + p.gameMode + "\n");
            w.close();
            saveInventoryFile(p);
        } catch (IOException e) {
            logger.warning(p.getName() + " player data could not be saved: " + e);
        }
    }

    public void reloadPlugins() {
        checkPrimary();
        pluginManager.disableAll();
        logger.info("Reloading plugins...");
        loadPermissions();
        SimplePluginManager pm = pluginManager;
        pm.clearForReload();
        pm.loadPlugins(new File(dir, "plugins"));
        pm.enableAll();
    }

    private void checkPrimary() {
        if (!isPrimaryThread()) {
            throw new IllegalStateException("This can only be called from the main server thread");
        }
    }

    // ================================================================ Server API

    public boolean isPrimaryThread() {
        return Thread.currentThread() == mainThread;
    }

    public String getName() {
        return NAME;
    }

    public String getVersion() {
        return VERSION;
    }

    /** Chunks in memory (-1 when the build cannot tell). */
    public int getLoadedChunkCount() {
        return bridge == null ? -1 : bridge.getLoadedChunkCount();
    }

    public String getGameVersion() {
        return gameVersion == null ? GameVersion.INF_20100630_1340.id : gameVersion.id;
    }

    public Collection<? extends Player> getOnlinePlayers() {
        return playerSnapshot;
    }

    public List<TWPlayer> getPlayerList() {
        return playerSnapshot;
    }

    public Player getPlayerExact(String name) {
        if (name == null) {
            return null;
        }
        for (TWPlayer p : playerSnapshot) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public Player getPlayer(String name) {
        Player exact = getPlayerExact(name);
        if (exact != null || name == null) {
            return exact;
        }
        String l = name.toLowerCase(java.util.Locale.ENGLISH);
        for (TWPlayer p : playerSnapshot) {
            if (p.getName().toLowerCase(java.util.Locale.ENGLISH).startsWith(l)) {
                return p;
            }
        }
        return null;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getPort() {
        return port;
    }

    public String getMotd() {
        return motd;
    }

    public World getWorld() {
        return world;
    }

    public int broadcastMessage(String message) {
        if (message == null) {
            return 0;
        }
        logger.info("[CHAT] " + ChatColor.stripColor(message));
        int n = 0;
        for (TWPlayer p : playerSnapshot) {
            p.sendMessage(message);
            n++;
        }
        return n;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public TWScheduler getScheduler() {
        return scheduler;
    }

    public ConsoleCommandSender getConsoleSender() {
        return console;
    }

    public CommandMap getCommandMap() {
        return commandMap;
    }

    public PluginCommand getPluginCommand(String name) {
        Command c = commandMap.get(name);
        return c instanceof PluginCommand ? (PluginCommand) c : null;
    }

    public Logger getLogger() {
        return logger;
    }

    public File getWorldContainer() {
        return dir;
    }

    public boolean isOp(String playerName) {
        return ops.contains(playerName);
    }

    public void setOp(String playerName, boolean op) {
        if (op) {
            ops.add(playerName);
        } else {
            ops.remove(playerName);
            // Players who lose operator status go back to survival
            Player pl = getPlayerExact(playerName);
            if (pl != null && pl.getGameMode() != 0) {
                pl.setGameMode(0);
            }
        }
        saveList("ops.txt", ops);
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }

    public net.thewellodev.server.net.NetworkHelper getNetwork() {
        return network;
    }

    public boolean isBanned(String playerName) {
        return banned.contains(playerName);
    }

    public void setBanned(String playerName, boolean b) {
        if (b) {
            banned.add(playerName);
        } else {
            banned.remove(playerName);
        }
        saveList("banned-players.txt", banned);
    }

    public String getBannedList() {
        return banned.toString();
    }

    public boolean isWhitelistEnabled() {
        return whitelistEnabled;
    }

    public void setWhitelistEnabled(boolean on) {
        whitelistEnabled = on;
        config.set("white-list", String.valueOf(on));
        config.saveIfDirty();
    }

    public boolean isWhitelisted(String playerName) {
        return whitelist.contains(playerName);
    }

    public void setWhitelisted(String playerName, boolean on) {
        if (on) {
            whitelist.add(playerName);
        } else {
            whitelist.remove(playerName);
        }
        saveList("white-list.txt", whitelist);
    }

    public String getWhitelist() {
        return whitelist.toString();
    }

    public double getTps() {
        return tps;
    }

    public double getMspt() {
        return mspt;
    }

    public void saveAll() {
        checkPrimary();
        for (TWPlayer p : players.values()) {
            savePlayerData(p);
        }
        bridge.save(true);
    }

    /** Number of different chunks sent to players (updated every second). */
    public int getLoadedChunkEstimate() {
        return statChunks;
    }

    /** Number of entities in the server world (updated every second). */
    public int getEntityCount() {
        return statEntities;
    }

    public long getUptimeMillis() {
        return System.currentTimeMillis() - startTime;
    }

    public String getUptimeString() {
        long s = getUptimeMillis() / 1000;
        return String.format("%dsa %02ddk %02dsn", s / 3600, (s / 60) % 60, s % 60);
    }

    public int getViewDistance() {
        return viewDistance;
    }
}
