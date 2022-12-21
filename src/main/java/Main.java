import com.google.gson.Gson;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {
    private static Gson gson = new Gson();

    public static void main(String[] args) throws IOException, InterruptedException {

        // 初始化程序数据
        String playerDataPath = "world/playerdata";
        String advancementsDataPath = "world/advancements";
        String statsDataPath = "world/stats";
        List<UUID> offlineUUID = new ArrayList<>();
        List<String> offlinePlayersName = new ArrayList<>();
        List<String> onlinePlayerName = new ArrayList<>();
        Map<UUID, String> offlineUUIDToName = new HashMap<>();
        Map<UUID, String> onlineUUIDToName = new HashMap<>();
        Map<UUID, UUID> onlineUUIDToOfflineUUID = new HashMap<>();
        Map<String, UUID> nameToOnlineUUID = new HashMap<>();
        Map<String, UUID> nameToOfflineUUID = new HashMap<>();
        File offlineUUIDFolder = new File(playerDataPath);
        File[] offlineUUIDFiles = offlineUUIDFolder.listFiles();

        // 获取离线用户名
        for (File file : offlineUUIDFiles) {
            NamedTag namedTag = NBTUtil.read(file);
            CompoundTag tag1 = (CompoundTag) namedTag.getTag();
            CompoundTag tag2 = (CompoundTag) tag1.get("bukkit");
            StringTag tag3 = (StringTag) tag2.get("lastKnownName");
            String userName = tag3.getValue();

            offlinePlayersName.add(userName);
            nameToOfflineUUID.put(userName, UUID.fromString(file.getName().substring(0, 36)));
            offlineUUIDToName.put(UUID.fromString(file.getName().substring(0, 36)), userName);
        }

        System.out.println("扫描到" + offlinePlayersName.size() + "个玩家的数据。\n");
        System.out.println("离线数据获取完成，正在获取正版UUID...\n");

        // 获取在线UUID
        URL mojangAPIURL = new URL("https://api.mojang.com/profiles/minecraft");

        // 等待获取UUID的玩家名队列
        List<String> players = new ArrayList<>(offlinePlayersName);
        List<String> requestPlayers = new ArrayList<>();

        while (!players.isEmpty()) {
            for (int i = 0; i < 10 && !players.isEmpty(); i++) {
                requestPlayers.add(players.remove(0));
            }

            System.out.println("正在请求：" + requestPlayers);
            System.out.println("剩余：" + players.size());

            HttpsURLConnection connection = (HttpsURLConnection) mojangAPIURL.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; encoding=UTF-8");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(gson.toJson(requestPlayers).getBytes(StandardCharsets.UTF_8));
            outputStream.close();

            Reader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            Profile[] profiles = gson.fromJson(in, Profile[].class);
            for (Profile profile : profiles) {
                if (nameToOfflineUUID.get(profile.getName()) != null) {
                    onlinePlayerName.add(profile.getName());
                    nameToOnlineUUID.put(profile.getName(), profile.getUUID());
                }
            }

            requestPlayers.clear();
            Thread.sleep(100);
        }

        System.out.println("正版UUID获取完成，顺便将没有购买正版的玩家过滤！\n");
        System.out.println("玩家名通过正版验证的玩家：" + onlinePlayerName.size() + "\n");

        for (String playerName : onlinePlayerName) {
            File playerData = new File(playerDataPath + "/" + nameToOfflineUUID.get(playerName).toString() + ".dat");
            File newPlayerData = new File(playerDataPath + "/" + nameToOnlineUUID.get(playerName).toString() + ".dat");
            File playerAdvancement = new File(advancementsDataPath + "/" + nameToOfflineUUID.get(playerName).toString() + ".json");
            File newPlayerAdvancement = new File(advancementsDataPath + "/" + nameToOnlineUUID.get(playerName).toString() + ".json");
            File playerStats = new File(statsDataPath + "/" + nameToOfflineUUID.get(playerName).toString() + ".json");
            File newPlayerStats = new File(statsDataPath + "/" + nameToOnlineUUID.get(playerName).toString() + ".json");

            System.out.println("玩家ID：" + playerName);
            System.out.println(nameToOfflineUUID.get(playerName) + " -> " + nameToOnlineUUID.get(playerName));
            System.out.println("");

            if (playerData.isFile() && playerData.exists()) {
                playerData.renameTo(newPlayerData);
            } else {
                System.out.println(playerName + "@" + nameToOfflineUUID.get(playerName) + "的数据文件不存在，已跳过。");
            }

            if (playerAdvancement.isFile() && playerAdvancement.exists()) {
                playerAdvancement.renameTo(newPlayerAdvancement);
            } else {
                System.out.println(playerName + "@" + nameToOfflineUUID.get(playerName) + "的成就文件不存在，已跳过。");
            }

            if (playerStats.isFile() && playerStats.exists()) {
                playerStats.renameTo(newPlayerStats);
            } else {
                System.out.println(playerName + "@" + nameToOfflineUUID.get(playerName) + "的统计信息文件不存在，已跳过。");
            }

        }


    }
}
