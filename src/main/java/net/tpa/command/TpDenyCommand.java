package net.tpa.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.tpa.TpaConfig;
import net.tpa.TpaMod;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.*;

public class TpDenyCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("tpadeny")
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

                // 移除请求
                TpaConfig.removeRequest(senderUuid);

                // 通知目标玩家（拒绝者）
                target.sendMessage(Text.literal("§c[TPA] §f您已拒绝 §e" + req.senderName + " §f的传送请求"), false);

                // 通知发送者
                ServerPlayerEntity sender = src.getServer().getPlayerManager().getPlayer(req.senderUuid);
                if (sender != null) {
                    sender.sendMessage(Text.literal("§c[TPA] §e" + target.getName().getString() + " §f拒绝了您的传送请求"), false);
                }

                TpaMod.LOGGER.info("[TPA] {} denied teleport request from {}", target.getName().getString(), req.senderName);
                return 1;
            })
        );
    }
}
