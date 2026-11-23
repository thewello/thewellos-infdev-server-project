package net.thewellodev.api316.event.player;

import net.thewellodev.api316.Player316;
import net.thewellodev.api316.Location316;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location316 respawnLocation;

    public PlayerRespawnEvent(Player316 player, Location316 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location316 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location316 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
