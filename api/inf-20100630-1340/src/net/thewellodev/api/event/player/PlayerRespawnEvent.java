package net.thewellodev.api.event.player;

import net.thewellodev.api.Location;
import net.thewellodev.api.entity.Player;

/** Called when a player respawns after death. The spawn location can be changed. */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location respawnLocation;

    public PlayerRespawnEvent(Player player, Location respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location getRespawnLocation() {
        return respawnLocation.clone();
    }

    public void setRespawnLocation(Location location) {
        this.respawnLocation = location.clone();
    }

    /** Always false: these builds have no beds. */
    public boolean isBedSpawn() {
        return false;
    }
}
