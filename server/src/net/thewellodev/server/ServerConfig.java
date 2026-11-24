package net.thewellodev.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * server.properties. The file is always written in the same layout (sections, comments, key order) as the
 * template in the release bundle (release/server/server.properties); keep the two in step.
 */
public class ServerConfig {

    /** Layout: a line starting with "#" or empty is written as it is; anything else is a key. */
    private static final String[] LAYOUT = {
            "# TheWello's Infdev Server settings. Restart the server after changing them.",
            "",
            "# Network",
            "server-port",
            "server-ip",
            "motd",
            "max-players",
            "upnp",
            "online-mode",
            "white-list",
            "",
            "# World",
            "level-name",
            "level-seed",
            "view-distance",
            "spawn-protection",
            "# A world taken over from an older build keeps its old land as it is (false), or its chunks get the features that the newer build's populate step makes: ores, trees, and the caves of inf-20100325-1640 (true)",
            "upgrade-old-terrain",
            "# When every chunk of an older build's world has been taken over (by play or by /convertworld) the old world's folder is deleted (true) or kept (false)",
            "delete-old-world",
            "",
            "# Gameplay (difficulty, monsters and animals do not exist in inf-20100227-1433, inf-20100313, inf-20100316, inf-20100320, inf-20100321, inf-20100325-1640, inf-20100327, inf-20100330-1611, inf-20100413-1953, inf-20100414 and inf-20100415)",
            "pvp",
            "difficulty",
            "spawn-monsters",
            "spawn-animals",
            "mob-cap-monsters",
            "mob-cap-animals",
            "",
            "# Saving",
            "autosave-minutes",
            "",
            "# Game version this server runs (put the matching original jar in this folder):",
            "#   inf-20100630-1340, inf-20100227-1433, inf-20100313, inf-20100316, inf-20100320, inf-20100321, inf-20100325-1640, inf-20100327, inf-20100330-1611, inf-20100413-1953, inf-20100414   or   inf-20100415",
            "game-version",
            "",
            "# Game code jar (auto = the inf-*.jar in this folder that belongs to game-version)",
            "game-jar",
    };

    private final File file;
    private final Properties props = new Properties();
    private boolean dirty;

    public ServerConfig(File file) {
        this.file = file;
        if (file.exists()) {
            try {
                InputStream in = new FileInputStream(file);
                try {
                    props.load(in);
                } finally {
                    in.close();
                }
            } catch (IOException e) {
                System.err.println("Could not read server.properties: " + e);
            }
        } else {
            dirty = true;
        }
    }

    public String getString(String key, String def) {
        String v = props.getProperty(key);
        if (v == null) {
            props.setProperty(key, def);
            dirty = true;
            return def;
        }
        return v.trim();
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(getString(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBoolean(String key, boolean def) {
        return Boolean.parseBoolean(getString(key, String.valueOf(def)));
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
        dirty = true;
    }

    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(file), "ISO-8859-1");
            try {
                Set<String> written = new HashSet<String>();
                for (String line : LAYOUT) {
                    if (line.length() == 0 || line.startsWith("#")) {
                        w.write(line);
                        w.write("\n");
                    } else {
                        String v = props.getProperty(line);
                        if (v != null) {
                            w.write(line + "=" + escape(v) + "\n");
                            written.add(line);
                        }
                    }
                }
                // keys that are not part of the layout (added by hand or by plugins) are kept at the end
                Set<String> others = new TreeSet<String>();
                for (String k : props.stringPropertyNames()) {
                    if (!written.contains(k)) {
                        others.add(k);
                    }
                }
                if (!others.isEmpty()) {
                    w.write("\n# Other\n");
                    for (String k : others) {
                        w.write(k + "=" + escape(props.getProperty(k)) + "\n");
                    }
                }
            } finally {
                w.close();
            }
            dirty = false;
        } catch (IOException e) {
            System.err.println("Could not write server.properties: " + e);
        }
    }

    /** Escapes a value for a properties file (ISO-8859-1, so other characters become \\uXXXX). */
    private static String escape(String v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (c == ' ' && i == 0) {
                sb.append("\\ ");
            } else if (c < 32 || c > 126) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
