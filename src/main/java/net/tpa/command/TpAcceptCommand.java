package net.tpa.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.tpa.TpaConfig;
import net.tpa.TpaCountdown;
import net.tpa.TpaMod;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.*;

public class TpAcceptCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("tpaccept")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity target = src.getPlayerOrThrow();
                UUID targetUuid = target.getUuid();
                String targetName = target.getName().getString();

                TpaMod.LOGGER.info("[TPA] /tpaccept by {} (uuid={})", targetName, targetUuid);

                // 1. 检查是否收到了传送请求
                UUID senderUuid = TpaConfig.getPendingSenderFor(targetUuid);
                if (senderUuid == null) {
                    TpaMod.LOGGER.info("[TPA] {} has no incoming request (targetToSender lookup returned null)", targetName);
                    target.sendMessage(Text.literal("§c[TPA] 您没有收到任何传送请求"), false);
                    return 0;
                }

                // 2. 检查请求是否存在
                TpaConfig.TpaRequest req = TpaConfig.getRequest(senderUuid);
                if (req == null) {
                    TpaMod.LOGGER.info("[TPA] request for {} is null (expired or already processed)", targetName);
                    target.sendMessage(Text.literal("§c[TPA] 传送请求已过期或已被处理"), false);
                    return 0;
                }

                // 3. 找发送者
                ServerPlayerEntity sender = src.getServer().getPlayerManager().getPlayer(req.senderUuid);
                if (sender == null) {
                    TpaMod.LOGGER.info("[TPA] sender of request is offline, removing");
                    target.sendMessage(Text.literal("§c[TPA] 发送者已离线，传送请求取消"), false);
                    TpaConfig.removeRequest(senderUuid);
                    return 0;
                }

                // 4. 检查发送者是否已在倒计时
                if (TpaCountdown.isActive(senderUuid)) {
                    target.sendMessage(Text.literal("§c[TPA] 发送者已在传送倒计时中，请等待"), false);
                    return 0;
                }

                // 5. 移除请求
                TpaConfig.removeRequest(senderUuid);

                // 6. 通知双方
                target.sendMessage(Text.literal("§a[TPA] §f您已同意 §e" + sender.getName().getString() + " §f的传送请求，3 秒后开始传送！"), false);
                target.sendMessage(Text.literal("§7请 §e勿移动§7，移动会取消传送"), false);

                sender.sendMessage(Text.literal("§a[TPA] §e" + targetName + " §f已同意您的传送请求！"), false);
                sender.sendMessage(Text.literal("§7请 §e勿移动§7，倒计时 3 秒后开始"), false);

                // 7. 获取目标位置并开始倒计时
                double toX = target.getX();
                double toY = target.getY();
                double toZ = target.getZ();
                String worldId = target.getServerWorld().getRegistryKey().getValue().toString();

                boolean started = TpaCountdown.startCountdown(sender, toX, toY, toZ, worldId, targetName);
                if (!started) {
                    sender.sendMessage(Text.literal("§c[TPA] 传送启动失败（可能在倒计时中），请重试"), false);
                    TpaMod.LOGGER.warn("[TPA] startCountdown returned false for {}", sender.getName().getString());
                }

                TpaMod.LOGGER.info("[TPA] {} accepted teleport request from {}", targetName, sender.getName().getString());
                return 1;
            })
        );
    }
}