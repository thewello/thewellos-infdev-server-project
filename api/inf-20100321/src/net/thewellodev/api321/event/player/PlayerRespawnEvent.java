package net.thewellodev.api321.event.player;

import net.thewellodev.api321.Player321;
import net.thewellodev.api321.Location321;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location321 respawnLocation;

    public PlayerRespawnEvent(Player321 player, Location321 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location321 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location321 location) {
        this.respawnLocation = location;
    }
}
