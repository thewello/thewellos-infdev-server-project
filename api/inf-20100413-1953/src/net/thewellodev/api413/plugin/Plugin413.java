package net.thewellodev.api413.plugin;

import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.api413.Server413;
import net.thewellodev.api413.TheWello413;

/**
 * Base class of plugins for inf-20100413-1953. The plugin.yml must say
 * {@code api: inf-20100413-1953}; the server refuses it otherwise (and refuses plugins of other builds).
 *
 * <pre>
 * public class MyPlugin extends Plugin413 implements Listener {
 *     public void onEnable() {
 *         server().getPluginManager().registerEvents(this, this);
 *     }
 * }
 * </pre>
 *
 * Use the types of {@code net.thewellodev.api413} (Server413, Player413, World413, Block413, Location413,
 * Material413) and the events of {@code net.thewellodev.api413.event}. The classes of {@code net.thewellodev.api}
 * that are in the jar for the other build (Server, World, Player, Block, Material, ItemStack and their events) are
 * not for this build.
 */
public abstract class Plugin413 extends JavaPlugin {

    /** The server, as seen by this build's API. */
    public final Server413 server() {
        return TheWello413.getServer();
    }
}
