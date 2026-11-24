package net.thewellodev.api414;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100414 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello414.broadcastMessage("&amp;eHello everybody!");
 * Player414 p = TheWello414.getPlayer("Notch");
 * </pre>
 */
public final class TheWello414 {

    private static Server414 server;

    private TheWello414() {
    }

    public static Server414 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server414 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player414> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player414 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World414 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
