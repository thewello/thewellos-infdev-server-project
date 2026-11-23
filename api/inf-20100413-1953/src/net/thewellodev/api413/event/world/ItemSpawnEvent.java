package net.thewellodev.api413.event.world;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api.event.Event;
import net.thewellodev.api413.Location413;
import net.thewellodev.api413.Player413;

/**
 * Called when an item appeared in a player's own game (a broken block, a thrown item, an item that a mob dropped) and is
 * about to be shown to the other players. If cancelled, the item exists in the game of its owner only.
 */
public class ItemSpawnEvent extends Event implements Cancellable {
    private final Player413 owner;
    private final Location413 location;
    private final int itemNumber;
    private final int typeId;
    private final int amount;
    private final int data;
    private boolean cancelled;

    public ItemSpawnEvent(Player413 owner, Location413 location, int itemNumber, int typeId, int amount, int data) {
        this.owner = owner;
        this.location = location;
        this.itemNumber = itemNumber;
        this.typeId = typeId;
        this.amount = amount;
        this.data = data;
    }

    /** The player whose game made the item. */
    public Player413 getOwner() {
        return owner;
    }

    public Location413 getLocation() {
        return location;
    }

    public int getItemNumber() {
        return itemNumber;
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

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
