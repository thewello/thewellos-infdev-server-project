package net.thewellodev.api327;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100327 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello327.broadcastMessage("&amp;eHello everybody!");
 * Player327 p = TheWello327.getPlayer("Notch");
 * </pre>
 */
public final class TheWello327 {

    private static Server327 server;

    private TheWello327() {
    }

    public static Server327 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server327 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player327> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player327 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World327 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
