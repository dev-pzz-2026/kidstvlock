# 儿童锁屏 · 电视答题解锁

一款专为电视（Android TV）设计的儿童锁屏应用，开机自动启动计时，到时锁屏，需答对题目才能解锁。

## 项目简介

- **答题解锁**：锁屏时显示汉字/题目，答对指定数量题目才能解锁。
- **答题模式**：累计答题解锁 / 连续答题解锁（Web 配置页可选，立即生效）。
- **开机自启**：电视开机后自动启动计时（默认开启）。
- **定时锁屏**：10/15/30/45/60/90/120 分钟可选，默认 10 分钟，到时自动锁屏。
- **重启仍锁**：锁屏后即使关机重启，电视仍保持锁屏状态。
- **题库丰富**：内置 1-9 年级语文/数学/英语/科学题库及背诵篇目。
- **错题库**：答错收入错题库（默认 `错题库.json`），答对移除；Web 配置页可选择默认题库。
- **可配**：通过 Web 配置页（端口 8080）调整参数。

## 适用人群与场景

- 家长控制儿童观看电视时长。
- 教育机构/培训机构限制娱乐时间。
- 需要答题解锁的儿童教育场景。

## 源码结构

```
kidslock/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/kidslock/app/
│   │   │   ├── KidsLockApplication.java    # 应用进程入口，启动即启动 Web 监听和计时服务
│   │   │   ├── MainActivity.java           # 设置界面，启动时检查并启动 Web 监听服务
│   │   │   ├── LockScreenActivity.java     # 锁屏答题界面，答完关闭时检查端口监听
│   │   │   ├── LockService.java            # 计时前台服务，支持 AlarmManager 闹钟唤醒
│   │   │   ├── BootReceiver.java           # 开机自启动，处理闹钟唤醒
│   │   │   ├── CharacterBank.java          # 题库加载，支持错题库增删
│   │   │   ├── PrefManager.java            # 设置持久化
│   │   │   ├── WebConfigServer.java        # Web 配置服务（端口 8080）
│   │   │   └── WebConfigService.java       # Web 配置服务封装
│   │   ├── res/
│   │   │   ├── layout/                     # 布局 XML
│   │   │   ├── drawable/                   # 按钮选择器、图标、TV banner
│   │   │   ├── mipmap-*/                   # 启动图标
│   │   │   └── values/                     # 颜色、尺寸、字符串、主题
│   │   └── assets/                         # 题库 JSON（含 错题库.json 模板）
├── build.gradle
├── settings.gradle
├── gradle.properties
├── LICENSE
└── README.md
```

## 二次开发注意点

- 包名：`com.kidslock.app`。
- 默认观看时长：10 分钟（`PrefManager.DEFAULT_WATCH_LIMIT_MIN`）。
- 默认答题模式：累计答题解锁（`cumulative`），可在 Web 配置页切换为连续答题解锁（`continuous`）。
- 锁屏仅通过答题解锁，无家长 PIN / 密码入口。
- 默认错题库文件：`错题库.json`（`filesDir` 下，不存在则自动创建）。
- 常驻通知：`LockService` 前台服务，需通知权限。
- 开机自启：`BootReceiver` 监听 `BOOT_COMPLETED`，闹钟唤醒监听 `com.kidslock.app.ACTION_WAKEUP`。
- Web 配置：端口 **8080**（固定），局域网访问 `http://<电视IP>:8080`。
- Web 配置页支持：观看时长、解锁题数、锁定时长、锁屏间隔、重新锁屏延迟、开机自启、答题模式、默认题库选择。
- 题库：`assets/` 下 JSON，`CharacterBank` 解析；错题库增删由 `CharacterBank.addToWrongBank` / `removeFromWrongBank` 实现。
- 修改图标：更新 `mipmap-*/ic_launcher*.png` 与 `drawable/tv_banner.png`（320×180，图标仅，无全文）。
- 二次开发请保留 MIT 协议。

## 打包与安装

### 环境

- JDK 17
- Android SDK (API 34)
- Gradle 8.2

### 编译

```bash
export JAVA_HOME=<JDK路径>
export ANDROID_HOME=<SDK路径>
./gradlew assembleRelease
```

Windows：

```powershell
$env:JAVA_HOME="D:\data\Java\jdk-17.0.2"
$env:ANDROID_HOME="D:\data\Android\Sdk"
.\gradlew.bat assembleDebug --no-daemon
```

### 安装

1. 将 APK 复制到 U 盘。
2. U 盘插入电视 USB 口。
3. 电视打开「高清播放器」或「文件管理」。
4. 找到 APK，点击安装。
5. 安装前需在「设置 → 账号与安全」中开启「允许安装未知来源应用」。

### ADB 安装与授权命令

