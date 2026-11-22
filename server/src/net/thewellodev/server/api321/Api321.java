package net.thewellodev.server.api321;

import net.thewellodev.api.Location;
import net.thewellodev.api.Server;
import net.thewellodev.api.World;
import net.thewellodev.api.block.Block;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api321.Block321;
import net.thewellodev.api321.Location321;
import net.thewellodev.api321.Player321;
import net.thewellodev.api321.World321;

import java.util.HashMap;
import java.util.Map;

/**
 * The inf-20100321 API is a view of the server's own objects: this class makes (and keeps) the wrappers
 * and converts locations. The server itself works with the types of net.thewellodev.api.
 */
public final class Api321 {

    private final Server server;
    private final Map<Player, Player321Impl> players = new HashMap<Player, Player321Impl>();
    private World321Impl world;

    public Api321(Server server) {
        this.server = server;
    }

    public synchronized Player321 player(Player p) {
        if (p == null) {
            return null;
        }
        Player321Impl w = players.get(p);
        if (w == null) {
            w = new Player321Impl(this, p);
            players.put(p, w);
        }
        return w;
    }

    /** Forgets the wrapper of a player who left. */
    public synchronized void forget(Player p) {
        players.remove(p);
    }

    public synchronized World321 world(World w) {
        if (w == null) {
            return null;
        }
        if (world == null || world.handle != w) {
            world = new World321Impl(this, w);
        }
        return world;
    }

    public Block321 block(Block b) {
        return b == null ? null : new Block321Impl(this, b);
    }

    public Location321 location(Location l) {
        return l == null ? null : new Location321(world(l.getWorld()), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch());
    }

    public Location toServer(Location321 l) {
        if (l == null) {
            return null;
        }
        World w = l.getWorld() instanceof World321Impl ? ((World321Impl) l.getWorld()).handle : server.getWorld();
        return new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** The server's player behind a wrapper (or the object itself). */
    public static Player unwrap(Player321 p) {
        return p instanceof Player321Impl ? ((Player321Impl) p).handle : null;
    }
}
