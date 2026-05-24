package net.tpa.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.tpa.TpaConfig;
import net.tpa.TpaMod;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.*;

public class TpaCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("tpa")
            .requires(src -> src.hasPermissionLevel(0))  // 任何玩家可用
            .then(argument("player", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    // 建议在线玩家名
                    for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                        if (!p.getName().getString().equals(ctx.getSource().getName())) {
                            builder.suggest(p.getName().getString());
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    ServerPlayerEntity sender = src.getPlayerOrThrow();
                    String targetName = StringArgumentType.getString(ctx, "player");

                    // 找到目标玩家
                    ServerPlayerEntity target = src.getServer().getPlayerManager().getPlayer(targetName);
                    if (target == null) {
                        sender.sendMessage(Text.literal("§c[TPA] 找不到玩家 §e" + targetName + "§c，他们可能不在线"), false);
                        return 0;
                    }
                    if (target == sender) {
                        sender.sendMessage(Text.literal("§c[TPA] 不能向自己发送传送请求"), false);
                        return 0;
                    }

                    // 检查是否已有待处理请求
                    if (TpaConfig.hasPendingRequest(sender.getUuid())) {
                        sender.sendMessage(Text.literal("§c[TPA] 您已有待处理的传送请求，请等待处理或使用 /tpacancel 取消后再试"), false);
                        return 0;
                    }

                    // 创建请求
                    boolean ok = TpaConfig.createRequest(
                        sender.getUuid(), sender.getName().getString(),
                        target.getUuid(), target.getName().getString()
                    );

                    if (!ok) {
                        sender.sendMessage(Text.literal("§c[TPA] 创建请求失败，您可能已有待处理请求"), false);
                        return 0;
                    }

                    // 通知发送者
                    sender.sendMessage(Text.literal("§a[TPA] §f已向 §e" + target.getName().getString() + " §f发送传送请求，请等待对方同意"), false);
                    sender.sendMessage(Text.literal("§7请求将在 60 秒后自动过期取消"), false);

                    // 通知目标玩家
                    target.sendMessage(Text.literal("§6═══[TPA 传送请求]═══"), false);
                    target.sendMessage(Text.literal("§e" + sender.getName().getString() + " §f请求传送到您的位置"), false);
                    target.sendMessage(Text.literal("§a/tpaccept §f- 同意  |  §c/tpadeny §f- 拒绝"), false);
                    target.sendMessage(Text.literal("§7请求将在 60 秒后自动过期"), false);

                    TpaMod.LOGGER.info("[TPA] {} requested teleport to {}", sender.getName().getString(), target.getName().getString());
                    return 1;
                })
            )
            // 无参数时显示帮助
            .executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                player.sendMessage(Text.literal("§6═══[TPA 帮助]═══"), false);
                player.sendMessage(Text.literal("§e/tpa <玩家名> §f- 向某玩家发送传送请求"), false);
                player.sendMessage(Text.literal("§a/tpaccept §f- 同意收到的传送请求"), false);
                player.sendMessage(Text.literal("§c/tpadeny §f- 拒绝收到的传送请求"), false);
                player.sendMessage(Text.literal("§7/tpacancel §f- 取消您发出的传送请求"), false);
                return 1;
            })
        );
    }
}
