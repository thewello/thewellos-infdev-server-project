package net.thewellodev.api608;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100608 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello608.broadcastMessage("&amp;eHello everybody!");
 * Player608 p = TheWello608.getPlayer("Notch");
 * </pre>
 */
public final class TheWello608 {

    private static Server608 server;

    private TheWello608() {
    }

    public static Server608 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server608 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player608> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player608 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World608 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
