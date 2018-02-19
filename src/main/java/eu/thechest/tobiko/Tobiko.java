package eu.thechest.tobiko;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.game.Game;
import eu.thechest.chestapi.game.GameManager;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.maps.MapLocationType;
import eu.thechest.chestapi.maps.MapVotingManager;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.GameType;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.BountifulAPI;
import eu.thechest.tobiko.user.TobikoPlayer;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.UUID;

public class Tobiko extends JavaPlugin {
    private static Tobiko instance;

    public static Map MAP;
    public static String MAP_WORLDNAME;

    public static Player TOBIKO;
    public static int TOBIKO_HEALTH = 20;
    public static int TIME_LEFT = 300;

    public static int MAX_AMMO = 20;
    public static int AMMO_LEFT = MAX_AMMO;
    public static ArrayList<Integer> AMMO_REFILL_SCHEDULERS = new ArrayList<Integer>();

    public static ArrayList<Player> SURVIVORS;
    public static ArrayList<Player> SPECTATORS;

    public static ArrayList<Player> DOUBLEJUMPCOOLDOWN = new ArrayList<Player>();

    public static boolean RAPID_FIRE = false;

    public void onEnable(){
        instance = this;
        ServerSettingsManager.ADJUST_CHAT_FORMAT = true;
        ServerSettingsManager.ARROW_TRAILS = true;
        ServerSettingsManager.KILL_EFFECTS = false;
        ServerSettingsManager.AUTO_OP = true;
        ServerSettingsManager.ENABLE_CHAT = true;
        ServerSettingsManager.ENABLE_NICK = true;
        ServerSettingsManager.MAP_VOTING = true;
        ServerSettingsManager.PROTECT_ARMORSTANDS = true;
        ServerSettingsManager.PROTECT_FARMS = true;
        ServerSettingsManager.PROTECT_ITEM_FRAMES = true;
        ServerSettingsManager.RUNNING_GAME = GameType.TOBIKO;
        ServerSettingsManager.SHOW_FAME_TITLE_ABOVE_HEAD = false;
        ServerSettingsManager.SHOW_LEVEL_IN_EXP_BAR = false;
        ServerSettingsManager.VIP_JOIN = true;
        ServerSettingsManager.MIN_PLAYERS = 4;
        ServerSettingsManager.updateGameState(GameState.LOBBY);
        ServerSettingsManager.setMaxPlayers(13);

        MapVotingManager.chooseMapsForVoting();

        for(World w : Bukkit.getWorlds()) prepareWorld(w);

        SURVIVORS = new ArrayList<Player>();
        SPECTATORS = new ArrayList<Player>();

        MainExecutor exec = new MainExecutor();
        getCommand("tobiko").setExecutor(exec);

        Bukkit.getPluginManager().registerEvents(new MainListener(),this);
    }

    public void onDisable(){
        for(Player all : Bukkit.getOnlinePlayers()) TobikoPlayer.unregister(all);
    }

    public static void spectate(Player p){
        if(SURVIVORS.contains(p)) SURVIVORS.remove(p);
        if(!SPECTATORS.contains(p)) SPECTATORS.add(p);

        ChestUser.getUser(p).bukkitReset();
        ChestUser.getUser(p).mayChat = false;
        p.setGameMode(GameMode.SPECTATOR);
        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(p).getTranslatedMessage("You are now a spectator."));

