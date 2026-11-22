package net.thewellodev.api321;

/** A block position in the world. Values are read live from the world on each call. */
public interface Block321 {

    World321 getWorld();

    int getX();

    int getY();

    int getZ();

    Location321 getLocation();

    /** The block id (0 = air). */
    int getTypeId();

    /** The material if it is known, otherwise null. */
    Material321 getType();

    boolean setTypeId(int id);

    /** The block metadata (0 to 15). */
    int getData();

    /** Changes the block and its metadata (0 to 15). */
    boolean setTypeIdAndData(int id, int data);

    boolean setType(Material321 material);

    /** A neighbouring block relative to this one. */
    Block321 getRelative(int dx, int dy, int dz);
}
