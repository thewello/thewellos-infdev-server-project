package net.thewellodev.api420;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Static access point to the inf-20100420 API (like the {@code Bukkit} class in Bukkit).
 *
 * <pre>
 * TheWello420.broadcastMessage("&amp;eHello everybody!");
 * Player420 p = TheWello420.getPlayer("Notch");
 * </pre>
 */
public final class TheWello420 {

    private static Server420 server;

    private TheWello420() {
    }

    public static Server420 getServer() {
        return server;
    }

    /** Called once by the server. */
    public static void setServer(Server420 s) {
        if (server != null) {
            throw new IllegalStateException("Server is already set");
        }
        server = s;
    }

    public static Collection<? extends Player420> getOnlinePlayers() {
        return server.getOnlinePlayers();
    }

    public static Player420 getPlayer(String name) {
        return server.getPlayer(name);
    }

    public static int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public static World420 getWorld() {
        return server.getWorld();
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

    public static String getVersion() {
        return server.getVersion();
    }
}
