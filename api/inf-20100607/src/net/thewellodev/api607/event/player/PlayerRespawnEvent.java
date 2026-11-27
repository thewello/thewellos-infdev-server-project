package net.thewellodev.api607.event.player;

import net.thewellodev.api607.Player607;
import net.thewellodev.api607.Location607;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location607 respawnLocation;

    public PlayerRespawnEvent(Player607 player, Location607 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location607 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location607 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
