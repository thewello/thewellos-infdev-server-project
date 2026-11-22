package net.thewellodev.api321.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api321.Server321;
import net.thewellodev.api321.TheWello321;

/**
 * Base class of plugins for inf-20100321. The plugin.yml must say
 * {@code api: inf-20100321}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin321 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api321} (Server321, Player321, World321, Block321, Location321,
 * Material321) and the events of {@code net.thewellodev.api321.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin321 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server321 server() {
        return TheWello321.getServer();
    }
}
