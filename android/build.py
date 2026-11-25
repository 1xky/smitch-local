"""Offline build using the locally available JDK, apktool resources, DX and apksig.

Android API declarations below are compile-only; none are packaged in the APK.
For a normal Android SDK build, open this directory in Android Studio instead.
"""
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
PREVIOUS = Path(r"C:\Users\scarg\Documents\Codex\2026-10-28\https-github-com-morluto-rea-https\work")
JAVA = Path(os.environ.get("SMITCH_JAVA_HOME", str(PREVIOUS / "jdk21/jdk-21.0.13.0.2+1"))) / "bin"
TOOLS = Path(os.environ.get("SMITCH_BUILD_TOOLS", str(PREVIOUS / "android-tools")))
FRAMEWORK = Path(os.environ.get("SMITCH_FRAMEWORK", str(PREVIOUS / "framework/1.apk")))
BUILD = ROOT / ".build"
JAVA_SUFFIX = ".exe" if os.name == "nt" else ""

STUBS = {
"android/content/Context.java": '''package android.content; import android.content.res.AssetManager;
public class Context { public static final String CONNECTIVITY_SERVICE="connectivity"; public Object getSystemService(String n){return null;} public SharedPreferences getSharedPreferences(String n,int m){return null;} public AssetManager getAssets(){return null;} public void startActivity(Intent i){} }''',
"android/content/Intent.java": 'package android.content; public class Intent { public Intent(String a){} }',
"android/content/SharedPreferences.java": '''package android.content; public interface SharedPreferences {
String getString(String n,String d); int getInt(String n,int d); Editor edit(); interface Editor { Editor putString(String n,String v); Editor putInt(String n,int v); void apply(); } }''',
"android/content/res/AssetManager.java": 'package android.content.res; public class AssetManager { public java.io.InputStream open(String n) throws java.io.IOException {return null;} }',
"android/app/Activity.java": '''package android.app; import android.content.Context; import android.os.Bundle; import android.view.*;
public class Activity extends Context { protected void onCreate(Bundle s){} protected void onDestroy(){} public Window getWindow(){return null;} public void setContentView(View v){} public void runOnUiThread(Runnable r){} }''',
"android/os/Bundle.java": 'package android.os; public class Bundle {}',
"android/view/Window.java": 'package android.view; public class Window { public void setStatusBarColor(int c){} public void setNavigationBarColor(int c){} public void setSoftInputMode(int m){} }',
"android/view/View.java": '''package android.view; import android.content.Context;
public class View { public View(Context c){} public void setBackgroundColor(int c){} public void setPadding(int l,int t,int r,int b){} public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener l){} public interface OnApplyWindowInsetsListener {WindowInsets onApplyWindowInsets(View v,WindowInsets i);} }''',
"android/view/WindowInsets.java": 'package android.view; public class WindowInsets { public int getSystemWindowInsetLeft(){return 0;} public int getSystemWindowInsetTop(){return 0;} public int getSystemWindowInsetRight(){return 0;} public int getSystemWindowInsetBottom(){return 0;} }',
"android/webkit/WebView.java": '''package android.webkit; import android.content.Context; import android.view.View;
public class WebView extends View { public WebView(Context c){super(c);} public WebSettings getSettings(){return null;} public void setWebViewClient(WebViewClient c){} public void addJavascriptInterface(Object o,String n){} public void removeJavascriptInterface(String n){} public void loadDataWithBaseURL(String b,String d,String m,String e,String h){} public void loadData(String d,String m,String e){} public void evaluateJavascript(String s,ValueCallback<String> c){} public void destroy(){} }''',
"android/webkit/WebSettings.java": '''package android.webkit; public class WebSettings { public void setJavaScriptEnabled(boolean b){} public void setDomStorageEnabled(boolean b){} public void setAllowFileAccess(boolean b){} public void setAllowContentAccess(boolean b){} public void setAllowFileAccessFromFileURLs(boolean b){} public void setAllowUniversalAccessFromFileURLs(boolean b){} public void setBlockNetworkLoads(boolean b){} }''',
"android/webkit/WebViewClient.java": 'package android.webkit; public class WebViewClient { public boolean shouldOverrideUrlLoading(WebView v,String u){return false;} public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return false;} }',
"android/webkit/WebResourceRequest.java": 'package android.webkit; public interface WebResourceRequest {}',
"android/webkit/ValueCallback.java": 'package android.webkit; public interface ValueCallback<T> { void onReceiveValue(T v); }',
"android/webkit/JavascriptInterface.java": 'package android.webkit; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD) public @interface JavascriptInterface {}',
"android/provider/Settings.java": 'package android.provider; public class Settings { public static final String ACTION_WIFI_SETTINGS="android.settings.WIFI_SETTINGS"; }',
"android/net/ConnectivityManager.java": 'package android.net; public class ConnectivityManager { public Network[] getAllNetworks(){return null;} public NetworkCapabilities getNetworkCapabilities(Network n){return null;} public LinkProperties getLinkProperties(Network n){return null;} }',
"android/net/Network.java": 'package android.net; public class Network { public javax.net.SocketFactory getSocketFactory(){return null;} }',
"android/net/NetworkCapabilities.java": 'package android.net; public class NetworkCapabilities { public static final int TRANSPORT_WIFI=1; public boolean hasTransport(int t){return false;} }',
"android/net/LinkProperties.java": 'package android.net; public class LinkProperties { public java.util.List<LinkAddress> getLinkAddresses(){return null;} }',
"android/net/LinkAddress.java": 'package android.net; public class LinkAddress { public java.net.InetAddress getAddress(){return null;} public int getPrefixLength(){return 0;} }',
}

