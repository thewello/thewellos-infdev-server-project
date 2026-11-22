package net.thewellodev.api325.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api325.Player325;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player325 player;

    protected PlayerEvent(Player325 player) {
        this.player = player;
    }

    public Player325 getPlayer() {
        return player;
    }
}
