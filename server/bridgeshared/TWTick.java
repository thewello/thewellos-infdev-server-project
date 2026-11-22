import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Builds from inf-20100325-1640 on: the game's blocks tick again (leaves decay, fire spreads, grass spreads, flowers die
 * in the dark), and the server is the one that ticks them. The bridge opens a window around each world tick
 * ({@link #capturing}); the hooks that the class loader inserts into the world class report the blocks that change in it
 * and the items that the blocks drop, and the bridge sends both on to the players.
 *
 * The class sits in the bridge folder of the server jar, so the game's class loader finds it (as it does TWSeed).
 */
public final class TWTick {

    private TWTick() {
    }

    /** True while the bridge runs the world's tick. */
    public static boolean capturing;
    /** True while a chunk is being made: what the generator writes is not a change that the players have to hear of. */
    public static boolean quiet;

    private static final LinkedHashSet<Long> changes = new LinkedHashSet<Long>();
    private static final List<double[]> items = new ArrayList<double[]>();
    private static Field itemStack, stackCount, stackId, stackDamage, posX, posY, posZ, motX, motY, motZ;

    private static long pack(int x, int y, int z) {
        return ((long) (x + (1 << 25)) << 33) | ((long) (z + (1 << 25)) << 7) | y;
    }

    /** Hook: a block of the world is about to be set (world coordinates). */
    public static void changed(int x, int y, int z) {
        if (capturing && !quiet && y >= 0 && y < 128 && changes.size() < 30000) {
            changes.add(pack(x, y, z));
        }
    }

    /** The positions that changed since the last call (x, y, z each), and forgets them. */
    public static int[] takeChanges() {
        int[] out = new int[changes.size() * 3];
        int i = 0;
        for (long k : changes) {
            out[i++] = (int) (k >>> 33) - (1 << 25);
            out[i++] = (int) (k & 127);
            out[i++] = (int) ((k >>> 7) & 0x3FFFFFF) - (1 << 25);
        }
        changes.clear();
        return out;
    }

    /**
     * Hook: an entity is about to be added to the world. Nothing in the server's world is ever ticked, so no entity is
     * added; an item that a block drops during a tick is passed on (the players' games run the items).
     * @return true to cancel the addition
     */
    public static boolean entity(Object e) {
        if (capturing && e != null && "net.minecraft.a.c.c.b".equals(e.getClass().getName()) && items.size() < 2000) {
            try {
                if (itemStack == null) {
                    Class<?> ic = e.getClass();
                    Class<?> base = Class.forName("net.minecraft.a.c.b", true, ic.getClassLoader());
                    itemStack = ic.getField("a");
                    Class<?> st = Class.forName("net.minecraft.a.b.k", true, ic.getClassLoader());
                    stackCount = st.getField("a");
                    stackId = st.getField("c");
                    stackDamage = st.getField("d");
                    posX = base.getField("g");
                    posY = base.getField("h");
                    posZ = base.getField("i");
                    motX = base.getField("j");
                    motY = base.getField("k");
                    motZ = base.getField("l");
                }
                Object stack = itemStack.get(e);
                if (stack != null) {
                    items.add(new double[]{posX.getDouble(e), posY.getDouble(e), posZ.getDouble(e), motX.getDouble(e),
                            motY.getDouble(e), motZ.getDouble(e), stackId.getInt(stack), stackCount.getInt(stack),
                            stackDamage.getInt(stack)});
                }
            } catch (Exception ex) {
                // an item that cannot be read is dropped
            }
        }
        return true;
    }

    /** The items that blocks dropped since the last call: x, y, z, motion x, y, z, id, count, damage. */
    public static List<double[]> takeItems() {
        List<double[]> out = new ArrayList<double[]>(items);
        items.clear();
        return out;
    }
}
