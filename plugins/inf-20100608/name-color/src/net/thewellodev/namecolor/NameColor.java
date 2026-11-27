package net.thewellodev.namecolor;

import net.thewellodev.api.event.EventHandler;
import net.thewellodev.api.event.EventPriority;
import net.thewellodev.api.event.Listener;
import net.thewellodev.api608.Player608;
import net.thewellodev.api608.event.player.PlayerChatEvent;
import net.thewellodev.api608.event.player.PlayerJoinEvent;
import net.thewellodev.api608.plugin.Plugin608;

import java.util.ArrayList;
import java.util.List;

/**
 * NameColor for inf-20100608: colours player names in chat by role.
 * <ul>
 *   <li>{@code gold-names} players: gold (even if they are operators)</li>
 *   <li>Other operators: red</li>
 *   <li>Everyone else: default colour</li>
 * </ul>
 * Settings are in {@code plugins/NameColor/config.yml}; role changes (/op, /deop) take effect immediately.
 */
public class NameColor extends Plugin608 implements Listener {

    private static final String GOLD = "&6";
    private static final String RED = "&c";

    private List<String> goldNames = new ArrayList<String>();
    private String goldColor = GOLD;
    private String opColor = RED;
    private String defaultColor = "&f";
    private String format = "<%name%> %message%";

    @Override
    public void onEnable() {
        getConfig().addDefault("gold-names", new java.util.ArrayList<String>());
        getConfig().addDefault("gold-color", GOLD);
        getConfig().addDefault("op-color", RED);
        getConfig().addDefault("default-color", "&f");
        getConfig().addDefault("chat-format", "<%name%> %message%");
        saveConfig();
        reload();
        server().getPluginManager().registerEvents(this, this);
        getLogger().info("NameColor enabled (" + goldNames.size() + " gold players).");
    }

    private void reload() {
        goldNames = new ArrayList<String>();
        for (String n : getConfig().getStringList("gold-names")) {
            goldNames.add(n.toLowerCase(java.util.Locale.ENGLISH));
        }
        goldColor = getConfig().getString("gold-color", GOLD);
        opColor = getConfig().getString("op-color", RED);
        defaultColor = getConfig().getString("default-color", "&f");
        format = getConfig().getString("chat-format", "<%name%> %message%");
    }

    /** The colour of a player's name as a code, for example "&6". */
    private String colorOf(Player608 p) {
        if (goldNames.contains(p.getName().toLowerCase(java.util.Locale.ENGLISH))) {
            return goldColor;
        }
        return p.isOp() ? opColor : defaultColor;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player608 p = e.getPlayer();
        p.setDisplayName(colorOf(p) + p.getName() + "&f");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(PlayerChatEvent e) {
        Player608 p = e.getPlayer();
        // The display name is recalculated for every message, so /op and /deop take effect immediately.
        p.setDisplayName(colorOf(p) + p.getName() + "&f");
        // The message is inserted with %2$s rather than as a template, so '%' characters typed by players are kept.
        String f = format.replace("%name%", "%1$s").replace("%message%", "%2$s");
        e.setFormat(f);
    }
}
