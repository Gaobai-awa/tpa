package net.tpa;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * TPA 传送倒计时系统。
 * 照搬 Home_mod / RTP 的倒计时逻辑：
 * - 3 秒倒计时（60 tick）
 * - 螺旋粒子特效
 * - 移动检测（取消传送）
 * - Title 倒计时显示
 * - 传送后粒子爆发
 */
public class TpaCountdown {

    /** 3 秒倒计时 = 60 tick */
    private static final int COUNTDOWN_TICKS = 60;
    /** 每隔 20 tick 更新一次 Title（精准每秒） */
    private static final int TITLE_INTERVAL = 20;
    /** 移动检测阈值：0.2 格 */
    private static final double MOVE_THRESHOLD_SQ = 0.04;

    private static final Map<UUID, TpaCountdownState> countdownTasks = new HashMap<>();

    // ─────────────── 公开 API ───────────────

    /**
     * 开始向目标玩家的传送倒计时。
     * @param from 发起者（将被传送的玩家）
     * @param toX 目标 X
     * @param toY 目标 Y
     * @param toZ 目标 Z
     * @param toWorld 目标世界 ID
     * @param targetPlayerName 目标玩家名（用于消息显示）
     * @return true 成功启动，false 已有倒计时在进行
     */
    public static boolean startCountdown(ServerPlayerEntity from,
                                          double toX, double toY, double toZ,
                                          String toWorld,
                                          String targetPlayerName) {
        UUID uuid = from.getUuid();
        if (countdownTasks.containsKey(uuid)) {
            return false;
        }

        TpaCountdownState state = new TpaCountdownState(from, toX, toY, toZ, toWorld, targetPlayerName);
        countdownTasks.put(uuid, state);

        // 初始 Title 显示
        sendTitle(from,
            Text.literal("§b§l传送中..."),
            Text.literal("§7目标: §e" + targetPlayerName + " §7- §e§l3"));

        TpaMod.LOGGER.info("[TPA] Countdown started for {} -> {} ({}, {}, {})",
            from.getName().getString(), targetPlayerName,
            String.format("%.1f", toX), String.format("%.1f", toY), String.format("%.1f", toZ));
        return true;
    }

    public static boolean isActive(UUID uuid) {
        return countdownTasks.containsKey(uuid);
    }

    // ─────────────── 每 tick 入口 ───────────────

    public static void tickAll(MinecraftServer server) {
        if (countdownTasks.isEmpty()) return;

        Iterator<Map.Entry<UUID, TpaCountdownState>> iter = countdownTasks.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<UUID, TpaCountdownState> entry = iter.next();
            TpaCountdownState state = entry.getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                iter.remove();
                TpaMod.LOGGER.info("[TPA] Countdown cancelled (player disconnected)");
                continue;
            }

            // 玩家死亡 → 取消
            if (!player.isAlive()) {
                player.sendMessage(Text.literal("§c[TPA] 你在传送倒计时期间死亡，传送已取消"), false);
                clearTitle(player);
                iter.remove();
                continue;
            }

            // 移动检测
            Vec3d current = player.getPos();
            if (current.squaredDistanceTo(state.startPos) > MOVE_THRESHOLD_SQ) {
                player.sendMessage(Text.literal("§c[TPA] 你在传送倒计时期间移动了！传送已取消！"), false);
                clearTitle(player);
                sendTitle(player, Text.literal("§c传送取消"), Text.literal("§7移动了"));
                iter.remove();
                TpaMod.LOGGER.info("[TPA] Countdown cancelled (player moved)");
                continue;
            }

            // 严格递增计数器
            state.tickCounter++;
            state.remainingTicks--;

            // 每 20 tick = 每秒更新 Title
            if (state.tickCounter % TITLE_INTERVAL == 0) {
                int seconds = state.remainingTicks / TITLE_INTERVAL;
                if (seconds == 1) {
                    sendTitle(player, Text.literal("§a即将传送"), Text.literal("§e§l1"));
                } else if (seconds == 2) {
                    sendTitle(player, Text.literal("§b传送中..."), Text.literal("§e§l2"));
                } else {
                    sendTitle(player, Text.literal("§b传送中..."), Text.literal("§7" + seconds));
                }
            }

            // 每 2 tick 生成螺旋粒子
            if (state.tickCounter % 2 == 0) {
                spawnSpiralParticles(player, state.tickCounter / 2);
            }

