package net.thewellodev.api415.event.player;

import net.thewellodev.api415.Player415;
import net.thewellodev.api415.Location415;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location415 respawnLocation;

    public PlayerRespawnEvent(Player415 player, Location415 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location415 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location415 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
