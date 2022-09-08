package com.github.neapovil.oauth;

import java.io.File;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.kyori.adventure.text.Component;
import spark.Spark;

public final class OAuth extends JavaPlugin implements Listener
{
    private static OAuth instance;
    private FileConfig config;
    private String secret = "";
    private final Map<String, Auth> codes = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onEnable()
    {
        instance = this;

        this.getServer().getPluginManager().registerEvents(this, this);

        this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            this.codes.values().removeIf(i -> Instant.now().isAfter(i.time));
        }, 0, 20);

        this.saveResource("config.json", false);

        this.config = FileConfig.builder(new File(this.getDataFolder(), "config.json"))
                .autoreload()
                .autosave()
                .build();

        this.config.load();

        if (((String) this.config.get("secret")).isBlank())
        {
            final String secret = this.generateSecret();
            this.config.set("secret", secret);
        }

        this.secret = this.config.get("secret");

        final int port = this.config.getInt("port");

        Spark.port(port);

        Spark.before((req, res) -> {
            res.type("application/json");

            final String secret = req.headers("secret");

            final ResponseError response = new ResponseError("");

            if (secret == null)
            {
                Spark.halt(404, this.gson.toJson(response));
            }

            if (!secret.equals(this.secret))
            {
                Spark.halt(404, this.gson.toJson(response));
            }

            RequestBody data = null;

            try
            {
                data = this.gson.fromJson(req.body(), RequestBody.class);
            }
            catch (Exception e)
            {
                Spark.halt(400, this.gson.toJson(response));
            }

            final Auth auth = this.codes.get(data.ip);

            if (auth == null || data.code != auth.code || Instant.now().isAfter(auth.time))
            {
                response.message = "INVALID CODE";
                Spark.halt(400, this.gson.toJson(response));
            }
        });

        Spark.get("/verify", "application/json", (req, res) -> {
            res.type("application/json");

            final RequestBody data = this.gson.fromJson(req.body(), RequestBody.class);
            final Auth auth = this.codes.remove(data.ip);

            if (auth == null)
            {
                Spark.halt(400, this.gson.toJson(new ResponseError("EXPIRED")));
            }

            final ResponseSuccess response = new ResponseSuccess(auth.username, auth.avatar);

            return this.gson.toJson(response);
        });
    }

    @Override
    public void onDisable()
    {
        Spark.stop();
    }

    public static OAuth getInstance()
    {
        return instance;
    }

    private String generateSecret()
    {
        final int leftlimit = 48; // numeral '0'
        final int rightlimit = 122; // letter 'z'
        final int targetlength = 20;

        final SecureRandom random = new SecureRandom();

        return random.ints(leftlimit, rightlimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetlength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private int generateCode()
    {
        final SecureRandom random = new SecureRandom();

        return random.nextInt(100000, 1000000);
    }

    @EventHandler
    private void AsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event)
    {
        final String ip = event.getAddress().getHostAddress();
        final UUID id = event.getUniqueId();
        final Auth auth = this.codes.get(ip);

        if (auth == null || Instant.now().isAfter(auth.time))
        {
            final int code = this.generateCode();

            this.codes.put(ip, new Auth(code, Instant.now().plusSeconds(60), ip, id, event.getName()));

            event.disallow(Result.KICK_OTHER, Component.text(this.formatCode(code)));

            return;
        }

        event.disallow(Result.KICK_OTHER, Component.text(this.formatCode(auth.code)));
    }

    private String formatCode(int s)
    {
        final StringBuilder sb = new StringBuilder("" + s);

        sb.insert(3, " ");

        return sb.toString();
    }

    class Auth
    {
        public final int code;
        public final Instant time;
        public final String address;
        public final UUID id;
        public final String username;
        public final String avatar;

        public Auth(int code, Instant time, String address, UUID id, String username)
        {
            this.code = code;
            this.time = time;
            this.address = address;
            this.id = id;
            this.username = username;
            this.avatar = "https://crafatar.com/avatars/" + id;
        }
    }

    class RequestBody
    {
        public final int code;
        public final String ip;

        public RequestBody(int code, String ip)
        {
            this.code = code;
            this.ip = ip;
        }
    }

    class ResponseError
    {
        public String message;

        public ResponseError(String message)
        {
            this.message = message;
        }
    }

    class ResponseSuccess
    {
        public String username;
        public String avatar;

        public ResponseSuccess(String username, String avatar)
        {
            this.username = username;
            this.avatar = avatar;
        }
    }
}
