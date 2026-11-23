package net.thewellodev.server.api327;

import net.thewellodev.api.block.Block;
import net.thewellodev.api327.Block327;
import net.thewellodev.api327.Location327;
import net.thewellodev.api327.Material327;
import net.thewellodev.api327.World327;

/** A block as the inf-20100327 API shows it. */
final class Block327Impl implements Block327 {

    private final Api327 api;
    private final Block handle;

    Block327Impl(Api327 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World327 getWorld() {
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

    public Location327 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material327 getType() {
        return Material327.getMaterial(handle.getTypeId());
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

    public boolean setType(Material327 material) {
        return handle.setTypeId(material.getId());
    }

    public Block327 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block327Impl && ((Block327Impl) o).getX() == getX() && ((Block327Impl) o).getY() == getY()
                && ((Block327Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block327{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
