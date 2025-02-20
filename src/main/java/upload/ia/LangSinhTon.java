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

    @Override
    public void onEnable() {
        if (!isSupportedVersion()) {
            getLogger().warning("This plugin only supports Paper versions from 1.21.1 to 1.21.4! Please upgrade or downgrade your server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        displayAsciiArt();
        loadAccessToken();

        this.getCommand("iaupload").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to execute this command.");
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

    private boolean isSupportedVersion() {
        String version = Bukkit.getVersion();
        return version.contains("1.21.1") || version.contains("1.21.2") || version.contains("1.21.3") || version.contains("1.21.4");
    }

    private void loadAccessToken() {
        File tokenFile = new File(getDataFolder(), "token.yml");
        if (!tokenFile.exists()) {
            tokenFile.getParentFile().mkdirs();
            saveResource("token.yml", false);  // Ensure token.yml is copied from resources if it does not exist
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

    private File createInterfaceJson() throws IOException {
        File interfaceJson = new File(getDataFolder(), "Interface.json");
        if (!interfaceJson.exists()) {
            interfaceJson.getParentFile().mkdirs();
            interfaceJson.createNewFile();
        }

        String jsonContent = "{\"interface\": \"Used to prevent malicious uploads\"}";
        try (FileWriter writer = new FileWriter(interfaceJson)) {
            writer.write(jsonContent);
        }
        return interfaceJson;
    }

    private void addFileToZip(File sourceFile, String entryName) throws IOException {
        File tempFile = File.createTempFile("temp_generated", ".zip");
        File originalZip = new File("plugins/ItemsAdder/output/generated.zip");

        try (ZipFile zipFile = new ZipFile(originalZip);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
            boolean entryExists = false;

            for (Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (entry.getName().equals(entryName)) {
                    entryExists = true;
                }
                zos.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream is = zipFile.getInputStream(entry)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }

            if (!entryExists) {
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(sourceFile)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }

        if (!originalZip.delete() || !tempFile.renameTo(originalZip)) {
            throw new IOException("Failed to update the zip file!");
        }
    }

    private void uploadFileAsync(File file, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String uploadResponse = uploadFileToDropbox(file);

                if (uploadResponse != null) {
                    Bukkit.getScheduler().runTask(this, () -> {
                        modifyConfigFile(uploadResponse);
                        sender.sendMessage(ChatColor.GREEN + "File uploaded successfully! Please use /iareload to load the resource.");
                    });
                } else {
                    sender.sendMessage(ChatColor.RED + "File upload failed!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + "An error occurred during the upload process!");
            }
        });
    }

    private String uploadFileToDropbox(File file) throws IOException {
        String dropboxPath = "/generated.zip";
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        Request request = new Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .addHeader("Authorization", "Bearer " + dropboxAccessToken)
                .addHeader("Dropbox-API-Arg", "{\"path\":\"" + dropboxPath + "\",\"mode\":\"overwrite\",\"mute\":false}")
                .addHeader("Content-Type", "application/octet-stream")
                .post(RequestBody.create(fileBytes))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return createDropboxSharedLink(dropboxPath);
            } else {
                System.err.println("Failed to upload: " + response.body().string());
            }
        }
        return null;
    }

    private String createDropboxSharedLink(String filePath) throws IOException {
        String json = "{\"path\": \"" + filePath + "\", \"short_url\": false}";

        Request request = new Request.Builder()
                .url("https://api.dropboxapi.com/2/sharing/create_shared_link_with_settings")
                .addHeader("Authorization", "Bearer " + dropboxAccessToken)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json.getBytes()))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                return jsonResponse.getString("url").replace("?dl=0", "?dl=1");
            } else {
                System.err.println("Failed to create shared link: " + response.body().string());
            }
        }
        return null;
    }

    private void modifyConfigFile(String newUrl) {
        File configFile = new File(getServer().getPluginManager().getPlugin("ItemsAdder").getDataFolder(), "config.yml");
        StringBuilder newContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("      url: ")) {
                    line = "      url: " + newUrl;
                }
                newContent.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write(newContent.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
