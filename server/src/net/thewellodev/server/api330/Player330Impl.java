package net.thewellodev.server.api330;

import net.thewellodev.api.Server;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api330.Block330;
import net.thewellodev.api330.Location330;
import net.thewellodev.api330.Player330;
import net.thewellodev.api330.World330;

import java.net.InetSocketAddress;

/** A player as the inf-20100330-1611 API shows it. */
final class Player330Impl implements Player330 {

    private final Api330 api;
    final Player handle;

    Player330Impl(Api330 api, Player handle) {
        this.api = api;
        this.handle = handle;
    }

    public int getEntityId() {
        return handle.getEntityId();
    }

    public String getDisplayName() {
        return handle.getDisplayName();
    }

    public void setDisplayName(String name) {
        handle.setDisplayName(name);
    }

    public Location330 getLocation() {
        return api.location(handle.getLocation());
    }

    public World330 getWorld() {
        return api.world(handle.getWorld());
    }

    public void teleport(Location330 location) {
        handle.teleport(api.toServer(location));
    }

    public void teleport(double x, double y, double z) {
        handle.teleport(x, y, z);
    }

    public void kickPlayer(String reason) {
        handle.kickPlayer(reason);
    }

    public boolean isOnline() {
        return handle.isOnline();
    }

    public boolean isOnGround() {
        return handle.isOnGround();
    }

    public InetSocketAddress getAddress() {
        return handle.getAddress();
    }

    public int getPing() {
        return handle.getPing();
    }

    public void chat(String message) {
        handle.chat(message);
    }

    public void sendBlockChange(int x, int y, int z, int typeId) {
        handle.sendBlockChange(x, y, z, typeId, 0);
    }

    public int getHealth() {
        return handle.getHealth();
    }

    public void setHealth(int health) {
        handle.setHealth(health);
    }

    public boolean isDead() {
        return handle.isDead();
    }

    public int getGameMode() {
        return handle.getGameMode();
    }

    public void setGameMode(int mode) {
        handle.setGameMode(mode);
    }

    // CommandSender

    public void sendMessage(String message) {
        handle.sendMessage(message);
    }

    public String getName() {
        return handle.getName();
    }

    public boolean isOp() {
        return handle.isOp();
    }

    public void setOp(boolean op) {
        handle.setOp(op);
    }

    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    public Server getServer() {
        return handle.getServer();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Player330Impl && ((Player330Impl) o).handle == handle;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(handle);
    }

    @Override
    public String toString() {
        return "Player330{" + handle.getName() + "}";
    }

    public Location330 getEyeLocation() {
        return api.location(handle.getEyeLocation());
    }

    public Block330 getTargetBlock(int maxDistance) {
        net.thewellodev.api.block.Block b = handle.getTargetBlock(maxDistance);
        return b == null ? null : api.block(b);
    }

    public long getLoginTime() {
        return handle.getLoginTime();
    }
}
