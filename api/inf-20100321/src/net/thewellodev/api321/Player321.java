package net.thewellodev.api321;

import net.thewellodev.api.command.CommandSender;

import java.net.InetSocketAddress;

/**
 * A player connected to the server (inf-20100321).
 *
 * This build keeps the inventory on the client and has no item system: the API has no inventory, no items and no
 * ground items. It has health, the game mode (survival or creative with flying), PvP and teleporting.
 */
public interface Player321 extends CommandSender {

    /** Unique entity id on this server. */
    int getEntityId();

    /** Name shown in chat (default: the account name). */
    String getDisplayName();

    void setDisplayName(String name);

    /** The player's location (feet level). */
    Location321 getLocation();

    World321 getWorld();

    /** Teleports the player. */
    void teleport(Location321 location);

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
     * Shows this player a fake block (only this player sees it; the world itself is not changed).
     */
    void sendBlockChange(int x, int y, int z, int typeId);

    /** Health (0-20, 20 = 10 hearts). */
    int getHealth();

    /** Sets health (0 kills the player). */
    void setHealth(int health);

    boolean isDead();

    /**
     * 0 = survival, 1 = creative (flying, blocks break at once, nothing hurts), 2 = spectator (flying through walls, invisible,
     * nothing hurts and nothing can be changed; builds 316 and later). Creative and spectator are for operators only.
     */
    int getGameMode();

    void setGameMode(int mode);

    /** The location of the player's eyes (the feet location raised by 1.62 blocks), with the player's yaw and pitch. */
    Location321 getEyeLocation();

    /**
     * The first block that is not air or liquid on the line of sight, or null when there is none within
     * {@code maxDistance} blocks (1 to 100) or the line leaves the loaded chunks. Call it from the main thread.
     */
    Block321 getTargetBlock(int maxDistance);

    /** The time (milliseconds since 1970) at which this player connected. */
    long getLoginTime();

    /** Makes the player run a command as if it was typed in the chat. */
    default boolean performCommand(String commandLine) {
        return TheWello321.getServer().dispatchCommand(this, commandLine);
    }

    /** Teleports the player to another player. */
    default void teleport(Player321 other) {
        teleport(other.getLocation());
    }
}
