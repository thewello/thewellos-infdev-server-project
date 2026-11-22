package net.thewellodev.server.world;

import net.thewellodev.protocol.GameVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Brings a world of an older build into a newer build when the server is started with a newer game-version.
 *
 * Worlds are kept apart for each build (worlds/&lt;build&gt;/&lt;level-name&gt;/). When the world of the selected build does
 * not exist yet but the same level exists for an older build, the new world is created with the small files only
 * (level.dat, the players' last positions and a note, legacy.properties, saying where it came from). The chunks are
 * not copied: the bridge of the new build reads a chunk from the old world's folder the first time the chunk is
 * needed, converts it and keeps it in the new world (it is written there with the next save). So the server starts at
 * once, only the chunks that players visit are converted, and the old world is only ever read, never changed: the
 * server of the old build still opens it as before.
 *
 * <ul>
 * <li>A chunk in the new world's folder always wins over the old one.</li>
 * <li>/convertworld converts the chunks nobody has visited yet, a batch every tick. When all are done, legacy.properties
 *     says {@code complete=true} and the old folder is not read any more; only then it is safe to delete it.</li>
 * <li>While {@code complete=false} the console says so at every start.</li>
 * </ul>
 *
 * Steps that exist:
 * <ul>
 * <li>inf-20100227-1433 -> inf-20100313: the chunk files have the same tags and block numbers, so a chunk is read
 *     as it is. The old build saved no light ("full light everywhere"); the 20100313 bridge works the light out when
 *     it loads such a chunk.</li>
 * <li>inf-20100313 -> inf-20100316 (also 227 -> 316): the same chunk files with light and metadata, and the terrain
 *     generator of 316 has the same code as that of 313, so the same seed gives the same land: land generated after
 *     the conversion fits the converted part.</li>
 * <li>inf-20100316 -> inf-20100320 (also 313 and 227 -> 320): the same chunk files. The generator of 320 adds trees and ores:
 *     the chunks that are taken over keep what they have (they count as populated), and land made after the takeover
 *     is generated with the new terrain, so it differs from the converted land at its edges.</li>
 * <li>inf-20100320 -> inf-20100321 (also 316, 313 and 227 -> 321): the world code of the two builds is identical, so 320 is
 *     taken over without any change in terrain; the older ones are taken over as into 320.</li>
 * <li>inf-20100321 -> inf-20100325-1640 (also 320, 316, 313 and 227): the same chunk files. The generator of 325-1640 adds
 *     caves and ore blobs: the chunks that are taken over keep what they have, new land has the caves, so the edge of the
 *     converted part is visible.</li>
 * </ul>
 * A new build is added with one more case in {@link #supportsStep}, and the bridge of the build converts the chunk it
 * reads from the old folder. What cannot be carried over: the terrain generators of the builds differ, so land
 * generated after the conversion does not fit the converted part.
 */
public final class WorldConverter {

    public static final String MARKER = "legacy.properties";

    private WorldConverter() {
    }

    /**
     * Prepares the world of the target build.
     * @return the folder of the older world whose chunks the new world still has to take over, or null when there is
     *         nothing to take over (no older world, or everything was taken over already)
     */
    public static File prepare(File worldsDir, String level, GameVersion target, Logger log) {
        File targetDir = new File(new File(worldsDir, target.id), level);
        if (hasWorld(targetDir)) {
            return pending(worldsDir, targetDir, level, log);
        }
        GameVersion[] order = GameVersion.chronological();
        int t = indexOf(order, target);
        if (t < 0) {
            return null; // no conversion into this build (yet)
        }
        for (int i = t - 1; i >= 0; i--) {
            File src = new File(new File(worldsDir, order[i].id), level);
            if (!hasWorld(src)) {
                continue;
            }
            if (!supportsStep(order[i], target)) {
                log.warning("There is a world '" + level + "' of " + order[i].id + ", but no conversion from it to "
                        + target.id + ". A new world is made.");
                return null;
            }
            try {
                // a world that is itself only partly taken over from an older build: its remaining chunks are taken
                // over first, so that the new world finds every chunk in the folder it reads
                completeAncestors(worldsDir, src, level, log, 0);
                createFrom(src, targetDir, order[i]);
            } catch (IOException ex) {
                log.severe("The world could not be taken over from " + order[i].id + ": " + ex);
                return null;
            }
            log.info("The world '" + level + "' of " + order[i].id + " is taken over: its chunks are converted when they "
                    + "are first needed (the old world stays as it is). Type /convertworld to convert the rest.");
            return src;
        }
        return null;
    }

    /**
     * If the world in {@code dir} is still being taken over from an older build, copies the chunks it does not have yet from
     * there (the older world first, when it is incomplete too) and marks it complete. Without this a world taken over
     * twice in a row (for example 316, then 320, then 321) would lose the chunks that nobody had visited in the middle build.
     */
    private static void completeAncestors(File worldsDir, File dir, String level, Logger log, int depth)
            throws IOException {
        Properties p = readMarker(dir);
        if (p == null || "true".equals(p.getProperty("complete")) || depth > 8) {
            return;
        }
        File from = new File(new File(worldsDir, p.getProperty("from", "")), level);
        if (!hasWorld(from)) {
            return; // the older world is gone: nothing more can be taken over
        }
        completeAncestors(worldsDir, from, level, log, depth + 1);
        File[] files = from.listFiles();
        int copied = 0;
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (f.isFile() && n.startsWith("c.") && n.endsWith(".dat")) {
                    File mine = new File(dir, n);
                    if (!mine.exists()) {
                        copyFile(f, mine);
                        copied++;
                    }
                }
            }
        }
        markComplete(dir);
        log.info("The world '" + level + "' of " + dir.getParentFile().getName() + " was still being taken over from "
                + p.getProperty("from") + ": " + copied + " more chunks were copied before the next takeover.");
    }

    /** The old folder of a world that was taken over and is not complete, or null. */
    private static File pending(File worldsDir, File targetDir, String level, Logger log) {
        Properties p = readMarker(targetDir);
        if (p == null || "true".equals(p.getProperty("complete"))) {
            return null;
        }
        File src = new File(new File(worldsDir, p.getProperty("from", "")), level);
        if (!hasWorld(src)) {
            log.warning("The world '" + level + "' came from " + p.getProperty("from") + " and was not converted "
                    + "completely, but that world is gone: chunks that were not visited are lost (new land is made there).");
            return null;
        }
        log.info("The world '" + level + "' is still being converted from " + p.getProperty("from")
                + " (chunk by chunk). Keep worlds/" + p.getProperty("from") + "/" + level
                + " until /convertworld says it is done.");
        return src;
    }

    /** Whether a chunk of the older build can be used by the newer one. */
    public static boolean supportsStep(GameVersion from, GameVersion to) {
        if (to == GameVersion.INF_20100325_1640) {
            // 321 (and 320, 316, 313, 227) -> 325-1640: the same chunk files. The generator of 325 makes caves and ore blobs, so
            // land made after the takeover differs from the converted land at its edges; the chunks that are taken over
            // are not changed (they count as populated)
            return from == GameVersion.INF_20100321 || from == GameVersion.INF_20100320
                    || from == GameVersion.INF_20100316 || from == GameVersion.INF_20100313
                    || from == GameVersion.INF_20100227_1433;
        }
        if (to == GameVersion.INF_20100321) {
            // 320 -> 321: the world code is the same in both builds (checked in the jars), so the chunks are read as they are;
            // from 316, 313 and 227 the same as into 320
            return from == GameVersion.INF_20100320 || from == GameVersion.INF_20100316
                    || from == GameVersion.INF_20100313 || from == GameVersion.INF_20100227_1433;
        }
        if (to == GameVersion.INF_20100320) {
            // 316 -> 320 (and 313, 227 -> 320): the same chunk files. The terrain generator of 320 adds trees and ores, so land
            // made after the takeover differs from the converted land at its edges; the chunks that are taken over are
            // not changed (they count as populated).
            return from == GameVersion.INF_20100316 || from == GameVersion.INF_20100313
                    || from == GameVersion.INF_20100227_1433;
        }
        if (to == GameVersion.INF_20100316) {
            // 20100313 -> 20100316: the same chunk files and the same terrain generator (checked in the jars): a world of
            // 20100227 is taken over into 20100316 the same way (its chunks have no light, the bridge works it out)
            return from == GameVersion.INF_20100313 || from == GameVersion.INF_20100227_1433;
        }
        return from == GameVersion.INF_20100227_1433 && to == GameVersion.INF_20100313;
    }

    /** The id of the build a world was taken over from, or null. */
    public static String origin(File targetDir) {
        Properties p = readMarker(targetDir);
        return p == null ? null : p.getProperty("from");
    }

    /** Records that every chunk of the old world was taken over. */
    public static void markComplete(File targetDir) throws IOException {
        Properties p = readMarker(targetDir);
        if (p == null) {
            return;
        }
        p.setProperty("complete", "true");
        writeMarker(targetDir, p);
    }

    private static boolean hasWorld(File dir) {
        return new File(dir, "level.dat").isFile();
    }

    private static int indexOf(GameVersion[] order, GameVersion v) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] == v) {
                return i;
            }
        }
        return -1;
    }

    /** The small files of the new world: level.dat, the players' positions and the note about the origin. */
    private static void createFrom(File src, File dst, GameVersion from) throws IOException {
        if (!dst.isDirectory() && !dst.mkdirs()) {
            throw new IOException("Could not create " + dst);
        }
        copyPlayers(src, dst);
        copyFile(new File(src, "level.dat"), new File(dst, "level.dat"));
        Properties p = new Properties();
        p.setProperty("from", from.id);
        p.setProperty("complete", "false");
        writeMarker(dst, p); // last but one: level.dat first, so a world with a note always has a level.dat
    }

    private static Properties readMarker(File dir) {
        File f = new File(dir, MARKER);
        if (!f.isFile()) {
            return null;
        }
        Properties p = new Properties();
        try {
            InputStream in = new java.io.FileInputStream(f);
            try {
                p.load(in);
            } finally {
                in.close();
            }
        } catch (IOException ex) {
            return null;
        }
        return p;
    }

    private static void writeMarker(File dir, Properties p) throws IOException {
        File tmp = new File(dir, MARKER + ".tmp");
        OutputStream out = new java.io.FileOutputStream(tmp);
        try {
            p.store(out, "A world taken over from an older build (see WorldConverter)");
        } finally {
            out.close();
        }
        File f = new File(dir, MARKER);
        if (f.exists() && !f.delete()) {
            throw new IOException("Could not replace " + f);
        }
        if (!tmp.renameTo(f)) {
            throw new IOException("Could not write " + f);
        }
    }

    private static void copyPlayers(File src, File dst) throws IOException {
        File[] players = new File(src, "players").listFiles();
        if (players == null) {
            return;
        }
        File out = new File(dst, "players");
        if (!out.isDirectory() && !out.mkdirs()) {
            throw new IOException("Could not create " + out);
        }
        for (File f : players) {
            if (f.isFile()) {
                copyFile(f, new File(out, f.getName()));
            }
        }
    }

    public static void copyFile(File from, File to) throws IOException {
        File tmp = new File(to.getPath() + ".tmp");
        InputStream in = new java.io.FileInputStream(from);
        try {
            OutputStream out = new java.io.FileOutputStream(tmp);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
        if (to.exists() && !to.delete()) {
            throw new IOException("Could not replace " + to);
        }
        if (!tmp.renameTo(to)) {
            throw new IOException("Could not write " + to);
        }
    }
}
