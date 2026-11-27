package net.thewellodev.server.api420;

import net.thewellodev.api.block.Block;
import net.thewellodev.api420.Block420;
import net.thewellodev.api420.Location420;
import net.thewellodev.api420.Material420;
import net.thewellodev.api420.World420;

/** A block as the inf-20100420 API shows it. */
final class Block420Impl implements Block420 {

    private final Api420 api;
    private final Block handle;

    Block420Impl(Api420 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World420 getWorld() {
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

    public Location420 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material420 getType() {
        return Material420.getMaterial(handle.getTypeId());
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

    public boolean setType(Material420 material) {
        return handle.setTypeId(material.getId());
    }

    public Block420 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block420Impl && ((Block420Impl) o).getX() == getX() && ((Block420Impl) o).getY() == getY()
                && ((Block420Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block420{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
