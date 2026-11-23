package net.thewellodev.api227.event.player;

import net.thewellodev.api227.Player227;
import net.thewellodev.api227.Location227;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location227 respawnLocation;

    public PlayerRespawnEvent(Player227 player, Location227 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location227 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location227 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
