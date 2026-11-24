package net.thewellodev.api415.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api415.Server415;
import net.thewellodev.api415.TheWello415;

/**
 * Base class of plugins for inf-20100415. The plugin.yml must say
 * {@code api: inf-20100415}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin415 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api415} (Server415, Player415, World415, Block415, Location415,
 * Material415) and the events of {@code net.thewellodev.api415.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin415 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server415 server() {
        return TheWello415.getServer();
    }
}
