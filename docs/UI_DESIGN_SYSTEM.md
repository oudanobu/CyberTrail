# CyberTrail Design System
Version: 1.0

Theme: Cyberpunk Tactical HUD

---

## Design Philosophy
目标：
让用户感觉自己正在使用：
战术终端
登山计算机
探险记录仪
而不是普通运动App。

---

## Visual Style
关键词：
Cyberpunk
Military
Terminal
HUD
Wireframe
Minimal

---

## Color Palette
Background: #000000
Primary: #00FF7F (荧光绿色)
Warning: #FFD400 (黄色)
Danger: #FF4D4D (红色)
Target Lock: #FF2A8A (粉色)
Text: #FFFFFF

---

## Typography
Primary: JetBrains Mono
Fallback: Noto Sans

---

## Spacing
Base Unit: 8dp
Allowed: 8, 16, 24, 32, 48

---

## Components

### HUD Panel
显示：速度, 海拔, 距离, 时间
风格：
边框线框
轻微发光
无阴影

### Tactical Button
矩形
线框
发光边框
状态：
Normal
Pressed
Disabled

### Status Indicator
GPS
BARO
REC
BATTERY
颜色：
绿色正常
黄色警告
红色错误

---

## Main Screens

### Home
显示：
快速开始
最近活动
统计摘要

### Tracking
主界面。
布局：
TOP: Heading, Altitude, Speed
CENTER: Track View
BOTTOM: Altitude Profile

### History
历史活动列表。

### Settings
应用设置。

---

## Animations
允许：
淡入淡出
扫描线
脉冲
数字滚动

禁止：
复杂粒子特效
3D场景动画
高功耗效果

---

## Rendering Rules
优先：
Canvas
Compose DrawScope

V1禁止：
OpenGL
Vulkan

---

## Performance Targets
60FPS
普通设备：稳定运行
RAM开销： <20MB UI层

---

## Accessibility
支持：
大字体
高对比度
系统字体缩放

---

## V1 Non Goals
不实现：
地图瓦片
AR界面
3D地形
复杂图表库
WebView界面
广告SDK
