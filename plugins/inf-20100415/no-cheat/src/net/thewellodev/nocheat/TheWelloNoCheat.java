package net.thewellodev.nocheat;

import net.thewellodev.api415.Location415;
import net.thewellodev.api415.Material415;
import net.thewellodev.api415.World415;
import net.thewellodev.api.command.Command;
import net.thewellodev.api.command.CommandSender;
import net.thewellodev.api415.Player415;
import net.thewellodev.api.event.EventHandler;
import net.thewellodev.api.event.EventPriority;
import net.thewellodev.api.event.Listener;
import net.thewellodev.api415.event.block.BlockBreakEvent;
import net.thewellodev.api415.event.block.BlockPlaceEvent;
import net.thewellodev.api415.event.player.PlayerAttackEvent;
import net.thewellodev.api415.event.player.PlayerChatEvent;
import net.thewellodev.api415.event.player.PlayerJoinEvent;
import net.thewellodev.api415.event.player.PlayerQuitEvent;
import net.thewellodev.api415.event.player.PlayerRespawnEvent;
import net.thewellodev.api415.event.player.PlayerMoveEvent;
import net.thewellodev.api415.plugin.Plugin415;
import net.thewellodev.api.util.ChatColor;

import java.util.HashMap;
import java.util.Map;

/**
 * TheWelloNoCheat for inf-20100415 - optional anti-cheat (the plugin of inf-20100630-1340, made for this
 * build's API). This build keeps the inventory on the client and has no health reports, items or mobs, so the
 * health and inventory checks of the other build are not here.
 *
 * <p>The server core only enforces game rules and stability. This plugin performs the checks below;
 * if the plugin is removed, these checks are disabled.</p>
 * <ul>
 *   <li><b>teleport</b>: a single packet moves the player too far</li>
 *   <li><b>speed</b>: horizontal speed above the limit, per second</li>
 *   <li><b>fly</b>: continuous rising in the air (flying)</li>
 *   <li><b>noclip</b>: walking through solid blocks</li>
 *   <li><b>rate</b>: too many movement packets per second</li>
 *   <li><b>reach</b>: breaking or placing blocks from too far away</li>
 *   <li><b>attack-reach / attack-rate</b>: hitting a player from too far away or too quickly</li>
 *   <li><b>chat-spam</b>: messages sent too fast or repeated</li>
 * </ul>
 * Operators (and players in creative mode) are exempt if configured. Every violation is logged and reported
 * to operators; a player is kicked when the violation count exceeds {@code kick-after-violations}.
 */
public class TheWelloNoCheat extends Plugin415 implements Listener {

    /** Per-player state. */
    private static final class State {
        boolean hasLast;
        double lastX, lastY, lastZ;
        long speedWindow;
        double speedDist;
        long moveWindow;
        int moves;
        double airRise;
        long graceUntil;
        long lastAttack;
        long[] chatTimes = new long[5];
        int chatIndex;
        String lastChat = "";
        long lastChatTime;
        int violations;
        final Map<String, Long> lastAlert = new HashMap<String, Long>();
        final Map<String, Long> lastCount = new HashMap<String, Long>();
    }

    private final Map<String, State> states = new HashMap<String, State>();

    // settings
    private boolean exemptOps;
    private boolean exemptCreative;
    private int kickAfter;
    private boolean alertOps;
    private double maxTeleport;
    private double maxSpeed;
    private int maxMoves;
    private double maxRise;
    private long graceMs;
    private double reachBlock;
    private double reachMelee;
    private long attackIntervalMs;
    private boolean checkChat;
    private boolean checkNoclip;

    @Override
    public void onEnable() {
        getConfig().addDefault("exempt-ops", true);
        getConfig().addDefault("exempt-creative", true);
        getConfig().addDefault("kick-after-violations", 100);
        getConfig().addDefault("alert-ops", true);
        getConfig().addDefault("teleport.max-blocks", 10.0);
        getConfig().addDefault("speed.max-blocks-per-second", 12.0);
        getConfig().addDefault("rate.max-moves-per-second", 30);
        getConfig().addDefault("fly.max-rise", 1.5);
        getConfig().addDefault("grace-seconds-after-join", 5);
        getConfig().addDefault("noclip.enabled", true);
        getConfig().addDefault("reach.block", 7.0);
        getConfig().addDefault("reach.melee", 6.0);
        getConfig().addDefault("attack.min-interval-ms", 150);
        getConfig().addDefault("chat.enabled", true);
        saveConfig();
        loadSettings();
        server().getPluginManager().registerEvents(this, this);
        getLogger().info("TheWelloNoCheat enabled (operators exempt: " + exemptOps + ", kick threshold: " + kickAfter + ").");
    }

    @Override
    public void onDisable() {
        getLogger().info("TheWelloNoCheat disabled.");
    }

