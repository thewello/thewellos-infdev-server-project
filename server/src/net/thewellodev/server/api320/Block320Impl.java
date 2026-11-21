package net.thewellodev.server.api320;

import net.thewellodev.api.block.Block;
import net.thewellodev.api320.Block320;
import net.thewellodev.api320.Location320;
import net.thewellodev.api320.Material320;
import net.thewellodev.api320.World320;

/** A block as the inf-20100320 API shows it. */
final class Block320Impl implements Block320 {

    private final Api320 api;
    private final Block handle;

    Block320Impl(Api320 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World320 getWorld() {
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

    public Location320 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material320 getType() {
        return Material320.getMaterial(handle.getTypeId());
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

    public boolean setType(Material320 material) {
        return handle.setTypeId(material.getId());
    }

    public Block320 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block320Impl && ((Block320Impl) o).getX() == getX() && ((Block320Impl) o).getY() == getY()
                && ((Block320Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block320{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
