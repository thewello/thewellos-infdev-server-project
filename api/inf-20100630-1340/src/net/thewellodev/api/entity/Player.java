package net.thewellodev.api.entity;

import net.thewellodev.api.ItemStack;
import net.thewellodev.api.Location;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.World;
import net.thewellodev.api.command.CommandSender;

import java.net.InetSocketAddress;

/** A player connected to the server. */
public interface Player extends CommandSender {

    /** Unique entity id on this server. */
    int getEntityId();

    /** Name shown in chat (default: the account name). */
    String getDisplayName();

    void setDisplayName(String name);

    /** The player's location (feet level). */
    Location getLocation();

    World getWorld();

    /** Teleports the player. */
    void teleport(Location location);

    void teleport(double x, double y, double z);

    /** Kicks the player from the server. */
    void kickPlayer(String reason);

    boolean isOnline();

    boolean isOnGround();

    InetSocketAddress getAddress();

    /** Ping (ms), or -1 if unknown. */
    int getPing();

    /** Makes the player say a message or run a command (if it starts with "/"). */
    void chat(String message);

    /**
     * Sends the client a fake block state for one block (only this player sees it;
     * the world itself is not changed).
     */
    void sendBlockChange(int x, int y, int z, int typeId, int data);

    /** Health (0-20, 20 = 10 hearts). The last value reported by the client. */
    int getHealth();

    /** Sets health (0 kills the player). */
    void setHealth(int health);

    boolean isDead();

    /** Gives an item to the inventory (drops it on the ground if there is no room). */
    void giveItem(int typeId, int amount, int data);

    /** Id of the held item, or -1 if empty. */
    int getItemInHand();

    /**
     * The last known inventory (per slot; empty slots are null). The inventory is kept on the client,
     * so these values can be a few ticks old.
     */
    ItemStack[] getInventoryContents();

    /** Sets an inventory slot, or empties it when item is null. The change is applied to the player's client. */
    void setInventorySlot(int slot, ItemStack item);

    /** 0 = survival, 1 = creative. (The builds 316 and later also have 2 = spectator: invisible, flying through walls.) */
    int getGameMode();

    void setGameMode(int mode);

    /** The location of the player's eyes (the feet location raised by 1.62 blocks), with the player's yaw and pitch. */
    Location getEyeLocation();

    /**
     * The first block that is not air or liquid on the line of sight, or null when there is none within
     * {@code maxDistance} blocks (1 to 100) or the line leaves the loaded chunks. Call it from the main thread.
     */
    Block getTargetBlock(int maxDistance);

    /** The time (milliseconds since 1970) at which this player connected. */
    long getLoginTime();

    /** Makes the player run a command as if it was typed in the chat. */
    default boolean performCommand(String commandLine) {
        return net.thewellodev.api.TheWello.getServer().dispatchCommand(this, commandLine);
    }

    /** Teleports the player to another player. */
    default void teleport(Player other) {
        teleport(other.getLocation());
    }
}
