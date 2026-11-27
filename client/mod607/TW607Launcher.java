import net.minecraft.client.MinecraftApplet;

import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts the patched inf-20100607 client without BetaCraft.
 *
 * java -Djava.library.path=natives -cp "inf-20100607-mp.jar;lwjgl.jar;lwjgl_util.jar;jinput.jar"
 *      TW607Launcher [userName] [--connect host[:port]] [--width W --height H --x X --y Y]
 */
public class TW607Launcher implements AppletStub {

    private final Map<String, String> params = new HashMap<String, String>();

    private TW607Launcher(String username) {
        params.put("username", username);
        params.put("sessionid", System.getProperty("thewellodev.token", "-"));
        params.put("stand-alone", "true");
    }

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 && !args[0].startsWith("--") ? args[0] : "Player" + (System.currentTimeMillis() % 1000);
        int w = 854, h = 480, px = Integer.MIN_VALUE, py = Integer.MIN_VALUE; // a position can be negative (a monitor left of the main one)
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--x")) {
                px = Integer.parseInt(args[i + 1]);
            } else if (args[i].equals("--y")) {
                py = Integer.parseInt(args[i + 1]);
            } else if (args[i].equals("--width")) {
                w = Integer.parseInt(args[i + 1]);
            } else if (args[i].equals("--height")) {
                h = Integer.parseInt(args[i + 1]);
            } else if (args[i].equals("--connect")) {
                System.setProperty("thewellodev.autoconnect", args[i + 1]);
            }
        }
        System.setProperty("thewellodev.user", name);
        final Frame frame = new Frame("Minecraft inf-20100607 (TheWello's Infdev) - " + name);
        frame.setLayout(new BorderLayout());
        final MinecraftApplet applet = new MinecraftApplet();
        applet.setStub(new TW607Launcher(name));
        applet.setPreferredSize(new Dimension(w, h));
        frame.add(applet, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        if (px != Integer.MIN_VALUE && py != Integer.MIN_VALUE) {
            frame.setLocation(px, py);
        }
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                new Thread() {
                    public void run() {
                        try {
                            Thread.sleep(30000L);
                        } catch (InterruptedException ignored) {
                        }
                        System.exit(0);
                    }
                }.start();
                applet.stop();
                applet.destroy();
                frame.dispose();
                System.exit(0);
            }
        });
        if (Boolean.getBoolean("thewellodev.nofocus")) {
            frame.setAutoRequestFocus(false);
            frame.setFocusableWindowState(false);
        }
        final int exitAfter = Integer.getInteger("thewellodev.exitafter", 0);
        if (exitAfter > 0) {
            // test runs: close the client by itself after this many seconds
            Thread killer = new Thread() {
                public void run() {
                    try {
                        Thread.sleep(exitAfter * 1000L);
                    } catch (InterruptedException ignored) {
                    }
                    System.exit(0);
                }
            };
            killer.setDaemon(true);
            killer.start();
        }
        frame.setVisible(true);
        TWSounds.install(); // the sound list comes from BetaCraft's proxy (see TWSounds)
        applet.init();
        applet.start();
    }

    public boolean isActive() {
        return true;
    }

    /**
     * The game reads a URL built from the document base when it sets a world (an old online check) and only keeps
     * the world if that check passes. A closed local port makes the request fail at once, so nothing is sent to
     * minecraft.net; the mod sets the world field itself (see TW607Client).
     */
    public URL getDocumentBase() {
        try {
            return new URL("http://127.0.0.1:9/game/");
        } catch (Exception e) {
            return null;
        }
    }

    public URL getCodeBase() {
        return getDocumentBase();
    }

    public String getParameter(String name) {
        return params.get(name);
    }

    public AppletContext getAppletContext() {
        return null;
    }

    public void appletResize(int width, int height) {
    }
}
