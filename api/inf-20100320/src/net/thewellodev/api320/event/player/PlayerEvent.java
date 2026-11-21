package net.thewellodev.api320.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api320.Player320;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player320 player;

    protected PlayerEvent(Player320 player) {
        this.player = player;
    }

    public Player320 getPlayer() {
        return player;
    }
}
