package net.thewellodev.api.util;

/**
 * The sides of a block, as in the Bukkit API of the time: north is towards a lower x, east towards a lower z, south towards a
 * higher x and west towards a higher z.
 */
public enum BlockFace {
    NORTH(-1, 0, 0),
    EAST(0, 0, -1),
    SOUTH(1, 0, 0),
    WEST(0, 0, 1),
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    NORTH_EAST(-1, 0, -1),
    NORTH_WEST(-1, 0, 1),
    SOUTH_EAST(1, 0, -1),
    SOUTH_WEST(1, 0, 1),
    SELF(0, 0, 0);

    private final int modX, modY, modZ;

    BlockFace(int modX, int modY, int modZ) {
        this.modX = modX;
        this.modY = modY;
        this.modZ = modZ;
    }

    public int getModX() {
        return modX;
    }

    public int getModY() {
        return modY;
    }

    public int getModZ() {
        return modZ;
    }

    /** The opposite side. */
    public BlockFace getOppositeFace() {
        switch (this) {
            case NORTH:
                return SOUTH;
            case EAST:
                return WEST;
            case SOUTH:
                return NORTH;
            case WEST:
                return EAST;
            case UP:
                return DOWN;
            case DOWN:
                return UP;
            case NORTH_EAST:
                return SOUTH_WEST;
            case NORTH_WEST:
                return SOUTH_EAST;
            case SOUTH_EAST:
                return NORTH_WEST;
            case SOUTH_WEST:
                return NORTH_EAST;
            default:
                return SELF;
        }
    }
}
