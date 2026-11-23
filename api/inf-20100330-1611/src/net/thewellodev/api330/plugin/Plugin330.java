package net.thewellodev.api330.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api330.Server330;
import net.thewellodev.api330.TheWello330;

/**
 * Base class of plugins for inf-20100330-1611. The plugin.yml must say
 * {@code api: inf-20100330-1611}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin330 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api330} (Server330, Player330, World330, Block330, Location330,
 * Material330) and the events of {@code net.thewellodev.api330.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin330 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server330 server() {
        return TheWello330.getServer();
    }
}
