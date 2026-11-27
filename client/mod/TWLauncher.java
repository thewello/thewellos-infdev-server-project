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
 * Simple launcher to start the patched client without BetaCraft.
 *
 * java -Djava.library.path=natives -cp "inf-20100630-1340-mp.jar;lwjgl.jar;lwjgl_util.jar;jinput.jar"
 *      TWLauncher [userName]
 */
public class TWLauncher implements AppletStub {

    private final Map<String, String> params = new HashMap<String, String>();
    private final Applet applet;

    private TWLauncher(Applet applet, String username) {
        this.applet = applet;
        params.put("username", username);
        params.put("sessionid", System.getProperty("thewellodev.token", "-"));
        params.put("stand-alone", "true");
    }

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "Player" + (System.currentTimeMillis() % 1000);
        int w = 854, h = 480, px = Integer.MIN_VALUE, py = Integer.MIN_VALUE; // a position can be negative (a monitor left of the main one)
        for (int i = 1; i + 1 < args.length; i += 2) {
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
        final Frame frame = new Frame("Minecraft inf-20100630-1340 (TheWello's Infdev) - " + name);
        frame.setLayout(new BorderLayout());
        final MinecraftApplet applet = new MinecraftApplet();
        applet.setStub(new TWLauncher(applet, name));
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
            // test/background: the window must not take focus or grab the mouse
            frame.setAutoRequestFocus(false);
            frame.setFocusableWindowState(false);
        }
        frame.setVisible(true);
        TWSounds.install(); // the sound list comes from BetaCraft's proxy (see TWSounds)
        applet.init();
        applet.start();
    }

    public boolean isActive() {
        return true;
    }

    public URL getDocumentBase() {
        try {
            return new URL("http://www.minecraft.net/game/");
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