        p.teleport(Tobiko.MAP.getLocations(MapLocationType.TK_TOBIKO).get(0).toBukkitLocation(MAP_WORLDNAME));
    }

    public static void startEndScheduler(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Tobiko.getInstance(), new Runnable() {
            public void run() {

                if(TIME_LEFT == 240 || TIME_LEFT == 180 || TIME_LEFT == 120 || TIME_LEFT == 60 || TIME_LEFT == 30 || TIME_LEFT == 20 || TIME_LEFT == 10 || TIME_LEFT == 5 || TIME_LEFT == 4 || TIME_LEFT == 3 || TIME_LEFT == 2){
                    for(Player all : Bukkit.getOnlinePlayers()){
                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("The game ends in %s seconds!").replace("%s",ChatColor.AQUA.toString() + TIME_LEFT + ChatColor.GOLD.toString()));
                    }
                } else if(TIME_LEFT == 1){
                    for(Player all : Bukkit.getOnlinePlayers()){
                        all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + ChestUser.getUser(all).getTranslatedMessage("The game ends in %s second!").replace("%s",ChatColor.AQUA.toString() + TIME_LEFT + ChatColor.GOLD.toString()));
                    }
                } else if(TIME_LEFT == 0){
                    Tobiko.endGame(false);
                    return;
                }

                TIME_LEFT--;

                for(Player all : Bukkit.getOnlinePlayers()) TobikoPlayer.get(all).updateScoreboard(true);
            }
        }, 20L,20L);
    }

    public static void startTobikoAuraScheduler(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Tobiko.getInstance(), new Runnable() {
            public void run() {
                Player p = TOBIKO;

                if(p != null){
                    p.getWorld().playEffect(p.getLocation(),Effect.WITCH_MAGIC, 10);
                    p.getWorld().playEffect(p.getLocation(),Effect.COLOURED_DUST, 10);
                    p.getWorld().playEffect(p.getLocation(),Effect.SPELL, 5);
                }
            }
        }, 1L, 1L);
    }

    public static void prepareWorld(World w){
        w.setStorm(false);
        w.setThundering(false);

        w.setTime(5000);

        w.setGameRuleValue("doMobSpawning","false");
        w.setGameRuleValue("doMobGriefing","false");
        w.setGameRuleValue("doDaylightCycle","false");
        w.setGameRuleValue("doFireTick","false");

        w.getWorldBorder().reset();

        for(Entity e : w.getEntities()) if(e.getType() != EntityType.PLAYER && e.getType() != EntityType.ARMOR_STAND && e.getType() != EntityType.PAINTING && e.getType() != EntityType.ITEM_FRAME) e.remove();
    }

    public static void death(Player p){
        ChestUser u = ChestUser.getUser(p);
        TobikoPlayer t = TobikoPlayer.get(p);
        final Game g = GameManager.getCurrentGames().get(0);

        if(SURVIVORS.contains(p)){
            if(TOBIKO != null){
                TobikoPlayer.get(TOBIKO).addKills(1);
                TobikoPlayer.get(TOBIKO).addPoints(16);
                TobikoPlayer.get(TOBIKO).getUser().addCoins(2);
            }
            spectate(p);

            p.setVelocity(new Vector(0,0,0));
            g.addPlayerDeathEvent(p);
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 2*20, 1, true, true));
            p.playSound(p.getEyeLocation(),Sound.IRONGOLEM_DEATH,1f,1f);

            BountifulAPI.sendTitle(p,0,2*20,0,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("YOU DIED!"),ChatColor.GRAY + u.getTranslatedMessage("You are now a spectator."));

            for(Player all : Bukkit.getOnlinePlayers()){
                if(!ChestUser.isLoaded(all)) continue;

                ChestUser a = ChestUser.getUser(all);
                if(a.hasPermission(Rank.VIP)){
                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p was %e").replace("%p",u.getRank().getColor() + p.getName() + ChatColor.GOLD).replace("%e",ChatColor.DARK_RED.toString() + ChatColor.BOLD.toString() + a.getTranslatedMessage("ELIMINATED") + ChatColor.GOLD));
                } else {
                    all.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + a.getTranslatedMessage("%p was %e").replace("%p",p.getDisplayName() + ChatColor.GOLD).replace("%e",ChatColor.DARK_RED.toString() + ChatColor.BOLD.toString() + a.getTranslatedMessage("ELIMINATED") + ChatColor.GOLD));
                }
            }

            if(SURVIVORS.size() == 0){
                endGame(true);
            }
        }
    }

    public static void endGame(boolean tobikoWins){
        Bukkit.getScheduler().cancelAllTasks();

        ArrayList<Player> eligible = new ArrayList<Player>();
        ServerSettingsManager.updateGameState(GameState.ENDING);

        final Game g = GameManager.getCurrentGames().get(0);
        g.setCompleted(true);

        if(tobikoWins){
            for(UUID uuid : GameManager.getCurrentGames().get(0).getParticipants()){
                if(TOBIKO.getUniqueId().toString().equals(uuid.toString())){
                    GameManager.getCurrentGames().get(0).getWinners().add(uuid);
                }
            }

            g.addTobikoWinEvent(TOBIKO);
        } else {
            for(UUID uuid : GameManager.getCurrentGames().get(0).getParticipants()){
                if(!TOBIKO.getUniqueId().toString().equals(uuid.toString())){
                    GameManager.getCurrentGames().get(0).getWinners().add(uuid);
                }
            }

            g.addTobikoLoseEvent();
        }

        for(Player p : Bukkit.getOnlinePlayers()){
            if(!ChestUser.isLoaded(p)) continue;
            ChestUser u = ChestUser.getUser(p);
            TobikoPlayer t = TobikoPlayer.get(p);
            u.clearScoreboard();

            u.mayChat = true;

            if(t.isTobiko()){
                eligible.add(p);
                if(tobikoWins){
                    u.playVictoryEffect();
                    t.addPoints(56);
                    t.addVictories(1);
                    p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,2f,1f);
                    BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("VICTORY!"),ChatColor.GRAY + u.getTranslatedMessage("The Tobiko has won the game!"));
                } else {
                    p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,0.5f);
                    BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("DEFEAT!"),ChatColor.GRAY + u.getTranslatedMessage("The survivors have won the game!"));
                }
            } else if(t.isSurvivor() || t.ogSurvivor) {
                eligible.add(p);
                if(tobikoWins){
                    p.playSound(p.getEyeLocation(), Sound.NOTE_BASS,1f,0.5f);
                    BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("DEFEAT!"),ChatColor.GRAY + u.getTranslatedMessage("The Tobiko has won the game!"));
                } else {
                    t.addPoints(24);
                    t.addVictories(1);
                    p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,2f,1f);
                    BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("VICTORY!"),ChatColor.GRAY + u.getTranslatedMessage("The survivors have won the game!"));
                }
            } else if(t.isSpectator()){
                if(tobikoWins){
                    p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,1f,1f);
                    BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Game OVER!"),ChatColor.GRAY + u.getTranslatedMessage("The Tobiko has won the game!"));
                } else {
                    p.playSound(p.getEyeLocation(), Sound.LEVEL_UP,1f,1f);
                    BountifulAPI.sendTitle(p,10,5*20,10,ChatColor.RED.toString() + ChatColor.BOLD.toString() + u.getTranslatedMessage("Game OVER!"),ChatColor.GRAY + u.getTranslatedMessage("The survivors have won the game!"));
                }
            }

            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The game is OVER!"));

            if(tobikoWins){
                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The Tobiko has won the game!"));
            } else {
                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GOLD + u.getTranslatedMessage("The Tobiko has been defeated!"));
            }

            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.AQUA + u.getTranslatedMessage("This server restarts in 10 seconds!"));
            t.achievementCheck();
        }

        ChestAPI.giveAfterGameCrate(eligible.toArray(new Player[]{}));

        Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
            public void run() {
                for(Player p : Bukkit.getOnlinePlayers()) {
                    if(!ChestUser.isLoaded(p)) continue;
                    ChestUser u = ChestUser.getUser(p);
                    TobikoPlayer t = TobikoPlayer.get(p);

                    u.sendAfterGamePremiumAd();
                }
            }
        }, 5*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
            public void run() {
                for(Player p : Bukkit.getOnlinePlayers()) {
                    if(!ChestUser.isLoaded(p)) continue;
                    ChestUser u = ChestUser.getUser(p);
                    TobikoPlayer t = TobikoPlayer.get(p);

                    u.sendGameLogMessage(g.getID());
                }
            }
        }, 7*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
            public void run() {
                for(Player p : Bukkit.getOnlinePlayers()) {
                    if(!ChestUser.isLoaded(p)) continue;
                    ChestUser u = ChestUser.getUser(p);
                    u.connectToLobby();
                }
            }
        }, 10*20);

        Bukkit.getScheduler().scheduleSyncDelayedTask(Tobiko.getInstance(), new Runnable() {
            public void run() {
                //Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"restart");
                ChestAPI.stopServer();
            }
        }, 15*20);

        g.saveData();
    }

    public static Tobiko getInstance(){
        return instance;
    }
}
