package net.thewellodev.api607;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100607 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello607.broadcastMessage("&amp;eHello everybody!");
 * Player607 p = TheWello607.getPlayer("Notch");
 * </pre>
 */
public final class TheWello607 {

    private static Server607 server;

    private TheWello607() {
    }

    public static Server607 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server607 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player607> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player607 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World607 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
