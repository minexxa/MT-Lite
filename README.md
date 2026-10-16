# MT-Lite

轻量级双窗口文件管理器，灵感来自 [MT管理器](https://mt.cc)。

使用 **Jetpack Compose** + **Kotlin** 构建，优先支持 Root 与 Shizuku。

## 功能（MVP）

- **双窗口文件浏览器**：左右独立面板，点击切换活动窗口
- **基础文件操作**：复制 / 剪切 / 粘贴 / 删除 / 新建文件与文件夹
- **导航**：前进 / 后退 / 上级 / 同步窗口 / 路径跳转
- **ZIP 浏览**：直接进入 .zip / .apk 内部查看（只读）
- **简单文本编辑器**：支持常见文本格式打开与保存
- **侧边栏**：存储信息 + 工具入口（预留）
- **权限**：请求 MANAGE_EXTERNAL_STORAGE，集成 libsu (Root) 与 Shizuku API

## 技术栈

- Kotlin 2.0 + Jetpack Compose
- Material 3
- libsu (Root)
- Shizuku
- Zip4j
- 仅编译 `arm64-v8a`

## 构建

```bash
./gradlew assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

GitHub Actions 工作流会自动构建 arm64 版本并上传 Artifact。

## 后续计划

- [ ] 完整 Root / Shizuku 文件操作后端
- [ ] 压缩包内增删改
- [ ] 多选批量操作优化
- [ ] 主题切换
- [ ] 书签系统
- [ ] 基础 APK 信息查看

## License

MIT
