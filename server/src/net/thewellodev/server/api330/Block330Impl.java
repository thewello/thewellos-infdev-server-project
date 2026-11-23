package net.thewellodev.server.api330;

import net.thewellodev.api.block.Block;
import net.thewellodev.api330.Block330;
import net.thewellodev.api330.Location330;
import net.thewellodev.api330.Material330;
import net.thewellodev.api330.World330;

/** A block as the inf-20100330-1611 API shows it. */
final class Block330Impl implements Block330 {

    private final Api330 api;
    private final Block handle;

    Block330Impl(Api330 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World330 getWorld() {
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

    public Location330 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material330 getType() {
        return Material330.getMaterial(handle.getTypeId());
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

    public boolean setType(Material330 material) {
        return handle.setTypeId(material.getId());
    }

    public Block330 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block330Impl && ((Block330Impl) o).getX() == getX() && ((Block330Impl) o).getY() == getY()
                && ((Block330Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block330{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
