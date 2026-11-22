package net.thewellodev.server.api227;

import net.thewellodev.api.Location;
import net.thewellodev.api.entity.Player;
import net.thewellodev.api.event.Event;
import net.thewellodev.api.event.block.BlockBreakEvent;
import net.thewellodev.api.event.block.BlockPlaceEvent;
import net.thewellodev.api.event.player.PlayerAttackEvent;
import net.thewellodev.api.event.player.PlayerChatEvent;
import net.thewellodev.api.event.player.PlayerCommandPreprocessEvent;
import net.thewellodev.api.event.player.PlayerDamageByPlayerEvent;
import net.thewellodev.api.event.player.PlayerDeathEvent;
import net.thewellodev.api.event.player.PlayerJoinEvent;
import net.thewellodev.api.event.player.PlayerKickEvent;
import net.thewellodev.api.event.player.PlayerLoginEvent;
import net.thewellodev.api.event.player.PlayerMoveEvent;
import net.thewellodev.api.event.player.PlayerQuitEvent;
import net.thewellodev.api.event.player.PlayerRespawnEvent;
import net.thewellodev.api227.Location227;
import net.thewellodev.api227.Player227;
import net.thewellodev.server.plugin.SimplePluginManager;

/**
 * Turns the server's events into the events of the inf-20100227-1433 API. The server fires its own events as it
 * always does; after the handlers of those have run, the same thing is announced to the plugins of this build with
 * the API's event classes, and what the plugins changed (cancelling, messages, locations) is copied back.
 *
 * Events this build cannot have are not translated: item drops and pickups, inventory, health reports, items on the
 * ground, and hits against mobs.
 */
public final class Events227 implements net.thewellodev.server.plugin.EventTranslator {

    private final SimplePluginManager plugins;
    private final Api227 api;

    public Events227(SimplePluginManager plugins, Api227 api) {
        this.plugins = plugins;
        this.api = api;
    }

