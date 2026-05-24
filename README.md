# TPA Mod

Minecraft Fabric 1.20.1 传送请求 Mod。

## 功能

- `/tpa <玩家名>` - 向某玩家发送传送请求
- `/tpaccept` - 同意收到的传送请求（对方将传送至您的位置）
- `/tpadeny` - 拒绝收到的传送请求
- `/tpacancel` - 取消您发出的传送请求

## 规则

1. 玩家 A 向玩家 B 发送请求
2. 玩家 B 输入 `/tpaccept` 同意
3. 玩家 A 收到提示，开始 3 秒倒计时
4. 倒计时期间玩家 A 不得移动，否则传送取消
5. 3 秒后玩家 A 传送到玩家 B 的位置
6. 请求 60 秒内未处理则自动过期

## 传送效果

- 3 秒 Title 倒计时（屏幕中央）
- 螺旋粒子特效（END_ROD + ENCHANTED_HIT）
- 传送到达粒子爆发（PORTAL + HEART + BUBBLE_POP）
- 移动检测：传送期间移动自动取消

## 环境

- Minecraft 1.20.1
- Fabric Loader 0.15+
- Java 17+

## 安装

将 `tpa-mod-1.0.0.jar` 放入 `.minecraft/mods/` 目录即可。

## 许可

MIT License
