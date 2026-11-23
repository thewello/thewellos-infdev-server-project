package net.thewellodev.api320.event.player;

import net.thewellodev.api320.Player320;
import net.thewellodev.api320.Location320;

/** Called when a dead player respawns. The respawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location320 respawnLocation;

    public PlayerRespawnEvent(Player320 player, Location320 respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location320 getRespawnLocation() {
        return respawnLocation;
    }

    public void setRespawnLocation(Location320 location) {
        this.respawnLocation = location;
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
