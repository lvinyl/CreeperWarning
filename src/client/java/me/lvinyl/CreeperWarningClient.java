package me.lvinyl;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class CreeperWarningClient implements ClientModInitializer {
    private static final String CONFIG_FILE = "creeperwarning.properties";
    private boolean chatWarningEnabled = true;
    private boolean titleWarningEnabled = true;
    private boolean actionbarWarningEnabled = true;
    private boolean modEnabled = true;
    private boolean onlyWorkInSurvival = true;
    private double explosionRadius = 3.5;
    private double titleWarningDistance = explosionRadius + 0.5;
    private KeyBinding toggleKey;
    private Properties config;
    private boolean isTitleVisible = false;
    private int titleTicks = 0;
    private int titleCooldown = 0;
    private boolean isCreeperInRadius = false;
    private int chatWarningCooldown = 0;

    @Override
    public void onInitializeClient() {
        config = new Properties();
        loadConfig();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.creeperwarning.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "category.creeperwarning"
        ));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("creeperwarning")
                    .then(ClientCommandManager.literal("chatWarning")
                            .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                    .executes(context -> {
                                        chatWarningEnabled = context.getArgument("enabled", Boolean.class);
                                        context.getSource().sendFeedback(Text.literal("Chat warning " + (chatWarningEnabled ? "enabled" : "disabled")));
                                        saveConfig();
                                        return 0;
                                    })))
                    .then(ClientCommandManager.literal("titleWarning")
                            .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                    .executes(context -> {
                                        titleWarningEnabled = context.getArgument("enabled", Boolean.class);
                                        context.getSource().sendFeedback(Text.literal("Title warning " + (titleWarningEnabled ? "enabled" : "disabled")));
                                        saveConfig();
                                        return 0;
                                    })))
                    .then(ClientCommandManager.literal("actionbarWarning")
                            .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                    .executes(context -> {
                                        actionbarWarningEnabled = context.getArgument("enabled", Boolean.class);
                                        context.getSource().sendFeedback(Text.literal("Actionbar warning " + (actionbarWarningEnabled ? "enabled" : "disabled")));
                                        saveConfig();
                                        return 0;
                                    })))
                    .then(ClientCommandManager.literal("onlyWorkInSurvival")
                            .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                    .executes(context -> {
                                        onlyWorkInSurvival = context.getArgument("enabled", Boolean.class);
                                        context.getSource().sendFeedback(Text.literal("Only work in survival mode " + (onlyWorkInSurvival ? "enabled" : "disabled")));
                                        saveConfig();
                                        return 0;
                                    })))
                    .then(ClientCommandManager.literal("explosionRadius")
                            .then(ClientCommandManager.argument("radius", FloatArgumentType.floatArg(0))
                                    .executes(context -> {
                                        explosionRadius = context.getArgument("radius", Double.class);
                                        titleWarningDistance = explosionRadius + 0.5;
                                        context.getSource().sendFeedback(Text.literal("Explosion radius set to " + explosionRadius));
                                        saveConfig();
                                        return 0;
                                    })))
                    .then(ClientCommandManager.literal("toggle")
                            .executes(context -> {
                                modEnabled = !modEnabled;
                                context.getSource().sendFeedback(Text.literal("Creeper Warning Mod " + (modEnabled ? "enabled" : "disabled")));
                                saveConfig();
                                return 0;
                            }))
                    .then(ClientCommandManager.literal("settings")
                            .executes(context -> {
                                context.getSource().sendFeedback(Text.literal("Creeper Warning Mod Settings:"));
                                context.getSource().sendFeedback(Text.literal("- Mod Enabled: " + (modEnabled ? "Yes" : "No")));
                                context.getSource().sendFeedback(Text.literal("- Chat Warning: " + (chatWarningEnabled ? "Enabled" : "Disabled")));
                                context.getSource().sendFeedback(Text.literal("- Title Warning: " + (titleWarningEnabled ? "Enabled" : "Disabled")));
                                context.getSource().sendFeedback(Text.literal("- Actionbar Warning: " + (actionbarWarningEnabled ? "Enabled" : "Disabled")));
                                context.getSource().sendFeedback(Text.literal("- Only Work in Survival: " + (onlyWorkInSurvival ? "Enabled" : "Disabled")));
                                context.getSource().sendFeedback(Text.literal("- Explosion Radius: " + explosionRadius));
                                return 0;
                            }))
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (modEnabled && client.world != null && client.player != null) {
                if (onlyWorkInSurvival && client.interactionManager != null && client.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL) {
                    return;
                }
                boolean creeperFound = false;
                for (Entity entity : client.world.getEntities()) {
                    if (entity instanceof CreeperEntity) {
                        CreeperEntity creeper = (CreeperEntity) entity;
                        double distance = client.player.getPos().distanceTo(creeper.getPos());
                        if (distance <= explosionRadius) {
                            if (chatWarningEnabled && chatWarningCooldown <= 0) {
                                client.player.sendMessage(Text.of("§c[CreeperWarning] Creeper nearby! Watch out!"), false);
                                chatWarningCooldown = 20; // Set a cooldown of 20 ticks (1 second)
                            }
                            if (actionbarWarningEnabled) {
                                client.inGameHud.setOverlayMessage(Text.of("§cCreeper nearby!"), false);
                            }
                            creeperFound = true;
                        }
                        if (titleWarningEnabled && distance <= titleWarningDistance) {
                            isCreeperInRadius = true;
                        }
                    }
                }
                if (!creeperFound) {
                    isCreeperInRadius = false;
                }
            }

            if (titleWarningEnabled) {
                if (isCreeperInRadius && !isTitleVisible && titleCooldown <= 0) {
                    isTitleVisible = true;
                    titleTicks = 0;
                    client.inGameHud.setTitle(Text.of("§cCreeper Warning!"));
                    client.inGameHud.setTitleTicks(10, 20, 10);
                }

                if (isTitleVisible) {
                    titleTicks++;
                    if (titleTicks >= 40) {
                        isTitleVisible = false;
                        titleCooldown = 60; // Set a cooldown of 60 ticks (3 seconds)
                    }
                }

                if (titleCooldown > 0) {
                    titleCooldown--;
                }
            }

            if (chatWarningCooldown > 0) {
                chatWarningCooldown--;
            }

            while (toggleKey.wasPressed()) {
                modEnabled = !modEnabled;
                client.player.sendMessage(Text.of("Creeper Warning Mod " + (modEnabled ? "enabled" : "disabled")), false);
                saveConfig();
            }
        });
    }

    private void loadConfig() {
        try {
            File configFile = new File(MinecraftClient.getInstance().runDirectory, CONFIG_FILE);
            if (configFile.exists()) {
                FileReader reader = new FileReader(configFile);
                config.load(reader);

                chatWarningEnabled = Boolean.parseBoolean(config.getProperty("chatWarningEnabled", String.valueOf(chatWarningEnabled)));
                titleWarningEnabled = Boolean.parseBoolean(config.getProperty("titleWarningEnabled", String.valueOf(titleWarningEnabled)));
                actionbarWarningEnabled = Boolean.parseBoolean(config.getProperty("actionbarWarningEnabled", String.valueOf(actionbarWarningEnabled)));
                modEnabled = Boolean.parseBoolean(config.getProperty("modEnabled", String.valueOf(modEnabled)));
                onlyWorkInSurvival = Boolean.parseBoolean(config.getProperty("onlyWorkInSurvival", String.valueOf(onlyWorkInSurvival)));
                explosionRadius = Double.parseDouble(config.getProperty("explosionRadius", String.valueOf(explosionRadius)));
                titleWarningDistance = explosionRadius + 0.5;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        try {
            File configFile = new File(MinecraftClient.getInstance().runDirectory, CONFIG_FILE);
            FileWriter writer = new FileWriter(configFile);

            config.setProperty("chatWarningEnabled", String.valueOf(chatWarningEnabled));
            config.setProperty("titleWarningEnabled", String.valueOf(titleWarningEnabled));
            config.setProperty("actionbarWarningEnabled", String.valueOf(actionbarWarningEnabled));
            config.setProperty("modEnabled", String.valueOf(modEnabled));
            config.setProperty("onlyWorkInSurvival", String.valueOf(onlyWorkInSurvival));
            config.setProperty("explosionRadius", String.valueOf(explosionRadius));

            config.store(writer, "Creeper Warning Mod Settings");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}