package net.thewellodev.server.api413;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api413.Block413;
import net.thewellodev.api413.Location413;
import net.thewellodev.api413.Player413;
import net.thewellodev.api413.World413;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100413-1953 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api413 {

    private final Server server;
    private final Map<Player, Player413Impl> players = new HashMap<Player, Player413Impl>();
    private World413Impl world;

    public Api413(Server server) {
        this.server = server;
    }

    public synchronized Player413 player(Player p) {
        if (p == null) {
            return null;
        }
        Player413Impl w = players.get(p);
        if (w == null) {
            w = new Player413Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World413 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World413Impl(this, w);
        }
        return world;
    }

    public Block413 block(Block b) {
        return b == null ? null : new Block413Impl(this, b);
    }

    public Location413 location(Location l) {
        return l == null ? null : new Location413(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location413 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World413Impl ? ((World413Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player413 p) {
        return p instanceof Player413Impl ? ((Player413Impl) p).handle : null;
    }
}
