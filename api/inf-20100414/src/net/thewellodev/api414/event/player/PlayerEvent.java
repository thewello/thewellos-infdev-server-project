package net.thewellodev.api414.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api414.Player414;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player414 player;

    protected PlayerEvent(Player414 player) {
        this.player = player;
    }

    public Player414 getPlayer() {
        return player;
    }
}
