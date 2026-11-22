package net.thewellodev.api325;

import java.util.HashMap;
import java.util.Map;

/**
 * The blocks of Infdev inf-20100325-1640 (the ids match the game). The build has the blocks with ids 1 to 62; this enum names
 * ids 0 to 51 and the chest (54), diamond ore (56) and the furnaces (61 and 62). There are no tools or other items in the
 * inventory API: this API has no items.
 */
public enum Material325 {
    AIR(0), STONE(1), GRASS(2), DIRT(3), COBBLESTONE(4), WOOD(5), SAPLING(6), BEDROCK(7),
    WATER(8), STATIONARY_WATER(9), LAVA(10), STATIONARY_LAVA(11), SAND(12), GRAVEL(13),
    GOLD_ORE(14), IRON_ORE(15), COAL_ORE(16), LOG(17), LEAVES(18), SPONGE(19), GLASS(20),
    RED_CLOTH(21), ORANGE_CLOTH(22), YELLOW_CLOTH(23), LIME_CLOTH(24), GREEN_CLOTH(25), AQUA_GREEN_CLOTH(26),
    CYAN_CLOTH(27), BLUE_CLOTH(28), PURPLE_CLOTH(29), INDIGO_CLOTH(30), VIOLET_CLOTH(31), MAGENTA_CLOTH(32),
    PINK_CLOTH(33), BLACK_CLOTH(34), GRAY_CLOTH(35), WHITE_CLOTH(36),
    YELLOW_FLOWER(37), RED_ROSE(38), BROWN_MUSHROOM(39), RED_MUSHROOM(40),
    GOLD_BLOCK(41), IRON_BLOCK(42), DOUBLE_STEP(43), STEP(44), BRICK(45), TNT(46),
    BOOKSHELF(47), MOSSY_COBBLESTONE(48), OBSIDIAN(49), TORCH(50), FIRE(51),
    CHEST(54), DIAMOND_ORE(56), FURNACE(61), BURNING_FURNACE(62);

    private static final Map<Integer, Material325> BY_ID = new HashMap<Integer, Material325>();

    static {
        for (Material325 m : values()) {
            BY_ID.put(m.id, m);
        }
    }

    private final int id;

    Material325(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /** Whether a player can walk through this block (air, plants, liquids, torch, fire). */
    public boolean isPassable() {
        return this == AIR || this == SAPLING || this == WATER || this == STATIONARY_WATER || this == LAVA
                || this == STATIONARY_LAVA || this == YELLOW_FLOWER || this == RED_ROSE || this == BROWN_MUSHROOM
                || this == RED_MUSHROOM || this == TORCH || this == FIRE;
    }

    /** Whether this block is water or lava. */
    public boolean isLiquid() {
        return this == WATER || this == STATIONARY_WATER || this == LAVA || this == STATIONARY_LAVA;
    }

    /** The material for an id, or null. */
    public static Material325 getMaterial(int id) {
        return BY_ID.get(id);
    }

    /** Finds a material by name (STONE, stone, "gold ore") or numeric id ("1"). */
    public static Material325 matchMaterial(String name) {
        if (name == null) {
            return null;
        }
        try {
            return getMaterial(Integer.parseInt(name.trim()));
        } catch (NumberFormatException ignored) {
        }
        String n = name.trim().toUpperCase(java.util.Locale.ENGLISH).replace(' ', '_').replace('-', '_');
        try {
            return valueOf(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