            // 倒计时结束 → 执行传送
            if (state.remainingTicks <= 0) {
                executeTeleport(player, state);
                iter.remove();
            }
        }
    }

    // ─────────────── 传送执行 ───────────────

    private static void executeTeleport(ServerPlayerEntity player, TpaCountdownState state) {
        clearTitle(player);

        // 解析目标世界
        ServerWorld targetWorld = player.getServerWorld();
        if (state.worldId != null && !state.worldId.isEmpty()) {
            try {
                MinecraftServer server = player.getServer();
                net.minecraft.util.Identifier ident = net.minecraft.util.Identifier.tryParse(state.worldId);
                if (ident != null && server != null) {
                    // 遍历所有世界
                    for (ServerWorld w : server.getWorlds()) {
                        if (w.getRegistryKey().getValue().equals(ident)) {
                            targetWorld = w;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                targetWorld = player.getServerWorld();
            }
        }

        // 执行传送
        player.teleport(targetWorld, state.toX, state.toY, state.toZ,
            player.getYaw(), player.getPitch());

        // 传送到达粒子爆发
        spawnArrivalParticles(player);

        player.sendMessage(Text.literal("§a[TPA] §f已传送至 §e" + state.targetPlayerName), false);
        TpaMod.LOGGER.info("[TPA] Player {} teleported to {}", player.getName().getString(), state.targetPlayerName);
    }

    // ─────────────── 螺旋粒子 ───────────────

    private static final double SPIRAL_RADIUS = 0.9;
    private static final double SPIRAL_HEIGHT_PER_TICK = 0.08;
    private static final double SPIRAL_ANGLE_PER_TICK = Math.toRadians(25);

    private static void spawnSpiralParticles(ServerPlayerEntity player, int step) {
        double x = player.getX();
        double y = player.getY() + 0.1;
        double z = player.getZ();
        double angle = step * SPIRAL_ANGLE_PER_TICK;
        double height = step * SPIRAL_HEIGHT_PER_TICK;

        // 内圈粒子（END_ROT，红色）
        double ix = x + Math.cos(angle) * SPIRAL_RADIUS;
        double iy = y + height;
        double iz = z + Math.sin(angle) * SPIRAL_RADIUS;
        player.getServerWorld().spawnParticles(
            ParticleTypes.END_ROD, ix, iy, iz, 1, 0, 0, 0, 0
        );

        // 外圈粒子（ENCHANTED_HIT，闪绿光）
        double ox = x + Math.cos(angle + Math.PI) * SPIRAL_RADIUS;
        double oy = y + height;
        double oz = z + Math.sin(angle + Math.PI) * SPIRAL_RADIUS;
        player.getServerWorld().spawnParticles(
            ParticleTypes.ENCHANTED_HIT, ox, oy, oz, 1, 0, 0, 0, 0
        );

        // 中心上升粒子（REVERSE_PORTAL，紫蓝色）
        player.getServerWorld().spawnParticles(
            ParticleTypes.REVERSE_PORTAL, x, y + height * 0.5, z, 1, 0, 0, 0, 0
        );
    }

    private static void spawnArrivalParticles(ServerPlayerEntity player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        ServerWorld world = player.getServerWorld();

        // 中心爆发（PORTAL，紫蓝色）
        for (int i = 0; i < 30; i++) {
            world.spawnParticles(ParticleTypes.PORTAL,
                x + (Math.random() - 0.5) * 2.0,
                y + Math.random() * 1.5,
                z + (Math.random() - 0.5) * 2.0,
                1, 0, 0, 0, 0);
        }
        // 心形粒子（HEART，粉色）
        world.spawnParticles(ParticleTypes.HEART,
            x + (Math.random() - 0.5) * 1.5,
            y + 0.5 + Math.random() * 1.0,
            z + (Math.random() - 0.5) * 1.5,
            8, 0, 0, 0, 0);
        // 上升气泡（BUBBLE_POP）
        world.spawnParticles(ParticleTypes.BUBBLE_POP,
            x + (Math.random() - 0.5) * 1.0,
            y + Math.random() * 0.8,
            z + (Math.random() - 0.5) * 1.0,
            5, 0, 0, 0, 0);
    }

    // ─────────────── Title 发送 ───────────────

    private static void sendTitle(ServerPlayerEntity player, Text title, Text subtitle) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
    }

    private static void clearTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 0, 0));
    }

    // ─────────────── 状态类 ───────────────

    private static class TpaCountdownState {
        public final String targetPlayerName;
        public final String worldId;
        public final double toX, toY, toZ;
        public final Vec3d startPos;
        /** 独立递增计数器（不受服务器卡顿影响） */
        public int tickCounter = 0;
        /** 剩余 tick 数 */
        public int remainingTicks = COUNTDOWN_TICKS;

        public TpaCountdownState(ServerPlayerEntity player,
                                 double toX, double toY, double toZ,
                                 String worldId, String targetPlayerName) {
            this.toX = toX;
            this.toY = toY;
            this.toZ = toZ;
            this.worldId = worldId;
            this.targetPlayerName = targetPlayerName;
            this.startPos = player.getPos();
        }
    }
}
