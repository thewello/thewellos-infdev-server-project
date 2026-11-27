package net.thewellodev.api607.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api607.Server607;
import net.thewellodev.api607.TheWello607;

/**
 * Base class of plugins for inf-20100607. The plugin.yml must say
 * {@code api: inf-20100607}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin607 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api607} (Server607, Player607, World607, Block607, Location607,
 * Material607) and the events of {@code net.thewellodev.api607.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin607 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server607 server() {
        return TheWello607.getServer();
    }
}
