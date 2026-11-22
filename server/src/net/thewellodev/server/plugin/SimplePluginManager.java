package net.thewellodev.server.plugin;

import net.thewellodev.api.command.PluginCommand;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;
import net.thewellodev.api.event.EventHandler;
import net.thewellodev.api.event.EventPriority;
import net.thewellodev.api.event.Listener;
import net.thewellodev.api.event.server.PluginDisableEvent;
import net.thewellodev.api.event.server.PluginEnableEvent;
import net.thewellodev.api.plugin.InvalidPluginException;
import net.thewellodev.api.plugin.Plugin;
import net.thewellodev.api.plugin.PluginDescriptionFile;
import net.thewellodev.api.plugin.PluginManager;
import net.thewellodev.api.plugin.java.JavaPlugin;
import net.thewellodev.server.TWServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/** Plugin loading and event dispatch. */
public class SimplePluginManager implements PluginManager {

    private static final class RegisteredHandler {
        final Plugin plugin;
        final Listener listener;
        final Method method;
        final EventPriority priority;
        final boolean ignoreCancelled;

        RegisteredHandler(Plugin plugin, Listener listener, Method method, EventPriority priority,
                          boolean ignoreCancelled) {
            this.plugin = plugin;
            this.listener = listener;
            this.method = method;
            this.priority = priority;
            this.ignoreCancelled = ignoreCancelled;
        }
    }

    private final TWServer server;
    private final Map<String, Plugin> plugins = new LinkedHashMap<String, Plugin>();
    private final Map<Plugin, PluginClassLoader> loaders = new HashMap<Plugin, PluginClassLoader>();
    private final Map<Class<?>, List<RegisteredHandler>> handlers = new HashMap<Class<?>, List<RegisteredHandler>>();
    private final Map<Class<?>, List<RegisteredHandler>> bakedCache = new HashMap<Class<?>, List<RegisteredHandler>>();

    /** Announces events to the plugins of a build with its own API (inf-20100227-1433, inf-20100316), or null. */
    private EventTranslator translator;

    public SimplePluginManager(TWServer server) {
        this.server = server;
    }

    public void setTranslator(EventTranslator translator) {
        this.translator = translator;
    }

    // ------------------------------------------------------------ loading

