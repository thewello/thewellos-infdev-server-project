package net.thewellodev.api414.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api414.Server414;
import net.thewellodev.api414.TheWello414;

/**
 * Base class of plugins for inf-20100414. The plugin.yml must say
 * {@code api: inf-20100414}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin414 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api414} (Server414, Player414, World414, Block414, Location414,
 * Material414) and the events of {@code net.thewellodev.api414.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin414 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server414 server() {
        return TheWello414.getServer();
    }
}
