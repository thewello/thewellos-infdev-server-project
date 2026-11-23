package net.thewellodev.api413.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api413.Player413;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player413 player;

    protected PlayerEvent(Player413 player) {
        this.player = player;
    }

    public Player413 getPlayer() {
        return player;
    }
}
