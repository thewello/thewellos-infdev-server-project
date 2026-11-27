package net.thewellodev.api607.event.player;

import net.thewellodev.api.event.Event;
import net.thewellodev.api607.Player607;

/** Base class of the events about one player. */
public abstract class PlayerEvent extends Event {

    private final Player607 player;

    protected PlayerEvent(Player607 player) {
        this.player = player;
    }

    public Player607 getPlayer() {
        return player;
    }
}
