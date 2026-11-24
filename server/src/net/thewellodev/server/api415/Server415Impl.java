package net.thewellodev.server.api415;

import net.thewellodev.api.Server;
import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api.command.ConsoleCommandSender;
import net.thewellodev.api.command.PluginCommand;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api.plugin.PluginManager;
import net.thewellodev.api.scheduler.Scheduler;
import net.thewellodev.api415.Player415;
import net.thewellodev.api415.Server415;
import net.thewellodev.api415.World415;
import net.thewellodev.server.TWServer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/** The server as the inf-20100415 API shows it. */
public final class Server415Impl implements Server415 {

    private final TWServer server;
    private final Api415 api;

    public Server415Impl(TWServer server) {
        this.server = server;
        this.api = new Api415(server);
    }

    /** The wrappers and conversions (also used by the event translator). */
    public Api415 api() {
        return api;
    }

    public String getName() {
        return server.getName();
    }

    public String getVersion() {
        return server.getVersion();
    }

    public String getGameVersion() {
        return server.getGameVersion();
    }

    public Collection<? extends Player415> getOnlinePlayers() {
        List<Player415> list = new ArrayList<Player415>();
        for (Player p : server.getOnlinePlayers()) {
            list.add(api.player(p));
        }
        return list;
    }

    public Player415 getPlayerExact(String name) {
        return api.player(server.getPlayerExact(name));
    }

    public Player415 getPlayer(String name) {
        return api.player(server.getPlayer(name));
    }

    public int getMaxPlayers() {
        return server.getMaxPlayers();
    }

    public boolean isOnlineMode() {
        return server.isOnlineMode();
    }

    public int getPort() {
        return server.getPort();
    }

    public int getViewDistance() {
        return server.getViewDistance();
    }

    public int getSpawnRadius() {
        return server.getSpawnProtection();
    }

    public String getMotd() {
        return server.getMotd();
    }

    public World415 getWorld() {
        return api.world(server.getWorld());
    }

    public int broadcastMessage(String message) {
        return server.broadcastMessage(message);
    }

    public PluginManager getPluginManager() {
        return server.getPluginManager();
    }

    public Scheduler getScheduler() {
        return server.getScheduler();
    }

    public ConsoleCommandSender getConsoleSender() {
        return server.getConsoleSender();
    }

    public boolean dispatchCommand(CommandSender sender, String commandLine) {
        if (sender instanceof Player415) {
            Player p = Api415.unwrap((Player415) sender);
            if (p != null) {
                return server.dispatchCommand(p, commandLine);
            }
        }
        return server.dispatchCommand(sender, commandLine);
    }

    public PluginCommand getPluginCommand(String name) {
        return server.getPluginCommand(name);
    }

    public Logger getLogger() {
        return server.getLogger();
    }

    public File getWorldContainer() {
        return server.getWorldContainer();
    }

    public boolean isOp(String playerName) {
        return server.isOp(playerName);
    }

    public void setOp(String playerName, boolean op) {
        server.setOp(playerName, op);
    }

    public boolean isBanned(String playerName) {
        return server.isBanned(playerName);
    }

    public void setBanned(String playerName, boolean banned) {
        server.setBanned(playerName, banned);
    }

    public boolean isWhitelistEnabled() {
        return server.isWhitelistEnabled();
    }

    public boolean isWhitelisted(String playerName) {
        return server.isWhitelisted(playerName);
    }

    public double getTps() {
        return server.getTps();
    }

    public double getMspt() {
        return server.getMspt();
    }

    public int getLoadedChunkCount() {
        return server.getLoadedChunkCount();
    }

    public void saveAll() {
        server.saveAll();
    }

    public void shutdown() {
        server.shutdown();
    }

    public Collection<String> getOperators() {
        return server.getOperators();
    }

    public Collection<String> getBannedPlayers() {
        return server.getBannedPlayers();
    }

    public Collection<String> getWhitelistedPlayers() {
        return server.getWhitelistedPlayers();
    }

    public int broadcastMessage(String message, String permission) {
        return server.broadcastMessage(message, permission);
    }

    public List<? extends Player415> matchPlayers(String partialName) {
        List<Player415> list = new ArrayList<Player415>();
        for (Player p : server.matchPlayers(partialName)) {
            list.add(api.player(p));
        }
        return list;
    }

    public long getUptimeMillis() {
        return server.getUptimeMillis();
    }
}
