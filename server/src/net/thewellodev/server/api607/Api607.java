package net.thewellodev.server.api607;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api607.Block607;
import net.thewellodev.api607.Location607;
import net.thewellodev.api607.Player607;
import net.thewellodev.api607.World607;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100607 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api607 {

    private final Server server;
    private final Map<Player, Player607Impl> players = new HashMap<Player, Player607Impl>();
    private World607Impl world;

    public Api607(Server server) {
        this.server = server;
    }

    public synchronized Player607 player(Player p) {
        if (p == null) {
            return null;
        }
        Player607Impl w = players.get(p);
        if (w == null) {
            w = new Player607Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World607 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World607Impl(this, w);
        }
        return world;
    }

    public Block607 block(Block b) {
        return b == null ? null : new Block607Impl(this, b);
    }

    public Location607 location(Location l) {
        return l == null ? null : new Location607(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location607 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World607Impl ? ((World607Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player607 p) {
        return p instanceof Player607Impl ? ((Player607Impl) p).handle : null;
    }
}
