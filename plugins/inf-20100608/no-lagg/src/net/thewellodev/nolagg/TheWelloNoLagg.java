package net.thewellodev.nolagg;

import net.thewellodev.api.command.Command;
import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api608.Player608;
import net.thewellodev.api608.plugin.Plugin608;

import java.util.Locale;

/**
 * TheWelloNoLagg for inf-20100608: keeps a server healthy. This build has no mobs, no items on the ground and
 * no TNT, so (unlike the plugin for inf-20100630-1340) there is nothing to cap or merge. What can still go wrong is
 * memory and speed: the game keeps every chunk it ever made in memory and never lets one go.
 *
 * Features (all configurable in config.yml):
 * <ul>
 *   <li>a memory guard: when the used memory stays above a limit, the plugin frees what it can (garbage collection)
 *       and tells the operators,</li>
 *   <li>a chunk watch: operators are told when the number of chunks in memory passes a limit, and the world is
 *       saved (the chunks stay on disk, so a restart frees them),</li>
 *   <li>a TPS watch: operators are told when the server stays slow.</li>
 * </ul>
 * Operators can check the state with /nolagg status and run /nolagg gc and /nolagg save.
 */
public class TheWelloNoLagg extends Plugin608 {

    private boolean memoryGuard;
    private int memoryPercent;
    private int memorySecondsBeforeAction;
    private int chunkLimit;
    private boolean tpsWatch;
    private double tpsFloor;
    private int tpsSecondsBeforeAlert;
    private int cooldownSeconds;

    private int highMemorySeconds;
    private int slowSeconds;
    private long lastMemoryAction;
    private long lastChunkAlert;
    private long lastTpsAlert;
    private int gcRuns;
    private int saves;
    private int tpsAlerts;

    @Override
    public void onEnable() {
        getConfig().addDefault("memory-guard.enabled", true);
        getConfig().addDefault("memory-guard.percent", 85);
        getConfig().addDefault("memory-guard.seconds", 10);
        getConfig().addDefault("max-loaded-chunks", 6000);
        getConfig().addDefault("tps-watch.enabled", true);
        getConfig().addDefault("tps-watch.min-tps", 14.0);
        getConfig().addDefault("tps-watch.low-seconds", 10);
        getConfig().addDefault("cooldown-seconds", 120);
        saveConfig();
        memoryGuard = getConfig().getBoolean("memory-guard.enabled", true);
        memoryPercent = Math.max(30, Math.min(99, getConfig().getInt("memory-guard.percent", 85)));
        memorySecondsBeforeAction = Math.max(1, getConfig().getInt("memory-guard.seconds", 10));
        chunkLimit = getConfig().getInt("max-loaded-chunks", 6000);
        tpsWatch = getConfig().getBoolean("tps-watch.enabled", true);
        tpsFloor = getConfig().getDouble("tps-watch.min-tps", 14.0);
        tpsSecondsBeforeAlert = Math.max(1, getConfig().getInt("tps-watch.low-seconds", 10));
        cooldownSeconds = Math.max(10, getConfig().getInt("cooldown-seconds", 120));

        server().getScheduler().runTaskTimer(this, new Runnable() {
            public void run() {
                onSecond();
            }
        }, 20L, 20L);
        getLogger().info("TheWelloNoLagg enabled: memory guard at " + memoryPercent + "%, chunk limit " + chunkLimit + ".");
    }

    @Override
    public void onDisable() {
        getLogger().info("TheWelloNoLagg disabled.");
    }

    private void onSecond() {
        long now = System.currentTimeMillis();
        boolean cooled = now - lastMemoryAction > cooldownSeconds * 1000L;

        if (memoryGuard) {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            boolean high = used * 100L > rt.maxMemory() * memoryPercent;
            highMemorySeconds = high ? highMemorySeconds + 1 : 0;
            if (highMemorySeconds >= memorySecondsBeforeAction && cooled) {
                lastMemoryAction = now;
                highMemorySeconds = 0;
                gcRuns++;
                System.gc();
                long after = (rt.totalMemory() - rt.freeMemory()) >> 20;
                tellOperators(String.format(Locale.ENGLISH, "&e[NoLagg] &fMemory was high (%d MB of %d MB): freed memory, "
                        + "now %d MB.", used >> 20, rt.maxMemory() >> 20, after));
            }
        }

        int chunks = server().getLoadedChunkCount();
        if (chunkLimit > 0 && chunks > chunkLimit && now - lastChunkAlert > cooldownSeconds * 1000L) {
            lastChunkAlert = now;
            saves++;
            server().saveAll();
            tellOperators("&e[NoLagg] &f" + chunks + " chunks are in memory (limit " + chunkLimit + "): the world was saved. "
                    + "A restart frees them.");
        }

        if (tpsWatch) {
            double tps = server().getTps();
            boolean someoneOnline = !server().getOnlinePlayers().isEmpty();
            slowSeconds = (tps < tpsFloor && someoneOnline) ? slowSeconds + 1 : 0;
            if (slowSeconds >= tpsSecondsBeforeAlert && now - lastTpsAlert > cooldownSeconds * 1000L) {
                lastTpsAlert = now;
                slowSeconds = 0;
                tpsAlerts++;
                String message = String.format(Locale.ENGLISH, "&e[NoLagg] &fTPS has been low (%.1f, tick %.1f ms).", tps,
                        server().getMspt());
                getLogger().warning(message.replaceAll("&[0-9a-fk-or]", ""));
                tellOperators(message);
            }
        }
    }

    private void tellOperators(String message) {
        for (Player608 p : server().getOnlinePlayers()) {
            if (p.isOp()) {
                p.sendMessage(message);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equals("nolagg")) {
            return false;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ENGLISH);
        Runtime rt = Runtime.getRuntime();
        if (sub.equals("status")) {
            long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
            sender.sendMessage("&6--- TheWelloNoLagg ---");
            sender.sendMessage(String.format(Locale.ENGLISH, "&eTPS: &f%.1f  &eTick: &f%.1f ms  &eMemory: &f%d/%d MB",
                    server().getTps(), server().getMspt(), usedMb, rt.maxMemory() >> 20));
            sender.sendMessage("&ePlayers: &f" + server().getOnlinePlayers().size() + "  &eChunks in memory: &f"
                    + server().getLoadedChunkCount());
            sender.sendMessage("&eSince start: &f" + gcRuns + " memory clean-ups, " + saves + " saves, " + tpsAlerts
                    + " TPS alerts");
            return true;
        }
        if (sub.equals("gc")) {
            long before = (rt.totalMemory() - rt.freeMemory()) >> 20;
            System.gc();
            long after = (rt.totalMemory() - rt.freeMemory()) >> 20;
            sender.sendMessage("&aMemory: &f" + before + " MB -> " + after + " MB");
            return true;
        }
        if (sub.equals("save")) {
            server().saveAll();
            sender.sendMessage("&aThe world was saved.");
            return true;
        }
        return false;
    }
}
