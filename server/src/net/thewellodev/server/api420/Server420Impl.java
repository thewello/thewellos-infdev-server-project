package net.thewellodev.server.api420;

import net.thewellodev.api.Server;
import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api.command.ConsoleCommandSender;
import net.thewellodev.api.command.PluginCommand;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api.plugin.PluginManager;
import net.thewellodev.api.scheduler.Scheduler;
import net.thewellodev.api420.Player420;
import net.thewellodev.api420.Server420;
import net.thewellodev.api420.World420;
import net.thewellodev.server.TWServer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/** The server as the inf-20100420 API shows it. */
public final class Server420Impl implements Server420 {

    private final TWServer server;
    private final Api420 api;

    public Server420Impl(TWServer server) {
        this.server = server;
        this.api = new Api420(server);
    }

    /** The wrappers and conversions (also used by the event translator). */
    public Api420 api() {
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

    public Collection<? extends Player420> getOnlinePlayers() {
        List<Player420> list = new ArrayList<Player420>();
        for (Player p : server.getOnlinePlayers()) {
            list.add(api.player(p));
        }
        return list;
    }

    public Player420 getPlayerExact(String name) {
        return api.player(server.getPlayerExact(name));
    }

    public Player420 getPlayer(String name) {
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

    public World420 getWorld() {
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
        if (sender instanceof Player420) {
            Player p = Api420.unwrap((Player420) sender);
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

    public List<? extends Player420> matchPlayers(String partialName) {
        List<Player420> list = new ArrayList<Player420>();
        for (Player p : server.matchPlayers(partialName)) {
            list.add(api.player(p));
        }
        return list;
    }

    public long getUptimeMillis() {
        return server.getUptimeMillis();
    }
}
