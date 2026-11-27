package net.thewellodev.api608.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api608.Player608;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player608 player;

    protected PlayerEvent(Player608 player) {
        this.player = player;
    }

    public Player608 getPlayer() {
        return player;
    }
}
