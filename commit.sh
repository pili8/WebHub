#!/bin/bash
cd /d/AI/webhub
git add app/src/main/java/com/crm/webview/MainActivity.java
git commit -m "改回多WebView架构，按需创建，切换不重新加载"
git push origin master
