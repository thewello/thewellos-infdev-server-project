package net.thewellodev.api608.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api608.Server608;
import net.thewellodev.api608.TheWello608;

/**
 * Base class of plugins for inf-20100608. The plugin.yml must say
 * {@code api: inf-20100608}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin608 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api608} (Server608, Player608, World608, Block608, Location608,
 * Material608) and the events of {@code net.thewellodev.api608.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin608 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server608 server() {
        return TheWello608.getServer();
    }
}
