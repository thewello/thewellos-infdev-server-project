package net.thewellodev.server.event;

import net.thewellodev.api.entity.Player;
import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;

/**
 * Builds 316, 320 and 321: an item appeared in a player's own game (a broken block, a thrown item, an item that a mob
 * dropped) and is about to be shown to the other players. If cancelled, it stays in that game only.
 */
public class SharedItemSpawnEvent extends Event implements Cancellable {
    private final Player owner;
    private final int number, typeId, amount, data;
    private final double x, y, z;
    private boolean cancelled;

    public SharedItemSpawnEvent(Player owner, int number, int typeId, int amount, int data, double x, double y, double z) {
        this.owner = owner;
        this.number = number;
        this.typeId = typeId;
        this.amount = amount;
        this.data = data;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Player getOwner() {
        return owner;
    }

    public int getNumber() {
        return number;
    }

    public int getTypeId() {
        return typeId;
    }

    public int getAmount() {
        return amount;
    }

    public int getData() {
        return data;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
