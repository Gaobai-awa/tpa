package net.tpa;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有 TPA 请求的存储和过期。
 *
 * 请求结构：
 *   senderUuid -> TpaRequest
 *   targetUuid -> senderUuid (双向索引)
 */
public class TpaConfig {

    /** 传送请求有效期：60 秒 */
    public static final int REQUEST_TTL_SECONDS = 60;

    /** 活跃的传送请求：senderUuid -> request */
    private static final Map<UUID, TpaRequest> pendingRequests = new ConcurrentHashMap<>();
    /** 反向索引：targetUuid -> senderUuid（方便目标玩家查找发给自己的请求） */
    private static final Map<UUID, UUID> targetToSender = new ConcurrentHashMap<>();

    // ─────────────── 请求存储 ───────────────

    /**
     * 创建一个传送请求。
     * @return true 表示创建成功，false 表示该玩家已有待处理请求
     */
    public static boolean createRequest(UUID senderUuid, String senderName,
                                        UUID targetUuid, String targetName) {
        if (pendingRequests.containsKey(senderUuid)) {
            return false; // 已有请求
        }
        TpaRequest req = new TpaRequest(senderUuid, senderName, targetUuid, targetName);
        pendingRequests.put(senderUuid, req);
        targetToSender.put(targetUuid, senderUuid);
        TpaMod.LOGGER.info("[TPA] {} -> {} request created (expires in {}s)",
            senderName, targetName, REQUEST_TTL_SECONDS);
        return true;
    }

    /** 获取发给指定玩家的待处理请求的发送者 UUID */
    public static UUID getPendingSenderFor(UUID targetUuid) {
        return targetToSender.get(targetUuid);
    }

    /** 获取指定发送者的请求 */
    public static TpaRequest getRequest(UUID senderUuid) {
        return pendingRequests.get(senderUuid);
    }

    /** 移除请求（同意/拒绝/取消/过期时调用） */
    public static void removeRequest(UUID senderUuid) {
        TpaRequest req = pendingRequests.remove(senderUuid);
        if (req != null) {
            targetToSender.remove(req.targetUuid);
        }
    }

    /** 发送者是否有待处理请求 */
    public static boolean hasPendingRequest(UUID senderUuid) {
        return pendingRequests.containsKey(senderUuid);
    }

    /** 检查玩家是否收到待处理请求 */
    public static boolean hasIncomingRequest(UUID targetUuid) {
        return targetToSender.containsKey(targetUuid);
    }

    // ─────────────── 自动过期（每 tick 检查） ───────────────

    public static void tickExpireRequests(MinecraftServer server) {
        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> {
            TpaRequest req = entry.getValue();
            if (now - req.createdAt > REQUEST_TTL_SECONDS * 1000L) {
                // 自动拒绝：通知双方
                ServerPlayerEntity sender = server.getPlayerManager().getPlayer(req.senderUuid);
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(req.targetUuid);

                if (sender != null) {
                    sender.sendMessage(Text.literal("§7[TPA] §f发送给 §e" + req.targetName + " §f的传送请求已超时自动取消"), false);
                }
                if (target != null) {
                    target.sendMessage(Text.literal("§7[TPA] §e" + req.senderName + " §f发送给您的传送请求已超时自动取消"), false);
                }
                targetToSender.remove(req.targetUuid);
                TpaMod.LOGGER.info("[TPA] Request {} expired", req.senderName);
                return true;
            }
            return false;
        });
    }

    // ─────────────── 请求数据类 ───────────────

    public static class TpaRequest {
        public final UUID senderUuid;
        public final String senderName;
        public final UUID targetUuid;
        public final String targetName;
        public final long createdAt;

        public TpaRequest(UUID senderUuid, String senderName, UUID targetUuid, String targetName) {
            this.senderUuid = senderUuid;
            this.senderName = senderName;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
