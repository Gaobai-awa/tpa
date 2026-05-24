package net.tpa.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.argument.EntityArgumentType;
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

                // 检查是否收到了传送请求
                UUID senderUuid = TpaConfig.getPendingSenderFor(targetUuid);
                if (senderUuid == null) {
                    target.sendMessage(Text.literal("§c[TPA] 您没有收到任何传送请求"), false);
                    return 0;
                }

                TpaConfig.TpaRequest req = TpaConfig.getRequest(senderUuid);
                if (req == null) {
                    target.sendMessage(Text.literal("§c[TPA] 传送请求已过期或已被处理"), false);
                    return 0;
                }

                // 找到发送者
                ServerPlayerEntity sender = src.getServer().getPlayerManager().getPlayer(req.senderUuid);
                if (sender == null) {
                    target.sendMessage(Text.literal("§c[TPA] 发送者已离线，传送请求取消"), false);
                    TpaConfig.removeRequest(senderUuid);
                    return 0;
                }

                // 检查发送者是否已经在传送倒计时中
                if (TpaCountdown.isActive(senderUuid)) {
                    target.sendMessage(Text.literal("§c[TPA] 发送者已在传送倒计时中，请等待"), false);
                    return 0;
                }

                // 移除请求（一旦同意就不能再用 /tpaccept）
                TpaConfig.removeRequest(senderUuid);

                // 通知双方
                target.sendMessage(Text.literal("§a[TPA] §f您已同意 §e" + sender.getName().getString() + " §f的传送请求，传送将在 3 秒后开始"), false);
                target.sendMessage(Text.literal("§7请 §e勿移动§7，倒计时期间移动会取消传送"), false);
                sender.sendMessage(Text.literal("§a[TPA] §e" + target.getName().getString() + " §f已同意您的传送请求！"), false);
                sender.sendMessage(Text.literal("§7请 §e勿移动§7，3 秒后开始传送"), false);

                // 获取目标玩家位置
                double toX = target.getX();
                double toY = target.getY();
                double toZ = target.getZ();
                String worldId = target.getServerWorld().getRegistryKey().getValue().toString();
                String targetName = target.getName().getString();

                // 开始倒计时
                boolean started = TpaCountdown.startCountdown(sender, toX, toY, toZ, worldId, targetName);
                if (!started) {
                    sender.sendMessage(Text.literal("§c[TPA] 传送失败（可能在倒计时中），请重试"), false);
                }

                TpaMod.LOGGER.info("[TPA] {} accepted teleport request from {}", target.getName().getString(), sender.getName().getString());
                return 1;
            })
        );
    }
}
