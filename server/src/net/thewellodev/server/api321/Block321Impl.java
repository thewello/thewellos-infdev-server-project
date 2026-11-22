package net.thewellodev.server.api321;

import net.thewellodev.api.block.Block;
import net.thewellodev.api321.Block321;
import net.thewellodev.api321.Location321;
import net.thewellodev.api321.Material321;
import net.thewellodev.api321.World321;

/** A block as the inf-20100321 API shows it. */
final class Block321Impl implements Block321 {

    private final Api321 api;
    private final Block handle;

    Block321Impl(Api321 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World321 getWorld() {
        return api.world(handle.getWorld());
    }

    public int getX() {
        return handle.getX();
    }

    public int getY() {
        return handle.getY();
    }

    public int getZ() {
        return handle.getZ();
    }

    public Location321 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material321 getType() {
        return Material321.getMaterial(handle.getTypeId());
    }

    public boolean setTypeId(int id) {
        return handle.setTypeId(id);
    }

    public int getData() {
        return handle.getWorld().getBlockDataAt(getX(), getY(), getZ());
    }

    public boolean setTypeIdAndData(int id, int data) {
        return handle.getWorld().setBlockTypeIdAndData(getX(), getY(), getZ(), id, data);
    }

    public boolean setType(Material321 material) {
        return handle.setTypeId(material.getId());
    }

    public Block321 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block321Impl && ((Block321Impl) o).getX() == getX() && ((Block321Impl) o).getY() == getY()
                && ((Block321Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block321{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
