import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * The sounds of the game. The game asks http://www.minecraft.net/resources/ for the list of its sounds and loads them only
 * when the list arrives; that address does not answer any more. The proxy of BetaCraft answers it. The launchers send
 * exactly those requests through it: every other request (account checks, skins, the server) goes the usual way, so
 * nothing about the player reaches the proxy.
 *
 * -Dthewellodev.nosoundproxy=true switches this off. A proxy that was set for the whole program
 * (-Dhttp.proxyHost, as BetaCraft does) is left as it is.
 */
public final class TWSounds {

    private static final String HOST = "betacraft.uk";
    private static final int PORT = 11702;

    private TWSounds() {
    }

    /** Called by the launcher before the game starts. */
    public static void install() {
        if (Boolean.getBoolean("thewellodev.nosoundproxy") || System.getProperty("http.proxyHost") != null) {
            return;
        }
        final ProxySelector usual = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                if (uri != null && "http".equalsIgnoreCase(uri.getScheme()) && "www.minecraft.net".equalsIgnoreCase(uri.getHost())
                        && uri.getPath() != null && uri.getPath().startsWith("/resources/")) {
                    if (Boolean.getBoolean("thewellodev.debug")) {
                        System.out.println("[Infdev MP] sound request through the proxy: " + uri.getPath());
                    }
                    return Collections.singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(HOST, PORT)));
                }
                return usual == null ? Collections.singletonList(Proxy.NO_PROXY) : usual.select(uri);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
                if (usual != null) {
                    usual.connectFailed(uri, address, failure);
                }
            }
        });
    }
}
