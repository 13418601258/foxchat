# 狐狸信（FoxChat）

一款基于 Jetpack Compose 的**一对一聊天** Android 应用。两台设备通过同一个「配对密钥」建立联系，消息、头像、聊天背景都通过 Supabase 云端中转同步，支持文字、图片、语音、引用、撤回、打卡互动和定时提醒。

## 功能特性

| 模块 | 说明 |
|------|------|
| 一对一聊天 | 文字、图片、语音消息 |
| 引用回复 | 长按消息引用，发送后气泡内展示被引用内容 |
| 消息撤回 | 撤回后显示「消息已撤回」，可一键「重新编辑」 |
| 打卡互动 | 卡片式打卡：发起人发起主题，对方打卡后显示「打卡成功」 |
| 定时通知 | 自定义时间和内容，到点发送系统通知 |
| 共享聊天背景 | 任意一方更换背景，双方自动同步显示 |
| 自定义头像 | 登录时从相册选图，双方以圆形头像展示 |
| 消息通知 | 后台收到新消息、打卡时弹出横幅通知 |
| 云端同步 | Supabase 云端 + Room 本地数据库，离线可聊、联网即同步 |
| 数据工具 | 周报、聊天分析、记录导出、清空聊天 |

## 技术栈

- **UI**：Kotlin + Jetpack Compose + Material3
- **本地存储**：Room（消息、参与者、会话、周报）
- **云端**：Supabase（PostgREST 同步 + Storage 文件存储）
- **后台同步**：WorkManager（每 15 分钟一轮）
- **定时通知**：AlarmManager + BroadcastReceiver
- **网络**：OkHttp + org.json
- **AI 扩展**：登录页预留 AI 接口配置（默认 DeepSeek）

## 环境要求

- Android Studio（建议最新稳定版）
- JDK 17
- Android SDK Platform 34（`minSdk 26`）
- 一个 Supabase 项目

## 快速开始

### 1. 部署 Supabase 后端

在 Supabase 项目的 **SQL Editor** 中执行 `supabase/schema.sql` 的全部内容，它会创建：

- 数据表：`rooms`、`room_members`、`messages`、`weekly_reports`、`analysis_consent`
- RPC 函数：`pair_device`、`set_avatar`、`set_room_background`、`set_analysis_consent`
- 存储桶：`chat-media`（图片、语音、头像、背景），带行级安全策略

### 2. 配置密钥

在项目根目录的 `local.properties` 中填入 Supabase 信息（该文件含密钥，**不要提交到版本控制**）：

```properties
sdk.dir=你的Android SDK路径
supabase.url=https://你的项目.supabase.co
supabase.anonKey=你的anon/publishable key
ai.baseUrl=https://api.deepseek.com
ai.apiKey=你的AI接口密钥（可选）
```

### 3. 构建安装

```bash
# Windows
gradlew.bat assembleDebug

# 或直接在 Android Studio 中运行 app 模块
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## 使用方法

### 首次登录与配对

1. 打开狐狸信，在登录页输入**配对密钥**（至少 6 位，双方事先约定同一个）。
2. 选择你的**角色**（A 或 B），双方需选择不同角色。
3. 从**相册选择一张图片作为头像**。
4. （可选）勾选「允许数据分析」。
5. 点击开始。另一方用**相同的配对密钥**、选择**另一个角色**登录后，双方即完成配对，进入聊天页。

> 未配置 Supabase 时仍可离线聊天，但消息、头像、背景只保存在本机，无法跨设备同步。

### 聊天

- **发文字**：底部输入框输入，点击发送按钮。
- **发图片**：点输入框旁「+」号，选择相册或拍照。
- **发语音**：长按麦克风按钮录音，松开发送。
- **时间显示**：消息时间显示在气泡下方。

### 引用与撤回

- **引用**：长按某条消息 → 点「引用」，输入框上方会出现引用内容，发送后气泡内展示引用。
- **撤回**：长按自己发送的消息 → 点「撤回」，消息变为「消息已撤回」，点击可「重新编辑」恢复原文。

### 打卡

1. 点聊天页**左上角菜单**打开侧边栏 → 点「打卡」。
2. 输入打卡主题（如「今天读书了吗？」），实时预览卡片，点「发送打卡」。
3. 对方在聊天流中看到打卡卡片，点卡片上的「打卡」，输入内容发送后显示「打卡成功」。
4. 发起方卡片上显示「等待对方打卡」或「对方已打卡」。
5. 对方打卡时，若你在后台，会收到「有新的打卡」通知。

### 定时通知

1. 侧边栏 → 「定时通知」。
2. 输入提醒内容，选择日期和时间，点「添加」。
3. 到点后系统会发送通知（App 在后台或已关闭也能触发）。

### 共享聊天背景

1. 点聊天页右上角「⋮」→ 「设置背景」→ 从相册选图。
2. 背景会同步到云端，**双方**的聊天界面都会显示同一张背景。
3. 「重置背景」可恢复默认。

### 头像

- 头像在**登录时选择**，双方可见，聊天中以圆形头像展示。
- 顶部栏显示 App 图标，双方头像在消息气泡旁展示。

## 项目结构

```
app/src/main/java/com/wjy/foxchat/
├── ui/                    # 界面
│   ├── compose/           # Compose 组件（聊天页、气泡、打卡卡片、主题等）
│   ├── ChatActivity.kt    # 主聊天页
│   ├── ApiKeyActivity.kt  # 登录 / 配对页
│   ├── CheckinCreateActivity.kt        # 发起打卡
│   └── ScheduledNotificationActivity.kt # 定时通知
├── data/
│   ├── local/             # Room 实体、DAO、数据库
│   ├── remote/            # Supabase 同步与存储
│   └── repository/        # 统一数据层
├── notification/          # 通知管理、定时调度
└── analysis/              # 后台同步 Worker
supabase/
└── schema.sql             # 后端表 / 函数 / 存储桶
```

## 权限说明

| 权限 | 用途 |
|------|------|
| 通知（POST_NOTIFICATIONS） | 消息、打卡、定时通知，Android 13+ 需运行时授权 |
| 精确闹钟（SCHEDULE_EXACT_ALARM） | 定时通知精确到分钟，Android 12+ 需声明；未开启时会降级为不精确提醒 |
| 相册 / 相机 | 选择头像、发送图片、设置背景 |
| 录音 | 发送语音消息 |

## 注意事项

1. **消息通知非实时**：后台同步由 WorkManager 每 15 分钟执行一轮，通知最多延迟约 15 分钟（受系统省电策略影响可能更长）。如需秒级实时推送，需接入 FCM。
2. **首次同步不弹通知**：新设备首次同步历史消息时，不会为历史消息刷通知。
3. **通知重要级别**：通知渠道的「重要级别」在系统创建后会被缓存，如需调整级别，请卸载重装应用。
4. **local.properties 含密钥**：请勿提交到 Git，已配置的密钥注意保密。
