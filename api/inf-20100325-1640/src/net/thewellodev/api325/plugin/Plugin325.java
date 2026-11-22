package net.thewellodev.api325.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api325.Server325;
import net.thewellodev.api325.TheWello325;

/**
 * Base class of plugins for inf-20100325-1640. The plugin.yml must say
 * {@code api: inf-20100325-1640}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin325 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api325} (Server325, Player325, World325, Block325, Location325,
 * Material325) and the events of {@code net.thewellodev.api325.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin325 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server325 server() {
        return TheWello325.getServer();
    }
}
