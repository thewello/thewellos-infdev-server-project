package net.thewellodev.server.api325;

import net.thewellodev.api.block.Block;
import net.thewellodev.api325.Block325;
import net.thewellodev.api325.Location325;
import net.thewellodev.api325.Material325;
import net.thewellodev.api325.World325;

/** A block as the inf-20100325-1640 API shows it. */
final class Block325Impl implements Block325 {

    private final Api325 api;
    private final Block handle;

    Block325Impl(Api325 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World325 getWorld() {
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

    public Location325 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material325 getType() {
        return Material325.getMaterial(handle.getTypeId());
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

    public boolean setType(Material325 material) {
        return handle.setTypeId(material.getId());
    }

    public Block325 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block325Impl && ((Block325Impl) o).getX() == getX() && ((Block325Impl) o).getY() == getY()
                && ((Block325Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block325{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
