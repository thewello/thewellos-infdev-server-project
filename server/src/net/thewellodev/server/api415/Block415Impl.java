package net.thewellodev.server.api415;

import net.thewellodev.api.block.Block;
import net.thewellodev.api415.Block415;
import net.thewellodev.api415.Location415;
import net.thewellodev.api415.Material415;
import net.thewellodev.api415.World415;

/** A block as the inf-20100415 API shows it. */
final class Block415Impl implements Block415 {

    private final Api415 api;
    private final Block handle;

    Block415Impl(Api415 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World415 getWorld() {
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

    public Location415 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material415 getType() {
        return Material415.getMaterial(handle.getTypeId());
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

    public boolean setType(Material415 material) {
        return handle.setTypeId(material.getId());
    }

    public Block415 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block415Impl && ((Block415Impl) o).getX() == getX() && ((Block415Impl) o).getY() == getY()
                && ((Block415Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block415{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
