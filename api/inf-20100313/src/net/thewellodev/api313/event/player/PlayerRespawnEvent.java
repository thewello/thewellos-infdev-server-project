package net.thewellodev.api313.event.player;

import net.thewellodev.api313.Player313;
import net.thewellodev.api313.Location313;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location313 respawnLocation;

    public PlayerRespawnEvent(Player313 player, Location313 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location313 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location313 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
