package net.thewellodev.server.api413;

import net.thewellodev.api.block.Block;
import net.thewellodev.api413.Block413;
import net.thewellodev.api413.Location413;
import net.thewellodev.api413.Material413;
import net.thewellodev.api413.World413;

/** A block as the inf-20100413-1953 API shows it. */
final class Block413Impl implements Block413 {

    private final Api413 api;
    private final Block handle;

    Block413Impl(Api413 api, Block handle) {
        this.api = api;
        this.handle = handle;
    }

    public World413 getWorld() {
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

    public Location413 getLocation() {
        return api.location(handle.getLocation());
    }

    public int getTypeId() {
        return handle.getTypeId();
    }

    public Material413 getType() {
        return Material413.getMaterial(handle.getTypeId());
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

    public boolean setType(Material413 material) {
        return handle.setTypeId(material.getId());
    }

    public Block413 getRelative(int dx, int dy, int dz) {
        return api.block(handle.getRelative(dx, dy, dz));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Block413Impl && ((Block413Impl) o).getX() == getX() && ((Block413Impl) o).getY() == getY()
                && ((Block413Impl) o).getZ() == getZ();
    }

    @Override
    public int hashCode() {
        return (getX() * 31 + getY()) * 31 + getZ();
    }

    @Override
    public String toString() {
        return "Block413{" + getX() + "," + getY() + "," + getZ() + " id " + getTypeId() + "}";
    }
}
