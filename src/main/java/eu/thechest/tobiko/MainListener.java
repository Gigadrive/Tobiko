package eu.thechest.tobiko;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.event.FinalMapLoadedEvent;
import eu.thechest.chestapi.event.PlayerDataLoadedEvent;
import eu.thechest.chestapi.event.PlayerLandOnGroundEvent;
import eu.thechest.chestapi.event.VotingEndEvent;
import eu.thechest.chestapi.game.Game;
import eu.thechest.chestapi.game.GameManager;
import eu.thechest.chestapi.items.ItemUtil;
import eu.thechest.chestapi.maps.MapLocationData;
import eu.thechest.chestapi.maps.MapLocationType;
import eu.thechest.chestapi.maps.MapRatingManager;
import eu.thechest.chestapi.maps.MapVotingManager;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.BountifulAPI;
import eu.thechest.chestapi.util.ParticleEffect;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.tobiko.user.TobikoPlayer;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;

public class MainListener implements Listener {
    public static int y = 20;

    @EventHandler
    public void onLogin(PlayerLoginEvent e){
        Player p = e.getPlayer();

        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.ENDING){
            e.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            e.setKickMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "The game is currently ending.");
        } else if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.WARMUP){
            e.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            e.setKickMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "Warmup has already started.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();
    }

    @EventHandler
    public void onLoaded(PlayerDataLoadedEvent e){
        Player p = e.getPlayer();

        ChestAPI.async(() -> {
            ChestUser u = ChestUser.getUser(p);
            TobikoPlayer t = TobikoPlayer.get(p);

            t.achievementCheck();

            if(ServerSettingsManager.CURRENT_GAMESTATE != GameState.LOBBY){
                Tobiko.spectate(p);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        TobikoPlayer t = TobikoPlayer.get(p);

        t.saveData();

        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.LOBBY){
            if(Tobiko.TOBIKO == p) Tobiko.TOBIKO = null;
        } else if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.WARMUP || ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
            if(t.isTobiko()){
                Tobiko.endGame(false);
            } else {
                Tobiko.death(p);
            }
        }
    }

    @EventHandler
    public void onLoad(WorldLoadEvent e){
        Tobiko.prepareWorld(e.getWorld());
    }

    @EventHandler
    public void onChange(WeatherChangeEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        TobikoPlayer t = TobikoPlayer.get(p);

        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME || ServerSettingsManager.CURRENT_GAMESTATE == GameState.WARMUP || ServerSettingsManager.CURRENT_GAMESTATE == GameState.ENDING){
            if(t.isSpectator()){
                GameManager.getCurrentGames().get(0).addSpectatorChatEvent(p,e.getMessage());
            } else {
                GameManager.getCurrentGames().get(0).addPlayerChatEvent(p,e.getMessage());
            }

            if(t.isSpectator() && ServerSettingsManager.CURRENT_GAMESTATE != GameState.ENDING){
                e.setCancelled(true);
                for(Player s : Tobiko.SPECTATORS){
                    if(ChestUser.getUser(s).hasPermission(Rank.VIP)){
                        s.sendMessage(ChatColor.AQUA + "[SPECTATOR] " + ChatColor.GRAY + p.getName() + ": " + ChatColor.WHITE + e.getMessage());
                    } else {
                        s.sendMessage(ChatColor.AQUA + "[SPECTATOR] " + ChatColor.GRAY + ChatColor.stripColor(p.getDisplayName()) + ": " + ChatColor.WHITE + e.getMessage());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        TobikoPlayer t = TobikoPlayer.get(p);

        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.WARMUP){
            Location from = e.getFrom();
            Location to = e.getTo();
            double x = Math.floor(from.getX());
            double z = Math.floor(from.getZ());

            if(Math.floor(to.getX()) != x || Math.floor(to.getZ()) != z){
                x += .5;
                z += .5;
                e.getPlayer().teleport(new Location(from.getWorld(),x,from.getY(),z,from.getYaw(),from.getPitch()));
            }
        } else if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
            if(p.getLocation().getY() <= 0){
                if(t.isTobiko()){
                    p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,0.5f);
                    p.setVelocity(new Vector(0,5,0));
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You can't jump into the void."));
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Right-click with your gun to kill the survivors!"));
                    return;
                } else if(t.isSurvivor()){
                    Tobiko.death(p);
                    return;
                } else if(t.isSpectator()){
                    p.teleport(Tobiko.MAP.getLocations(MapLocationType.TK_TOBIKO).get(0).toBukkitLocation(Tobiko.MAP_WORLDNAME));
                    return;
                }
            } else if(p.getLocation().getY() >= 140){
                if(t.isTobiko()){
                    p.setVelocity(new Vector(0,-3,0));
                    p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,0.5f);
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You can't go above the height limit."));
                    return;
                }
            }

            if(t.isTobiko()){
                if(p.getLocation().distance(Tobiko.MAP.getLocations(MapLocationType.TK_TOBIKO).get(0).toBukkitLocation(Tobiko.MAP_WORLDNAME)) >= 100){
                    p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,0.5f);
                    p.setVelocity(p.getLocation().toVector().subtract(Tobiko.MAP.getLocations(MapLocationType.TK_TOBIKO).get(0).toBukkitLocation(Tobiko.MAP_WORLDNAME).toVector()).multiply(-3));
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You can't leave the arena."));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        final Player p = e.getPlayer();
        final ChestUser u = ChestUser.getUser(p);
        final TobikoPlayer t = TobikoPlayer.get(p);

        if(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK){
            if(p.getItemInHand() != null){
                if(t.isTobiko() && ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
                    if(p.getItemInHand().getType() == Material.DIAMOND_HOE){
                        for(int i : Tobiko.AMMO_REFILL_SCHEDULERS) Bukkit.getScheduler().cancelTask(i);
                        Tobiko.AMMO_REFILL_SCHEDULERS.clear();

                        if(Tobiko.AMMO_LEFT == 0){
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You are out of ammo!"));
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Wait a little while to reload your gun."));
                            p.playSound(p.getEyeLocation(),Sound.ANVIL_LAND,1f,1f);
                        } else {
                            SmallFireball fireball = (SmallFireball)p.launchProjectile(SmallFireball.class);
                            fireball.setFireTicks(5*20);
                            fireball.setVelocity(fireball.getVelocity().multiply(6));

                            if(!Tobiko.RAPID_FIRE){
                                p.setVelocity(p.getLocation().getDirection().multiply(-0.5));

                                Tobiko.AMMO_LEFT--;
                            }
                        }

                        t.updateHUD();

                        if(!Tobiko.RAPID_FIRE){
                            Tobiko.AMMO_REFILL_SCHEDULERS.add(Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
                                public void run() {
                                    BukkitTask task = new BukkitRunnable(){
                                        public void run() {
                                            if(Tobiko.AMMO_LEFT == 20){
                                                cancel();
                                                Tobiko.AMMO_REFILL_SCHEDULERS.remove(Integer.valueOf(getTaskId()));
                                            } else {
                                                Tobiko.AMMO_LEFT++;
                                                t.updateHUD();
                                            }
                                        }
                                    }.runTaskTimer(Tobiko.getInstance(),2,5);

                                    Tobiko.AMMO_REFILL_SCHEDULERS.add(task.getTaskId());
                                }
                            },10));
                        }
                    } else if(p.getItemInHand().getType() == Material.BLAZE_POWDER){
                        if(u.hasGamePerk(5)){
                            if(!Tobiko.RAPID_FIRE){
                                e.setCancelled(true);
                                e.setUseInteractedBlock(Event.Result.DENY);
                                e.setUseItemInHand(Event.Result.DENY);

                                if(p.getItemInHand().getAmount() == 1){
                                    p.setItemInHand(new ItemStack(Material.AIR));
                                } else {
                                    p.getItemInHand().setAmount(p.getItemInHand().getAmount()-1);
                                }

                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Rapid Fire enabled!"));
                                Tobiko.RAPID_FIRE = true;

                                for(Player all : Bukkit.getOnlinePlayers()){
                                    ChestUser a = ChestUser.getUser(all);
                                    TobikoPlayer ta = TobikoPlayer.get(all);

                                    if(!ta.isTobiko()){
                                        BountifulAPI.sendTitle(all,10,2*20,10,ChatColor.RED.toString() + a.getTranslatedMessage("BEWARE!"),ChatColor.GRAY + u.getTranslatedMessage("The Tobiko activated the Rapid Fire powerup!"));
                                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The Tobiko activated the Rapid Fire powerup!"));
                                    }
                                }

                                for(int i : Tobiko.AMMO_REFILL_SCHEDULERS) Bukkit.getScheduler().cancelTask(i);
                                Tobiko.AMMO_REFILL_SCHEDULERS.clear();

                                Tobiko.AMMO_LEFT = Tobiko.MAX_AMMO;
                                t.updateHUD();

                                Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
                                    public void run() {
                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Rapid Fire disabled!"));
                                        Tobiko.RAPID_FIRE = false;

                                        for(Player all : Bukkit.getOnlinePlayers()){
                                            ChestUser a = ChestUser.getUser(all);
                                            TobikoPlayer ta = TobikoPlayer.get(all);

                                            if(!ta.isTobiko()){
                                                all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("The Tobiko's Rapid Fire powerup has worn off!"));
                                            }
                                        }
                                    }
                                },5*20);
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e){
        Projectile pr = e.getEntity();
        pr.remove();

        if(pr.getType() == EntityType.SMALL_FIREBALL){
            //pr.getLocation().getWorld().createExplosion(pr.getLocation(),3.5f,false);
            pr.getLocation().getWorld().playSound(pr.getLocation(),Sound.EXPLODE,1f,1f);
            ParticleEffect.EXPLOSION_LARGE.display(0f,0f,0f,0.01f,1,pr.getLocation(),100);

            for(Location loc : ChestAPI.getBlocksInRadius(pr.getLocation(),1)){
                if(StringUtils.getChanceBoolean(80,25)){
                    loc.getBlock().setType(Material.AIR);
                }
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e){
        if(e.getCause() != EntityDamageEvent.DamageCause.PROJECTILE && e.getCause() != EntityDamageEvent.DamageCause.CUSTOM) e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e){
        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
            if(e.getEntity() instanceof Player && e.getDamager() instanceof Arrow && ((Arrow)e.getDamager()).getShooter() instanceof Player){
                Player p = (Player)e.getEntity();
                ChestUser u = ChestUser.getUser(p);
                TobikoPlayer t = TobikoPlayer.get(p);

                Player p2 = ((Player)((Arrow)e.getDamager()).getShooter());
                ChestUser u2 = ChestUser.getUser(p2);
                TobikoPlayer t2 = TobikoPlayer.get(p2);

                if(t.isTobiko()){
                    e.setDamage(0);
                    e.getDamager().remove();

                    t2.addHits(1);
                    u2.addCoins(2);
                    u2.achieve(53);
                    t2.addPoints(7);

                    GameManager.getCurrentGames().get(0).addTobikoHitEvent(p2);

                    p.getWorld().playSound(p.getEyeLocation(),Sound.IRONGOLEM_HIT,1f,1f);

                    Tobiko.TOBIKO_HEALTH--;

                    for(Player all : Bukkit.getOnlinePlayers()){
                        ChestUser a = ChestUser.getUser(all);
                        TobikoPlayer ta = TobikoPlayer.get(all);
                        ta.updateScoreboard();

                        if(a.hasPermission(Rank.VIP)){
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p has landed a hit on the Tobiko!").replace("%p",u2.getRank().getColor() + p2.getName() + ChatColor.GOLD));
                        } else {
                            all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p has landed a hit on the Tobiko!").replace("%p",p2.getDisplayName() + ChatColor.GOLD));
                        }
                    }

                    if(Tobiko.TOBIKO_HEALTH == 0){
                        t2.addFinalHits(1);
                        t2.addPoints(12);
                        u2.addCoins(18);
                        u2.achieve(54);
                        Tobiko.endGame(false);
                    }
                } else {
                    e.setCancelled(true);
                }
            } else {
                e.setCancelled(true);
            }
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e){
        e.setCancelled(true);
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);
        TobikoPlayer t = TobikoPlayer.get(p);

        if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
            if(t.isSurvivor()){
                if(!p.isFlying()){
                    e.setCancelled(true);
                    p.setFlying(false);
                    p.setVelocity(p.getLocation().getDirection().multiply(1.5).setY(1));

                    p.setAllowFlight(false);
                    Tobiko.DOUBLEJUMPCOOLDOWN.add(p);
                }
            }
        }
    }

    @EventHandler
    public void onLand(PlayerLandOnGroundEvent e){
        Player p = e.getPlayer();

        if(Tobiko.DOUBLEJUMPCOOLDOWN.contains(p)){
            p.setAllowFlight(true);
            Tobiko.DOUBLEJUMPCOOLDOWN.remove(p);
        }
    }

    @EventHandler
    public void onLoad(FinalMapLoadedEvent e){
        Tobiko.MAP_WORLDNAME = e.getWorld().getName();
        Tobiko.prepareWorld(e.getWorld());
    }

    @EventHandler
    public void onMapVotingFinish(VotingEndEvent e) {
        ServerSettingsManager.VIP_JOIN = false;
        ServerSettingsManager.updateGameState(GameState.WARMUP);
        Tobiko.MAP = e.getFinalMap();
        MapRatingManager.MAP_TO_RATE = e.getFinalMap();

        ArrayList<Player> a = new ArrayList<Player>();
        a.addAll(Bukkit.getOnlinePlayers());
        Collections.shuffle(a);

        ArrayList<MapLocationData> s = new ArrayList<MapLocationData>();
        s.addAll(Tobiko.MAP.getLocations(MapLocationType.TK_SURVIVOR));
        Collections.shuffle(s);

        Game g = GameManager.initializeNewGame(GameType.TOBIKO,e.getFinalMap());

        for(Player p : a){
            ChestUser u = ChestUser.getUser(p);
            TobikoPlayer t = TobikoPlayer.get(p);
            boolean tobiko;
            tobiko = Tobiko.TOBIKO == null || (Tobiko.TOBIKO != null && Tobiko.TOBIKO == p);

            u.bukkitReset();
            u.clearScoreboard();
            Tobiko.MAP.sendMapCredits(p);
            g.getParticipants().add(p.getUniqueId());

            t.addPlayedGames(1);

            if(tobiko){
                Tobiko.TOBIKO = p;
                u.achieve(55);

                p.teleport(Tobiko.MAP.getLocations(MapLocationType.TK_TOBIKO).get(0).toBukkitLocation(Tobiko.MAP_WORLDNAME));
            } else {
                MapLocationData spawnpoint = s.get(0);
                s.remove(spawnpoint);
                Tobiko.SURVIVORS.add(p);
                t.ogSurvivor = true;

                p.teleport(spawnpoint.toBukkitLocation(Tobiko.MAP_WORLDNAME));
            }
        }

        Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
            public void run() {
                for(Player p : Bukkit.getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);
                    TobikoPlayer t = TobikoPlayer.get(p);

                    if(t.isTobiko()){
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + u.getTranslatedMessage("You are the Tobiko!"),ChatColor.GRAY + u.getTranslatedMessage("Kill the survivors with your gun!"));
                        p.playSound(p.getEyeLocation(), Sound.CLICK,1f,1f);
                    } else if(t.isSurvivor()){
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.AQUA.toString() + u.getTranslatedMessage("You are a Survivor!"),ChatColor.GRAY + u.getTranslatedMessage("Shoot the Tobiko with your bow!"));
                        p.playSound(p.getEyeLocation(), Sound.CLICK,1f,1f);
                    }
                }
            }
        },2*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
            public void run() {
                for(Player p : Bukkit.getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);
                    TobikoPlayer t = TobikoPlayer.get(p);

                    if(t.isTobiko()){
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + u.getTranslatedMessage("Don't die!"),ChatColor.GRAY + u.getTranslatedMessage("If the survivors hit you 20 times, it's over!"));
                        p.playSound(p.getEyeLocation(), Sound.CLICK,1f,1f);
                    } else if(t.isSurvivor()){
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.AQUA.toString() + u.getTranslatedMessage("Kill him!"),ChatColor.GRAY + u.getTranslatedMessage("If you hit the Tobiko 20 times, it's over!"));
                        p.playSound(p.getEyeLocation(), Sound.CLICK,1f,1f);
                    }
                }
            }
        },9*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
            public void run() {
                for(Player p : Bukkit.getOnlinePlayers()){
                    ChestUser u = ChestUser.getUser(p);
                    TobikoPlayer t = TobikoPlayer.get(p);

                    if(t.isTobiko()){
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + u.getTranslatedMessage("Kill the survivors!"),ChatColor.GRAY + u.getTranslatedMessage("If you kill the survivors before you die, you win!"));
                        p.playSound(p.getEyeLocation(), Sound.CLICK,1f,1f);
                    } else if(t.isSurvivor()){
                        BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.AQUA.toString() + u.getTranslatedMessage("Don't die!"),ChatColor.GRAY + u.getTranslatedMessage("If you fall into the void, you're out!"));
                        p.playSound(p.getEyeLocation(), Sound.CLICK,1f,1f);
                    }
                }
            }
        },16*20);

        new BukkitRunnable(){
            public void run() {
                if(y == 20){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.DARK_GREEN.toString() + y,"");
                        p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                    }
                } else if(y == 10){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.DARK_GREEN.toString() + y,"");
                        p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                    }
                } else if(y == 5){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.GREEN.toString() + y,"");
                        p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                    }
                } else if(y == 4){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.YELLOW.toString() + y,"");
                        p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                    }
                } else if(y == 3){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.GOLD.toString() + y,"");
                        p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                    }
                } else if(y == 2){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.RED.toString() + y,"");
                        p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                    }
                } else if(y == 1){
                    for(Player p : Bukkit.getOnlinePlayers()){
                        BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.DARK_RED.toString() + y,"");
                        p.playSound(p.getEyeLocation(),Sound.NOTE_BASS,1f,1f);
                    }
                } else if(y == 0){
                    Tobiko.startEndScheduler();
                    Tobiko.startTobikoAuraScheduler();

                    for(final Player p : Bukkit.getOnlinePlayers()){
                        ChestUser u = ChestUser.getUser(p);
                        TobikoPlayer t = TobikoPlayer.get(p);

                        BountifulAPI.sendTitle(p,0,4*20,1*20,ChatColor.DARK_GREEN.toString() + u.getTranslatedMessage("GO!"),"");
                        p.playSound(p.getEyeLocation(),Sound.NOTE_PLING,1f,1f);
                        t.updateScoreboard();
                        Tobiko.MAP.sendRateMapInfo(p);

                        if(t.isTobiko()){
                            u.bukkitReset();
                            p.setAllowFlight(true);
                            p.setVelocity(new Vector(0,1.5,0));

                            Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
                                public void run() {
                                    p.setFlying(true);
                                }
                            },20);

                            p.getInventory().setItem(0, ItemUtil.addGlow(ItemUtil.hideFlags(ItemUtil.setUnbreakable(ItemUtil.namedItem(Material.DIAMOND_HOE,ChatColor.GREEN.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Tobiko Gun"),null),true))));
                            if(u.hasGamePerk(5)) p.getInventory().setItem(1,ItemUtil.namedItem(Material.BLAZE_POWDER,ChatColor.GOLD + u.getTranslatedMessage("Rapid Fire"),null));

                            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true, true));
                        } else if(t.isSurvivor()){
                            u.bukkitReset();
                            p.setAllowFlight(true);

                            ItemStack bow = ItemUtil.hideFlags(ItemUtil.setUnbreakable(ItemUtil.namedItem(Material.BOW,ChatColor.RED + u.getTranslatedMessage("Bow"),null),true));
                            bow.addEnchantment(Enchantment.ARROW_INFINITE,1);

                            p.getInventory().setItem(0,bow);
                            p.getInventory().setItem(9,new ItemStack(Material.ARROW));

                            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, true, true));
                            //p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 2, true, true));
                        }
                    }

                    ServerSettingsManager.updateGameState(GameState.INGAME);

                    cancel();
                    return;
                }

                y--;
            }
        }.runTaskTimer(Tobiko.getInstance(),23*20,20);
    }
}
