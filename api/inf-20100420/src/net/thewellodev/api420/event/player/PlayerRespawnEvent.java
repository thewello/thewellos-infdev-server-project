package net.thewellodev.api420.event.player;

import net.thewellodev.api420.Player420;
import net.thewellodev.api420.Location420;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location420 respawnLocation;

    public PlayerRespawnEvent(Player420 player, Location420 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location420 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location420 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
