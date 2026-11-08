# Blackout Marker

Minecraft **26.3 / Fabric** 客户端模组：给指定实体或方块绘制**固定像素尺寸的纯黑屏幕空间方块**。

项目 ID：`blackout-marker`，模组显示名：`Blackout Marker`，Modrinth：<https://modrinth.com/project/blackout-marker>，GitHub：<https://github.com/QQYANDQFY/blackout-marker>。

完全由模组自己绘制，**不需要 Iris 光影包**。旧的 `BlackoutMarker` 光影包已停用；不要再同时启用，否则会出现两层黑块。

## AI 生成声明

本项目代码、文档和部分资源在 AI 辅助下生成，由维护者进行人工审查、编译和游戏内测试。AI 输出不提供任何保证，使用风险自负。

This project was developed with AI assistance. The maintainer reviewed, compiled, and tested the behavior before release. AI output is provided without warranty.

## 功能

### 目标选择

- **实体类型**：如 `minecraft:cow`、`minecraft:zombie`
- **方块类型**：如 `minecraft:diamond_block`、`minecraft:chest`
- **计分板标签**：如 `/tag @e[...] add blackout`
- **手动目标**：准星对准实体/方块按 `V` 加入，`Shift+V` 移除
- **特殊标记**：
  - **固定方向标记**（“方向”页）：可添加多个世界朝向标记；点击“添加当前朝向”把当前视角方向加入列表。每个方向标记都可以单独设置尺寸
  - **太阳黑块 / 月亮黑块**（“特殊”页）：分别附着在太阳和月亮的当前位置，并可分别设置尺寸

方向/太阳/月亮标记不参与实体/方块列表的遮挡设置。昼夜循环关闭时太阳/月亮位置固定；开启后黑块会跟随它们移动。

### 遮挡模式

目标被完全遮挡时**默认不显示黑块**。

全局默认模式可在“常规”页用按钮切换：

- **遮挡时**：
  - `隐藏`（默认）：完全被挡住时不显示
  - `显示`：即使完全被挡住也显示黑块
- **实体遮挡**
  - `碰撞箱`：实体碰撞箱任意部分可见就显示
  - `中心`：实体中心点可见且未被方块挡住才显示
- **方块遮挡**
  - `部分`：方块形状任意部分可见就显示
  - `中心`：方块中心点可见且未被其它方块挡住才显示

每个配置条目还可以单独设置：

- **标签**
- **实体类型**
- **方块类型**
- **手动实体 / 手动方块**

单独设置优先于全局；没有单独设置时自动继承全局。单目标样式里可以单独选择“被挡住时隐藏”或“被挡住时仍显示”。

### 黑块尺寸

- 全局默认尺寸：默认 **320×320 像素**，可在“常规”页输入并应用。
- 每个标签/类型/手动目标、每个方向标记、太阳和月亮都可以单独设置尺寸。
- 单独样式里把尺寸留空，就使用全局尺寸。
- 尺寸范围：16–4096 像素。

### 单目标样式

在“标签 / 实体类型 / 方块类型 / 手动目标”页面：

1. 点击列表中的条目（选中）。
2. 点击底部的 **“样式”**。
3. 设置该条目的尺寸、遮挡模式和遮挡策略：
   - 尺寸留空 = 继承全局
   - 模式“继承全局” = 使用全局模式
   - 也可以强制“任意部分/碰撞箱”或“中心”
   - 遮挡策略：继承全局 / 被挡住时隐藏 / 被挡住时仍显示
4. 保存。

列表里的条目会显示样式摘要，例如 `[200 px / 中心 / 遮挡隐藏]`。删除条目时会同时删除它的样式覆盖。

方向标记和太阳/月亮的样式只包含尺寸；它们是天空/方向标记，不使用遮挡模式。方向标记在“方向”页选中后点“样式”；太阳/月亮在“特殊”页点各自“样式”按钮。

## 使用

| 按键 | 功能 |
|---|---|
| `B` | 打开/关闭设置 |
| `V` | 把准星对准的实体/方块加入手动目标 |
| `Shift+V` | 把准星对准的目标从手动目标移除 |

按键可在原版按键设置中修改。

## 安装

1. Fabric Loader **0.19.5+**
2. Minecraft **26.3**
3. Java **25**
4. **Fabric API**（必需）
5. 把 `blackout-marker-1.0.0.jar` 放进 `.minecraft/mods/`

**Mod Menu 可选**：安装后可在模组列表打开配置；不安装也能用 `B`。本项目没有把 Mod Menu 作为硬依赖。

## 配置

路径：

```text
.minecraft/config/blackout-marker.json
```

首次启动时会尝试从旧路径 `blackoutmarker.json` 迁移配置。

主要字段：

