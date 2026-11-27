package net.thewellodev.api420.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api420.Player420;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player420 player;

    protected PlayerEvent(Player420 player) {
        this.player = player;
    }

    public Player420 getPlayer() {
        return player;
    }
}
