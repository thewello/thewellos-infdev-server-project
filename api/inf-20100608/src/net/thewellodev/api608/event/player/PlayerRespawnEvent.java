package net.thewellodev.api608.event.player;

import net.thewellodev.api608.Player608;
import net.thewellodev.api608.Location608;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location608 respawnLocation;

    public PlayerRespawnEvent(Player608 player, Location608 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location608 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location608 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
