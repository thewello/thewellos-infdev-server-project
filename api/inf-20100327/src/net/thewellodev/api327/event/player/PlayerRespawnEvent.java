package net.thewellodev.api327.event.player;

import net.thewellodev.api327.Player327;
import net.thewellodev.api327.Location327;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location327 respawnLocation;

    public PlayerRespawnEvent(Player327 player, Location327 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location327 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location327 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