```bash
# 1. 连接电视
adb connect <电视IP>:5555

# 2. 安装 APK（覆盖安装）
adb install -r kidslock-release.apk

# 3. 授予开机自启权限（Android 10+）
adb shell appops set com.kidslock.app RUN_IN_BACKGROUND allow
adb shell appops set com.kidslock.app RUN_ANY_IN_BACKGROUND allow

# 4. 授予悬浮窗权限
adb shell appops set com.kidslock.app SYSTEM_ALERT_WINDOW allow

# 4b. 授予通知权限（Android 13+）
adb shell pm grant com.kidslock.app android.permission.POST_NOTIFICATIONS

# 4c. 授予忽略电池优化
adb shell dumpsys deviceidle whitelist +com.kidslock.app

# 4d. 授予开机自启（部分机型需要）
adb shell pm grant com.kidslock.app android.permission.RECEIVE_BOOT_COMPLETED

# 4e. 授予网络权限（部分机型需要）
adb shell pm grant com.kidslock.app android.permission.INTERNET
adb shell pm grant com.kidslock.app android.permission.ACCESS_WIFI_STATE
adb shell pm grant com.kidslock.app android.permission.ACCESS_NETWORK_STATE

# 4f. 授予全屏意图权限
adb shell pm grant com.kidslock.app android.permission.USE_FULL_SCREEN_INTENT

# 4g. 授予禁用锁屏权限
adb shell pm grant com.kidslock.app android.permission.DISABLE_KEYGUARD

# 5. 授予电池优化白名单（防止被系统杀掉）
adb shell dumpsys deviceidle whitelist +com.kidslock.app

# 6. 开启无障碍服务（增强锁定效果）
adb shell settings put secure enabled_accessibility_services com.kidslock.app/com.kidslock.app.KidsLockAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 7. 启动应用
adb shell am start -n com.kidslock.app/.MainActivity

# 8. 查看日志（调试用）
adb logcat -s LockService KidsLockAccessibilityService BootReceiver WebConfigServer
```

> **注意**：部分电视品牌（小米、TCL 等）可能需要在电视设置中手动授权「开机自启」和「后台运行」，ADB 命令仅作为辅助。

### 权限

安装后需在电视设置中授予：
- 开机自启
- 后台运行
- 悬浮窗（部分机型）
- 通知权限（Android 13+，用于计时通知）

声明的权限列表（AndroidManifest.xml）：
- `RECEIVE_BOOT_COMPLETED` — 开机自启
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — 前台服务
- `WAKE_LOCK` — 唤醒锁
- `SYSTEM_ALERT_WINDOW` — 悬浮窗
- `POST_NOTIFICATIONS` — 通知权限（Android 13+）
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — 忽略电池优化
- `QUERY_ALL_PACKAGES` — 查询已安装应用
- `INTERNET` / `ACCESS_WIFI_STATE` / `ACCESS_NETWORK_STATE` — 网络
- `USE_FULL_SCREEN_INTENT` — 全屏意图
- `DISABLE_KEYGUARD` — 禁用锁屏

电视建议将本应用加入白名单，避免被系统清理。

## 题库 JSON 格式

题库文件放置于 `app/src/main/assets/`，Web 配置页可上传自定义题库。

### 选择题格式

```json
{
  "bank_id": "grade1_chinese",
  "bank_name": "一年级语文",
  "grade": 1,
  "subject": "chinese",
  "subject_name": "语文",
  "version": 1,
  "updated_at": "2026-11-01",
  "total": 120,
  "questions": [
    {
      "id": 1001,
      "question": "字母 b 的正确读音",
      "type": "拼音",
      "difficulty": 1,
      "options": ["a", "b", "o", "e"],
      "correct_index": 1,
      "explanation": "辅音字母 b、p、m、f 等",
      "tags": ["字母", "声母"]
    }
  ]
}
```

### 背诵篇目格式

```json
{
  "bank_id": "recitation_1_6",
  "bank_name": "背诵篇目1-6年级",
  "subject": "recitation",
  "subject_name": "背诵",
  "grade_range": "1-6",
  "version": 1,
  "updated_at": "2026-11-01",
  "total": 75,
  "pieces": [
    {
      "id": 1,
      "title": "静夜思",
      "first_line": "床前明月光",
      "author": "李白",
      "dynasty": "唐",
      "genre": "五言绝句",
      "key_lines": "床前明月光，疑是地上霜。举头望明月，低头思故乡。",
      "note": "李白诗歌，表达思乡之情"
    }
  ]
}
```

### 错题库格式（自动生成）

默认文件名 `错题库.json`，位于应用内部存储目录。答错时自动写入，答对时自动移除。格式同选择题：

```json
{
  "bank_id": "wrong_bank",
  "bank_name": "错题库",
  "grade": 0,
  "subject": "wrong",
  "subject_name": "错题",
  "version": 1,
  "total": 0,
  "questions": []
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `bank_id` | string | 是 | 题库唯一标识 |
| `bank_name` | string | 是 | 题库显示名称 |
| `grade` / `grade_range` | int/string | 是 | 年级或年级范围 |
| `subject` | string | 是 | 学科标识（chinese/math/english/science/recitation/wrong） |
| `questions` | array | 选择题必填 | 选择题列表 |
| `pieces` | array | 背诵题必填 | 背诵篇目列表 |
| `id` | int | 是 | 题目/篇目 ID |
| `question` | string | 选择题必填 | 题目文本 |
| `options` | array | 选择题必填 | 选项列表 |
| `correct_index` | int | 选择题必填 | 正确答案索引 |
| `title` / `first_line` / `author` | string | 背诵题必填 | 篇目信息 |
| `key_lines` | string | 背诵题必填 | 关键诗句 |

## 许可证

本项目采用 MIT License 开源，详见 [LICENSE](LICENSE)。

---

**开机自启 · 答题解锁**