def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)

def javac(sources, output, classpath=None):
    output.mkdir(parents=True, exist_ok=True)
    args = [JAVA / ("javac" + JAVA_SUFFIX), "--release", "8", "-encoding", "UTF-8", "-d", output]
    if classpath:
        args.extend(["-classpath", classpath])
    run(*args, *sources)

def main():
    BUILD.mkdir(exist_ok=True)
    if not (JAVA / ("javac" + JAVA_SUFFIX)).is_file():
        sys.exit("Set SMITCH_JAVA_HOME to a JDK 17+ folder, or use Android Studio.")
    for name, body in STUBS.items():
        path = BUILD / "stubs" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
    javac(sorted((BUILD / "stubs").rglob("*.java")), BUILD / "stub-classes")
    javac(sorted((ROOT / "src").rglob("*.java")), BUILD / "classes", BUILD / "stub-classes")
    java = JAVA / ("java" + JAVA_SUFFIX)
    run(java, "-cp", TOOLS / "jadx-headless-mcp-0.7.1-all.jar", "com.android.dx.command.Main", "--dex", "--min-sdk-version=23", "--output=" + str(BUILD / "classes.dex"), BUILD / "classes")
    with zipfile.ZipFile(TOOLS / "apktool.jar") as archive:
        entry = "prebuilt/windows/aapt2.exe" if os.name == "nt" else "prebuilt/linux/aapt2"
        aapt = BUILD / ("aapt2" + JAVA_SUFFIX)
        aapt.write_bytes(archive.read(entry))
        if os.name != "nt":
            aapt.chmod(0o755)
    run(aapt, "compile", "--dir", ROOT / "res", "-o", BUILD / "resources.zip")
    run(aapt, "link", "-I", FRAMEWORK, "--manifest", ROOT / "AndroidManifest.xml", "-o", BUILD / "unsigned.apk", BUILD / "resources.zip")
    html = (ROOT / "web/index.html").read_text(encoding="utf-8")
    html = html.replace("/*STYLE*/", (ROOT / "web/app.css").read_text(encoding="utf-8"))
    html = html.replace("/*SCRIPT*/", (ROOT / "web/app.js").read_text(encoding="utf-8"))
    with zipfile.ZipFile(BUILD / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as archive:
        archive.write(BUILD / "classes.dex", "classes.dex")
        archive.writestr("assets/index.html", html.encode("utf-8"))
    javac([ROOT / "tools/SignApk.java"], BUILD / "sign-classes", TOOLS / "apksig.jar")
    signing = ROOT / "signing"
    signing.mkdir(exist_ok=True)
    keystore = signing / "smitch-local.jks"
    # This is an application update key, not a bulb/user credential.
    password = "smitch-local-build"
    if not keystore.exists():
        run(JAVA / ("keytool" + JAVA_SUFFIX), "-genkeypair", "-keystore", keystore, "-storetype", "JKS", "-alias", "smitch-local", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000", "-storepass", password, "-keypass", password, "-dname", "CN=Smitch Local Personal Build", "-noprompt")
    output = ROOT / "dist"
    output.mkdir(exist_ok=True)
    run(java, "-cp", str(BUILD / "sign-classes") + os.pathsep + str(TOOLS / "apksig.jar"), "SignApk", BUILD / "unsigned.apk", output / "Smitch-Local-Android.apk", keystore, password, "smitch-local")
    print("Built:", output / "Smitch-Local-Android.apk")

if __name__ == "__main__":
    main()
