package net.thewellodev.server.api608;

import net.thewellodev.api.block.Block;
import net.thewellodev.api608.Block608;
import net.thewellodev.api608.Location608;
import net.thewellodev.api608.Material608;
import net.thewellodev.api608.World608;

/** A block as the inf-20100608 API shows it. */
final class Block608Impl implements Block608 {

    private final Api608 api;
    private final Block handle;

    Block608Impl(Api608 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World608 getWorld() {
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

    public Location608 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material608 getType() {
        return Material608.getMaterial(handle.getTypeId());
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

    public boolean setType(Material608 material) {
        return handle.setTypeId(material.getId());
    }

    public Block608 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block608Impl && ((Block608Impl) o).getX() == getX() && ((Block608Impl) o).getY() == getY()
                && ((Block608Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block608{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
