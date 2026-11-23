package net.thewellodev.api227;

/** A block position in the world. Values are read live from the world on each call. */
public interface Block227 {

    World227 getWorld();

    int getX();

    int getY();

    int getZ();

    Location227 getLocation();

    /** The block id (0 = air). */
    int getTypeId();

    /** The material if it is known, otherwise null. */
    Material227 getType();

    boolean setTypeId(int id);

    boolean setType(Material227 material);

    /** A neighbouring block relative to this one. */
    Block227 getRelative(int dx, int dy, int dz);

    /** True if the block is air. */
    default boolean isEmpty() {
        return getTypeId() == 0;
    }

    /** True if the block is water or lava. */
    default boolean isLiquid() {
        int id = getTypeId();
        return id >= 8 && id <= 11;
    }

    /** The block on the given side. */
    default Block227 getRelative(net.thewellodev.api.util.BlockFace face) {
        return getRelative(face.getModX(), face.getModY(), face.getModZ());
    }

    /** The block that is the given number of steps away on the given side. */
    default Block227 getRelative(net.thewellodev.api.util.BlockFace face, int distance) {
        return getRelative(face.getModX() * distance, face.getModY() * distance, face.getModZ() * distance);
    }

    /** The side on which the other block touches this one, or null if it is not next to it. */
    default net.thewellodev.api.util.BlockFace getFace(Block227 other) {
        if (other == null || other.getWorld() != getWorld()) {
            return null;
        }
        int dx = other.getX() - getX(), dy = other.getY() - getY(), dz = other.getZ() - getZ();
        for (net.thewellodev.api.util.BlockFace f : net.thewellodev.api.util.BlockFace.values()) {
            if (f.getModX() == dx && f.getModY() == dy && f.getModZ() == dz) {
                return f;
            }
        }
        return null;
    }

    /** The x coordinate of the chunk that holds the block. */
    default int getChunkX() {
        return getX() >> 4;
    }

    /** The z coordinate of the chunk that holds the block. */
    default int getChunkZ() {
        return getZ() >> 4;
    }
}
