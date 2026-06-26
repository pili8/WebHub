package com.crm.webview.engine;

import android.webkit.WebView;

import com.crm.webview.config.ConfigManager;
import com.crm.webview.model.AppConfig.ActionItem;
import com.crm.webview.model.AppConfig.LinkItem;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 页面操作引擎。
 * 从 MainActivity 中提取所有页面操作相关逻辑。
 * 所有方法变为 static，通过参数传入所需上下文。
 */
public class PageActionEngine {

    private PageActionEngine() {} // 不可实例化

    // ==================== 入口方法 ====================

    /**
     * 执行自定义脚本（入口方法）。
     * 原 MainActivity.executeCustomScript()
     */
    public static void executeCustomScript(WebView webView, boolean pageActionsEnabled,
            List<List<LinkItem>> tabLinks, int currentTab, int currentLinkIndex,
            int activeLinkIndex, LinkItem currentLink) {
        executeCustomScriptWithRetry(webView, pageActionsEnabled, tabLinks, currentTab,
                currentLinkIndex, activeLinkIndex, currentLink, 0);
    }

    /**
     * 带重试的自定义脚本执行。
     * 原 MainActivity.executeCustomScriptWithRetry()
     */
    public static void executeCustomScriptWithRetry(WebView webView, boolean pageActionsEnabled,
            List<List<LinkItem>> tabLinks, int currentTab, int currentLinkIndex,
            int activeLinkIndex, LinkItem currentLink, int attempt) {
        if (!pageActionsEnabled) return;

        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl)) return;

        int linkIndex = activeLinkIndex >= 0 ? activeLinkIndex : currentLinkIndex;

        List<ActionItem> allActions = new ArrayList<>();

        for (int tabIndex = 0; tabIndex < tabLinks.size(); tabIndex++) {
            for (LinkItem link : tabLinks.get(tabIndex)) {
                if (!hasActions(link)) continue;
                if (isLinkMatchesPage(link, tabIndex, currentTab, currentLinkIndex, currentLink, currentUrl)) {
                    collectActionsFromLink(link, allActions);
                }
            }
        }

        if (allActions.isEmpty()) return;

        String js = buildScriptFromActions(allActions);
        if (js.isEmpty()) return;

        String checkJs = buildElementCheckJs(allActions);
        webView.evaluateJavascript(checkJs, value -> {
            boolean found = "true".equals(value);

            if (found) {
                runActionScript(webView, js);
            } else if (attempt < 10) {
                webView.postDelayed(() -> executeCustomScriptWithRetry(webView, pageActionsEnabled,
                        tabLinks, currentTab, currentLinkIndex, activeLinkIndex, currentLink, attempt + 1), 500);
            } else {
                runActionScript(webView, js);
            }
        });
    }

    // ==================== 脚本构建 ====================

    /**
     * 从操作列表构建 JS 脚本。
     * 原 MainActivity.buildScriptFromActions()
     */
    public static String buildScriptFromActions(List<ActionItem> actions) {
        StringBuilder js = new StringBuilder();
        js.append("(function(){");

        for (ActionItem action : actions) {
            if (action == null) continue;

            if ("script".equals(action.type)) {
                String script = action.value;
                if (script != null && !script.isEmpty()) {
                    int delay = Math.max(0, action.delay);
                    if (delay > 0) {
                        js.append("setTimeout(function(){try{").append(script).append("}catch(e){}},").append(delay * 1000).append(");");
                    } else {
                        js.append("try{").append(script).append("}catch(e){}");
                    }
                }
                continue;
            }

            if (action.selector == null || action.selector.isEmpty()) continue;

            String selector = jsString(action.selector);
            if ("hide".equals(action.type)) {
                js.append("try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.style.setProperty('display','none','important'));}catch(e){}");
            } else if ("click".equals(action.type)) {
                int delay = Math.max(0, action.delay);
                if (delay > 0) {
                    js.append("setTimeout(function(){try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.click());}catch(e){}},").append(delay * 1000).append(");");
                } else {
                    js.append("try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.click());}catch(e){}");
                }
            } else if ("modify".equals(action.type)) {
                js.append("try{document.querySelectorAll(").append(selector).append(").forEach(el=>el.textContent=").append(jsString(action.value)).append(");}catch(e){}");
            }
        }

        js.append("})()");
        return js.toString();
    }

    /**
     * 构建检测元素是否存在的 JS。
     * 原 MainActivity.buildElementCheckJs()
     */
    public static String buildElementCheckJs(List<ActionItem> actions) {
        StringBuilder js = new StringBuilder("(function(){");
        boolean hasSelectors = false;
        for (ActionItem action : actions) {
            if ("script".equals(action.type)) continue;
            if (action.selector == null || action.selector.isEmpty()) continue;
            js.append("if(document.querySelectorAll(").append(jsString(action.selector)).append(").length>0)return true;");
            hasSelectors = true;
        }
        if (!hasSelectors) {
            js.append("return true;");
            js.append("})()");
            return js.toString();
        }
        // 检查 iframe 内部
        js.append("try{var iframes=document.querySelectorAll('iframe');for(var i=0;i<iframes.length;i++){try{var doc=iframes[i].contentDocument||iframes[i].contentWindow.document;");
        for (ActionItem action : actions) {
            if ("script".equals(action.type)) continue;
            if (action.selector == null || action.selector.isEmpty()) continue;
            js.append("if(doc.querySelectorAll(").append(jsString(action.selector)).append(").length>0)return true;");
        }
        js.append("}catch(e){}}}catch(e){}");
        js.append("return false;})()");
        return js.toString();
    }

    // ==================== 脚本执行 ====================

    /**
     * 执行操作脚本（主文档 + iframe）。
     * 原 MainActivity.runActionScript()
     */
    public static void runActionScript(WebView webView, String mainJs) {
        webView.evaluateJavascript(mainJs, null);

        String iframeJs = "(function(){" +
                "try{" +
                "var iframes=document.querySelectorAll('iframe');" +
                "for(var i=0;i<iframes.length;i++){" +
                "  try{" +
                "    var doc=iframes[i].contentDocument||iframes[i].contentWindow.document;" +
                "    if(doc)" + mainJs +
                "  }catch(e){}" +
                "}" +
                "}catch(e){}" +
                "})()";
        webView.evaluateJavascript(iframeJs, null);

        startMutationObserver(webView, mainJs);
    }

    /**
     * 启动 MutationObserver。
     * 原 MainActivity.startMutationObserver()
     */
    public static void startMutationObserver(WebView webView, String actionJs) {
        String observerJs = "(function() {" +
                "if (window._webhubObserver) return;" +
                "var timeout = null;" +
                "var actionFn = function() {" +
                "  try { " + actionJs + " } catch(e) {}" +
                "  try{" +
                "    var iframes=document.querySelectorAll('iframe');" +
                "    for(var i=0;i<iframes.length;i++){" +
                "      try{var doc=iframes[i].contentDocument||iframes[i].contentWindow.document;if(doc)" + actionJs + "}catch(e){}" +
                "    }" +
                "  }catch(e){}" +
                "};" +
                "window._webhubObserver = new MutationObserver(function(mutations) {" +
                "  if (timeout) clearTimeout(timeout);" +
                "  timeout = setTimeout(actionFn, 1000);" +
                "});" +
                "if (document.body) {" +
                "  window._webhubObserver.observe(document.body, {" +
                "    childList: true," +
                "    subtree: true," +
                "    characterData: true" +
                "  });" +
                "  actionFn();" +
                "}" +
                "if (!window._webhubNavHooked) {" +
                "  window._webhubNavHooked = true;" +
                "  var origPush = history.pushState;" +
                "  var origReplace = history.replaceState;" +
                "  var navTimeout = null;" +
                "  var notifyNav = function() {" +
                "    if (navTimeout) clearTimeout(navTimeout);" +
                "    navTimeout = setTimeout(function() {" +
                "      try { _webhub.onSpaNavigate(); } catch(e) { actionFn(); }" +
                "    }, 1500);" +
                "  };" +
                "  history.pushState = function() {" +
                "    origPush.apply(this, arguments);" +
                "    notifyNav();" +
                "  };" +
                "  history.replaceState = function() {" +
                "    origReplace.apply(this, arguments);" +
                "    notifyNav();" +
                "  };" +
                "  window.addEventListener('popstate', function() {" +
                "    notifyNav();" +
                "  });" +
                "}" +
                "})()";

        webView.evaluateJavascript(observerJs, null);
    }

    /**
     * 停止 MutationObserver。
     * 原 MainActivity.stopMutationObserver()
     */
    public static void stopMutationObserver(WebView webView) {
        if (webView != null) {
            webView.evaluateJavascript(
                "(function() {" +
                "if (window._webhubObserver) {" +
                "  window._webhubObserver.disconnect();" +
                "  window._webhubObserver = null;" +
                "}" +
                "})()", null);
        }
    }

    // ==================== 操作收集 ====================

    /**
     * 从单个链接收集操作。
     * 原 MainActivity.collectActions(LinkItem, List)
     */
    public static void collectActionsFromLink(LinkItem link, List<ActionItem> allActions) {
        if (link.actionItems != null && !link.actionItems.isEmpty()) {
            allActions.addAll(link.actionItems);
        } else if (link.actions != null && !link.actions.isEmpty()) {
            allActions.addAll(ConfigManager.parseLegacyActions(link.actions));
        }
    }

    /**
     * 从链接列表收集操作。
     * 原 MainActivity.collectActions(List, List)
     */
    public static void collectActionsFromLinks(List<LinkItem> links, List<ActionItem> allActions) {
        for (LinkItem link : links) {
            collectActionsFromLink(link, allActions);
        }
    }

    /**
     * 判断链接是否有操作。
     * 原 MainActivity.hasActions()
     */
    public static boolean hasActions(LinkItem link) {
        return (link.actionItems != null && !link.actionItems.isEmpty())
                || (link.actions != null && !link.actions.isEmpty());
    }

    // ==================== 匹配逻辑 ====================

    /**
     * 判断某个链接的操作是否应该应用到当前页面。
     * 原 MainActivity.isLinkMatchesPage()
     */
    public static boolean isLinkMatchesPage(LinkItem link, int tabIndex, int currentTab,
            int currentLinkIndex, LinkItem currentLink, String currentUrl) {
        String scope = link.scope;

        if ("all".equals(scope)) {
            return true;
        } else if ("tab".equals(scope)) {
            return tabIndex == currentTab;
        } else if ("domain".equals(scope)) {
            String linkDomain = getDomain(link.url);
            String pageDomain = getDomain(currentUrl);
            return !linkDomain.isEmpty() && linkDomain.equals(pageDomain);
        } else {
            return tabIndex == currentTab && link == currentLink;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 获取 URL 的主域名。
     * 原 MainActivity.getDomain()
     */
    public static String getDomain(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null) return "";
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }
            return host;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 将字符串转为 JS 字面量。
     * 原 MainActivity.jsString()
     */
    public static String jsString(String value) {
        if (value == null) value = "";
        return JSONObject.quote(value);
    }
}
