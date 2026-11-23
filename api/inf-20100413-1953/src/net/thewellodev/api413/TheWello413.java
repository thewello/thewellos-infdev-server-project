package net.thewellodev.api413;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100413-1953 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello413.broadcastMessage("&amp;eHello everybody!");
 * Player413 p = TheWello413.getPlayer("Notch");
 * </pre>
 */
public final class TheWello413 {

    private static Server413 server;

    private TheWello413() {
    }

    public static Server413 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server413 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player413> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player413 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World413 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
