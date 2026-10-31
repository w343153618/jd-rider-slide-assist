# 大爷骑火箭 · 小火箭图标

## 生成与来源

- 使用内置 `image_gen` 生成（按 imagegen 技能的默认 built-in 模式），没有使用 API/CLI、外部图标库或第三方品牌素材。
- 生成时间：2026-10-31。工具原始产物为1254×1254、不含alpha的PNG；原始结果保留在工具默认生成目录，同时已复制进本仓库，避免依赖工作区外路径。
- 原始副本：`assets/branding/rocket-icon-original.png`。
- 1024×1024源图：`assets/branding/rocket-icon-source.png`。
- 512×512运行资源：`app/src/main/res/drawable-nodpi/rocket_icon_art.png`。
- 只用macOS `sips` 做尺寸打包，不改变构图、颜色或绘制内容。

## 图形和安卓打包

原创暖象牙色背景、珊瑚橙圆润火箭、青绿色圆舷窗小脸与柔和短火焰。图像无文字、水印、京东或微信标志。已目视检查主体和边缘，无透明黑边。

- API24–25的普通图标：`mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi/ic_launcher.png`，分别为48/72/96/144/192像素。
- API26+自适应图标：`mipmap-anydpi-v26/ic_launcher.xml`。
- 自适应前景使用同一512像素图片，经`drawable/rocket_icon_foreground.xml`向内留12dp边距，保护斜向火箭及火焰在圆形/圆角方形蒙版内的完整性。
- 背景色资源为象牙色`#FFF5E9`；`android:icon`与`android:roundIcon`均引用同一个可自适应资源。
- 应用名称、包版本与首页标题由主代理更新；图标集成不改变应用包名或签名。
- Android自适应图标的双图层、108dp容器和66dp安全区规则已核对[官方文档](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)。不同桌面的最终蒙版与位移动效仍以安装检查为准。

资源SHA256：

- 原始副本：`cb179767d1031bea18d6995f055d97a6a7837ba676e5841f602f55fa5321fff2`
- 1024源图：`c2c851e1a8797ced615e13c1781a72c968e644468f29bc0ad06ff4f9590678ec`
- 512运行图：`ae731cf4c0e2fc3a88ede3a9aeb8fe41e370a18c4a7310bffcc1a62f744bc59e`

## 最终生成提示词

```text
Use case: logo-brand. Asset type: Android launcher app icon, square 1024 by 1024 raster artwork for an app named 大爷骑火箭 (do not include any text). Primary request: an original cute little rocket character, friendly and rounded, simple enough to recognize at 32px. Scene/backdrop: completely opaque full-bleed flat warm ivory background #FFF5E9, no separate rounded app tile or outer border. Subject: one plump coral-orange rocket, slightly tilted upward to the right, soft cream highlights, two small rounded stabilizer fins, a large round teal porthole with two cheerful tiny dark eyes inside suggesting a friendly face, a small gentle golden-orange flame. Style: polished soft 3D clay illustration, clean silhouette, a few large color shapes, subtle shading, calm and playful. Composition: center the complete rocket plus its short flame within the central 60 to 65 percent of the square, with generous identical ivory space on every side; no part of the rocket or flame near the edges, safe for Android circular and squircle adaptive masks. The ivory background fills the whole canvas, no transparency. Avoid: text, letters, numerals, Chinese characters, watermark, logos from JD or WeChat or any existing brand, extra rockets, stars, clutter, heavy shadows, realistic machinery, chrome, black outlines, an icon mockup or a phone screenshot.
```

## 验证范围

已检查生成图、PNG尺寸与无alpha状态，并检查资源XML/Manifest引用。最终Android构建、安装与桌面自适应蒙版效果由主代理继续验证；图片已显示给用户，原始1254像素、1024源图和运行资源均已持久保存在项目内。
