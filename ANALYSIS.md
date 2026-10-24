# MT Manager 2.26.10 反编译分析 → MT-Lite 功能映射

反编译对象：`MT2.26.10.apk`（小米应用商店渠道包）

| 项 | 值 |
| --- | --- |
| 包名 | `bin.mt.plus` |
| 版本 | 2.26.10 (26101061) |
| minSdk / targetSdk | 21 / 30 |
| 入口 Activity | `bin.mt.plus.Main`（别名 `MainLightIcon` / `MainDarkIcon` / `MainNoBgIcon`） |
| dex | 5 个（classes.dex ~ classes5.dex，约 37 MB） |
| Activity / Service | 102 / 若干（类名已混淆为 `l.۫ۚ᩷` 等；主实现位于 `l` 包，8102 个类） |
| 资源 | 2961 条字符串、684 色值、7420 条样式、493 个布局（资源名已混淆为 `MT_Protector_res_0x7f…`，值可读） |
| 权限 | 25 项，含 `MANAGE_EXTERNAL_STORAGE`、`ACCESS_SUPERUSER`、`QUERY_ALL_PACKAGES`、`REQUEST_INSTALL_PACKAGES`、Shizuku/Dhizuku 权限 |

工具链：`jadx 1.5.6`（源码 + 资源解码）、`androguard`（Manifest/资源校验）。

## 从字符串资源提取的产品能力清单

| 领域 | MT 管理器中的能力（来自反编译资源） | MT-Lite 落地情况 |
| --- | --- | --- |
| 双窗口文件浏览 | 左右窗口、同步、历史、路径跳转 | ✅ 已具备并强化（独立历史/排序、活动窗指示条） |
| 排序 | 名称 / 大小 / 日期 / 类型 + 升降序 + 文件夹优先 | ✅ `SortDialog` + `FileItem.sortItems` |
| 书签 | 分组书签、置顶添加、滑动手势、书签栏 | ✅ `BookmarkStore`（JSON 持久化）、侧边栏直达 + 独立管理页 |
| 复制/移动 | 冲突处理（覆盖 / 跳过 / 应用到后续） | ✅ `ConflictResolver` + `ConflictDialog`（覆盖/跳过/保留两者） |
| 删除 | 回收站、自动清理 | ✅ 回收站（`.MT_Lite_Trash`）+ 永久删除开关 |
| 文件操作 | 重命名、批量重命名、属性、chmod | ✅ 均已实现（批量重命名支持查找替换并保留扩展名） |
| 搜索 | 按名称、按内容、高级搜索 | ✅ 递归搜索（深度 12）+ 文本内容搜索（≤1MB 文本文件） |
| 压缩包 | 创建（含密码/分卷）、解压、测试、添加、7z/rar 等 | ✅ 创建（zip，可选密码）、解压、添加到已有包、完整性测试（zip4j） |
| 文本编辑器 | 语法高亮、查找替换、翻译、自动缩进、平滑模式 | ✅ 查找/替换全部、行列统计、只读、自动换行（基础版） |
| 查看器 | 图片、视频、音频、字体、十六进制 | ✅ 图片查看器、十六进制查看器（均为只读） |
| APK 信息 | 包名/版本/SDK/权限/签名/组件 | ✅ 只读展示（`ApkInfoUtil`） |
| APK 安装 | Shizuku > Dhizuku > Root > 系统安装器 优先级 | ⚠️ 仅实现系统安装器调用（未引入 Shizuku/Dhizuku 安装链路） |
| 终端 / 脚本 | 终端模拟器、脚本执行器 | ✅ 终端（libsu，含历史） |
| 设置 | 外观主题、底部工具栏、回收站清理、权限说明 | ✅ 主题（系统/浅色/深色）、显示隐藏、默认路径/排序、回收站、删除确认、自动换行 |
| 远程管理 | FTP / SMB / WebDAV / OSS / S3 | ❌ 未实现（保留入口占位） |
| 插件系统 | MT-Plugin 加载与 API（`bin.mt.plugin.api.*`，源码可见） | ❌ 未实现（保留入口占位） |
| 屏幕取色 / Activity 记录 | ColorPickerActivity / ActivityRecordActivity | ❌ 未实现（保留入口占位） |
| **APK 逆向修改** | Dex 编辑器、Smali、Arsc 编辑器、Xml 批量替换、重打包、签名、混淆保护（VIP/服务端任务） | ❌ **明确不实现**（用户要求） |

## 关键源码结构（反编译所得）

```
sources/
├── bin/mt/plus/      Features2/Features3/R  —— 入口类与 JNI（`libmt2.so`），大部分逻辑已被重命名进 `l` 包
├── bin/mt/plugin/api/ MT 插件 SDK（PluginContext / PluginView / 菜单 / 正则封装）
├── bin/mt/json/      自带 JSON 解析器
└── l/                8102 个混淆类，承载主 UI 与业务逻辑（类名形如 C0255.java）
```

混淆方式：类名 `C####` / `l.۫ۚ᩷`、资源名统一替换为 `MT_Protector_res_0x7fXXXXXX`，因此**无法逐类照搬实现**；本次采用「**以反编译资源与字符串为功能依据、在 Compose 架构下重写实现**」的方式落地。

## 实现说明

- 所有代码均为 **Jetpack Compose + Kotlin 重写**，未拷贝混淆实现。
- 新增依赖：无（沿用 zip4j / libsu / Shizuku；设置与书签用 SharedPreferences + JSON 文件持久化）。
- 构建校验：`gradle assembleDebug` 通过（Android SDK 35 / AGP 8.7.2 / Kotlin 2.0.21）。
