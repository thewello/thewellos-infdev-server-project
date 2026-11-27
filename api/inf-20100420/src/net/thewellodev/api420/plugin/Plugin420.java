package net.thewellodev.api420.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api420.Server420;
import net.thewellodev.api420.TheWello420;

/**
 * Base class of plugins for inf-20100420. The plugin.yml must say
 * {@code api: inf-20100420}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin420 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api420} (Server420, Player420, World420, Block420, Location420,
 * Material420) and the events of {@code net.thewellodev.api420.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin420 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server420 server() {
        return TheWello420.getServer();
    }
}
