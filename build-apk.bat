@echo off
setlocal enabledelayedexpansion

echo ========================================
echo 金山多维表 CRM WebView APK 构建工具
echo ========================================
echo.

:: 设置工作目录
set "PROJECT_DIR=%~dp0"
set "SDK_DIR=%PROJECT_DIR%android-sdk"
set "JAVA_HOME=C:\Program Files\Java\jre1.8.0_361"

:: 检查 Java
echo [1/5] 检查 Java 环境...
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo 错误: 未找到 Java，请安装 Java 8 或更高版本
    pause
    exit /b 1
)
echo Java 版本:
"%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /i "version"
echo.

:: 创建 SDK 目录
echo [2/5] 准备 Android SDK...
if not exist "%SDK_DIR%" mkdir "%SDK_DIR%"

:: 下载 Android 命令行工具
set "CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-9477386_latest.zip"
set "CMDLINE_TOOLS_ZIP=%SDK_DIR%\cmdline-tools.zip"
set "CMDLINE_TOOLS_DIR=%SDK_DIR%\cmdline-tools"

if not exist "%CMDLINE_TOOLS_DIR%\latest\bin\sdkmanager.bat" (
    echo 下载 Android SDK 命令行工具...
    echo 这可能需要几分钟，取决于网络速度...
    echo.

    :: 使用 PowerShell 下载
    powershell -Command "& {$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%CMDLINE_TOOLS_URL%' -OutFile '%CMDLINE_TOOLS_ZIP%'}"

    if not exist "%CMDLINE_TOOLS_ZIP%" (
        echo 错误: 下载失败，请检查网络连接
        echo 您也可以手动下载: %CMDLINE_TOOLS_URL%
        echo 并保存到: %CMDLINE_TOOLS_ZIP%
        pause
        exit /b 1
    )

    echo 解压命令行工具...
    powershell -Command "& {$ProgressPreference='SilentlyContinue'; Expand-Archive -Path '%CMDLINE_TOOLS_ZIP%' -DestinationPath '%CMDLINE_TOOLS_DIR%' -Force}"

    :: 重命名目录结构
    if exist "%CMDLINE_TOOLS_DIR%\cmdline-tools" (
        if not exist "%CMDLINE_TOOLS_DIR%\latest" (
            ren "%CMDLINE_TOOLS_DIR%\cmdline-tools" "latest"
        )
    )

    :: 清理 zip 文件
    del "%CMDLINE_TOOLS_ZIP%" 2>nul
)
echo.

:: 设置环境变量
set "ANDROID_HOME=%SDK_DIR%"
set "ANDROID_SDK_ROOT=%SDK_DIR%"
set "PATH=%CMDLINE_TOOLS_DIR%\latest\bin;%SDK_DIR%\platform-tools;%PATH%"

:: 接受许可证并安装 SDK 组件
echo [3/5] 安装 Android SDK 组件...
echo 这将下载约 500MB 的文件，请耐心等待...
echo.

:: 创建 licenses 目录
if not exist "%SDK_DIR%\licenses" mkdir "%SDK_DIR%\licenses"

:: 接受所有许可证
echo y| "%CMDLINE_TOOLS_DIR%\latest\bin\sdkmanager.bat" --licenses >nul 2>&1

:: 安装必要的 SDK 组件
echo 安装 platform-tools...
"%CMDLINE_TOOLS_DIR%\latest\bin\sdkmanager.bat" "platform-tools" >nul 2>&1

echo 安装 build-tools;30.0.3...
"%CMDLINE_TOOLS_DIR%\latest\bin\sdkmanager.bat" "build-tools;30.0.3" >nul 2>&1

echo 安装 platforms;android-30...
"%CMDLINE_TOOLS_DIR%\latest\bin\sdkmanager.bat" "platforms;android-30" >nul 2>&1

echo SDK 组件安装完成
echo.

:: 创建 local.properties
echo [4/5] 配置项目...
echo sdk.dir=%SDK_DIR:\=/%> "%PROJECT_DIR%local.properties"
echo local.properties 已创建
echo.

:: 构建 APK
echo [5/5] 构建 APK...
echo 这将下载 Gradle 和项目依赖，首次构建可能需要 5-10 分钟...
echo.

cd /d "%PROJECT_DIR%"

:: 使用 gradlew 构建
call gradlew.bat assembleDebug

if %ERRORLEVEL% neq 0 (
    echo.
    echo 构建失败！请检查错误信息。
    echo 常见问题:
    echo 1. 网络连接问题（需要下载依赖）
    echo 2. Java 版本不兼容
    echo 3. 磁盘空间不足
    pause
    exit /b 1
)

:: 复制 APK 到项目根目录
set "APK_PATH=%PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk"
if exist "%APK_PATH%" (
    copy "%APK_PATH%" "%PROJECT_DIR%CRM-WebView.apk" >nul
    echo.
    echo ========================================
    echo 构建成功！
    echo ========================================
    echo.
    echo APK 文件位置: %PROJECT_DIR%CRM-WebView.apk
    echo.
    echo 安装说明:
    echo 1. 将 APK 文件传输到手机
    echo 2. 在手机上打开 APK 文件
    echo 3. 允许安装未知来源应用
    echo 4. 完成安装后打开 APP
    echo.
    echo 首次使用需要登录金山账号，之后会自动保持登录状态。
    echo.
) else (
    echo 错误: 未找到生成的 APK 文件
    echo 请检查构建日志中的错误信息
)

pause
