# VibrationBlocker

一个 LSPosed 模块，在指定时段或手动模式下屏蔽 Android 系统的振动。

> ⚠️ **本项目在魅族 22（Flyme 12 / Android 16）上开发并测试**，未在其他机型或 ROM 上验证。理论兼容 Android 8 ~ 16，但可能存在机型差异。

## ✨ 特性

- **全局拦截**：Hook 系统 `VibratorManagerService`，拦截打字、视频长按倍速、音量条、通知、RealityTap 线性马达特效等所有振动
- **定时屏蔽**：自定义开始 / 结束时间，支持跨天（如 22:00 - 07:00）
- **手动模式**：随时「全部屏蔽」或「取消屏蔽」，与定时互斥
- **白名单**：指定应用不参与屏蔽，支持搜索、图标加载
- **Material 3 UI**：卡片式布局，自动适配深色 / 浅色模式
- **实时生效**：修改配置后无需重启目标 App

## 📋 系统要求

Android:8.0 ~ 16（API 26+）
框架：LSPosed 2.0+ 
作用域：系统框架（system）

## 🔧 安装

1. 下载 [Releases] 中最新 APK
2. 安装到手机
3. 打开 App，授予 root 权限
4. 打开 LSPosed 管理器 → 模块 → 启用「振动屏蔽」
5. 作用域勾选「系统框架」
6. **重启手机**

## 📖 使用

### 定时屏蔽
设置开始和结束时间，例如 `22:00 - 07:00`。跨天时段自动识别。点击时间行弹出时间选择器。

### 手动模式
打开开关后，选择「全部屏蔽」或「取消屏蔽」。手动模式与定时互斥，开启后定时设置不再生效。

### 白名单
点击「白名单」进入应用列表，勾选不需要屏蔽的应用。被选中的应用无论在什么时段都不会被拦截。典型场景：

- **输入法**（如 `com.sohu.inputmethod.sogou.meizu`）：保留打字振动
- **电话 / Telecom**（如 `com.android.server.telecom`）：保留来电振动

> 不同 ROM 来电振动的发起方包名不同，如遇到来电不震动，可将 `电话`、`Dialer`、`Telecom` 相关项都加入白名单。

## ❓ 常见问题

**Q: 打字没振动了怎么办？**
把输入法加入白名单。

**Q: 来电没振动了怎么办？**
把「电话」或 `com.android.server.telecom` 加入白名单。可先用 LSPosed 日志查看来电时 `opPkg=` 的值，再精准添加。

**Q: 修改配置后不生效？**
配置实时生效，无需重启。若仍无效，检查 LSPosed 日志中是否出现 `VibBlocker` 关键字。

**Q: 首次启用后一定要重启吗？**
是。Hook 系统框架只能在开机时注入，首次激活必须重启手机。

**Q: 显示「未激活」但功能正常？**
尝试重新安装 APK 并重启手机。若问题持续，请在 Issues 中附上 LSPosed 日志。

## ⚠️ 兼容性说明

**本项目仅在以下环境开发并测试：**

设备：魅族 22
ROM：Flyme 12.6.0.0A
Android：16（API 36）
Root 方案：APatch
LSPosed：2.1.0

**其他 ROM 的可能差异：**

- **类名差异**：部分定制 ROM（MIUI / HyperOS / ColorOS 等）可能修改了 `VibratorManagerService` 的包路径或方法名。本项目代码已尝试兼容 AOSP 标准类名与常见变体，但无法覆盖所有厂商定制。
- **振动路径差异**：少数厂商使用私有 `vendor` 接口直接驱动马达，绕过 Android Framework，这类振动可能无法被拦截。
- **来电振动包名差异**：来电振动的 `opPkg` 因 ROM 而异（`com.android.dialer`、`com.android.server.telecom`、`com.android.incallui` 等），需根据实际情况加入白名单。
- **Android 8 ~ 11**：使用 `VibratorService` 而非 `VibratorManagerService`，代码已兼容，但未实测。

## 🛠 从源码编译

```bash
git clone https://github.com/ExEafnv/VibrationBlocker.git
cd VibrationBlocker
./gradlew assembleRelease
```

## 📄 许可
本项目基于 GPL-3.0 许可开源。

🙏 致谢
LSPosed — 框架

libsu — Root 调用

Material Components for Android — UI