    private void loadSettings() {
        exemptOps = getConfig().getBoolean("exempt-ops", true);
        exemptCreative = getConfig().getBoolean("exempt-creative", true);
        kickAfter = getConfig().getInt("kick-after-violations", 100);
        alertOps = getConfig().getBoolean("alert-ops", true);
        maxTeleport = getConfig().getDouble("teleport.max-blocks", 10.0);
        maxSpeed = getConfig().getDouble("speed.max-blocks-per-second", 12.0);
        maxMoves = getConfig().getInt("rate.max-moves-per-second", 30);
        maxRise = getConfig().getDouble("fly.max-rise", 1.5);
        graceMs = getConfig().getInt("grace-seconds-after-join", 5) * 1000L;
        checkNoclip = getConfig().getBoolean("noclip.enabled", true);
        reachBlock = getConfig().getDouble("reach.block", 7.0);
        reachMelee = getConfig().getDouble("reach.melee", 6.0);
        attackIntervalMs = getConfig().getInt("attack.min-interval-ms", 150);
        checkChat = getConfig().getBoolean("chat.enabled", true);
    }

    private State state(Player415 p) {
        String k = p.getName().toLowerCase(java.util.Locale.ENGLISH);
        State s = states.get(k);
        if (s == null) {
            s = new State();
            states.put(k, s);
        }
        return s;
    }

    /** Whether this player is exempt from the checks. */
    private boolean exempt(Player415 p) {
        if (exemptOps && p.isOp()) {
            return true;
        }
        return exemptCreative && p.getGameMode() != 0;
    }

    /**
     * Records a violation. The player is kicked when the limit is reached. Every violation is logged; operators
     * are notified at most once every 5 seconds per check.
     */
    private void violate(Player415 p, String check, String detail) {
        State s = state(p);
        long now = System.currentTimeMillis();
        Long lc = s.lastCount.get(check);
        if (lc != null && now - lc < 500) {
            return; // the same kind of violation in a burst counts once
        }
        s.lastCount.put(check, now);
        s.violations++;
        getLogger().warning(p.getName() + " [" + check + "] " + detail + " (violations: " + s.violations + ")");
        Long last = s.lastAlert.get(check);
        if (alertOps && (last == null || now - last > 5000)) {
            s.lastAlert.put(check, now);
            String msg = "&c[NoCheat] &f" + p.getName() + " &7" + check + " &f(" + detail + ") violations: " + s.violations;
            for (Player415 o : server().getOnlinePlayers()) {
                if (o.isOp()) {
                    o.sendMessage(msg);
                }
            }
        }
        if (kickAfter > 0 && s.violations >= kickAfter) {
            p.kickPlayer("Anti-cheat: too many violations");
        }
    }

    // ------------------------------------------------------------------ movement

    /** Movement checks are paused for a short time after joining or respawning (the position settles). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        state(e.getPlayer()).graceUntil = System.currentTimeMillis() + graceMs;
    }

    /**
     * When a player leaves, the state is dropped if they have no violations. Players with violations are kept,
     * so leaving and rejoining does not reset the count.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        String k = e.getPlayer().getName().toLowerCase(java.util.Locale.ENGLISH);
        State s = states.get(k);
        if (s != null && s.violations == 0) {
            states.remove(k);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        state(e.getPlayer()).graceUntil = System.currentTimeMillis() + graceMs;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player415 p = e.getPlayer();
        State s = state(p);
        Location415 from = e.getFrom(), to = e.getTo();
        long now = System.currentTimeMillis();
        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        double horiz2 = dx * dx + dz * dz;
        boolean ex = exempt(p);
        boolean grace = now < s.graceUntil;
        boolean cancel = false;

        if (!ex) {
            // Teleport: too far in a single packet (always checked)
            if (horiz2 + dy * dy > maxTeleport * maxTeleport) {
                violate(p, "teleport", String.format("%.1f blocks in one packet", Math.sqrt(horiz2 + dy * dy)));
                cancel = true;
            }
            if (!grace) {
                // One-second window for the horizontal speed
                if (now - s.speedWindow > 1000) {
                    s.speedWindow = now;
                    s.speedDist = 0;
                }
                s.speedDist += Math.sqrt(horiz2);
                if (!cancel && s.speedDist > maxSpeed) {
                    violate(p, "speed", String.format("%.1f blocks/s", s.speedDist));
                    cancel = true;
                }
            }
            // Packets per second
            if (now - s.moveWindow > 1000) {
                s.moveWindow = now;
                s.moves = 0;
            }
            s.moves++;
            if (!cancel && s.moves > maxMoves) {
                violate(p, "rate", s.moves + " moves/s");
                cancel = true;
            }
            // Flying: only a continuous rise counts. The counter resets when the player lands, is on a ladder or in water, is inside a block, or is not going up.
            // is standing on something, in water or on a ladder, or is not going up.
            World415 w = p.getWorld();
            int feetId = w.getBlockTypeIdAt((int) Math.floor(to.getX()), (int) Math.floor(to.getY()), (int) Math.floor(to.getZ()));
            boolean climbing = Material415.getMaterial(feetId) != null && Material415.getMaterial(feetId).isLiquid();
            boolean inside = solidAt(w, to.getX(), to.getY(), to.getZ());
            if (p.isOnGround() || climbing || inside || dy <= 0 || grace) {
                s.airRise = 0;
            } else {
                s.airRise += dy;
                if (s.airRise > maxRise) {
                    // Logged only: the move is not reverted, because reverting can make the client loop
                    violate(p, "fly", String.format("%.2f blocks of continuous rise", s.airRise));
                    s.airRise = 0;
                }
            }
            // Walking through walls: entering a solid block where the feet or head were not before
            if (!cancel && checkNoclip && !grace && s.hasLast) {
                boolean wasIn = solidAt(w, s.lastX, s.lastY, s.lastZ);
                if (inside && !wasIn) {
                    violate(p, "noclip", "entered a solid block");
                    cancel = true;
                }
            }
        }
        s.lastX = from.getX();
        s.lastY = from.getY();
        s.lastZ = from.getZ();
        s.hasLast = true;
        if (cancel) {
            e.setCancelled(true);
        }
    }

    /** True if a full solid block is at foot and head height (slabs, stairs, doors and plants do not count). */
    private boolean solidAt(World415 w, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        return fullSolid(w.getBlockTypeIdAt(bx, (int) Math.floor(y), bz))
                && fullSolid(w.getBlockTypeIdAt(bx, (int) Math.floor(y + 1.0), bz));
    }