    /** Loads all jars in the plugins folder (onLoad is called; not enabled yet). */
    public void loadPlugins(File dir) {
        dir.mkdirs();
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        Map<String, File> byName = new LinkedHashMap<String, File>();
        Map<String, PluginDescriptionFile> descs = new HashMap<String, PluginDescriptionFile>();
        for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase(java.util.Locale.ENGLISH).endsWith(".jar")) {
                continue;
            }
            try {
                PluginDescriptionFile d = readDescription(f);
                if (!d.getApi().equals(server.getGameVersion())) {
                    server.getLogger().severe(f.getName() + " was not loaded: it is made for " + d.getApi()
                            + " but this server runs " + server.getGameVersion()
                            + ". Use the plugins of the folder optional-plugins/" + server.getGameVersion() + ".");
                    continue;
                }
                if (byName.containsKey(d.getName().toLowerCase(java.util.Locale.ENGLISH))) {
                    server.getLogger().warning("Two plugins with the same name: " + d.getName() + " (" + f.getName() + " skipped)");
                    continue;
                }
                byName.put(d.getName().toLowerCase(java.util.Locale.ENGLISH), f);
                descs.put(d.getName().toLowerCase(java.util.Locale.ENGLISH), d);
            } catch (InvalidPluginException e) {
                server.getLogger().severe(f.getName() + " could not be loaded: " + e.getMessage());
            }
        }
        // Load in dependency order
        Set<String> loading = new HashSet<String>();
        for (String n : byName.keySet()) {
            loadWithDeps(n, byName, descs, loading);
        }
    }

    private void loadWithDeps(String key, Map<String, File> files, Map<String, PluginDescriptionFile> descs,
                              Set<String> loading) {
        if (plugins.containsKey(key) || !files.containsKey(key)) {
            return;
        }
        if (!loading.add(key)) {
            server.getLogger().severe("Circular dependency: " + key);
            return;
        }
        PluginDescriptionFile d = descs.get(key);
        for (String dep : d.getDepend()) {
            String dk = dep.toLowerCase(java.util.Locale.ENGLISH);
            if (!files.containsKey(dk)) {
                server.getLogger().severe(d.getName() + " could not be loaded: required plugin missing: " + dep);
                return;
            }
            loadWithDeps(dk, files, descs, loading);
            if (!plugins.containsKey(dk)) {
                server.getLogger().severe(d.getName() + " could not be loaded: a dependency failed to load: " + dep);
                return;
            }
        }
        for (String dep : d.getSoftDepend()) {
            loadWithDeps(dep.toLowerCase(java.util.Locale.ENGLISH), files, descs, loading);
        }
        try {
            loadPlugin(files.get(key), d);
        } catch (Throwable t) {
            server.getLogger().log(Level.SEVERE, d.getName() + " could not be loaded", t);
        }
    }

    private PluginDescriptionFile readDescription(File f) throws InvalidPluginException {
        JarFile jar = null;
        try {
            jar = new JarFile(f);
            JarEntry e = jar.getJarEntry("plugin.yml");
            if (e == null) {
                throw new InvalidPluginException("plugin.yml not found in the jar");
            }
            InputStream in = jar.getInputStream(e);
            try {
                return new PluginDescriptionFile(in);
            } finally {
                in.close();
            }
        } catch (IOException ex) {
            throw new InvalidPluginException("could not read the jar", ex);
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void loadPlugin(File file, PluginDescriptionFile d) throws Exception {
        PluginClassLoader cl = new PluginClassLoader(file, getClass().getClassLoader(), this);
        Class<?> main;
        try {
            main = Class.forName(d.getMain(), true, cl);
        } catch (ClassNotFoundException e) {
            throw new InvalidPluginException("main class not found: " + d.getMain());
        }
        if (!JavaPlugin.class.isAssignableFrom(main)) {
            throw new InvalidPluginException(d.getMain() + " does not extend JavaPlugin");
        }
        JavaPlugin p = (JavaPlugin) main.newInstance();
        File data = new File(file.getParentFile(), d.getName());
        p.initialize(server, d, data, file, cl);
        plugins.put(d.getName().toLowerCase(java.util.Locale.ENGLISH), p);
        loaders.put(p, cl);
        server.getLogger().info(d.getFullName() + " loading");
        p.onLoad();
    }

    Class<?> findClassFromOtherPlugins(String name, PluginClassLoader except) {
        for (PluginClassLoader l : loaders.values()) {
            if (l == except) {
                continue;
            }
            try {
                return l.findClass(name, false);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    /** For /reload: forgets all plugins and closes their class loaders (disableAll must be called first). */
    public void clearForReload() {
        for (PluginClassLoader l : loaders.values()) {
            try {
                l.close();
            } catch (IOException ignored) {
            }
        }
        loaders.clear();
        plugins.clear();
        handlers.clear();
        bakedCache.clear();
    }

    public void enableAll() {
        for (Plugin p : new ArrayList<Plugin>(plugins.values())) {
            enablePlugin(p);
        }
    }

    public void disableAll() {
        List<Plugin> l = new ArrayList<Plugin>(plugins.values());
        Collections.reverse(l);
        for (Plugin p : l) {
            disablePlugin(p);
        }
    }

    public void enablePlugin(Plugin plugin) {
        if (plugin.isEnabled()) {
            return;
        }
        server.getLogger().info(plugin.getDescription().getFullName() + " is being enabled");
        for (PluginDescriptionFile.CommandInfo ci : plugin.getDescription().getCommands().values()) {
            PluginCommand cmd = new PluginCommand(ci.name, plugin);
            cmd.setDescription(ci.description);
            cmd.setUsage(ci.usage);
            cmd.setPermission(ci.permission);
            cmd.setAliases(ci.aliases);
            server.getCommandMap().register(cmd, plugin);
        }
        try {
            ((JavaPlugin) plugin).setEnabled(true);
        } catch (Throwable t) {
            server.getLogger().log(Level.SEVERE, plugin.getName() + " error while enabling", t);
            try {
                ((JavaPlugin) plugin).setEnabled(false);
            } catch (Throwable ignored) {
            }
            unregisterEvents(plugin);
            server.getCommandMap().unregisterAll(plugin);
            return;
        }
        callEvent(new PluginEnableEvent(plugin));
    }

    public void disablePlugin(Plugin plugin) {
        if (!plugin.isEnabled()) {
            return;
        }
        server.getLogger().info(plugin.getDescription().getFullName() + " is being disabled");
        try {
            ((JavaPlugin) plugin).setEnabled(false);
        } catch (Throwable t) {
            server.getLogger().log(Level.SEVERE, plugin.getName() + " error while disabling", t);
        }
        server.getScheduler().cancelTasks(plugin);
        unregisterEvents(plugin);
        server.getCommandMap().unregisterAll(plugin);
        callEvent(new PluginDisableEvent(plugin));
    }

    public Plugin getPlugin(String name) {
        return name == null ? null : plugins.get(name.toLowerCase(java.util.Locale.ENGLISH));
    }

    public List<Plugin> getPlugins() {
        return new ArrayList<Plugin>(plugins.values());
    }

    public boolean isPluginEnabled(String name) {
        Plugin p = getPlugin(name);
        return p != null && p.isEnabled();
    }

    // ------------------------------------------------------------ event'ler

    @SuppressWarnings("unchecked")
    public void registerEvents(Listener listener, Plugin plugin) {
        if (!plugin.isEnabled()) {
            throw new IllegalStateException(plugin.getName() + " cannot register listeners while disabled");
        }
        for (Method m : listener.getClass().getMethods()) {
            EventHandler eh = m.getAnnotation(EventHandler.class);
            if (eh == null) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1 || !Event.class.isAssignableFrom(params[0])) {
                plugin.getLogger().warning(listener.getClass().getName() + "." + m.getName()
                        + " invalid @EventHandler (must have exactly one Event parameter)");
                continue;
            }
            m.setAccessible(true);
            Class<? extends Event> type = (Class<? extends Event>) params[0];
            List<RegisteredHandler> list = handlers.get(type);
            if (list == null) {
                list = new ArrayList<RegisteredHandler>();
                handlers.put(type, list);
            }
            list.add(new RegisteredHandler(plugin, listener, m, eh.priority(), eh.ignoreCancelled()));
        }
        bakedCache.clear();
    }

    public void unregisterEvents(Plugin plugin) {
        for (List<RegisteredHandler> list : handlers.values()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).plugin == plugin) {
                    list.remove(i);
                }
            }
        }
        bakedCache.clear();
    }

    private List<RegisteredHandler> handlersFor(Class<?> type) {
        List<RegisteredHandler> l = bakedCache.get(type);
        if (l != null) {
            return l;
        }
        l = new ArrayList<RegisteredHandler>();
        for (Map.Entry<Class<?>, List<RegisteredHandler>> e : handlers.entrySet()) {
            if (e.getKey().isAssignableFrom(type)) {
                l.addAll(e.getValue());
            }
        }
        Collections.sort(l, new Comparator<RegisteredHandler>() {
            public int compare(RegisteredHandler a, RegisteredHandler b) {
                return a.priority.ordinal() - b.priority.ordinal();
            }
        });
        bakedCache.put(type, l);
        return l;
    }

    /** The sender in the form the plugins of this build expect (see {@link EventTranslator#wrapSender}). */
    public net.thewellodev.api.command.CommandSender wrapSender(net.thewellodev.api.command.CommandSender sender) {
        return translator == null ? sender : translator.wrapSender(sender);
    }

    /** Calls an event: the server's own plugins first, then (for a build with its own API) the translated event. */
    public <T extends Event> T callEvent(T event) {
        dispatch(event);
        if (translator != null) {
            translator.translate(event);
        }
        return event;
    }

    /** Hands an event to the handlers registered for its class (no translation). */
    public <T extends Event> T dispatch(T event) {
        List<RegisteredHandler> list = handlersFor(event.getClass());
        for (RegisteredHandler h : list) {
            if (!h.plugin.isEnabled()) {
                continue;
            }
            if (h.ignoreCancelled && event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
                continue;
            }
            try {
                h.method.invoke(h.listener, event);
            } catch (InvocationTargetException e) {
                server.getLogger().log(Level.SEVERE, event.getEventName() + " error while handling ("
                        + h.plugin.getDescription().getFullName() + ")", e.getCause());
            } catch (Throwable t) {
                server.getLogger().log(Level.SEVERE, event.getEventName() + " error while handling ("
                        + h.plugin.getDescription().getFullName() + ")", t);
            }
        }
        return event;
    }
}
