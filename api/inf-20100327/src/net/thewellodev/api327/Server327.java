package net.thewellodev.api327;

import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api.command.ConsoleCommandSender;
import net.thewellodev.api.command.PluginCommand;
import net.thewellodev.api.plugin.PluginManager;
import net.thewellodev.api.scheduler.Scheduler;

import java.io.File;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * A running TheWello's Infdev Server that runs Infdev inf-20100327.
 *
 * Get it from {@link TheWello327#getServer()} or from {@link net.thewellodev.api327.plugin.Plugin327#server()}.
 */
public interface Server327 {

    /** Server software name. */
    String getName();

    /** Server version, e.g. "1.0.0". */
    String getVersion();

    /** The game build this server runs: "inf-20100327". */
    String getGameVersion();

    Collection<? extends Player327> getOnlinePlayers();

    /** The player whose name matches exactly (case-insensitive), or null. */
    Player327 getPlayerExact(String name);

    /** The player whose name matches exactly, otherwise the first player whose name starts with the prefix; or null. */
    Player327 getPlayer(String name);

    int getMaxPlayers();

    /** Whether players are verified against their original (Microsoft) account. */
    boolean isOnlineMode();

    int getPort();

    String getMotd();

    /** The world (Infdev has only one). */
    World327 getWorld();

    /** Sends a message to all players and the console. '&amp;' colour codes are supported. @return players reached */
    int broadcastMessage(String message);

    PluginManager getPluginManager();

    Scheduler getScheduler();

    ConsoleCommandSender getConsoleSender();

    /** Runs a command (with or without a leading /). @return true if the command was found */
    boolean dispatchCommand(CommandSender sender, String commandLine);

    /** The command a plugin defined in plugin.yml, or null. */
    PluginCommand getPluginCommand(String name);

    Logger getLogger();

    /** The folder the server runs from. */
    File getWorldContainer();

    boolean isOp(String playerName);

    void setOp(String playerName, boolean op);

    boolean isBanned(String playerName);

    void setBanned(String playerName, boolean banned);

    boolean isWhitelistEnabled();

    boolean isWhitelisted(String playerName);

    /** Average tick rate over the last few seconds (target 20). */
    double getTps();

    /** Average tick duration (ms); it should stay below 50. */
    double getMspt();

    /** Number of chunks the world has in memory. */
    int getLoadedChunkCount();

    /** Saves the world. */
    void saveAll();

    /** Shuts the server down cleanly. */
    void shutdown();

    /** The names of the operators. */
    java.util.Collection<String> getOperators();

    /** The names of the banned players. */
    java.util.Collection<String> getBannedPlayers();

    /** The names on the white list. */
    java.util.Collection<String> getWhitelistedPlayers();

    /**
     * Sends a message to every online player that has the permission, and to the console.
     * @return the number of players that got it
     */
    int broadcastMessage(String message, String permission);

    /** The online players whose name starts with (or, when none does, contains) the text, ignoring case. */
    java.util.List<? extends Player327> matchPlayers(String partialName);

    /** How long the server has been running, in milliseconds. */
    long getUptimeMillis();

    /** The view distance in chunks. */
    int getViewDistance();

    /** The size of the area around the spawn point that only operators may change (spawn-protection, in blocks). */
    int getSpawnRadius();

    /** The only world of the server, in a list. */
    default java.util.List<? extends World327> getWorlds() {
        return java.util.Collections.singletonList(getWorld());
    }

    /** The world with this name (case does not matter), or null. */
    default World327 getWorld(String name) {
        World327 w = getWorld();
        return w != null && w.getName().equalsIgnoreCase(name) ? w : null;
    }

    /** The same as {@link #isOnlineMode()}. */
    default boolean getOnlineMode() {
        return isOnlineMode();
    }

    /** The same as {@link #isWhitelistEnabled()}. */
    default boolean hasWhitelist() {
        return isWhitelistEnabled();
    }

    /** The same as {@link #matchPlayers(String)}. */
    default java.util.List<? extends Player327> matchPlayer(String partialName) {
        return matchPlayers(partialName);
    }

    /** The same as {@link #broadcastMessage(String, String)}. */
    default int broadcast(String message, String permission) {
        return broadcastMessage(message, permission);
    }
}
