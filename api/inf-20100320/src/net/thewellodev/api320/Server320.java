package net.thewellodev.api320;

import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api.command.ConsoleCommandSender;
import net.thewellodev.api.command.PluginCommand;
import net.thewellodev.api.plugin.PluginManager;
import net.thewellodev.api.scheduler.Scheduler;

import java.io.File;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * A running TheWello's Infdev Server that runs Infdev inf-20100320.
 *
 * Get it from {@link TheWello320#getServer()} or from {@link net.thewellodev.api320.plugin.Plugin320#server()}.
 */
public interface Server320 {

    /** Server software name. */
    String getName();

    /** Server version, e.g. "1.0.0". */
    String getVersion();

    /** The game build this server runs: "inf-20100320". */
    String getGameVersion();

    Collection<? extends Player320> getOnlinePlayers();

    /** The player whose name matches exactly (case-insensitive), or null. */
    Player320 getPlayerExact(String name);

    /** The player whose name matches exactly, otherwise the first player whose name starts with the prefix; or null. */
    Player320 getPlayer(String name);

    int getMaxPlayers();

    /** Whether players are verified against their original (Microsoft) account. */
    boolean isOnlineMode();

    int getPort();

    String getMotd();

    /** The world (Infdev has only one). */
    World320 getWorld();

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
    java.util.List<? extends Player320> matchPlayers(String partialName);

    /** How long the server has been running, in milliseconds. */
    long getUptimeMillis();
}
