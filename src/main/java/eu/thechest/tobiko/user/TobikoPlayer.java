package eu.thechest.tobiko.user;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.util.StringUtils;
import eu.thechest.tobiko.Tobiko;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class TobikoPlayer {
    public static HashMap<Player,TobikoPlayer> STORAGE = new HashMap<Player,TobikoPlayer>();

    public static TobikoPlayer get(Player p){
        if(STORAGE.containsKey(p)){
            return STORAGE.get(p);
        } else {
            new TobikoPlayer(p);

            if(STORAGE.containsKey(p)){
                return STORAGE.get(p);
            } else {
                return null;
            }
        }
    }

    public static void unregister(Player p){
        if(STORAGE.containsKey(p)){
            STORAGE.get(p).saveData();
            STORAGE.remove(p);
        }
    }

    private Player p;

    private int startPoints;
    private int points;

    private int startHits;
    private int hits;

    private int startFinalHits;
    private int finalHits;

    private int startKills;
    private int kills;

    private int startPlayedGames;
    private int playedGames;

    private int startVictories;
    private int victories;

    public boolean ogSurvivor = false;

    public TobikoPlayer(Player p){
        if(STORAGE.containsKey(p)) return;

        this.p = p;

        try {
            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `tk_stats` WHERE `uuid` = ?");
            ps.setString(1,p.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if(rs.first()){
                startPoints = rs.getInt("points");
                startHits = rs.getInt("hits");
                startFinalHits = rs.getInt("finalHits");
                startKills = rs.getInt("kills");
                startPlayedGames = rs.getInt("playedGames");
                startVictories = rs.getInt("victories");

                STORAGE.put(p,this);
            } else {
                PreparedStatement insert = MySQLManager.getInstance().getConnection().prepareStatement("INSERT INTO `tk_stats` (`uuid`) VALUES(?);");
                insert.setString(1,p.getUniqueId().toString());
                insert.executeUpdate();
                insert.close();

                new TobikoPlayer(p);
            }

            MySQLManager.getInstance().closeResources(rs,ps);
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public Player getBukkitPlayer(){
        return this.p;
    }

    public ChestUser getUser(){
        return ChestUser.getUser(p);
    }

    public int getPoints(){
        return this.startPoints+this.points;
    }

    public int getCurrentPoints(){
        return this.points;
    }

    public void addPoints(int points){
        for(int i = 0; i < points; i++){
            //if((startPoints+this.points+i)<=0) break;

            this.points++;
        }
    }

    public void reducePoints(int points){
        for(int i = 0; i < points; i++){
            if((startPoints+this.points+(i/-1))<=0) break;

            this.points--;
        }
    }

    public int getHits(){
        return this.startHits+this.hits;
    }

    public int getCurrentHits(){
        return this.hits;
    }

    public void addHits(int i){
        this.hits+=i;
    }

    public int getFinalHits(){
        return this.startFinalHits+this.finalHits;
    }

    public int getCurrentFinalHits(){
        return this.finalHits;
    }

    public void addFinalHits(int i){
        this.finalHits+=i;
    }

    public int getKills(){
        return this.startKills+this.kills;
    }

    public int getCurrentKills(){
        return this.kills;
    }

    public void addKills(int i){
        this.kills+=i;
    }

    public int getPlayedGames(){
        return this.startPlayedGames+this.playedGames;
    }

    public int getCurrentPlayedGames(){
        return this.playedGames;
    }

    public void addPlayedGames(int i){
        this.playedGames+=i;
    }

    public int getVictories(){
        return this.startVictories+this.victories;
    }

    public int getCurrentVictories(){
        return this.victories;
    }

    public void addVictories(int i){
        this.victories+=i;
    }

    public boolean isTobiko(){
        return Tobiko.TOBIKO == p;
    }

    public boolean isSurvivor(){
        return Tobiko.SURVIVORS.contains(p);
    }

    public boolean isSpectator(){
        return Tobiko.SPECTATORS.contains(p);
    }

    public void updateScoreboard(){
        updateScoreboard(false);
    }

    public void updateScoreboard(boolean displayNameOnly){
        ChestAPI.sync(() -> {
            if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.INGAME){
                if(displayNameOnly){
                    Scoreboard b = getUser().getScoreboard();

                    if(b.getObjective(DisplaySlot.SIDEBAR) != null){
                        b.getObjective(DisplaySlot.SIDEBAR).setDisplayName(ChatColor.LIGHT_PURPLE + "Tobiko " + ChatColor.GREEN + StringUtils.secondsToString(Tobiko.TIME_LEFT));
                    } else {
                        updateScoreboard();
                    }
                } else {
                    Scoreboard b = getUser().getScoreboard();

                    Objective ob = null;

                    if(b.getObjective(DisplaySlot.SIDEBAR) != null){
                        b.getObjective(DisplaySlot.SIDEBAR).unregister();
                    }

                    ob = b.registerNewObjective("side","dummy");

                    ob.setDisplayName(ChatColor.LIGHT_PURPLE + "Tobiko " + ChatColor.GREEN + StringUtils.secondsToString(Tobiko.TIME_LEFT));
                    ob.setDisplaySlot(DisplaySlot.SIDEBAR);

                    ArrayList<Player> a = new ArrayList<Player>();
                    a.addAll(Tobiko.SURVIVORS);

                    Collections.sort(a, new Comparator<Player>() {
                        public int compare(Player p1, Player p2) {
                            Integer points1 = TobikoPlayer.get(p1).getCurrentHits();
                            Integer points2 = TobikoPlayer.get(p2).getCurrentHits();

                            return points2.compareTo(points1);
                        }
                    });

                    ob.getScore(" ").setScore(14);
                    if(isTobiko()){
                        ob.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Kills") + ":").setScore(13);
                        ob.getScore(ChatColor.YELLOW + String.valueOf(getCurrentKills())).setScore(12);
                    } else {
                        ob.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Your hits") + ":").setScore(13);
                        ob.getScore(ChatColor.YELLOW + String.valueOf(getCurrentHits())).setScore(12);
                    }
                    ob.getScore("  ").setScore(11);
                    ob.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + "Tobiko Health" + ":").setScore(10);
                    ob.getScore(ChatColor.YELLOW + String.valueOf(Tobiko.TOBIKO_HEALTH) + " ").setScore(9);
                    ob.getScore("   ").setScore(8);
                    ob.getScore(ChatColor.AQUA.toString() + ChatColor.BOLD.toString() + getUser().getTranslatedMessage("Leading") + ":").setScore(7);
                    int score = 6;
                    int i = 1;

                    for(Player s : a){
                        if(i > 3) break;
                        ChestUser ss = ChestUser.getUser(s);
                        String st = StringUtils.limitString(ss.getRank().getColor() + s.getName(), 16);
                        int points = TobikoPlayer.get(s).getCurrentHits();
                        ob.getScore(st).setScore(score);
                        getUser().setPlayerPrefix(st,i + ". ");
                        getUser().setPlayerSuffix(st,ChatColor.WHITE + ": " + points);

                        i++;
                        score--;
                    }

                    String c = "";

                    while(i <= 3){
                        String st = ChatColor.DARK_GRAY + "???" + c;
                        ob.getScore(st).setScore(score);
                        getUser().setPlayerPrefix(st,i + ". ");
                        getUser().setPlayerSuffix(st,ChatColor.WHITE + ": " + 0);
                        c = c + " ";

                        score--;
                        i++;
                    }

                    ob.getScore("    ").setScore(3);
                    ob.getScore(StringUtils.SCOREBOARD_LINE_SEPERATOR).setScore(2);
                    ob.getScore(StringUtils.SCOREBOARD_FOOTER_IP).setScore(1);
                }
            } else {
                if(ServerSettingsManager.CURRENT_GAMESTATE != GameState.LOBBY){
                    getUser().clearScoreboard();
                }
            }
        });
    }

    public void updateHUD(){
        if(isTobiko()){
            p.setLevel(0);
            p.setExp((float) ((double) Tobiko.AMMO_LEFT / Tobiko.MAX_AMMO));
        }
    }

    public void achievementCheck(){
        if(getVictories() >= 10) getUser().achieve(50);
        if(getVictories() >= 25) getUser().achieve(51);
        if(getVictories() >= 50) getUser().achieve(52);

        if(getHits() > 0) getUser().achieve(53);
        if(getFinalHits() > 0) getUser().achieve(54);
    }

    public void saveData(){
        ChestAPI.async(() -> {
            try {
                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("UPDATE `tk_stats` SET `points`=`points`+?, `monthlyPoints`=`monthlyPoints`+?, `hits`=`hits`+?, `finalHits`=`finalHits`+?, `kills`=`kills`+?, `playedGames`=`playedGames`+?, `victories`=`victories`+? WHERE `uuid` = ?");
                ps.setInt(1,this.points);
                ps.setInt(2,this.points);
                ps.setInt(3,this.hits);
                ps.setInt(4,this.finalHits);
                ps.setInt(5,this.kills);
                ps.setInt(6,this.playedGames);
                ps.setInt(7,this.victories);
                ps.setString(8,p.getUniqueId().toString());
                ps.executeUpdate();
                ps.close();
            } catch(Exception e){
                e.printStackTrace();
            }
        });
    }
}
