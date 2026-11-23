package net.thewellodev.protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * A supported game build (for example Infdev inf-20100630-1340).
 *
 * One server jar can run several builds. The operator selects one build (game-version in
 * server.properties); the server then runs only that build's game code, and only clients of the same
 * build (same protocol number) can connect. Builds never share content.
 *
 * A game jar is identified in two ways:
 * <ul>
 *   <li>the manifest attribute {@link #MANIFEST_KEY} written by the client patcher (patched jars), or</li>
 *   <li>the SHA-256 of the original, unpatched jar.</li>
 * </ul>
 */
public final class GameVersion {

    /** Manifest attribute written into patched jars: the build id. */
    public static final String MANIFEST_KEY = "TheWelloDev-Game-Version";

    public static final GameVersion INF_20100630_1340 = new GameVersion(
            "inf-20100630-1340", "Infdev inf-20100630-1340", Protocol.VERSION,
            "92e4969e76cd242a53ae9ca10206ece9e6596b441ff9896fd9a34b9b61ad072e", true, "TWBridge",
            // no older build is taken over into this one
            "convertworld");

    /** Server bridge and client mod exist; no other players, chat or inventory yet (see docs/versions). */
    public static final GameVersion INF_20100227_1433 = new GameVersion(
            "inf-20100227-1433", "Infdev inf-20100227-1433", 10,
            "507cecfad21caa2e137a00c57d63ac6ebc299cb1191e703f1c49274d53e7fef3", true, "TWBridge227",
            // Built-in commands about things this build does not have: mobs, items and inventory,
            // the day and night cycle and the world seed
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "time", "seed", "convertworld");

    /** Server bridge and client mod exist: light, metadata, day and night cycle; nothing is saved by the game itself. */
    public static final GameVersion INF_20100313 = new GameVersion(
            "inf-20100313", "Infdev inf-20100313", 12,
            "4974d31eb0af16ddbaaa2d81e88943baeaf8bbc0106a8db20cda19f4a824152d", true, "TWBridge313",
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "seed");

    /** The build after 20100313: the world class is called e again; light, metadata and the day cycle as in 313. */
    public static final GameVersion INF_20100316 = new GameVersion(
            "inf-20100316", "Infdev inf-20100316", 13,
            "a363943f5e2c147ab0515930317dd535e9a2229fa0eb952c73fb6f078cfbda20", true, "TWBridge316",
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "seed");

    /**
     * The build after 20100316: world class f again, terrain with trees and ores, chests and furnaces that keep their
     * items, explosions and fire that can be punched out.
     */
    public static final GameVersion INF_20100320 = new GameVersion(
            "inf-20100320", "Infdev inf-20100320", 14,
            "7e6b3048a97384397e1adfb4458eafd3a0aae52845b816cfc6f138902da5e92a", true, "TWBridge320",
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "seed");

    /**
     * The build after 20100320: the game world is the same code (so the same bridge logic, items, mobs, chests and
     * explosions as 320); only the client changed (a session check against a server that no longer exists).
     */
    public static final GameVersion INF_20100321 = new GameVersion(
            "inf-20100321", "Infdev inf-20100321", 15,
            "9dd5ed05f557d508766d8b9d4fd3f77adc69c1943fd47f51058191ab81fc55bf", true, "TWBridge321",
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "seed");

    /**
     * The build after 20100321 (the builds between them are not supported): obfuscated names moved again; caves, ore
     * blobs, block ticks (leaves decay, fire spreads, grass spreads, flowers die in the dark), double slabs, and the
     * session check is gone.
     */
    public static final GameVersion INF_20100325_1640 = new GameVersion(
            "inf-20100325-1640", "Infdev inf-20100325-1640", 16,
            "1942c49c85167375182b4d181e71f2a199f223ae8f442332c4dbfaa5596c20a0", true, "TWBridge325",
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "seed");

    /**
     * The build after 20100325-1640 (the build 20100325-1545 and others between are not supported): the game saves its worlds
     * again, in the Alpha level format (a level.dat and chunk files in folders named by base 36 numbers), and has a new
     * terrain generator (no caves yet), sea level 64, the world's block coordinates are signed, furnaces smelt in 4 seconds.
     */
    public static final GameVersion INF_20100327 = new GameVersion(
            "inf-20100327", "Infdev inf-20100327", 17,
            "c696d187821b9034046e4572dfdb579a60b80530ff02598aadb6322e8ad8b247", true, "TWBridge327",
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "seed");

    /**
     * The build after 20100327 (20100330-1203, which lies between them, has no known copy): chunk files carry the tag
     * TerrainPopulated (a chunk that was populated is never populated again), furnaces smelt in 8 seconds again, containers
     * save reliably, the light of slabs and of the top of the world is right, broken blocks do not keep their data value and the
     * clouds no longer follow the player. The world is in the same Alpha level format as 20100327.
     */
    public static final GameVersion INF_20100330_1611 = new GameVersion(
            "inf-20100330-1611", "Infdev inf-20100330-1611", 18,
            "3beb70d42f81f717f04a8c47b847854eda4aabe5b5a23d62e6b9ef86a8aec64d", true, "TWBridge330",
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "seed");

    /**
     * The build after 20100330-1611 (20100413-1951, which lies between them, has no known copy): the world class moved to a/a/g and the
     * entity classes changed (the living entity is a/c/f, every entity field name moved by one letter), chunks hold entities,
     * the terrain has beaches, erosion and big trees, leaves no longer decay, arrows no longer make mobs and the light queue is
     * worked off by one call. The world is in the same Alpha level format as 20100327.
     */
    public static final GameVersion INF_20100413_1953 = new GameVersion(
            "inf-20100413-1953", "Infdev inf-20100413-1953", 19,
            "6b9513e811a83cbf81ac57bc0c8d01a02fa3f0a88a284afe08a5145961002679", true, "TWBridge413",
            "difficulty", "spawnmob", "killmobs", "clearitems", "give", "seed");

    private static final GameVersion[] ALL = {INF_20100630_1340, INF_20100227_1433, INF_20100313, INF_20100316,
            INF_20100320, INF_20100321, INF_20100325_1640, INF_20100327, INF_20100330_1611, INF_20100413_1953};

    /**
     * The builds a world can be converted along, from the oldest to the newest: from each one to the next.
     * inf-20100630-1340 is not in it: many builds lie between it and 20100313 that are not supported yet.
     */
    public static GameVersion[] chronological() {
        return new GameVersion[]{INF_20100227_1433, INF_20100313, INF_20100316, INF_20100320,
                INF_20100321, INF_20100325_1640, INF_20100327, INF_20100330_1611, INF_20100413_1953};
    }

    public final String id;
    public final String displayName;
    /** Protocol number the client of this build sends in the handshake. */
    public final int protocol;
    /** SHA-256 of the original, unpatched jar (lower-case hex). */
    public final String originalSha256;
    /** True when the server can run this build. */
    public final boolean supported;
    /** Name of the bridge class (in the server jar's bridge/ folder) that runs this build's world code. */
    public final String bridgeClass;
    /** Built-in server commands that make no sense in this build (they are not registered). */
    public final java.util.List<String> unavailableCommands;

    private GameVersion(String id, String displayName, int protocol, String originalSha256, boolean supported,
                        String bridgeClass, String... unavailableCommands) {
        this.id = id;
        this.displayName = displayName;
        this.protocol = protocol;
        this.originalSha256 = originalSha256;
        this.supported = supported;
        this.bridgeClass = bridgeClass;
        this.unavailableCommands = java.util.Collections.unmodifiableList(java.util.Arrays.asList(unavailableCommands));
    }

    public static GameVersion[] all() {
        return ALL.clone();
    }

    /** @return the build with this id, or null */
    public static GameVersion byId(String id) {
        if (id == null) {
            return null;
        }
        for (GameVersion v : ALL) {
            if (v.id.equalsIgnoreCase(id.trim())) {
                return v;
            }
        }
        return null;
    }

    /** @return ids of all known builds, separated by ", " (for messages) */
    public static String idList() {
        StringBuilder sb = new StringBuilder();
        for (GameVersion v : ALL) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v.id);
        }
        return sb.toString();
    }

    /**
     * Identifies a game jar.
     * @return the build the jar belongs to, or null if it is not a known build
     */
    public static GameVersion identify(File jar) throws IOException {
        if (jar == null || !jar.isFile()) {
            return null;
        }
        JarFile jf = new JarFile(jar);
        try {
            Manifest mf = jf.getManifest();
            if (mf != null) {
                String tag = mf.getMainAttributes().getValue(new Attributes.Name(MANIFEST_KEY));
                if (tag != null) {
                    return byId(tag);
                }
            }
            String hash = sha256(jar);
            for (GameVersion v : ALL) {
                if (v.originalSha256.equals(hash)) {
                    return v;
                }
            }
            // Patched jars made before the manifest attribute existed: the same class names that
            // the server always checked for inf-20100630-1340 (no other known build has them).
            if (jf.getEntry("fe.class") != null && jf.getEntry("jw.class") != null
                    && jf.getEntry("kb.class") != null) {
                return INF_20100630_1340;
            }
            return null;
        } finally {
            jf.close();
        }
    }

    private static String sha256(File f) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            } finally {
                in.close();
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }
}
