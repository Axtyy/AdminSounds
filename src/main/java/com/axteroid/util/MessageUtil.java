package com.axteroid.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern AMPERSAND_COLOR_PATTERN = Pattern.compile("&([0-9a-fk-orx])");
    
    public static String formatMessage(CommandSender sender, String message) {
        if (message == null) return "";
        
        String formatted = color(message);
        
        // Strip colors for console
        if (!(sender instanceof Player)) {
            return ChatColor.stripColor(formatted);
        }
        
        return formatted;
    }
    
    public static String color(String message) {
        if (message == null) return "";
        
        // Convert hex colors
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + color).toString());
        }
        matcher.appendTail(buffer);
        
        // Convert traditional color codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
    
    public static TextComponent coloredText(String message) {
        return new TextComponent(color(message));
    }
    
    public static BaseComponent[] coloredComponents(String message) {
        return new ComponentBuilder(color(message)).create();
    }
    
    public static void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player) {
            ((Player) sender).spigot().sendMessage(coloredComponents(message));
        } else {
            sender.sendMessage(ChatColor.stripColor(color(message)));
        }
    }
} 