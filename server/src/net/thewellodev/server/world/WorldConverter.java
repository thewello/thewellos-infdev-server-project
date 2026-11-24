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
 * <li>inf-20100325-1640 -> inf-20100327 (also 321, 320, 316, 313 and 227): the game now saves its worlds in the Alpha level
 *     format, so the chunk files are not copied as they are: the bridge reads each chunk from the old folder (a flat folder of
 *     c.X.Z.dat files with decimal numbers) and writes it in the new layout (folders named by X mod 64 and Z mod 64 in base 36,
 *     file names with X and Z in base 36). A chunk that nobody visits is converted by /convertworld, a few in each tick, and is
 *     dropped from memory again, so a world of any size can be converted. The chunks keep what they have.</li>
 * <li>inf-20100327 -> inf-20100330-1611 (also every older build): the same Alpha layout and tags, so the chunk files of 327 are moved
 *     over as the files they are; 330-1611 adds the tag TerrainPopulated, and a chunk without it counts as populated. The older
 *     world's folder may have either layout ({@link #chunkFiles}).</li>
 * <li>inf-20100330-1611 -> inf-20100413-1953 (also every older build): the same Alpha layout and tags, so the chunk files of 330-1611 are
 *     moved over as the files they are, as from 327 into 330-1611.</li>
 * <li>When every chunk of the older world is in the new world, the older world is deleted ({@code delete-old-world}).</li>
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
        int copied = 0;
        // the world in dir may be in the Alpha layout (327 and later); the chunk files of the older world are in either layout
        boolean alpha = false;
        for (GameVersion v : GameVersion.chronological()) {
            if (v.id.equals(dir.getParentFile().getName())) {
                alpha = usesAlphaLayout(v);
            }
        }
        for (File f : chunkFiles(from)) {
            int[] xz = chunkCoords(from, f);
            if (xz == null) {
                continue;
            }
            File mine = alpha ? alphaFile(dir, xz[0], xz[1]) : new File(dir, f.getName());
            if (!mine.exists()) {
                if (!mine.getParentFile().isDirectory() && !mine.getParentFile().mkdirs()) {
                    throw new IOException("Could not create " + mine.getParentFile());
                }
                copyFile(f, mine);
                copied++;
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
        if (to == GameVersion.INF_20100415) {
            // 414 -> 415: the same Alpha layout and tags; every older build as into 414
            return from == GameVersion.INF_20100414 || supportsStep(from, GameVersion.INF_20100414);
        }
        if (to == GameVersion.INF_20100414) {
            // 413-1953 -> 414: the same game and the same Alpha layout and tags; every older build as into 413-1953
            return from == GameVersion.INF_20100413_1953 || supportsStep(from, GameVersion.INF_20100413_1953);
        }
        if (to == GameVersion.INF_20100413_1953) {
            // 330-1611 -> 413-1953: the same Alpha layout and tags; every older build as into 330-1611
            return from == GameVersion.INF_20100330_1611 || supportsStep(from, GameVersion.INF_20100330_1611);
        }
        if (to == GameVersion.INF_20100330_1611) {
            // 327 -> 330-1611: the same Alpha layout and tags (330 adds TerrainPopulated; a chunk without it counts as populated
            // for the server); every older build as into 327
            return from == GameVersion.INF_20100327 || supportsStep(from, GameVersion.INF_20100327);
        }
        if (to == GameVersion.INF_20100327) {
            // 325-1640 (and 321, 320, 316, 313, 227) -> 327: the chunks have the same tags and block numbers; the files change
            // their layout (the Alpha level format, see TWBridge327), and so the bridge writes each chunk it takes over in the
            // new layout. The terrain generator of 327 is new (new noise, no caves, sea level 64): the chunks that are taken
            // over keep what they have, new land is made with the new generator.
            return from == GameVersion.INF_20100325_1640 || from == GameVersion.INF_20100321
                    || from == GameVersion.INF_20100320 || from == GameVersion.INF_20100316
                    || from == GameVersion.INF_20100313 || from == GameVersion.INF_20100227_1433;
        }
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

    /**
     * Whether every chunk file of the older world has its counterpart in the new world's folder (a chunk that is only in memory
     * does not count: it is not on the disk yet).
     */
    public static boolean isComplete(File oldDir, File newDir, GameVersion target) {
        File[] files = oldDir.listFiles();
        if (files == null) {
            return false;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("c\\.(-?\\d+)\\.(-?\\d+)\\.dat");
        for (File f : files) {
            java.util.regex.Matcher m = p.matcher(f.getName());
            if (!f.isFile() || !m.matches()) {
                continue;
            }
            File mine;
            int x = Integer.parseInt(m.group(1)), z = Integer.parseInt(m.group(2));
            if (usesAlphaLayout(target)) {
                mine = alphaFile(newDir, x, z);
            } else {
                mine = new File(newDir, f.getName());
            }
            if (!mine.isFile()) {
                return false;
            }
        }
        // chunk files of an older world that is in the Alpha layout itself (327 into 330-1611)
        for (File chunk : alphaChunkFiles(oldDir)) {
            int[] xz = alphaCoords(chunk);
            if (xz != null && !alphaFile(newDir, xz[0], xz[1]).isFile()) {
                return false;
            }
        }
        return true;
    }

    /** Builds whose worlds are in the Alpha level format (327 and later). */
    public static boolean usesAlphaLayout(GameVersion v) {
        return v == GameVersion.INF_20100327 || v == GameVersion.INF_20100330_1611 || v == GameVersion.INF_20100413_1953
                || v == GameVersion.INF_20100414 || v == GameVersion.INF_20100415;
    }

    /** The chunk file of the Alpha layout: X mod 64 and Z mod 64 are folders, the name has X and Z in base 36. */
    public static File alphaFile(File worldDir, int x, int z) {
        return new File(new File(new File(worldDir, Integer.toString(x & 63, 36)), Integer.toString(z & 63, 36)),
                "c." + Integer.toString(x, 36) + "." + Integer.toString(z, 36) + ".dat");
    }

    /** All chunk files that lie in the folders of the Alpha layout (two levels deep). */
    public static java.util.List<File> alphaChunkFiles(File worldDir) {
        java.util.List<File> out = new java.util.ArrayList<File>();
        File[] first = worldDir.listFiles();
        if (first == null) {
            return out;
        }
        for (File a : first) {
            File[] second = a.isDirectory() ? a.listFiles() : null;
            if (second == null) {
                continue;
            }
            for (File b : second) {
                File[] files = b.isDirectory() ? b.listFiles() : null;
                if (files == null) {
                    continue;
                }
                for (File f : files) {
                    if (f.isFile() && alphaCoords(f) != null) {
                        out.add(f);
                    }
                }
            }
        }
        return out;
    }

    /** X and Z of a chunk file of the Alpha layout (names like c.-1.1r.dat, in base 36), or null. */
    public static int[] alphaCoords(File f) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("c\\.(-?[0-9a-z]+)\\.(-?[0-9a-z]+)\\.dat").matcher(f.getName());
        if (!m.matches()) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(m.group(1), 36), Integer.parseInt(m.group(2), 36)};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Every chunk file of an older world, whichever layout it has: the flat folder of the builds before 327 (c.X.Z.dat with
     * decimal numbers) or the Alpha layout.
     */
    public static java.util.List<File> chunkFiles(File worldDir) {
        java.util.List<File> out = new java.util.ArrayList<File>();
        java.util.regex.Pattern flat = java.util.regex.Pattern.compile("c\\.(-?\\d+)\\.(-?\\d+)\\.dat");
        File[] files = worldDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && flat.matcher(f.getName()).matches()) {
                    out.add(f);
                }
            }
        }
        out.addAll(alphaChunkFiles(worldDir));
        return out;
    }

    /** X and Z of a chunk file found by {@link #chunkFiles}: a file that lies in the world folder itself has decimal numbers. */
    public static int[] chunkCoords(File worldDir, File f) {
        if (f.getParentFile() != null && f.getParentFile().equals(worldDir)) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("c\\.(-?\\d+)\\.(-?\\d+)\\.dat").matcher(f.getName());
            return m.matches() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
        }
        return alphaCoords(f);
    }

    /** The chunk file of an older world for a chunk, in either layout; null when there is none. */
    public static File findChunkFile(File worldDir, int x, int z) {
        File flat = new File(worldDir, "c." + x + "." + z + ".dat");
        if (flat.isFile()) {
            return flat;
        }
        File alpha = alphaFile(worldDir, x, z);
        return alpha.isFile() ? alpha : null;
    }

    /** Deletes a folder with everything in it. */
    public static void deleteTree(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteTree(f);
                } else if (!f.delete()) {
                    throw new IOException("Could not delete " + f);
                }
            }
        }
        if (!dir.delete()) {
            throw new IOException("Could not delete " + dir);
        }
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
