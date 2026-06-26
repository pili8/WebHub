# WebHub 架构重构总结

## 重构目标
将 4370 行的两个巨型 Activity 文件拆分为清晰的分层架构，提升可维护性。
**不改变任何功能行为，纯粹的代码结构重组。**

## 文件变更

### 新增文件（6个，共 1581 行）

| 文件 | 行数 | 职责 |
|------|------|------|
| `model/AppConfig.java` | 170 | 统一数据模型（LinkItem, ActionItem, TabData, LinkData, ActionData, SearchResult）和常量 |
| `config/ConfigManager.java` | 525 | 配置读写、JSON 解析、旧格式迁移、定时刷新配置 |
| `engine/PageActionEngine.java` | 342 | 页面操作执行引擎（JS 注入、MutationObserver、元素检查） |
| `webview/WebViewFactory.java` | 380 | WebView 创建、配置、UA 切换、桌面模式、夜间模式 CSS |
| `util/AliasManager.java` | 101 | 启动图标/应用名切换（7 套预设） |
| `util/UIHelper.java` | 63 | UI 工具方法（dpToPx、格式化、剪贴板） |

### 修改文件（2个）

| 文件 | 原行数 | 新行数 | 变化 |
|------|--------|--------|------|
| `MainActivity.java` | 2888 | 2039 | **-849 行 (-29%)** |
| `SettingsActivity.java` | 1482 | 1129 | **-353 行 (-24%)** |

**总计：4370 行 → 4750 行（+9%），但 Activity 代码从 4370 降至 3168 行 (-28%)**

## 架构图

```
com.crm.webview/
├── MainActivity.java          # 主界面（协调层）
├── SettingsActivity.java      # 设置页（UI 构建）
├── model/
│   └── AppConfig.java         # 数据模型 + 常量
├── config/
│   └── ConfigManager.java     # 配置管理
├── engine/
│   └── PageActionEngine.java  # 页面操作引擎
├── webview/
│   └── WebViewFactory.java    # WebView 工厂
└── util/
    ├── AliasManager.java      # 图标管理
    └── UIHelper.java          # UI 工具
```

## 具体变更

### MainActivity.java
- ✅ 删除内部类 `LinkItem`, `ActionItem`, `SearchResult` → 改用 `AppConfig.*`
- ✅ 删除 `parseLegacyActions()` → 改用 `ConfigManager.parseLegacyActions()`
- ✅ 删除页面操作方法（executeCustomScript, buildScriptFromActions, startMutationObserver 等 12 个方法）→ 改用 `PageActionEngine.*`
- ✅ 删除 WebView 方法（setupWebView, applyDesktopModeUA, injectNightModeCSS 等 8 个方法）→ 改用 `WebViewFactory.*`
- ✅ 删除 `ensureLauncherAlias()` → 改用 `AliasManager.ensureLauncherAlias()`
- ✅ 删除工具方法（dpToPx, formatBytes, formatInterval, extractJsonString）→ 改用 `UIHelper.*`
- ✅ 实现 `WebViewFactory.WebViewCallbacks` 接口，桥接 Activity 状态到 WebViewFactory
- ✅ 保留所有 UI 构建、弹窗、搜索、Tab 切换等协调逻辑

### SettingsActivity.java
- ✅ 删除内部类 `TabData`, `LinkData`, `ActionData` → 改用 `AppConfig.*`
- ✅ 删除 `loadConfig()`, `loadConfigFromJson()` → 改用 `ConfigManager.loadConfigAsTabs()`
- ✅ 删除 `parseLegacyLinks()`, `parseLegacyActions()` → 改用 `ConfigManager.*`
- ✅ 删除 `switchAppPreset()` → 改用 `AliasManager.switchAppPreset()`
- ✅ 删除 `buildTabsJson()`, `buildTabsJsonFromPrefs()` → 改用 `ConfigManager.*`
- ✅ 删除 `dpToPx()` → 改用 `UIHelper.dpToPx()`
- ✅ 保留所有 UI 构建方法（buildUI, addLinkCard, addActionRow）
- ✅ 保留配置保存、导入导出、缓存管理等逻辑

### 未修改文件
- ✅ `AndroidManifest.xml` - 未修改
- ✅ `build.gradle` - 未修改
- ✅ 所有布局 XML - 未修改
- ✅ 所有资源文件 - 未修改

## 注意事项
1. 所有新文件使用 `public static` 方法，无需实例化
2. `WebViewFactory.WebViewCallbacks` 接口让 WebViewFactory 能回调 Activity 的状态和方法
3. `ConfigManager.ConfigData` 内部类用于一次性返回多个配置值
4. `AppConfig` 中的 `TabData.sectionView`, `LinkData.cardView` 等 View 引用仅在 SettingsActivity 中使用
