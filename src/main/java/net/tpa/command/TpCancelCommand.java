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

public class TpCancelCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("tpacancel")
            .requires(src -> src.hasPermissionLevel(0))
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                ServerPlayerEntity sender = src.getPlayerOrThrow();
                UUID senderUuid = sender.getUuid();

                // 检查是否有待处理请求
                if (!TpaConfig.hasPendingRequest(senderUuid)) {
                    sender.sendMessage(Text.literal("§c[TPA] 您没有发送任何待处理的传送请求"), false);
                    return 0;
                }

                TpaConfig.TpaRequest req = TpaConfig.getRequest(senderUuid);
                if (req == null) {
                    sender.sendMessage(Text.literal("§c[TPA] 请求已过期或已被处理"), false);
                    return 0;
                }

                // 移除请求
                TpaConfig.removeRequest(senderUuid);

                // 通知发送者
                sender.sendMessage(Text.literal("§e[TPA] §f您已取消发送给 §e" + req.targetName + " §f的传送请求"), false);

                // 通知目标玩家（如果还在线）
                ServerPlayerEntity target = src.getServer().getPlayerManager().getPlayer(req.targetUuid);
                if (target != null) {
                    target.sendMessage(Text.literal("§7[TPA] §e" + sender.getName().getString() + " §f取消了传送请求"), false);
                }

                TpaMod.LOGGER.info("[TPA] {} cancelled teleport request to {}", sender.getName().getString(), req.targetName);
                return 1;
            })
        );
    }
}
