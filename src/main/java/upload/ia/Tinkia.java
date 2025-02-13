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

public final class Tinkia extends JavaPlugin {

    private final OkHttpClient httpClient = new OkHttpClient();
    private String dropboxAccessToken;

    @Override
    public void onEnable() {
        displayAsciiArt();
        loadAccessToken();

        this.getCommand("iaupload").setExecutor((sender, command, label, args) -> {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "你没有权限执行这个命令");
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
                        sender.sendMessage(ChatColor.RED + "文件不存在！");
                    }
                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "处理文件时发生错误！");
                    e.printStackTrace();
                }
            }
            return true;
        });
    }

    private void loadAccessToken() {
        File tokenFile = new File(getDataFolder(), "token.yml");
        if (!tokenFile.exists()) {
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
                "|'########:'####:'##::: ##:'##:::'##::'######::'########::|",
                "|... ##..::. ##:: ###:: ##: ##::'##::'##... ##: ##.... ##:|",
                "|::: ##::::: ##:: ####: ##: ##:'##::: ##:::..:: ##:::: ##:|" + "          Tinksp上传资源包插件",
                "|::: ##::::: ##:: ## ## ##: #####::::. ######:: ########::|" + "          群号:464570091",
                "|::: ##::::: ##:: ##. ####: ##. ##::::..... ##: ##.....:::|" + "          网站:https://www.tinksp.cn/",
                "|::: ##::::: ##:: ##:. ###: ##:. ##::'##::: ##: ##::::::::|",
                "|::: ##::::'####: ##::. ##: ##::. ##:. ######:: ##::::::::|",
                "|:::..:::::....::..::::..::..::::..:::......:::..:::::::::|"
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

        String jsonContent = "{\"interface\": \"用于防止恶意上传\"}";
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
            throw new IOException("无法更新压缩文件！");
        }
    }

    private void uploadFileAsync(File file, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String uploadResponse = uploadFileToDropbox(file);

                if (uploadResponse != null) {
                    Bukkit.getScheduler().runTask(this, () -> {
                        modifyConfigFile(uploadResponse);
                        sender.sendMessage(ChatColor.GREEN + "文件上传成功！请使用 /iareload 加载资源。");
                    });
                } else {
                    sender.sendMessage(ChatColor.RED + "文件上传失败！");
                }
            } catch (IOException e) {
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + "上传过程中出现错误！");
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
