package net.thewellodev.noillegals;

import net.thewellodev.api.event.EventHandler;
import net.thewellodev.api.event.EventPriority;
import net.thewellodev.api.event.Listener;
import net.thewellodev.api.util.ChatColor;
import net.thewellodev.api607.Material607;
import net.thewellodev.api607.Player607;
import net.thewellodev.api607.event.block.BlockBreakEvent;
import net.thewellodev.api607.event.block.BlockPlaceEvent;
import net.thewellodev.api607.plugin.Plugin607;

import java.util.HashSet;
import java.util.Set;

/**
 * NoIllegals for inf-20100607: stops blocks that a survival player cannot get in this build from being placed,
 * and blocks that cannot be broken from being broken. This build keeps the inventory on the client and has no items
 * on the ground, so (unlike the plugin for inf-20100630-1340) there is no inventory to scan and nothing to pick
 * up: the checks are on the blocks players place and break.
 *
 * Operators are exempt. The lists are in {@code plugins/NoIllegals/config.yml}:
 * <ul>
 *   <li>{@code illegal-blocks}: cannot be placed (ids or MATERIAL names; default: bedrock, water, lava, fire)</li>
 *   <li>{@code unbreakable-blocks}: cannot be broken (default: bedrock)</li>
 * </ul>
 */
public class NoIllegals extends Plugin607 implements Listener {

    private static final Material607[] DEFAULT_ILLEGAL = {
        Material607.BEDROCK, Material607.WATER, Material607.STATIONARY_WATER, Material607.LAVA,
        Material607.STATIONARY_LAVA, Material607.FIRE
    };
    private static final Material607[] DEFAULT_UNBREAKABLE = {Material607.BEDROCK};

    private final Set<Integer> illegal = new HashSet<Integer>();
    private final Set<Integer> unbreakable = new HashSet<Integer>();
    private int blockedTotal;

    @Override
    public void onEnable() {
        getConfig().addDefault("illegal-blocks", names(DEFAULT_ILLEGAL));
        getConfig().addDefault("unbreakable-blocks", names(DEFAULT_UNBREAKABLE));
        getConfig().addDefault("message", "&cYou cannot use this block in survival.");
        saveConfig();
        read("illegal-blocks", illegal);
        read("unbreakable-blocks", unbreakable);
        server().getPluginManager().registerEvents(this, this);
        getLogger().info("NoIllegals enabled (" + illegal.size() + " banned blocks, " + unbreakable.size()
                + " unbreakable).");
    }

    @Override
    public void onDisable() {
        getLogger().info("NoIllegals disabled (" + blockedTotal + " actions blocked in total).");
    }

    private static java.util.List<String> names(Material607[] list) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (Material607 m : list) {
            names.add(m.name());
        }
        return names;
    }

    private void read(String key, Set<Integer> into) {
        into.clear();
        for (String s : getConfig().getStringList(key)) {
            Material607 m = Material607.matchMaterial(s);
            if (m != null) {
                into.add(m.getId());
                continue;
            }
            try {
                into.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                getLogger().warning("Unknown block in " + key + " (ignored): " + s);
            }
        }
    }

    private static boolean exempt(Player607 p) {
        return p == null || p.isOp();
    }

    private static String describe(int id) {
        Material607 m = Material607.getMaterial(id);
        return m == null ? String.valueOf(id) : m.name();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player607 p = e.getPlayer();
        boolean unknown = !server().getWorld().isValidBlock(e.getNewTypeId());
        if (!exempt(p) && (unknown || illegal.contains(e.getNewTypeId()))) {
            e.setCancelled(true);
            blockedTotal++;
            p.sendMessage(getConfig().getString("message"));
            getLogger().warning(p.getName() + " tried to place a banned block: " + describe(e.getNewTypeId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player607 p = e.getPlayer();
        if (!exempt(p) && unbreakable.contains(e.getBlock().getTypeId())) {
            e.setCancelled(true);
            blockedTotal++;
            p.sendMessage(ChatColor.RED + "You cannot break this block.");
        }
    }
}
