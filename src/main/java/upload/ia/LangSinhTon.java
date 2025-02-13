import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class LangSinhTon extends JavaPlugin {

    private final OkHttpClient httpClient = new OkHttpClient();
    private String dropboxAccessToken;
    private static final String SUPPORTED_VERSION = "1.21.4";  // Latest supported version

    @Override
    public void onEnable() {
        displayAsciiArt();
        checkServerVersion();  // Check server version at startup
        loadAccessToken();

        this.getCommand("iaupload").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to execute this command.");
                return true;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("run")) {
                try {
                    File interfaceJson = createInterfaceJson();
                    addFileToZip(interfaceJson, "assets/minecraft/Interface/Interface.json");

                    File zipFile = new File("plugins/ItemsAdder/output/generated.zip");
                    if (zipFile.exists()) {
                        uploadFileAsync(zipFile, sender);
                    } else {
                        sender.sendMessage(ChatColor.RED + "File does not exist!");
                    }
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "An error occurred while processing the file!");
                    e.printStackTrace();
                }
            }
            return true;
        });
    }

    private void checkServerVersion() {
        String version = Bukkit.getVersion();
        Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[LangSinhTon] Server version: " + version);

        if (!version.contains(SUPPORTED_VERSION)) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[LangSinhTon] Warning: This plugin is tested only up to Minecraft " + SUPPORTED_VERSION + ".");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Unexpected behavior may occur on this version.");
        } else {
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[LangSinhTon] Server version is compatible.");
        }
    }

    private void loadAccessToken() {
        File tokenFile = new File(getDataFolder(), "token.yml");
        if (!tokenFile.exists()) {
            tokenFile.getParentFile().mkdirs();
            saveResource("token.yml", false);  // Ensure the token.yml is copied from resources if not present
        }

        FileConfiguration tokenConfig = YamlConfiguration.loadConfiguration(tokenFile);
        dropboxAccessToken = tokenConfig.getString("dropbox.accessToken");

        if (dropboxAccessToken == null || dropboxAccessToken.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Dropbox access token is missing in token.yml!");
        } else {
            Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "Dropbox access token loaded successfully.");
        }
    }

    private void displayAsciiArt() {
        String[] asciiArt = {
                "██╗      █████╗ ███╗   ██╗ ██████╗ ███████╗██╗███╗   ██╗████████╗ ██████╗ ███╗   ██╗",
                "██║     ██╔══██╗████╗  ██║██╔════╝ ██╔════╝██║████╗  ██║╚══██╔══╝██╔═══██╗████╗  ██║",
                "██║     ███████║██╔██╗ ██║██║  ███╗█████╗  ██║██╔██╗ ██║   ██║   ██║   ██║██╔██╗ ██║",
                "██║     ██╔══██║██║╚██╗██║██║   ██║██╔══╝  ██║██║╚██╗██║   ██║   ██║   ██║██║╚██╗██║",
                "███████╗██║  ██║██║ ╚████║╚██████╔╝███████╗██║██║ ╚████║   ██║   ╚██████╔╝██║ ╚████║",
                "╚══════╝╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚══════╝╚═╝╚═╝  ╚═══╝   ╚═╝    ╚═════╝ ╚═╝  ╚═══╝"
        };
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "——————————————————————————————");
        for (String line : asciiArt) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + line);
        }
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "——————————————————————————————");
    }

    // (Other methods like createInterfaceJson, addFileToZip, uploadFileAsync, uploadFileToDropbox, and modifyConfigFile remain the same)
}
