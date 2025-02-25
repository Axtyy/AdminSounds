package com.axteroid;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.axteroid.manager.CustomSoundManager;
import com.axteroid.model.SoundStep;
import com.axteroid.model.SoundEffect;
import com.axteroid.util.MessageUtil;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminSound extends JavaPlugin implements TabCompleter {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#[a-fA-F0-9]{6}");

    private Map<String, SoundEffect> soundEffects;
    private FileConfiguration langConfig;
    private FileConfiguration soundsConfig;
    private CustomSoundManager customSoundManager;
    private Map<String, List<String>> soundIssues = new HashMap<>();

    @Override
    public void onEnable() {
        // Initialize configs
        saveDefaultConfigs();
        
        // Load configs
        reloadConfigs();
        customSoundManager = new CustomSoundManager(this);
    }

    private void saveDefaultConfigs() {
        if (!new File(getDataFolder(), "lang.yml").exists()) {
            saveResource("lang.yml", false);
        }
        if (!new File(getDataFolder(), "sounds.yml").exists()) {
            saveResource("sounds.yml", false);
        }
    }

    public void reloadConfigs() {
        // Reload both config files from disk
        File langFile = new File(getDataFolder(), "lang.yml");
        File soundsFile = new File(getDataFolder(), "sounds.yml");
        
        try {
            // Force reload from disk
            langConfig = YamlConfiguration.loadConfiguration(langFile);
            soundsConfig = YamlConfiguration.loadConfiguration(soundsFile);
            
            // Also load from embedded resources to get defaults
            langConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("lang.yml"), StandardCharsets.UTF_8)));
            soundsConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("sounds.yml"), StandardCharsets.UTF_8)));
        } catch (Exception e) {
            String error = "&c[Config Error] &7Failed to parse configuration: &f" + e.getMessage();
            getLogger().severe(error);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("adminsounds.admin")) {
                    player.sendMessage(MessageUtil.color(error));
                }
            }
            return;
        }
        
        // Reload sound effects after config update
        loadSoundEffects();
    }

    private void checkConfigErrors() {
        List<String> errors = new ArrayList<>();

        // Check required message paths
        String[][] requiredPaths = {
            {"prefix"},
            {"success.reload", "success.playing"},
            {"errors.no-permission", "errors.player-not-found", "errors.sound-not-found", "errors.invalid-sound"},
            {"help.header", "help.play", "help.list", "help.reload", "help.debug", "help.footer"},
            {"list.header", "list.entry", "list.hover", "list.footer"},
            {"debug.header", "debug.entry", "debug.timing", "debug.footer"}
        };

        for (String[] group : requiredPaths) {
            for (String path : group) {
                String value = langConfig.getString("messages." + path);
                if (value == null) {
                    errors.add("&c[Config Error] &7Missing required message: &f" + path);
                } else {
                    // Check for invalid hex colors
                    Matcher matcher = HEX_PATTERN.matcher(value);
                    while (matcher.find()) {
                        String hex = matcher.group();
                        if (!hex.matches("&#[a-fA-F0-9]{6}")) {
                            errors.add("&c[Config Error] &7Invalid hex color in &f" + path + "&7: &f" + hex);
                        }
                    }

                    // Check for proper placeholder usage
                    if (path.contains("sound") && !value.contains("%sound%")) {
                        errors.add("&c[Config Error] &7Missing %sound% placeholder in: &f" + path);
                    }
                    if (path.contains("timing") && (!value.contains("%delay_seconds%") || !value.contains("%delay_ticks%"))) {
                        errors.add("&c[Config Error] &7Missing delay placeholders in: &f" + path);
                    }
                }
            }
        }

        // Report errors to operators
        if (!errors.isEmpty()) {
            getLogger().warning("Found " + errors.size() + " configuration errors!");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("adminsounds.admin")) {
                    player.sendMessage(MessageUtil.color("&c&l[AdminSounds] &7Found &f" + errors.size() + " &7configuration errors:"));
                    for (String error : errors) {
                        player.sendMessage(MessageUtil.color(error));
                    }
                }
            }
        }
    }

    private String getMessage(String path, String... replacements) {
        String message = langConfig.getString("messages." + path);
        if (message == null) {
            String error = "&c[Config Error] &7Missing message: &f" + path;
            getLogger().warning(MessageUtil.color(error));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("adminsounds.admin")) {
                    player.sendMessage(MessageUtil.color(error));
                }
            }
            return path;
        }
        
        // Apply replacements if any
        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i < replacements.length; i += 2) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }

        // Replace {prefix} placeholder with the actual prefix if it exists in the message
        if (message.contains("{prefix}")) {
            String prefix = langConfig.getString("messages.prefix", "&#4e5de6[AdminSounds] &r");
            message = message.replace("{prefix}", prefix);
        }
        
        return MessageUtil.color(message);
    }

    private void loadSoundEffects() {
        soundEffects = new HashMap<>();
        soundIssues.clear();
        ConfigurationSection soundsSection = soundsConfig.getConfigurationSection("sounds");
        List<String> errors = new ArrayList<>();
        
        if (soundsSection == null) {
            String error = "&c[Config Error] &7Invalid sounds.yml structure: Missing 'sounds' section";
            errors.add(error);
            getLogger().warning(MessageUtil.color(error));
            return;
        }

        for (String soundName : soundsSection.getKeys(false)) {
            List<String> currentSoundIssues = new ArrayList<>();
            
            try {
                List<Map<?, ?>> stepsConfig = soundsConfig.getMapList("sounds." + soundName + ".steps");
                if (stepsConfig.isEmpty()) {
                    errors.add("&c[Config Error] &7Invalid sound effect &f" + soundName + "&7: Empty or invalid steps list");
                    continue;
                }

                List<SoundStep> soundSteps = new ArrayList<>();
                int stepNumber = 0;
                int lastDelay = 0;

                for (Map<?, ?> step : stepsConfig) {
                    stepNumber++;
                    try {
                        // Parse required fields
                        if (!(step.get("sound") instanceof String)) {
                            errors.add("&c[Config Error] &7Invalid sound effect &f" + soundName + "&7: Step " + stepNumber + " has invalid sound type");
                            continue;
                        }
                        if (!(step.get("delay") instanceof Number)) {
                            errors.add("&c[Config Error] &7Invalid sound effect &f" + soundName + "&7: Step " + stepNumber + " has invalid delay type");
                            continue;
                        }
                        if (!(step.get("volume") instanceof Number)) {
                            errors.add("&c[Config Error] &7Invalid sound effect &f" + soundName + "&7: Step " + stepNumber + " has invalid volume type");
                            continue;
                        }
                        if (!(step.get("pitch") instanceof Number)) {
                            errors.add("&c[Config Error] &7Invalid sound effect &f" + soundName + "&7: Step " + stepNumber + " has invalid pitch type");
                            continue;
                        }

                        String soundId = (String) step.get("sound");
                        int delay = ((Number) step.get("delay")).intValue();
                        float volume = ((Number) step.get("volume")).floatValue();
                        float pitch = ((Number) step.get("pitch")).floatValue();
                        String namespace = (String) step.get("namespace");
                        String key = (String) step.get("key");

                        // Validate values for list display
                        if (delay < 0) {
                            currentSoundIssues.add("Step " + stepNumber + ": Invalid delay (must be >= 0)");
                        }
                        if (delay == 0 && stepNumber > 1) {
                            currentSoundIssues.add("Step " + stepNumber + ": No delay between sounds");
                        }
                        if (volume <= 0 || volume > 2.0) {
                            currentSoundIssues.add("Step " + stepNumber + ": Invalid volume (must be between 0-2)");
                        }
                        if (pitch <= 0 || pitch > 2.0) {
                            currentSoundIssues.add("Step " + stepNumber + ": Invalid pitch (must be between 0-2)");
                        }

                        // Try to validate the sound
                        if (!soundId.startsWith("custom:")) {
                            try {
                                Sound.valueOf(soundId);
                            } catch (IllegalArgumentException e) {
                                currentSoundIssues.add("Step " + stepNumber + ": Invalid sound ID: " + soundId);
                            }
                        }
                        
                        soundSteps.add(new SoundStep(soundId, delay, volume, pitch, namespace, key));
                        lastDelay = delay;
                    } catch (Exception e) {
                        errors.add("&c[Config Error] &7Failed to parse sound effect &f" + soundName + "&7 step " + stepNumber + ": " + e.getMessage());
                    }
                }
                
                if (!soundSteps.isEmpty()) {
                    SoundEffect effect = new SoundEffect(soundName, soundSteps, this);
                    soundEffects.put(soundName.toLowerCase(), effect);
                    if (!currentSoundIssues.isEmpty()) {
                        soundIssues.put(soundName.toLowerCase(), currentSoundIssues);
                    }
                }
            } catch (Exception e) {
                errors.add("&c[Config Error] &7Failed to parse sound effect &f" + soundName + "&7: " + e.getMessage());
            }
        }

        // Report parsing errors to operators
        if (!errors.isEmpty()) {
            getLogger().warning("Found " + errors.size() + " configuration parsing errors!");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("adminsounds.admin")) {
                    player.sendMessage(MessageUtil.color("&c&l[AdminSounds] &7Found &f" + errors.size() + " &7configuration parsing errors:"));
                    for (String error : errors) {
                        player.sendMessage(MessageUtil.color(error));
                    }
                }
            }
        }

        getLogger().info("Loaded " + soundEffects.size() + " sound effects");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("adminsounds")) {
            return false;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "play":
                if (args.length < 3) {
                    sender.sendMessage(getMessage("help.play"));
                    return true;
                }
                handlePlayCommand(sender, args[1], args[2]);
                break;

            case "playall":
                if (!sender.hasPermission("adminsounds.playall")) {
                    sender.sendMessage(getMessage("errors.no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(getMessage("help.playall"));
                    return true;
                }
                handlePlayAllCommand(sender, args[1]);
                break;

            case "reload":
                if (!sender.hasPermission("adminsounds.reload")) {
                    sender.sendMessage(getMessage("errors.no-permission"));
                    return true;
                }
                reloadConfigs();
                sender.sendMessage(getMessage("success.reload"));
                break;

            case "list":
                listSoundEffects(sender);
                break;

            case "debug":
                if (args.length < 2) {
                    sender.sendMessage(getMessage("help.debug"));
                    return true;
                }
                debugSoundTiming(sender, args[1]);
                break;

            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handlePlayCommand(CommandSender sender, String playerName, String soundName) {
        boolean selfPlay = sender instanceof Player && playerName.equalsIgnoreCase(sender.getName());
        
        if (selfPlay && !sender.hasPermission("adminsounds.play")) {
            sender.sendMessage(getMessage("errors.no-permission"));
            return;
        }
        if (!selfPlay && !sender.hasPermission("adminsounds.play.others")) {
            sender.sendMessage(getMessage("errors.no-permission"));
            return;
        }

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(getMessage("errors.player-not-found"));
            return;
        }

        String soundKey = soundName.toLowerCase();
        if (!soundEffects.containsKey(soundKey)) {
            sender.sendMessage(getMessage("errors.sound-not-found", "%sound%", soundName));
            return;
        }

        playSoundEffect(target, soundKey);
        sender.sendMessage(getMessage("success.playing", "%sound%", soundName));
    }

    private void handlePlayAllCommand(CommandSender sender, String soundName) {
        String soundKey = soundName.toLowerCase();
        if (!soundEffects.containsKey(soundKey)) {
            sender.sendMessage(getMessage("errors.sound-not-found", "%sound%", soundName));
            return;
        }

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayers) {
            playSoundEffect(player, soundKey);
        }
        
        sender.sendMessage(getMessage("success.playing_all", 
            "%sound%", soundName,
            "%count%", String.valueOf(onlinePlayers.size())
        ));
    }

    private void playSoundEffect(Player player, String soundName) {
        SoundEffect effect = soundEffects.get(soundName);
        if (effect == null) return;
        effect.play(player);
    }

    private void listSoundEffects(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            sender.sendMessage(getMessage("list.header"));
            
            for (String effect : soundEffects.keySet()) {
                net.md_5.bungee.api.chat.TextComponent message = new net.md_5.bungee.api.chat.TextComponent(
                    MessageUtil.color(langConfig.getString("messages.list.entry", "&#00bfff◆ &f%sound%").replace("%sound%", effect))
                );
                
                // Add warning if sound has issues
                if (soundIssues.containsKey(effect)) {
                    message.addExtra(MessageUtil.color(" &c⚠"));
                }
                
                message.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                    "/adminsounds play " + player.getName() + " " + effect
                ));
                
                String hoverText = langConfig.getString("messages.list.hover", "&#00ff7f♪ &7Click to play &f%sound%").replace("%sound%", effect);
                if (soundIssues.containsKey(effect) && (player.isOp() || player.hasPermission("adminsounds.admin"))) {
                    hoverText += "\n&c&lIssues found:";
                    for (String issue : soundIssues.get(effect)) {
                        hoverText += "\n&7- &f" + issue;
                    }
                }
                
                message.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder(MessageUtil.color(hoverText)).create()
                ));
                
                player.spigot().sendMessage(message);
            }
            sender.sendMessage(getMessage("list.footer"));
        } else {
            sender.sendMessage(getMessage("list.header"));
            for (String effect : soundEffects.keySet()) {
                String message = langConfig.getString("messages.list.entry", "- %sound%").replace("%sound%", effect);
                if (soundIssues.containsKey(effect)) {
                    message += " &c⚠ &7(Issues found)";
                    for (String issue : soundIssues.get(effect)) {
                        message += "\n  &7- &f" + issue;
                    }
                }
                sender.sendMessage(MessageUtil.color(message));
            }
            sender.sendMessage(getMessage("list.footer"));
        }
    }

    private void debugSoundTiming(CommandSender sender, String soundName) {
        SoundEffect effect = soundEffects.get(soundName);
        if (effect == null) {
            sender.sendMessage(getMessage("errors.sound-not-found", "%sound%", soundName));
            return;
        }

        sender.sendMessage(getMessage("debug.header", "%sound%", soundName));
        long totalDelay = 0;
        for (SoundStep step : effect.getSteps()) {
            totalDelay += step.getDelay();
            sender.sendMessage(getMessage("debug.entry", 
                "%sound%", step.getSound(),
                "%delay_seconds%", String.format("%.1f", totalDelay/20.0),
                "%delay_ticks%", String.valueOf(totalDelay)));
        }
        sender.sendMessage(getMessage("debug.footer"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(getMessage("help.header"));
        sender.sendMessage(getMessage("help.play"));
        if (sender.hasPermission("adminsounds.playall")) {
            sender.sendMessage(getMessage("help.playall"));
        }
        sender.sendMessage(getMessage("help.list"));
        if (sender.hasPermission("adminsounds.reload")) {
            sender.sendMessage(getMessage("help.reload"));
        }
        sender.sendMessage(getMessage("help.debug"));
        sender.sendMessage(getMessage("help.footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("adminsounds")) {
            return null;
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("play", "list", "debug"));
            if (sender.hasPermission("adminsounds.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("adminsounds.playall")) {
                completions.add("playall");
            }
            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "play":
                    return filterCompletions(
                        Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()),
                        args[1]
                    );
                case "debug":
                case "playall":
                    return filterCompletions(new ArrayList<>(soundEffects.keySet()), args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            return filterCompletions(new ArrayList<>(soundEffects.keySet()), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(List<String> completions, String partial) {
        String lowercasePartial = partial.toLowerCase();
        return completions.stream()
            .filter(str -> str.toLowerCase().startsWith(lowercasePartial))
            .collect(Collectors.toList());
    }

    public FileConfiguration getSoundsConfig() {
        return soundsConfig;
    }

    public CustomSoundManager getCustomSoundManager() {
        return customSoundManager;
    }
} 