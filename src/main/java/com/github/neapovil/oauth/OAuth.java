package com.github.neapovil.oauth;

import java.io.File;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.electronwill.nightconfig.core.file.FileConfig;

import net.kyori.adventure.text.Component;
import spark.Spark;

public final class OAuth extends JavaPlugin implements Listener
{
    private static OAuth instance;
    private FileConfig config;
    private String secret = "";
    private final Map<UUID, Auth> codes = new HashMap<>();

    @Override
    public void onEnable()
    {
        instance = this;
        
        this.getServer().getPluginManager().registerEvents(this, this);

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
            final String secret = req.headers("secret");
            
            if (secret == null)
            {
                Spark.halt(404);
            }
            
            if (!secret.equals(this.secret))
            {
                Spark.halt(404);
            }
        });
        
        Spark.get("/verify", (req, res) -> {
            return "Hello World!";
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
        final UUID id = event.getUniqueId();
        final Auth auth = this.codes.get(id);
        
        if (Instant.now().isAfter(auth.time))
        {
            event.kickMessage(Component.text(""));
        }
    }
    
    class Auth
    {
        public final String code;
        public final Instant time;
        
        public Auth(String code, Instant time)
        {
            this.code = code;
            this.time = time;
        }
    }
}
