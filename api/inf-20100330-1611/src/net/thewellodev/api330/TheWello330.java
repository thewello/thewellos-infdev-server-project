package net.thewellodev.api330;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100330-1611 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello330.broadcastMessage("&amp;eHello everybody!");
 * Player330 p = TheWello330.getPlayer("Notch");
 * </pre>
 */
public final class TheWello330 {

    private static Server330 server;

    private TheWello330() {
    }

    public static Server330 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server330 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player330> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player330 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World330 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
