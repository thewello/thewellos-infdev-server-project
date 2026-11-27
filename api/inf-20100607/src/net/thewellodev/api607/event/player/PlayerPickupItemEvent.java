package net.thewellodev.api607.event.player;

import net.thewellodev.api.event.Cancellable;
import net.thewellodev.api607.Player607;

/**
 * Called when a player's game wants to pick up a shared item (the server decides who gets an item that two players touch).
 * If cancelled, the item stays on the ground and the game asks again after a moment.
 */
public class PlayerPickupItemEvent extends PlayerEvent implements Cancellable {
    private final int itemNumber;
    private final int typeId;
    private final int amount;
    private final int data;
    private boolean cancelled;

    public PlayerPickupItemEvent(Player607 player, int itemNumber, int typeId, int amount, int data) {
        super(player);
        this.itemNumber = itemNumber;
        this.typeId = typeId;
        this.amount = amount;
        this.data = data;
    }

    /** The number the server gave the item. */
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
