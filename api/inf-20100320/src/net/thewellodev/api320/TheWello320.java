package net.thewellodev.api320;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100320 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello320.broadcastMessage("&amp;eHello everybody!");
 * Player320 p = TheWello320.getPlayer("Notch");
 * </pre>
 */
public final class TheWello320 {

    private static Server320 server;

    private TheWello320() {
    }

    public static Server320 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server320 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player320> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player320 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World320 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
