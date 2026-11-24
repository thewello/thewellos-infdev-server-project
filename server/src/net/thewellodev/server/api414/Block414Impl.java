package net.thewellodev.server.api414;

import net.thewellodev.api.block.Block;
import net.thewellodev.api414.Block414;
import net.thewellodev.api414.Location414;
import net.thewellodev.api414.Material414;
import net.thewellodev.api414.World414;

/** A block as the inf-20100414 API shows it. */
final class Block414Impl implements Block414 {

    private final Api414 api;
    private final Block handle;

    Block414Impl(Api414 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World414 getWorld() {
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

    public Location414 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material414 getType() {
        return Material414.getMaterial(handle.getTypeId());
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

    public boolean setType(Material414 material) {
        return handle.setTypeId(material.getId());
    }

    public Block414 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block414Impl && ((Block414Impl) o).getX() == getX() && ((Block414Impl) o).getY() == getY()
                && ((Block414Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block414{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
