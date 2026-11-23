package net.thewellodev.api327.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api327.Server327;
import net.thewellodev.api327.TheWello327;

/**
 * Base class of plugins for inf-20100327. The plugin.yml must say
 * {@code api: inf-20100327}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin327 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api327} (Server327, Player327, World327, Block327, Location327,
 * Material327) and the events of {@code net.thewellodev.api327.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin327 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server327 server() {
        return TheWello327.getServer();
    }
}
