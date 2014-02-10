package info.bytecraft.listener;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import info.bytecraft.Bytecraft;
import info.bytecraft.api.BooleanStringReturn;
import info.bytecraft.api.BytecraftPlayer;
import info.bytecraft.api.event.PlayerChangeZoneEvent;
import info.bytecraft.api.math.Point;
import info.bytecraft.zones.Zone;
import info.bytecraft.zones.Zone.Flag;
import info.bytecraft.zones.Zone.Permission;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import static org.bukkit.entity.EntityType.*;

import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

public class ZoneListener implements Listener
{

    private Bytecraft plugin;

    public ZoneListener(Bytecraft instance)
    {
        plugin = instance;
    }
    
    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        BytecraftPlayer player = plugin.getPlayer(event.getPlayer());
        if(player == null)return;
        
        Location l = player.getLocation();
        
        Zone zone = plugin.getZoneAt(l.getWorld(), new Point(l.getBlockX(), l.getBlockZ()));
        
        if(zone == null)return;
        
        player.setCurrentZone(zone);
        this.welcomeMessage(zone, player, zone.getUser(player));
    }
    
    @EventHandler
    public void onMoveZone(PlayerChangeZoneEvent event)
    {
        BytecraftPlayer player = event.getPlayer();
        if(event.getOldZone() != null){
           
            Zone zone = event.getOldZone();
            
            if(zone.hasFlag(Flag.CREATIVE)){
                if(!player.getRank().canKeepItems()){
                    player.getInventory().clear();
                    player.updateInventory();
                    player.setGameMode(GameMode.SURVIVAL);
                }
                player.sendMessage(ChatColor.RED + "[" + event.getOldZone().getName()
                        + "] " + event.getOldZone().getExitMessage());
            }
            
        }
        
        if(event.getNewZone() == null){
            player.setCurrentZone(null);
            return;
        }
        
        BooleanStringReturn returnValue = player.canBeHere(event.getTo());
        
        if (!returnValue.getBoolean()) {
            player.sendMessage(returnValue.getString());
            movePlayerBack(player, event.getFrom(), event.getTo());
            return;
        }
        
        Zone zone = event.getNewZone();
        
        player.setCurrentZone(zone);//Keeping this here as there is currently one reference to it
        welcomeMessage(zone, player, zone.getUser(player));
        
        if(zone.hasFlag(Flag.CREATIVE)){
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(ChatColor.RED + "[" + zone.getName() + "] This is a creative zone. "
                    + "You will lose all of your inventory when you leave");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event)
    {
        BytecraftPlayer player = plugin.getPlayer(event.getPlayer());
        Block block = event.getBlock();
        
        boolean bool = player.hasBlockPermission(block.getLocation(), true);
        if(!bool){
            player.setFireTicks(20 * 2);
            event.setBuild(false);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event)
    {
        BytecraftPlayer player = plugin.getPlayer(event.getPlayer());
        Block block = event.getBlock();
        
        boolean bool = player.hasBlockPermission(block.getLocation(), true);
        if(!bool){
            player.setFireTicks(20 * 2);
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onDrop(PlayerDropItemEvent event)
    {
        BytecraftPlayer player = plugin.getPlayer(event.getPlayer());
        Location loc = player.getLocation();
        Point p = new Point(loc.getBlockX(), loc.getBlockZ());
        
        Zone zone = plugin.getZoneAt(loc.getWorld(), p);
        if(zone == null)return;
        
        if(zone.hasFlag(Flag.CREATIVE)){
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You can't drop items in a creative zone.");
        }
    }
    
    @EventHandler
    public void onEmpty(PlayerBucketEvent event)
    {
        BytecraftPlayer player = plugin.getPlayer(event.getPlayer());
        Block block = event.getBlockClicked();
        
        boolean bool = player.hasBlockPermission(block.getLocation(), true);
        if(!bool){
            player.setFireTicks(20 * 2);
            event.setCancelled(true);
        }
    }

    public void permissionsMessage(Zone zone, BytecraftPlayer player)
    {
        String prefix = ChatColor.RED + "[" + zone.getName() + "] ";
        String message = null;
        Permission perm = zone.getUser(player);
        if(perm != null){
            switch(perm){
            case ALLOWED: message = prefix + "You are allowed in " + zone.getName();
            break;
            case BANNED: message = prefix + "You are banned from " + zone.getName();
            break;
            case OWNER: message = prefix + "You are an owner in " + zone.getName();
            break;
            case MAKER: message = prefix + "You are a maker in " + zone.getName();
            break;
            default: break;
            }
            if(message != null && !message.equalsIgnoreCase("")){
                player.sendMessage(message);
            }
        }
    }

    private final Set<EntityType> types = EnumSet.of(BLAZE, CREEPER, ENDERMAN, ENDER_DRAGON, CAVE_SPIDER, MAGMA_CUBE, SLIME,
            SILVERFISH, SKELETON, SPIDER, WITCH, WITHER, ZOMBIE);

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event)
    {
        Entity ent = event.getEntity();
        List<Zone> zones = plugin.getZones(ent.getWorld().getName());
        if(zones.isEmpty())return;
        for(Zone zone: zones){
            if(zone.contains(ent.getLocation()) && zone.getWorld().equalsIgnoreCase(ent.getWorld().getName())){
                if(!zone.hasFlag(Flag.HOSTILES)){
                    if(types.contains(ent.getType())){
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event)
    {
        Location to = event.getTo();
        Location from = event.getFrom();
        BytecraftPlayer player = plugin.getPlayer(event.getPlayer());
        List<Zone> zones = plugin.getZones(to.getWorld().getName());
        if(zones.isEmpty())return;
        for(Zone zone: zones){
            if(zone.contains(from) && !zone.contains(to)){
                player.sendMessage(ChatColor.RED + "[" + zone.getName() + "] " + zone.getExitMessage());
                player.setCurrentZone(null);
            }else if(zone.contains(to) && !zone.contains(from)){
                Permission p = zone.getUser(player);
                if(zone.hasFlag(Flag.WHITELIST)){
                    if((p == null || p == Permission.BANNED)){
                        if(!player.getRank().canEditZones()){
                            event.setCancelled(true);
                            player.sendMessage(ChatColor.RED + "[" + zone.getName() + "] You are not allowed in " + zone.getName());
                        }else{
                            player.sendMessage(ChatColor.RED + "[" + zone.getName() + "] " + zone.getEnterMessage());
                            this.permissionsMessage(zone, player);
                            player.setCurrentZone(zone);
                        }
                    }else{
                        player.sendMessage(ChatColor.RED + "[" + zone.getName() + "] " + zone.getEnterMessage());
                        this.permissionsMessage(zone, player);
                        player.setCurrentZone(zone);
                    }
                }else{
                    if((p != null && p == Permission.BANNED) && !player.getRank().canEditZones()){
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "[" + zone.getName() + "] You are not allowed in " + zone.getName());
                    }else{
                        player.sendMessage(ChatColor.RED + "[" + zone.getName() + "] " + zone.getEnterMessage());
                        this.permissionsMessage(zone, player);
                        player.setCurrentZone(zone);
                    }
                }
            }
        }
    }
    
    private void movePlayerBack(BytecraftPlayer player, Location movingFrom,
            Location movingTo)
    {
        Vector a = new Vector(movingFrom.getX(),
                              movingFrom.getY(),
                              movingFrom.getZ());

        Vector b = new Vector(movingTo.getX(),
                              movingTo.getY(),
                              movingTo.getZ());

        Vector diff = b.subtract(a);
        diff = diff.multiply(-5);

        Vector newPosVector = a.add(diff);

        Location newPos = new Location(player.getWorld(),
                                       newPosVector.getX(),
                                       newPosVector.getY(),
                                       newPosVector.getZ());

        player.teleport(newPos);
    }
    
    private void welcomeMessage(Zone currentZone, BytecraftPlayer player,
            Zone.Permission perm)
    {

        player.sendMessage(ChatColor.RED + "[" + currentZone.getName() + "] "
                + currentZone.getEnterMessage());

        if (currentZone.hasFlag(Flag.PVP)) {
            player.sendMessage(ChatColor.RED
                    + "[" + currentZone.getName() + "] "
                    + "Warning! This is a PVP zone! Other players can damage or kill you here.");
        }

        if (perm != null) {
            String permNotification = perm.getPermNotification();
            player.sendMessage(ChatColor.RED + "[" + currentZone.getName()
                    + "] " + permNotification);
        }
    }
}
