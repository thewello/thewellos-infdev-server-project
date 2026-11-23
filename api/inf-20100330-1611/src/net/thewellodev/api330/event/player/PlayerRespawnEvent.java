package net.thewellodev.api330.event.player;

import net.thewellodev.api330.Player330;
import net.thewellodev.api330.Location330;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location330 respawnLocation;

    public PlayerRespawnEvent(Player330 player, Location330 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location330 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location330 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
