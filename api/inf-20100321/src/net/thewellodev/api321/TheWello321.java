package net.thewellodev.api321;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100321 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello321.broadcastMessage("&amp;eHello everybody!");
 * Player321 p = TheWello321.getPlayer("Notch");
 * </pre>
 */
public final class TheWello321 {

    private static Server321 server;

    private TheWello321() {
    }

    public static Server321 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server321 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player321> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player321 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World321 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
