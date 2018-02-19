package eu.thechest.tobiko;

import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MainExecutor implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(cmd.getName().equalsIgnoreCase("tobiko")){
            if(sender instanceof Player){
                Player p = (Player)sender;
                ChestUser u = ChestUser.getUser(p);

                if(u.hasPermission(Rank.VIP)){
                    if(ServerSettingsManager.CURRENT_GAMESTATE == GameState.LOBBY){
                        if(Tobiko.TOBIKO == null){
                            Tobiko.TOBIKO = p;
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("You will be the Tobiko."));
                        } else {
                            if(Tobiko.TOBIKO == p){
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You have already chosen to be the Tobiko."));
                            } else {
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Another player has already chosen to be the Tobiko."));
                            }
                        }
                    } else {
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("The game has already started."));
                    }
                } else {
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You aren't permitted to execute this command."));
                }
            } else {
                sender.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "You must be a player to do this!");
            }
        }

        return false;
    }
}
