package net.thewellodev.api415;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100415 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello415.broadcastMessage("&amp;eHello everybody!");
 * Player415 p = TheWello415.getPlayer("Notch");
 * </pre>
 */
public final class TheWello415 {

    private static Server415 server;

    private TheWello415() {
    }

    public static Server415 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server415 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player415> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player415 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World415 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
