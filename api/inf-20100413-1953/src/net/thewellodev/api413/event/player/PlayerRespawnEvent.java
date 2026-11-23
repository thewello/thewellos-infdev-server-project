package net.thewellodev.api413.event.player;

import net.thewellodev.api413.Player413;
import net.thewellodev.api413.Location413;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location413 respawnLocation;

    public PlayerRespawnEvent(Player413 player, Location413 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location413 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location413 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