```json
{
  "enabled": true,
  "squareHalfSize": 160,
  "entityVisibility": "HITBOX",
  "blockVisibility": "PART",
  "hideWhenOccluded": true,
  "directionMarkers": [
    {
      "id": "dir-1",
      "yaw": 30.0,
      "style": { "squareSize": 80 }
    }
  ],
  "sunMarkerEnabled": false,
  "moonMarkerEnabled": false,
  "sunStyle": { "squareSize": 120 },
  "moonStyle": { "squareSize": 60 },
  "tags": ["blackout"],
  "entityTypes": [],
  "blockTypes": [],
  "tagStyles": {},
  "entityTypeStyles": {},
  "blockTypeStyles": {},
  "manualEntityStyles": {},
  "manualBlockStyles": {}
}
```

- `squareHalfSize`：全局半边长；160 ⇒ 320 px。
- `entityVisibility`：`HITBOX` 或 `CENTER`。
- `blockVisibility`：`PART` 或 `CENTER`。
- `hideWhenOccluded`：全局默认 `true`；完全被遮挡时隐藏。单目标样式可覆盖。
- `directionMarkers`：多个固定世界朝向标记；yaw 使用 Minecraft 0–360 度，每个标记有独立 `style.squareSize`。
- `sunMarkerEnabled` / `moonMarkerEnabled`：太阳/月亮标记。
- `sunStyle` / `moonStyle`：太阳/月亮各自的尺寸覆盖。
- 旧的 `directionMarkerEnabled` / `directionMarkerYaw` 仅用于迁移，新配置请使用 `directionMarkers`。
- `*Styles`：每个条目的可选覆盖：
  ```json
  {
    "squareSize": 200,
    "visibility": "CENTER",
    "occlusion": "INHERIT"
  }
  ```
  - `squareSize` 是完整像素尺寸；省略或 `null` 表示继承全局。
  - `visibility`：`INHERIT`、`ANY`、`CENTER`。
  - `occlusion`：`INHERIT`、`HIDE`、`SHOW`。

## 技术说明

- 在 `GameRenderer.render()` 的 `applyPostEffects()` 之后注入（Mixin）。
- 使用主渲染目标的颜色纹理建立临时 FBO，绘制 2D 黑块。
- 只对位于相机视锥前方、并且 `isInFront()` 的点绘制；这避免了点到相机后方时 `projectPointToScreen()` 产生镜像 NDC、从而在相反方向出现多余黑块的问题。
- 遮挡判断在 CPU 端用短方块射线完成：从相机向目标中心/内缩角点发射 `ClipContext.Block.OUTLINE` 射线；全部被其它方块挡住时隐藏。
- 中心模式只需要中心射线；部分/碰撞箱模式检查中心 + 八个角点。
- 固定方向标记和日/月标记只做视锥/前方检查，不做地形遮挡检查。
- 每个目标使用自身解析出的尺寸、遮挡模式和遮挡策略；未覆盖时使用全局值。
- 绘制前后保存/恢复 FBO、viewport、program、VAO、深度/混合/剔除/裁剪/颜色掩码等状态。
- 计分板标签从内置服务端读取，并切回服务端线程执行。

## 已知限制

- **实体使用 AABB 碰撞箱**，不是逐模型精确轮廓。实体中心模式也不计算其它实体的遮挡，只考虑世界方块。
- **部分/碰撞箱模式**使用中心 + 八个内缩角点射线近似；极小的露出部分可能被漏判。玻璃、铁栏杆等透明方块也会被视为遮挡。
- 方块类型目标只扫描玩家周围已加载区块（默认半径 48 格），每秒刷新一次。
- 每个目标每帧最多 9 条方块射线；目标很多时会有 CPU 开销，建议只选择需要的目标。
- 固定方向/太阳/月亮标记使用各自样式/全局尺寸，不做遮挡判断；太阳/月亮在落到地平线以下时不绘制。
- 默认最多处理 96 个目标 / 192 个方块类型候选。
- 计分板标签只在**单机 / LAN 主机**的内置服务端生效；远程服务器不同步标签。实体类型、方块类型、手动目标不受影响。

## 兼容性

- **Sodium 0.9.2**：已验证。
- 不要使用 **Sodium 0.9.3-beta.3**：该测试版有地形 UV 回归，会让所有方块贴图异常。
- **Sodium 0.9.3-beta.4** 与 Iris 1.11.7 不兼容。
- Iris：本模组测试时关闭光影；旧的 `BlackoutMarker` 光影包请保持禁用/移出。
- 必需依赖只有 Fabric API；Mod Menu 可选。

## 构建

源码目录：`blackout-marker/`。需要 JDK 25；先设置 `JAVA_HOME`。

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
```

输出：

```text
build/blackout-marker-1.0.0.jar
dist/blackout-marker-1.0.0.jar
```

`libs/` 是本地编译依赖，已被 `.gitignore` 排除，不随仓库分发。构建不使用 Gradle/Loom。

发布：推送到 GitHub 后创建 tag `1.0.0` 的 Release；`.github/workflows/publish.yml` 会用 `dist/blackout-marker-1.0.0.jar` 发布到 Modrinth。需要仓库 Secret：`MODRINTH_TOKEN`。

## License

**GNU Lesser General Public License v3.0 only**（`LGPL-3.0-only`）。详见 `LICENSE`、`COPYING` 和 `NOTICE`。
