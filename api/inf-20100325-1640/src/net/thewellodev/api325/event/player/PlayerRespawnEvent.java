package net.thewellodev.api325.event.player;

import net.thewellodev.api325.Player325;
import net.thewellodev.api325.Location325;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location325 respawnLocation;

    public PlayerRespawnEvent(Player325 player, Location325 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location325 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location325 location) {
        this.respawnLocation = location;
    }
}
