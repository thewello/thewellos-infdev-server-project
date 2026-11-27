package net.thewellodev.server.api608;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api608.Block608;
import net.thewellodev.api608.Location608;
import net.thewellodev.api608.Player608;
import net.thewellodev.api608.World608;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100608 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api608 {

    private final Server server;
    private final Map<Player, Player608Impl> players = new HashMap<Player, Player608Impl>();
    private World608Impl world;

    public Api608(Server server) {
        this.server = server;
    }

    public synchronized Player608 player(Player p) {
        if (p == null) {
            return null;
        }
        Player608Impl w = players.get(p);
        if (w == null) {
            w = new Player608Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World608 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World608Impl(this, w);
        }
        return world;
    }

    public Block608 block(Block b) {
        return b == null ? null : new Block608Impl(this, b);
    }

    public Location608 location(Location l) {
        return l == null ? null : new Location608(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location608 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World608Impl ? ((World608Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player608 p) {
        return p instanceof Player608Impl ? ((Player608Impl) p).handle : null;
    }
}