    public void translate(Event e) {
        if (e instanceof PlayerLoginEvent) {
            PlayerLoginEvent a = (PlayerLoginEvent) e;
            net.thewellodev.api227.event.player.PlayerLoginEvent b = new net.thewellodev.api227.event.player.PlayerLoginEvent(
                    api.player(a.getPlayer()), a.getAddress());
            plugins.dispatch(b);
            if (!b.isAllowed()) {
                a.disallow(b.getKickMessage());
            }
        } else if (e instanceof PlayerJoinEvent) {
            PlayerJoinEvent a = (PlayerJoinEvent) e;
            net.thewellodev.api227.event.player.PlayerJoinEvent b = new net.thewellodev.api227.event.player.PlayerJoinEvent(
                    api.player(a.getPlayer()), a.getJoinMessage());
            plugins.dispatch(b);
            a.setJoinMessage(b.getJoinMessage());
        } else if (e instanceof PlayerQuitEvent) {
            PlayerQuitEvent a = (PlayerQuitEvent) e;
            net.thewellodev.api227.event.player.PlayerQuitEvent b = new net.thewellodev.api227.event.player.PlayerQuitEvent(
                    api.player(a.getPlayer()), a.getQuitMessage());
            plugins.dispatch(b);
            a.setQuitMessage(b.getQuitMessage());
            api.forget(a.getPlayer());
        } else if (e instanceof PlayerKickEvent) {
            PlayerKickEvent a = (PlayerKickEvent) e;
            net.thewellodev.api227.event.player.PlayerKickEvent b = new net.thewellodev.api227.event.player.PlayerKickEvent(
                    api.player(a.getPlayer()), a.getReason(), a.getLeaveMessage());
            b.setCancelled(a.isCancelled());
            plugins.dispatch(b);
            a.setReason(b.getReason());
            a.setLeaveMessage(b.getLeaveMessage());
            a.setCancelled(b.isCancelled());
        } else if (e instanceof PlayerChatEvent) {
            PlayerChatEvent a = (PlayerChatEvent) e;
            net.thewellodev.api227.event.player.PlayerChatEvent b = new net.thewellodev.api227.event.player.PlayerChatEvent(
                    api.player(a.getPlayer()), a.getMessage());
            b.setFormat(a.getFormat());
            b.setCancelled(a.isCancelled());
            plugins.dispatch(b);
            a.setMessage(b.getMessage());
            a.setFormat(b.getFormat());
            a.setCancelled(b.isCancelled());
        } else if (e instanceof PlayerCommandPreprocessEvent) {
            PlayerCommandPreprocessEvent a = (PlayerCommandPreprocessEvent) e;
            net.thewellodev.api227.event.player.PlayerCommandPreprocessEvent b =
                    new net.thewellodev.api227.event.player.PlayerCommandPreprocessEvent(api.player(a.getPlayer()),
                            a.getMessage());
            b.setCancelled(a.isCancelled());
            plugins.dispatch(b);
            a.setMessage(b.getMessage());
            a.setCancelled(b.isCancelled());
        } else if (e instanceof PlayerMoveEvent) {
            PlayerMoveEvent a = (PlayerMoveEvent) e;
            Location227 to = api.location(a.getTo());
            net.thewellodev.api227.event.player.PlayerMoveEvent b = new net.thewellodev.api227.event.player.PlayerMoveEvent(
                    api.player(a.getPlayer()), api.location(a.getFrom()), to);
            b.setCancelled(a.isCancelled());
            plugins.dispatch(b);
            a.setCancelled(b.isCancelled());
            Location227 after = b.getTo();
            if (after != null && (after != to || !sameSpot(after, a.getTo()))) {
                a.setTo(api.toServer(after));
            }
        } else if (e instanceof PlayerAttackEvent) {
            PlayerAttackEvent a = (PlayerAttackEvent) e;
            if (a.isMob() || a.getTarget() == null) {
                return; // no mobs in this build
            }
            net.thewellodev.api227.event.player.PlayerAttackEvent b = new net.thewellodev.api227.event.player.PlayerAttackEvent(
                    api.player(a.getPlayer()), api.player(a.getTarget()), a.getDistanceSq(), a.getDamage(),
                    a.getHeldItem());
            b.setCancelled(a.isCancelled());
            plugins.dispatch(b);
            a.setCancelled(b.isCancelled());
        } else if (e instanceof PlayerDamageByPlayerEvent) {
            PlayerDamageByPlayerEvent a = (PlayerDamageByPlayerEvent) e;
            net.thewellodev.api227.event.player.PlayerDamageByPlayerEvent b =
                    new net.thewellodev.api227.event.player.PlayerDamageByPlayerEvent(api.player(a.getPlayer()),
                            api.player(a.getDamager()), a.getDamage());
            b.setCancelled(a.isCancelled());
            plugins.dispatch(b);
            a.setDamage(b.getDamage());
            a.setCancelled(b.isCancelled());
        } else if (e instanceof PlayerDeathEvent) {
            PlayerDeathEvent a = (PlayerDeathEvent) e;
            net.thewellodev.api227.event.player.PlayerDeathEvent b = new net.thewellodev.api227.event.player.PlayerDeathEvent(
                    api.player(a.getPlayer()), api.player(a.getKiller()), a.getDeathMessage());
            plugins.dispatch(b);
            a.setDeathMessage(b.getDeathMessage());
        } else if (e instanceof PlayerRespawnEvent) {
            PlayerRespawnEvent a = (PlayerRespawnEvent) e;
            net.thewellodev.api227.event.player.PlayerRespawnEvent b = new net.thewellodev.api227.event.player.PlayerRespawnEvent(
                    api.player(a.getPlayer()), api.location(a.getRespawnLocation()));
            plugins.dispatch(b);
            a.setRespawnLocation(api.toServer(b.getRespawnLocation()));
        } else if (e instanceof BlockBreakEvent) {
            BlockBreakEvent a = (BlockBreakEvent) e;
            net.thewellodev.api227.event.block.BlockBreakEvent b = new net.thewellodev.api227.event.block.BlockBreakEvent(
                    api.block(a.getBlock()), api.player(a.getPlayer()));
            b.setCancelled(a.isCancelled());
            plugins.dispatch(b);
            a.setCancelled(b.isCancelled());
        } else if (e instanceof BlockPlaceEvent) {
            BlockPlaceEvent a = (BlockPlaceEvent) e;
            net.thewellodev.api227.event.block.BlockPlaceEvent b = new net.thewellodev.api227.event.block.BlockPlaceEvent(
                    api.block(a.getBlock()), api.player(a.getPlayer()), a.getNewTypeId());
            b.setCancelled(a.isCancelled());
            plugins.dispatch(b);
            a.setCancelled(b.isCancelled());
        }
    }

    private static boolean sameSpot(Location227 a, Location b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ() && a.getYaw() == b.getYaw()
                && a.getPitch() == b.getPitch();
    }

    @Override
    public net.thewellodev.api.command.CommandSender wrapSender(net.thewellodev.api.command.CommandSender sender) {
        return sender instanceof Player ? api.player((Player) sender) : sender;
    }

    /** For callers that hold a plain player: the API's view of it. */
    public Player227 view(Player p) {
        return api.player(p);
    }
}
