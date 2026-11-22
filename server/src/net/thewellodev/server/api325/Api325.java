package net.thewellodev.server.api325;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api325.Block325;
import net.thewellodev.api325.Location325;
import net.thewellodev.api325.Player325;
import net.thewellodev.api325.World325;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100325-1640 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api325 {

    private final Server server;
    private final Map<Player, Player325Impl> players = new HashMap<Player, Player325Impl>();
    private World325Impl world;

    public Api325(Server server) {
        this.server = server;
    }

    public synchronized Player325 player(Player p) {
        if (p == null) {
            return null;
        }
        Player325Impl w = players.get(p);
        if (w == null) {
            w = new Player325Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World325 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World325Impl(this, w);
        }
        return world;
    }

    public Block325 block(Block b) {
        return b == null ? null : new Block325Impl(this, b);
    }

    public Location325 location(Location l) {
        return l == null ? null : new Location325(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location325 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World325Impl ? ((World325Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player325 p) {
        return p instanceof Player325Impl ? ((Player325Impl) p).handle : null;
    }
}
