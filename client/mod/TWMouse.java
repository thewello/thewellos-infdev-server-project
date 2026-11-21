/**
 * Mouse handling for two game windows on one computer.
 *
 * Every window reads the same mouse. The game turns the camera from the mouse movement it reads with
 * {@code Mouse.getDX()} and {@code Mouse.getDY()}; the patcher replaces those two calls in the game's renderer by
 * {@link #dx()} and {@link #dy()}. A window that does not have the keyboard focus reads no movement and lets go of
 * the mouse, so the camera of one window never follows the mouse of the other one.
 */
public final class TWMouse {

    private static boolean released;
    private static int flush;

    private TWMouse() {
    }

    /** Whether this game window has the focus (the window of LWJGL and a window of this process). */
    public static boolean active() {
        return org.lwjgl.opengl.Display.isActive()
                && java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null;
    }

    /** Horizontal mouse movement for the camera: nothing while the window has no focus. */
    public static int dx() {
        int d = org.lwjgl.input.Mouse.getDX();
        return d != 0 && active() && flush == 0 ? d : 0;
    }

    /** Vertical mouse movement for the camera: nothing while the window has no focus. */
    public static int dy() {
        int d = org.lwjgl.input.Mouse.getDY();
        if (flush > 0) {
            flush--;
        }
        return d != 0 && active() && flush == 0 ? d : 0;
    }

    /**
     * Once per game tick: a window without focus gives the mouse back; a window that gets the focus back grabs it
     * again (and ignores the jump of the cursor to the window centre).
     */
    public static void guard() {
        guard(true);
    }

    /**
     * Once per game tick (see {@link #guard()}). {@code canGrab}: the game is in the world with no menu or chat open; when
     * the focus comes back while a menu is open the mouse stays free (the game grabs it when the menu closes).
     */
    public static void guard(boolean canGrab) {
        if (!active()) {
            if (org.lwjgl.input.Mouse.isGrabbed()) {
                org.lwjgl.input.Mouse.setGrabbed(false);
                released = true;
            }
            org.lwjgl.input.Mouse.getDX();
            org.lwjgl.input.Mouse.getDY();
        } else if (released) {
            released = false;
            if (canGrab) {
                org.lwjgl.input.Mouse.setGrabbed(true);
                flush = 3;
            }
        }
    }
}
