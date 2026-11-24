package net.thewellodev.api414.event.player;

import net.thewellodev.api414.Player414;
import net.thewellodev.api414.Location414;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location414 respawnLocation;

    public PlayerRespawnEvent(Player414 player, Location414 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location414 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location414 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
