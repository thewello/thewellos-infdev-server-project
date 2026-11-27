package net.thewellodev.server.api607;

import net.thewellodev.api.block.Block;
import net.thewellodev.api607.Block607;
import net.thewellodev.api607.Location607;
import net.thewellodev.api607.Material607;
import net.thewellodev.api607.World607;

/** A block as the inf-20100607 API shows it. */
final class Block607Impl implements Block607 {

    private final Api607 api;
    private final Block handle;

    Block607Impl(Api607 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World607 getWorld() {
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

    public Location607 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material607 getType() {
        return Material607.getMaterial(handle.getTypeId());
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

    public boolean setType(Material607 material) {
        return handle.setTypeId(material.getId());
    }

    public Block607 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block607Impl && ((Block607Impl) o).getX() == getX() && ((Block607Impl) o).getY() == getY()
                && ((Block607Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block607{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
