# 节拍打靶 Beat Tap Arena

面向 PICO Spatial SDK / PICO OS 6 的轻量节奏反应游戏。玩家使用捏合或手柄命中左、中、右三通道目标，通过连击逐步点亮空间舞台。

## 功能

- 5 首内置节拍曲目与练习、普通、挑战三档难度
- 左、中、右固定命中通道
- Perfect / Good / Miss、连击、最大连击与 1–5 星结算
- 手势与手柄等价输入
- 倒计时、暂停、保护暂停、结果与再来一次流程
- 面向坐姿与站姿的舒适前向布局

## 项目信息

- Android 包名：`com.pico.swan.beattap`
- 启动 Activity：`.platform.LaunchActivity`
- 应用名称：`节拍打靶`
- UI：PICO SpatialUI + `PicoTheme`

## 构建

完成 PICO Spatial SDK 与 Android SDK 配置后运行：

```powershell
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。
