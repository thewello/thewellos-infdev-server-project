package net.thewellodev.server.command;

import net.thewellodev.api.Location;
import net.thewellodev.api.Material;
import net.thewellodev.api.command.Command;
import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api.plugin.Plugin;
import net.thewellodev.server.TWServer;

import java.util.Arrays;
import java.util.List;

/** Built-in server commands. */
public final class BuiltinCommands {

    /** Built-in commands that non-operators may use by default. */
    public static final List<String> DEFAULT_PERMISSIONS = Arrays.asList(
            "thewello.command.help", "thewello.command.list", "thewello.command.tell", "thewello.command.me",
            "thewello.command.plugins", "thewello.command.version", "thewello.command.spawn",
            "thewello.command.ping");

    private BuiltinCommands() {
    }

    private abstract static class Cmd extends Command {
        Cmd(String name, String usage, String desc, String... aliases) {
            super(name);
            setUsage(usage);
            setDescription(desc);
            setAliases(Arrays.asList(aliases));
            setPermission("thewello.command." + name);
        }

        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!testPermission(sender)) {
                return true;
            }
            if (!run(sender, label, args)) {
                sender.sendMessage("&eUsage: " + getUsage());
            }
            return true;
        }

        abstract boolean run(CommandSender s, String label, String[] a);
    }

    /** Lines per help page (fits the chat window). */
    static final int HELP_PER_PAGE = 10;

    /** Built-in command categories; the order is the order given here. */
    static final List<String> CATEGORY_ORDER = Arrays.asList("General", "Player", "World", "Admin", "Plugins", "Other");
    private static final java.util.Map<String, String> CATEGORY_OF = new java.util.HashMap<String, String>();

    static {
        category("General", "help list tell me ping version plugins seed status ip");
        category("Player", "spawn tp tphere give gamemode heal kill");
        category("World", "time setspawn setblock killmobs clearitems difficulty spawnmob save-all convertworld");
        category("Admin", "say kick ban pardon banlist op deop whitelist stop reload");
    }

    private static void category(String name, String commands) {
        for (String c : commands.split(" ")) {
            CATEGORY_OF.put(c, name);
        }
    }

    /** The commands the sender may see, one line each, grouped by category. */
    static List<String> helpLines(TWServer server, CommandSender s) {
        java.util.Map<String, List<Command>> groups = new java.util.LinkedHashMap<String, List<Command>>();
        for (String cat : CATEGORY_ORDER) {
            groups.put(cat, new java.util.ArrayList<Command>());
        }
        CommandMap map = server.getCommandMap();
        for (Command c : map.getCommands()) {
            if (c.getPermission() != null && !s.hasPermission(c.getPermission())) {
                continue;
            }
            String cat;
            if (map.getOwner(c) != null) {
                cat = "Plugins";
            } else {
                cat = CATEGORY_OF.containsKey(c.getName()) ? CATEGORY_OF.get(c.getName()) : "Other";
            }
            groups.get(cat).add(c);
        }
        List<String> out = new java.util.ArrayList<String>();
        for (java.util.Map.Entry<String, List<Command>> en : groups.entrySet()) {
            if (en.getValue().isEmpty()) {
                continue;
            }
            out.add("&6[" + en.getKey() + "]");
            for (Command c : en.getValue()) {
                out.add("&f" + usageOf(c) + " &7- " + c.getDescription());
            }
        }
        return out;
    }

    private static String usageOf(Command c) {
        String u = c.getUsage() == null ? "/" + c.getName() : c.getUsage();
        return u.replace("/<command>", "/" + c.getName());
    }

    /** /help <command>: usage, description, aliases and owner of one command. */
    static boolean helpFor(TWServer server, CommandSender s, String name) {
        String n = name.startsWith("/") ? name.substring(1) : name;
        Command c = server.getCommandMap().get(n);
        if (c == null || (c.getPermission() != null && !s.hasPermission(c.getPermission()))) {
            return helpSearch(server, s, n);
        }
        s.sendMessage("&e--- /" + c.getName() + " ---");
        s.sendMessage("&fUsage: &6" + usageOf(c));
        s.sendMessage("&fDescription: &f" + c.getDescription());
        if (c.getAliases() != null && !c.getAliases().isEmpty()) {
            s.sendMessage("&fAliases: &7" + join(c.getAliases().toArray(new String[0]), 0));
        }
        Plugin owner = map(server).getOwner(c);
        if (owner != null) {
            s.sendMessage("&fPlugin: &7" + owner.getName());
        }
        return true;
    }

    /** If there is no exact command, searches names and descriptions and shows all matches in one list (no paging). */
    static boolean helpSearch(TWServer server, CommandSender s, String term) {
        String t = term.toLowerCase(java.util.Locale.ENGLISH);
        List<String> hits = new java.util.ArrayList<String>();
        for (Command c : server.getCommandMap().getCommands()) {
            if (c.getPermission() != null && !s.hasPermission(c.getPermission())) {
                continue;
            }
            String desc = c.getDescription() == null ? "" : c.getDescription();
            if (c.getName().toLowerCase(java.util.Locale.ENGLISH).contains(t)
                    || desc.toLowerCase(java.util.Locale.ENGLISH).contains(t)) {
                hits.add("&f" + usageOf(c) + " &7- " + desc);
            }
        }
        if (hits.isEmpty()) {
            s.sendMessage("&cNo command named: /" + term + "  (use /help for the full list)");
            return true;
        }
        s.sendMessage("&e--- \"" + term + "\" matches: " + hits.size() + " ---");
        for (String h : hits) {
            s.sendMessage(h);
        }
        return true;
    }

    private static CommandMap map(TWServer server) {
        return server.getCommandMap();
    }

    static String join(String[] a, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < a.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(a[i]);
        }
        return sb.toString();
    }

    public static void registerAll(final TWServer server, CommandMap map) {
        map.registerBuiltin(new Cmd("help", "/help [page | command]",
                "Shows the command list, or the details of one command", "?") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length > 0 && !a[0].matches("\\d+")) {
                    return helpFor(server, s, a[0]);
                }
                List<String> lines = helpLines(server, s);
                int per = HELP_PER_PAGE;
                int pages = Math.max(1, (lines.size() + per - 1) / per);
                int page = 1;
                if (a.length > 0) {
                    try {
                        page = Integer.parseInt(a[0]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                page = Math.max(1, Math.min(pages, page));
                s.sendMessage("&e--- Help (" + page + "/" + pages + ")  |  /help <page>  or  /help <command> ---");
                for (int i = (page - 1) * per; i < Math.min(lines.size(), page * per); i++) {
                    s.sendMessage(lines.get(i));
                }
                return true;
            }
        });
        map.registerBuiltin(new Cmd("list", "/list", "Lists the online players", "online", "who") {
            boolean run(CommandSender s, String l, String[] a) {
                StringBuilder sb = new StringBuilder();
                for (Player p : server.getOnlinePlayers()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(p.getName());
                }
                s.sendMessage("&eOnline (" + server.getOnlinePlayers().size() + "/" + server.getMaxPlayers()
                        + "): &f" + sb);
                return true;
            }
        });
        map.registerBuiltin(new Cmd("say", "/say <message>", "Broadcasts a message to everyone") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                server.broadcastMessage("&d[" + (s instanceof Player ? s.getName() : "Server") + "] " + join(a, 0));
                return true;
            }
        });
        map.registerBuiltin(new Cmd("tell", "/tell <player> <message>", "Sends a private message", "msg", "w", "whisper") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length < 2) {
                    return false;
                }
                Player t = server.getPlayer(a[0]);
                if (t == null) {
                    s.sendMessage("&cPlayer not found: " + a[0]);
                    return true;
                }
                String msg = join(a, 1);
                t.sendMessage("&7" + s.getName() + " whispers to you: " + msg);
                s.sendMessage("&7To " + t.getName() + ": " + msg);
                return true;
            }
        });
        map.registerBuiltin(new Cmd("me", "/me <action>", "Sends an action message") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                server.broadcastMessage("* " + s.getName() + " " + join(a, 0));
                return true;
            }
        });
        map.registerBuiltin(new Cmd("tp", "/tp <player> [target] | /tp [player] <x> <y> <z>", "Teleports", "teleport") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 1 || a.length == 2) {
                    Player who = a.length == 2 ? server.getPlayer(a[0]) : (s instanceof Player ? (Player) s : null);
                    Player target = server.getPlayer(a[a.length - 1]);
                    if (who == null || target == null) {
                        s.sendMessage("&cPlayer not found.");
                        return true;
                    }
                    who.teleport(target.getLocation());
                    s.sendMessage("&e" + who.getName() + " -> " + target.getName() + " teleported.");
                    return true;
                }
                if (a.length == 3 || a.length == 4) {
                    Player who = a.length == 4 ? server.getPlayer(a[0]) : (s instanceof Player ? (Player) s : null);
                    if (who == null) {
                        s.sendMessage("&cPlayer not found.");
                        return true;
                    }
                    int o = a.length - 3;
                    try {
                        Location loc = who.getLocation();
                        double x = coord(a[o], loc.getX());
                        double y = coord(a[o + 1], loc.getY());
                        double z = coord(a[o + 2], loc.getZ());
                        who.teleport(x, y, z);
                        s.sendMessage(String.format("&e%s teleported: %.1f %.1f %.1f", who.getName(), x, y, z));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    return true;
                }
                return false;
            }
        });
        map.registerBuiltin(new Cmd("kick", "/kick <player> [reason]", "Kicks a player") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                Player t = server.getPlayer(a[0]);
                if (t == null) {
                    s.sendMessage("&cPlayer not found: " + a[0]);
                    return true;
                }
                String reason = a.length > 1 ? join(a, 1) : "Kicked from the server";
                t.kickPlayer(reason);
                server.broadcastMessage("&e" + t.getName() + " was kicked: " + reason);
                return true;
            }
        });
        map.registerBuiltin(new Cmd("ban", "/ban <player> [reason]", "Bans a player") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                server.setBanned(a[0], true);
                Player t = server.getPlayerExact(a[0]);
                String reason = a.length > 1 ? join(a, 1) : "You are banned from this server";
                if (t != null) {
                    t.kickPlayer(reason);
                }
                server.broadcastMessage("&e" + a[0] + " was banned.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("pardon", "/pardon <player>", "Removes a ban", "unban") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                server.setBanned(a[0], false);
                s.sendMessage("&e" + a[0] + " was unbanned.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("banlist", "/banlist", "Lists the banned players") {
            boolean run(CommandSender s, String l, String[] a) {
                s.sendMessage("&eBanned: &f" + server.getBannedList());
                return true;
            }
        });
        map.registerBuiltin(new Cmd("op", "/op <player>", "Makes a player an operator") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                server.setOp(a[0], true);
                s.sendMessage("&e" + a[0] + " is now an operator.");
                Player t = server.getPlayerExact(a[0]);
                if (t != null) {
                    t.sendMessage("&eYou are an operator now! &fYou can use all commands (/help) and bypass spawn protection "
                            + "and use colour codes (&&) in chat. Examples: /give, /gamemode, /heal, /tp, /time, /kick");
                }
                return true;
            }
        });
        map.registerBuiltin(new Cmd("deop", "/deop <player>", "Removes operator status") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                server.setOp(a[0], false);
                s.sendMessage("&e" + a[0] + " is no longer an operator.");
                Player t = server.getPlayerExact(a[0]);
                if (t != null) {
                    t.sendMessage("&eYou are no longer an operator.");
                }
                return true;
            }
        });
        map.registerBuiltin(new Cmd("whitelist", "/whitelist <on|off|add|remove|list> [player]", "Whitelist") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                String sub = a[0].toLowerCase(java.util.Locale.ENGLISH);
                if (sub.equals("on") || sub.equals("off")) {
                    server.setWhitelistEnabled(sub.equals("on"));
                    s.sendMessage("&eWhite list " + (sub.equals("on") ? "enabled." : "disabled."));
                } else if ((sub.equals("add") || sub.equals("remove")) && a.length > 1) {
                    server.setWhitelisted(a[1], sub.equals("add"));
                    s.sendMessage("&e" + a[1] + (sub.equals("add") ? " added." : " removed."));
                } else if (sub.equals("list")) {
                    s.sendMessage("&eWhitelist (" + (server.isWhitelistEnabled() ? "on" : "off") + "): &f"
                            + server.getWhitelist());
                } else {
                    return false;
                }
                return true;
            }
        });
        map.registerBuiltin(new Cmd("time", "/time <set|add> <value> | /time <day|night>", "Sets the world time") {
            boolean run(CommandSender s, String l, String[] a) {
                long day0 = "inf-20100313".equals(server.getGameVersion()) ? 12000 : 24000;
                if (a.length == 0) {
                    s.sendMessage("&eTime: " + server.getWorld().getTime() + " (time of day: "
                            + (server.getWorld().getTime() % day0) + ")");
                    return true;
                }
                long now = server.getWorld().getTime();
                // a day lasts 12000 ticks in inf-20100313 and 24000 ticks in the other builds
                long day = "inf-20100313".equals(server.getGameVersion()) ? 12000 : 24000;
                long dusk = day * 13 / 24;
                long base = now - (now % day);
                String sub = a[0].toLowerCase(java.util.Locale.ENGLISH);
                try {
                    if (sub.equals("day")) {
                        server.getWorld().setTime(base + day);
                    } else if (sub.equals("night")) {
                        server.getWorld().setTime(base + dusk + (now % day > dusk ? day : 0));
                    } else if (sub.equals("set") && a.length > 1) {
                        long v = a[1].equalsIgnoreCase("day") ? 0 : a[1].equalsIgnoreCase("night") ? dusk
                                : Long.parseLong(a[1]);
                        server.getWorld().setTime(base + v);
                    } else if (sub.equals("add") && a.length > 1) {
                        server.getWorld().setTime(now + Long.parseLong(a[1]));
                    } else {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
                s.sendMessage("&eTime set: " + (server.getWorld().getTime() % day));
                return true;
            }
        });
        map.registerBuiltin(new Cmd("convertworld", "/convertworld",
                "Converts the rest of a world that was taken over from an older build") {
            boolean run(CommandSender s, String l, String[] a) {
                server.startWorldConversion(s);
                return true;
            }
        });
        map.registerBuiltin(new Cmd("save-all", "/save-all", "Saves the world", "save") {
            boolean run(CommandSender s, String l, String[] a) {
                s.sendMessage("&eSaving the world...");
                server.saveAll();
                s.sendMessage("&eSaved.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("stop", "/stop", "Shuts down the server") {
            boolean run(CommandSender s, String l, String[] a) {
                s.sendMessage("&eShutting down...");
                server.shutdown();
                return true;
            }
        });
        map.registerBuiltin(new Cmd("plugins", "/plugins", "Lists the loaded plugins", "pl") {
            boolean run(CommandSender s, String l, String[] a) {
                StringBuilder sb = new StringBuilder();
                List<Plugin> ps = server.getPluginManager().getPlugins();
                for (Plugin p : ps) {
                    if (sb.length() > 0) {
                        sb.append("&f, ");
                    }
                    sb.append(p.isEnabled() ? "&a" : "&c").append(p.getName());
                }
                s.sendMessage("&ePlugin'ler (" + ps.size() + "): " + sb);
                return true;
            }
        });
        map.registerBuiltin(new Cmd("version", "/version", "Shows the server version", "ver", "about") {
            boolean run(CommandSender s, String l, String[] a) {
                s.sendMessage("&eTheWello's Infdev Server " + server.getVersion() + " &f(" + server.getGameVersion()
                        + ") - Infdev server with plugin support (plugin API)");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("seed", "/seed", "Shows the world seed") {
            boolean run(CommandSender s, String l, String[] a) {
                s.sendMessage("&eSeed: &f" + server.getWorld().getSeed());
                return true;
            }
        });
        map.registerBuiltin(new Cmd("spawn", "/spawn [player]", "Teleports to the spawn point") {
            boolean run(CommandSender s, String l, String[] a) {
                Player target;
                if (a.length > 0) {
                    if (!s.isOp()) {
                        s.sendMessage("&cYou must be an operator to teleport others.");
                        return true;
                    }
                    target = server.getPlayer(a[0]);
                    if (target == null) {
                        s.sendMessage("&cPlayer not found: " + a[0]);
                        return true;
                    }
                } else if (s instanceof Player) {
                    target = (Player) s;
                } else {
                    return false;
                }
                Location sp = server.getWorld().getSpawnLocation();
                int y = server.getWorld().getHighestBlockYAt(sp.getBlockX(), sp.getBlockZ());
                target.teleport(sp.getX(), y + 0.5, sp.getZ());
                s.sendMessage("&e" + target.getName() + " teleported to the spawn.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("setspawn", "/setspawn", "Sets the spawn point to your current position") {
            boolean run(CommandSender s, String l, String[] a) {
                if (!(s instanceof Player)) {
                    s.sendMessage("Only players can use this command.");
                    return true;
                }
                Location loc = ((Player) s).getLocation();
                server.getWorld().setSpawnLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                s.sendMessage("&eSpawn set: " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
                return true;
            }
        });
        map.registerBuiltin(new Cmd("setblock", "/setblock <x> <y> <z> <block> [data]", "Changes a block") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length < 4) {
                    return false;
                }
                try {
                    int x = Integer.parseInt(a[0]);
                    int y = Integer.parseInt(a[1]);
                    int z = Integer.parseInt(a[2]);
                    Material m = Material.matchMaterial(a[3]);
                    if (m == null) {
                        s.sendMessage("&cUnknown block: " + a[3]);
                        return true;
                    }
                    int data = a.length > 4 ? Integer.parseInt(a[4]) : 0;
                    server.getWorld().setBlockTypeIdAndData(x, y, z, m.getId(), data);
                    s.sendMessage("&eBlock changed: " + m.name());
                } catch (NumberFormatException e) {
                    return false;
                }
                return true;
            }
        });
        map.registerBuiltin(new Cmd("status", "/status", "RAM, TPS and player info", "tps", "mem", "gc") {
            boolean run(CommandSender s, String l, String[] a) {
                Runtime r = Runtime.getRuntime();
                long used = (r.totalMemory() - r.freeMemory()) / 1048576L;
                s.sendMessage(String.format("&eTPS: &f%.1f  &eRAM: &f%d/%d MB  &ePlayers: &f%d  &eChunks: &f%d  &eEntities: &f%d",
                        server.getTps(), used, r.maxMemory() / 1048576L, server.getOnlinePlayers().size(),
                        server.getLoadedChunkEstimate(), server.getEntityCount()));
                s.sendMessage("&eTick: &f" + String.format(java.util.Locale.ENGLISH, "%.1f", server.getMspt())
                        + " ms (50 ms limit)  &eUptime: &f" + server.getUptimeString());
                return true;
            }
        });
        map.registerBuiltin(new Cmd("ping", "/ping [player]", "Shows the latency") {
            boolean run(CommandSender s, String l, String[] a) {
                Player t = a.length > 0 ? server.getPlayer(a[0]) : (s instanceof Player ? (Player) s : null);
                if (t == null) {
                    s.sendMessage("&cPlayer not found.");
                    return true;
                }
                s.sendMessage("&e" + t.getName() + " ping: &f" + t.getPing() + " ms");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("give", "/give <player> <item> [amount] [data]", "Gives an item to a player", "i", "item") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length < 2) {
                    return false;
                }
                Player t = server.getPlayer(a[0]);
                if (t == null) {
                    s.sendMessage("&cPlayer not found: " + a[0]);
                    return true;
                }
                Material m = Material.matchMaterial(a[1]);
                int id;
                try {
                    id = m != null ? m.getId() : Integer.parseInt(a[1]);
                } catch (NumberFormatException e) {
                    s.sendMessage("&cUnknown item: " + a[1]);
                    return true;
                }
                if (!server.getWorld().isValidItem(id)) {
                    s.sendMessage("&cNo such item in this version: " + a[1]);
                    return true;
                }
                try {
                    int amount = a.length > 2 ? Integer.parseInt(a[2]) : 1;
                    int data = a.length > 3 ? Integer.parseInt(a[3]) : 0;
                    amount = Math.max(1, Math.min(64 * 36, amount));
                    t.giveItem(id, amount, data);
                    String name = m != null ? m.name() : String.valueOf(id);
                    s.sendMessage("&e" + t.getName() + " received " + amount + " x " + name + ".");
                } catch (NumberFormatException e) {
                    return false;
                }
                return true;
            }
        });
        map.registerBuiltin(new Cmd("gamemode", "/gamemode <0|1|2|survival|creative|spectator> [player]", "Changes the game mode", "gm") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                String m = a[0].toLowerCase(java.util.Locale.ENGLISH);
                int mode;
                if (m.equals("0") || (m.startsWith("s") && !m.startsWith("sp"))) {
                    mode = 0;
                } else if (m.equals("1") || m.startsWith("c")) {
                    mode = 1;
                } else if (m.equals("2") || m.startsWith("sp") || m.equals("invisible")) {
                    mode = 2;
                } else {
                    return false;
                }
                Player t = a.length > 1 ? server.getPlayer(a[1]) : (s instanceof Player ? (Player) s : null);
                if (t == null) {
                    s.sendMessage("&cPlayer not found.");
                    return true;
                }
                if (mode == 2 && !server.hasSpectatorMode()) {
                    s.sendMessage("&cThe spectator mode does not exist in " + server.getGameVersion() + ".");
                    return true;
                }
                if (mode != 0 && !t.isOp()) {
                    s.sendMessage("&c" + t.getName() + " is not an operator: creative and spectator mode are for operators only.");
                    return true;
                }
                t.setGameMode(mode);
                s.sendMessage("&e" + t.getName() + " game mode: " + (mode == 1 ? "Creative" : mode == 2 ? "Spectator" : "Survival"));
                return true;
            }
        });
        map.registerBuiltin(new Cmd("heal", "/heal [player]", "Refills health") {
            boolean run(CommandSender s, String l, String[] a) {
                Player t = a.length > 0 ? server.getPlayer(a[0]) : (s instanceof Player ? (Player) s : null);
                if (t == null) {
                    s.sendMessage("&cPlayer not found.");
                    return true;
                }
                if (t.isDead()) {
                    s.sendMessage("&c" + t.getName() + " su an olu.");
                    return true;
                }
                t.setHealth(20);
                t.sendMessage("&aYour health was restored.");
                if (t != s) {
                    s.sendMessage("&e" + t.getName() + " was healed.");
                }
                return true;
            }
        });
        map.registerBuiltin(new Cmd("kill", "/kill [player]", "Kills a player") {
            boolean run(CommandSender s, String l, String[] a) {
                Player t = a.length > 0 ? server.getPlayer(a[0]) : (s instanceof Player ? (Player) s : null);
                if (t == null) {
                    s.sendMessage("&cPlayer not found.");
                    return true;
                }
                t.setHealth(0);
                s.sendMessage("&e" + t.getName() + " was killed.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("tphere", "/tphere <player>", "Teleports a player to you", "s") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0 || !(s instanceof Player)) {
                    return false;
                }
                Player t = server.getPlayer(a[0]);
                if (t == null) {
                    s.sendMessage("&cPlayer not found: " + a[0]);
                    return true;
                }
                t.teleport(((Player) s).getLocation());
                s.sendMessage("&e" + t.getName() + " teleported to the player.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("difficulty", "/difficulty [0-3 | peaceful|easy|normal|hard]",
                "Shows or sets the difficulty (0 = no monsters)") {
            boolean run(CommandSender s, String l, String[] a) {
                String[] names = {"peaceful", "easy", "normal", "hard"};
                if (a.length == 0) {
                    s.sendMessage("&eDifficulty: " + names[server.getDifficulty()] + " (" + server.getDifficulty() + ")");
                    return true;
                }
                int d = -1;
                String v = a[0].toLowerCase(java.util.Locale.ENGLISH);
                for (int i = 0; i < names.length; i++) {
                    if (v.equals(names[i]) || v.equals(String.valueOf(i))
                            || v.equals(new String[]{"peaceful", "easy", "normal", "hard"}[i])) {
                        d = i;
                    }
                }
                if (d < 0) {
                    return false;
                }
                server.setDifficulty(d);
                server.broadcastMessage("&eDifficulty set: " + names[d]);
                return true;
            }
        });
        map.registerBuiltin(new Cmd("spawnmob", "/spawnmob <zombie|skeleton|creeper|spider|sheep|pig|giant> [count] [player]",
                "Spawns mobs next to the player", "summon") {
            boolean run(CommandSender s, String l, String[] a) {
                if (a.length == 0) {
                    return false;
                }
                String[] keys = {"zombie", "skeleton", "creeper", "spider", "sheep", "pig", null, "giant"};
                String[] en = {"zombie", "skeleton", "creeper", "spider", "sheep", "pig", null, "giant"};
                int type = -1;
                for (int i = 0; i < keys.length; i++) {
                    if (keys[i] != null && (a[0].equalsIgnoreCase(keys[i]) || a[0].equalsIgnoreCase(en[i]))) {
                        type = i;
                    }
                }
                if (type < 0) {
                    return false;
                }
                int count = 1;
                if (a.length > 1) {
                    try {
                        count = Math.max(1, Math.min(20, Integer.parseInt(a[1])));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                Player t = a.length > 2 ? server.getPlayer(a[2]) : (s instanceof Player ? (Player) s : null);
                if (t == null) {
                    s.sendMessage("&cPlayer not found.");
                    return true;
                }
                Location loc = t.getLocation();
                int n = server.spawnMob(type, loc.getX(), loc.getY(), loc.getZ(), count);
                s.sendMessage("&e" + n + " " + net.thewellodev.protocol.Protocol.MOB_NAMES[type] + " spawned.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("killmobs", "/killmobs", "Removes all mobs in the world") {
            boolean run(CommandSender s, String l, String[] a) {
                s.sendMessage("&e" + server.killAllMobs() + " mobs removed.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("clearitems", "/clearitems", "Removes all items on the ground") {
            boolean run(CommandSender s, String l, String[] a) {
                s.sendMessage("&e" + server.clearNetItems() + " items removed.");
                return true;
            }
        });
        map.registerBuiltin(new Cmd("ip", "/ip", "Shows the connection addresses (console only)") {
            boolean run(CommandSender s, String l, String[] a) {
                if (s instanceof Player) {
                    // The owner's public IP is never shown to players, not even operators.
                    s.sendMessage("&cThis command can only be used from the server console.");
                    return true;
                }
                net.thewellodev.server.net.NetworkHelper nh = server.getNetwork();
                if (nh == null) {
                    s.sendMessage("Network info is not ready yet.");
                    return true;
                }
                s.sendMessage("Internet: " + (nh.publicIp != null ? nh.publicIp + ":" + server.getPort() : "not found"));
                s.sendMessage("LAN: " + nh.lanIp + ":" + server.getPort());
                for (String v : nh.vpnAddresses) {
                    s.sendMessage(v + ":" + server.getPort());
                }
                return true;
            }
        });
        map.registerBuiltin(new Cmd("reload", "/reload", "Reloads the plugins") {
            boolean run(CommandSender s, String l, String[] a) {
                server.reloadPlugins();
                s.sendMessage("&ePlugins reloaded.");
                return true;
            }
        });
    }

    private static double coord(String s, double cur) {
        if (s.startsWith("~")) {
            return cur + (s.length() > 1 ? Double.parseDouble(s.substring(1)) : 0);
        }
        return Double.parseDouble(s);
    }
}
