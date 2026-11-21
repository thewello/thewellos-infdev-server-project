package net.thewellodev.api320.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api320.Server320;
import net.thewellodev.api320.TheWello320;

/**
 * Base class of plugins for inf-20100320. The plugin.yml must say
 * {@code api: inf-20100320}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin320 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api320} (Server320, Player320, World320, Block320, Location320,
 * Material320) and the events of {@code net.thewellodev.api320.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin320 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server320 server() {
        return TheWello320.getServer();
    }
}
