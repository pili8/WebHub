# 金山多维表 CRM WebView APP

基于安卓 WebView 封装的独立 APP，用于固定加载金山多维表专属视图链接。

## 当前版本

**V1.0** - 2026-06-16

## 功能特性

- 📱 3个底部选项卡，可配置图标和标题
- 🔗 每个选项卡支持多个链接，内联下拉切换
- ⚙️ 设置页面，可视化配置
- 🔄 刷新按钮，解决页面卡死
- 🔙 返回键智能关闭弹窗
- 🔐 Cookie 持久化，一次登录免重复
- 🎨 蓝色主题，美观简洁

## 使用方法

### 安装

将 `CRM-V1.apk` 传输到手机安装。

### 配置

1. 点击右上角 ⚙️ 进入设置
2. 配置每个选项卡的图标（emoji）、标题、链接
3. 链接格式：每行一个，`标题,网址`
4. 保存后自动返回

### 链接格式示例

```
销售机会,https://www.kdocs.cn/wo/sl/v12CEOZt
其他视图,https://www.kdocs.cn/wo/sl/xxxxx
```

## 版本历史

- **V1.0** (2026-06-16) - 首次发布

## 技术栈

- Android WebView
- GitHub Actions 自动构建
- 固定签名密钥（支持覆盖安装）

## 构建

使用 GitHub Actions 自动构建：

```bash
gh workflow run build-apk.yml --repo pili8/crm-webview-app
```

## 文件结构

```
WebViewApp/
├── V1/
│   └── CRM-V1.apk          # V1 版本备份
├── app-debug.apk            # 最新构建
├── VERSION.md               # 版本历史
└── README.md                # 本文件
```
