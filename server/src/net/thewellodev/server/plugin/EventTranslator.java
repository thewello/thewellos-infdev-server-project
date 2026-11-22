package net.thewellodev.server.plugin;

import net.thewellodev.api.event.Event;

/** Turns the server's events into the events of a build's own plugin API (see api227 and api316). */
public interface EventTranslator {
    void translate(Event e);

    /**
     * The sender as the plugins of the build see it: a plugin command of a build with its own API gets that API's player
     * type (so that {@code sender instanceof Player321} works), not the server's own.
     */
    default net.thewellodev.api.command.CommandSender wrapSender(net.thewellodev.api.command.CommandSender sender) {
        return sender;
    }
}
