# 金山多维表 CRM WebView APP

基于安卓 WebView 封装的独立 APP，用于固定加载金山多维表专属视图链接。

**已配置链接：** `https://www.kdocs.cn/wo/sl/v14T2gpD`

## 功能特性

- ✅ 全屏展示，隐藏地址栏和导航控件
- ✅ 强制竖屏锁定
- ✅ 禁止加载外部网址
- ✅ Cookie 持久化，一次登录免重复登录
- ✅ 网页缓存加速
- ✅ 返回键仅执行页面内返回

## 快速构建（推荐）

### 一键构建 APK

双击运行 `build-apk.bat` 脚本，它会自动：
1. 检查 Java 环境
2. 下载 Android SDK（约 500MB）
3. 安装必要的 SDK 组件
4. 构建 APK

构建完成后，APK 文件会生成在项目根目录：`CRM-WebView.apk`

**首次构建需要下载约 1GB 的文件（SDK + 依赖），请耐心等待。**

### 手动构建（需要 Android Studio）

1. 用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 菜单：Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## 安装到手机

1. 将 `CRM-WebView.apk` 文件传输到手机（微信、QQ、数据线等）
2. 在手机上打开 APK 文件
3. 允许安装未知来源应用
4. 完成安装后打开 APP

## 首次使用

1. 打开 APP 后会自动加载金山多维表页面
2. 首次需要登录金山账号
3. 登录后 Cookie 会自动保存，之后无需重复登录
4. 按返回键会执行页面内返回，不会退出 APP

## 项目结构

```
WebViewApp/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # 应用配置
│   │   ├── java/.../MainActivity.java   # 主程序
│   │   └── res/
│   │       ├── layout/activity_main.xml # 布局
│   │       └── values/strings.xml       # 字符串资源
│   └── build.gradle                     # 模块构建配置
├── build.gradle                         # 项目构建配置
├── build-apk.bat                        # 一键构建脚本
└── settings.gradle                      # 项目设置
```

## 注意事项

1. **网络要求**：APP 需要网络连接才能使用
2. **链接限制**：APP 会自动阻止加载非金山域名的链接
3. **返回键**：在多维表内按返回键会执行页面内返回，不会退出 APP
4. **登录状态**：Cookie 持久化保存，清除 APP 数据会丢失登录状态

## 自定义修改

### 修改链接

打开 `app/src/main/java/com/crm/webview/MainActivity.java`，修改第 25 行：

```java
private static final String TARGET_URL = "https://www.kdocs.cn/wo/sl/v14T2gpD";
```

### 修改 APP 名称

打开 `app/src/main/res/values/strings.xml`，修改：

```xml
<string name="app_name">CRM</string>
```

### 修改允许的域名

在 `MainActivity.java` 的 `isAllowedUrl()` 方法中添加或删除域名：

```java
private boolean isAllowedUrl(String url) {
    return url.contains("kdocs.cn")
            || url.contains("wps.cn")
            || url.contains("your-domain.com");  // 添加自定义域名
}
```

## 系统要求

- Windows 7/10/11
- Java 8 或更高版本
- 至少 2GB 可用磁盘空间
- 网络连接（下载 SDK 和依赖）
