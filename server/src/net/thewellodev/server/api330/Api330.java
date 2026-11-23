package net.thewellodev.server.api330;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api330.Block330;
import net.thewellodev.api330.Location330;
import net.thewellodev.api330.Player330;
import net.thewellodev.api330.World330;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100330-1611 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api330 {

    private final Server server;
    private final Map<Player, Player330Impl> players = new HashMap<Player, Player330Impl>();
    private World330Impl world;

    public Api330(Server server) {
        this.server = server;
    }

    public synchronized Player330 player(Player p) {
        if (p == null) {
            return null;
        }
        Player330Impl w = players.get(p);
        if (w == null) {
            w = new Player330Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World330 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World330Impl(this, w);
        }
        return world;
    }

    public Block330 block(Block b) {
        return b == null ? null : new Block330Impl(this, b);
    }

    public Location330 location(Location l) {
        return l == null ? null : new Location330(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location330 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World330Impl ? ((World330Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player330 p) {
        return p instanceof Player330Impl ? ((Player330Impl) p).handle : null;
    }
}
