package net.thewellodev.api325;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100325-1640 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello325.broadcastMessage("&amp;eHello everybody!");
 * Player325 p = TheWello325.getPlayer("Notch");
 * </pre>
 */
public final class TheWello325 {

    private static Server325 server;

    private TheWello325() {
    }

    public static Server325 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server325 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player325> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player325 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World325 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
