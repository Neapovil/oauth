package com.github.neapovil.oauth;

import java.io.File;
import java.security.SecureRandom;
import java.time.Duration;
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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import spark.Spark;

public final class OAuth extends JavaPlugin implements Listener
{
    private static OAuth instance;
    private FileConfig config;
    private final Map<String, Auth> auths = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable()
    {
        instance = this;

        this.getServer().getPluginManager().registerEvents(this, this);

        this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            this.auths.values().removeIf(i -> Instant.now().isAfter(i.time));
        }, 0, 20);

        this.saveResource("config.json", false);

        this.config = FileConfig.builder(new File(this.getDataFolder(), "config.json"))
                .autoreload()
                .autosave()
                .concurrent()
                .build();

        this.config.load();

        if (((String) this.config.get("secret")).isBlank())
        {
            final String secret = this.generateSecret();
            this.config.set("secret", secret);
        }

        Spark.port(this.config.getInt("port"));

        Spark.before((req, res) -> {
            res.type("application/json");

            final String secret = req.headers("secret");

            if (secret == null || !secret.equals(this.getSecret()))
            {
                Spark.halt(401);
            }

            RequestBody data = null;

            try
            {
                data = this.gson.fromJson(req.body(), RequestBody.class);
            }
            catch (Exception e)
            {
                Spark.halt(401);
            }

            final Auth auth = this.auths.get(data.ip);

            if (auth == null || Instant.now().isAfter(auth.time))
            {
                Spark.halt(400);
            }
        });

        Spark.post("/verify", "application/json", (req, res) -> {
            res.type("application/json");

            final RequestBody data = this.gson.fromJson(req.body(), RequestBody.class);
            final Auth auth = this.auths.remove(data.ip);

            if (auth == null)
            {
                Spark.halt(400);
            }

            final ResponseSuccess response = new ResponseSuccess(auth.username, auth.avatar, auth.id);

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

    @EventHandler
    private void asyncPlayerPreLogin(AsyncPlayerPreLoginEvent event)
    {
        final String ip = event.getAddress().getHostAddress();
        final UUID id = event.getUniqueId();
        final Auth auth = this.auths.get(ip);

        if (auth == null || Instant.now().isAfter(auth.time))
        {
            this.auths.put(ip, new Auth(Instant.now().plusSeconds(this.getTime()), ip, id, event.getName()));

            final Component component = this.miniMessage.deserialize(this.getMessage(), Placeholder.unparsed("time", "" + this.getTime()));

            event.disallow(Result.KICK_OTHER, component);

            return;
        }

        final Component component = this.miniMessage.deserialize(this.getMessage(),
                Placeholder.unparsed("time", "" + Duration.between(Instant.now(), auth.time).toSeconds()));

        event.disallow(Result.KICK_OTHER, component);
    }

    private String getSecret()
    {
        return this.config.get("secret");
    }

    private int getTime()
    {
        return this.config.getInt("time");
    }

    private String getMessage()
    {
        return this.config.get("message");
    }

    class Auth
    {
        public final Instant time;
        public final String address;
        public final UUID id;
        public final String username;
        public final String avatar;

        public Auth(Instant time, String address, UUID id, String username)
        {
            this.time = time;
            this.address = address;
            this.id = id;
            this.username = username;
            this.avatar = "https://crafatar.com/avatars/" + id;
        }
    }

    class RequestBody
    {
        public final String ip;

        public RequestBody(String ip)
        {
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
        public UUID id;

        public ResponseSuccess(String username, String avatar, UUID id)
        {
            this.username = username;
            this.avatar = avatar;
            this.id = id;
        }
    }
}