    private static boolean fullSolid(int id) {
        Material415 m = Material415.getMaterial(id);
        // slabs are not full blocks; everything the player can walk through is not solid
        return m != null && !m.isPassable() && m != Material415.STEP;
    }

    // ------------------------------------------------------------------ blocks

    private boolean tooFarFromBlock(Player415 p, double bx, double by, double bz) {
        Location415 l = p.getLocation();
        double dx = bx + 0.5 - l.getX(), dy = by + 0.5 - (l.getY() + 1.62), dz = bz + 0.5 - l.getZ();
        return dx * dx + dy * dy + dz * dz > reachBlock * reachBlock;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player415 p = e.getPlayer();
        if (exempt(p) || !tooFarFromBlock(p, e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ())) {
            return;
        }
        violate(p, "reach", "break distance");
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player415 p = e.getPlayer();
        if (exempt(p) || !tooFarFromBlock(p, e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ())) {
            return;
        }
        violate(p, "reach", "placing distance");
        e.setCancelled(true);
    }

    // ------------------------------------------------------------------ attacks

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PlayerAttackEvent e) {
        Player415 p = e.getPlayer();
        if (exempt(p)) {
            return;
        }
        State s = state(p);
        long now = System.currentTimeMillis();
        double limit = reachMelee;
        if (e.getDistanceSq() > limit * limit) {
            violate(p, "attack-reach", String.format("%.1f blocks away", Math.sqrt(e.getDistanceSq())));
            e.setCancelled(true);
            return;
        }
        if (now - s.lastAttack < attackIntervalMs) {
            violate(p, "attack-rate", (now - s.lastAttack) + " ms between attacks");
            e.setCancelled(true);
            return;
        }
        s.lastAttack = now;
    }

    // ------------------------------------------------------------------ chat

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(PlayerChatEvent e) {
        if (!checkChat) {
            return;
        }
        Player415 p = e.getPlayer();
        if (exempt(p)) {
            return;
        }
        State s = state(p);
        long now = System.currentTimeMillis();
        boolean repeat = e.getMessage().equals(s.lastChat) && now - s.lastChatTime < 1500;
        s.lastChat = e.getMessage();
        s.lastChatTime = now;
        s.chatTimes[s.chatIndex] = now;
        s.chatIndex = (s.chatIndex + 1) % s.chatTimes.length;
        // The last 5 messages within 3 seconds count as spam
        boolean fast = now - s.chatTimes[s.chatIndex] < 3000;
        if (repeat || fast) {
            violate(p, "chat-spam", repeat ? "repeated message" : "message sent too fast");
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equals("nocheat")) {
            return false;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ENGLISH);
        if (sub.equals("status")) {
            sender.sendMessage("&6--- TheWelloNoCheat ---");
            sender.sendMessage("&eMovement: &f" + on(true) + "  &eAttack: &f" + on(true) + "  &eReach: &f" + on(true)
                    + "  &eChat: &f" + on(checkChat));
            for (Map.Entry<String, State> en : states.entrySet()) {
                if (en.getValue().violations > 0) {
                    sender.sendMessage("&e" + en.getKey() + ": &f" + en.getValue().violations + " violations");
                }
            }
            return true;
        }
        if (sub.equals("reset") && args.length == 2) {
            State s = states.get(args[1].toLowerCase(java.util.Locale.ENGLISH));
            if (s != null) {
                s.violations = 0;
            }
            sender.sendMessage("&aViolation counter reset for " + args[1] + ".");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /nocheat status | reset <player>");
        return true;
    }

    private static String on(boolean b) {
        return b ? "on" : "off";
    }
}
