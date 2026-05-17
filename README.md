# EasyBox - 应用盒子

EasyBox 是一个 Android 应用盒子，内置多种小游戏和实用工具，支持人机对战和好友联机。

## 功能特色

- **应用盒子框架**：主界面图标网格，支持拖拽排序、自定义插件
- **中国象棋**：完整规则引擎 + Minimax AI（简单/中等/困难）+ MQTT 联机对战
- **国际象棋**：完整规则引擎（王车易位/吃过路兵/升变）+ AI + 联机对战
- **斗地主**：54张标准牌，叫地主机制，牌型识别，人机对战
- **随机转盘**：自定义选项和权重，预设保存/加载，动画旋转

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material3
- **数据库**：Room + DataStore
- **网络**：MQTT（HiveMQ 客户端 + emqx.io 公共代理）
- **AI**：Minimax + Alpha-Beta 剪枝
- **架构**：MVVM

## 开发环境

- Android Studio Hedgehog+
- Gradle 8.4
- Kotlin 1.9.22
- Compose BOM 2024.02.00
- minSdk 26 / targetSdk 34

## 快速开始

1. 用 Android Studio 打开项目目录
2. 等待 Gradle 同步完成
3. 连接安卓手机或启动模拟器
4. 点击 Run 运行

## 联机说明

- 联机通过 MQTT 协议进行
- 创建房间获取 6 位房间码
- 分享给好友即可加入对战
- 首次使用需输入昵称

## 许可证

MIT License
