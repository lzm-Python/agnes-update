# Agnes 工作台 · 源码

AI 创作工具，一套前端引擎同时挂多家模型服务：图片生成、视频生成、分镜剧本。
安卓端是 WebView 外壳，桌面端是 Electron，鸿蒙端是 ArkTS + Web 组件，三者共用同一份 `index.html`。

## 目录

```
src/android/        安卓外壳工程（Java + assets/index.html + build.ps1）
src/electron/pc/    Windows / Linux 64 位工程
src/electron/pc32/  Windows 32 位工程
src/harmony/        鸿蒙 ArkTS 工程（HAP）
src/versions/       版本前端快照
```

## 核心思路

界面与全部业务逻辑都在 `assets/index.html` 这一个文件里，外壳只负责注入原生能力：

- 安卓：`MainActivity.java` 向 WebView 注册 `Android` 桥，承接保存文件、剪贴板、退出确认。
- 桌面：`preload.js` 暴露 `window.bridge`，`main.js` 处理窗口与下载落盘。
- 鸿蒙：`Index.ets` 用 `Web` 组件加载 `rawfile/index.html`，下载走 `setWebDownloadDelegate`。

所以改功能基本只改 `index.html`，外壳代码很少动。

## 构建

安卓（需 JDK 17 + Android SDK build-tools 34、platform android-33）：

```
powershell -File src/android/build.ps1
```

产物 `build/AgnesVideo.apk`。签名库与口令不在本仓库，自行用 `keytool` 生成：

```
keytool -genkeypair -keystore agnes.jks -alias agnes -keyalg RSA -keysize 2048 -validity 36500
```

桌面：

```
cd src/electron/pc && npm install && npx electron-builder --win nsis portable
cd src/electron/pc && npx electron-builder --linux deb tar.gz --x64
```

鸿蒙：装好 HarmonyOS Command Line Tools 后，设 `DEVECO_CLI_CLT_PATH` 与 `DEVECO_SDK_HOME`，在 `src/harmony` 执行 `devecocli build`。

## 引擎

内置九家：Pavo、DeepSeek、智谱、硅基流动、百炼、火山方舟、OpenRouter、Groq、Kimi。
全部走 OpenAI 兼容协议，Key 由用户自己填在设置页，只存本机 localStorage，不上传。

## 不包含的东西

keystore、签名口令、发布用 token、构建产物与依赖目录。要发版自己配一套。

## 协议

MIT，见 LICENSE。
