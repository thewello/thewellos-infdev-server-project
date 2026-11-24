package com.example.hello;

import net.thewellodev.api.command.Command;
import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api.event.EventHandler;
import net.thewellodev.api.event.Listener;
import net.thewellodev.api.util.ChatColor;
import net.thewellodev.api415.Material415;
import net.thewellodev.api415.Player415;
import net.thewellodev.api415.event.block.BlockBreakEvent;
import net.thewellodev.api415.event.block.BlockPlaceEvent;
import net.thewellodev.api415.event.player.PlayerChatEvent;
import net.thewellodev.api415.event.player.PlayerJoinEvent;
import net.thewellodev.api415.plugin.Plugin415;

/**
 * Example plugin for the inf-20100415 API (net.thewellodev.api415).
 * - /hello [name]  : greets the player
 * - /top: teleports to the highest block above you
 * - Welcome message when a player joins
 * - Blocks TNT placement (can be turned on or off in the config)
 * - Announcement every 5 minutes
 * - Tells everybody when somebody breaks gold ore
 *
 * plugin.yml needs the line {@code api: inf-20100415}.
 */
public class HelloPlugin extends Plugin415 implements Listener {

    @Override
    public void onEnable() {
        // config.yml defaults
        getConfig().addDefault("welcome-message", "&aWelcome, %player%! &fThere are %online% players online.");
        getConfig().addDefault("block-tnt", true);
        getConfig().addDefault("announcement", "&6[Announcement] &fType /hello to see the rules!");
        getConfig().addDefault("announcement-minutes", 5);
        saveConfig();

        server().getPluginManager().registerEvents(this, this);

        long period = 20L * 60 * getConfig().getInt("announcement-minutes", 5);
        server().getScheduler().runTaskTimer(this, new Runnable() {
            public void run() {
                if (!server().getOnlinePlayers().isEmpty()) {
                    server().broadcastMessage(getConfig().getString("announcement"));
                }
            }
        }, period, period);

        getLogger().info("HelloPlugin enabled! Server: " + server().getName() + " " + server().getVersion()
                + " (" + server().getGameVersion() + ")");
    }

    @Override
    public void onDisable() {
        getLogger().info("HelloPlugin disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("hello")) {
            String who = args.length > 0 ? args[0] : sender.getName();
            sender.sendMessage(ChatColor.YELLOW + "Hello " + ChatColor.WHITE + who + ChatColor.YELLOW + "!");
            return true;
        }
        if (command.getName().equals("top")) {
            if (!(sender instanceof Player415)) {
                sender.sendMessage("Players only.");
                return true;
            }
            Player415 p = (Player415) sender;
            int x = p.getLocation().getBlockX();
            int z = p.getLocation().getBlockZ();
            int y = p.getWorld().getHighestBlockYAt(x, z);
            p.teleport(x + 0.5, y + 0.1, z + 0.5);
            p.sendMessage(ChatColor.GREEN + "Teleported to the top (y=" + y + ")");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String msg = getConfig().getString("welcome-message")
                .replace("%player%", e.getPlayer().getName())
                .replace("%online%", String.valueOf(server().getOnlinePlayers().size()));
        e.getPlayer().sendMessage(msg);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.getNewTypeId() == Material415.TNT.getId() && getConfig().getBoolean("block-tnt", true)
                && !e.getPlayer().isOp()) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Placing TNT is not allowed on this server!");
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material415.GOLD_ORE) {
            server().broadcastMessage(ChatColor.YELLOW + e.getPlayer().getName() + " found gold!");
        }
    }

    @EventHandler
    public void onChat(PlayerChatEvent e) {
        if (e.getPlayer().isOp()) {
            e.setFormat("&c[OP] &f<%1$s> %2$s");
        }
    }
}
