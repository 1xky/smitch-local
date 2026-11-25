package local.smitch;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.net.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private WebView web;
    private volatile boolean closed;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private Map<String,Object> connected;
    private Network connectedNetwork;
    private SharedPreferences preferences;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences=getSharedPreferences("smitch-local",0);
        getWindow().setStatusBarColor(0xff111722);
        getWindow().setNavigationBarColor(0xff111722);
        getWindow().setSoftInputMode(16);
        web=new WebView(this);
        web.setBackgroundColor(0xff111722);
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v,WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url) { return true; }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) { return true; }
        });
        web.addJavascriptInterface(new Bridge(),"SmitchNative");
        setContentView(web);
        try {
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            try(InputStream stream=getAssets().open("index.html")) {
                byte[] buffer=new byte[4096]; int count;
                while((count=stream.read(buffer))!=-1) data.write(buffer,0,count);
            }
            web.loadDataWithBaseURL("https://smitch.invalid/",new String(data.toByteArray(),"UTF-8"),"text/html","UTF-8",null);
        } catch(IOException ex) { web.loadData("Could not open the local controller. Please reinstall the APK.","text/plain","UTF-8"); }
    }
    @Override protected void onDestroy() {
        closed=true; work.shutdownNow();
        web.removeJavascriptInterface("SmitchNative"); web.destroy();
        super.onDestroy();
    }
    public final class Bridge {
        @JavascriptInterface public void request(final int id,final String payload) {
            if(closed || id<0) return;
            if(payload==null || payload.length()>8192) { reply(id,Json.map("error","Request is too large.")); return; }
            try {
                work.execute(new Runnable() { @Override public void run() {
                    try { reply(id,Json.map("result",dispatch(Json.object(payload)))); }
                    catch(IllegalArgumentException ex) { reply(id,Json.map("error",ex.getMessage())); }
                    catch(IOException ex) {
                        String message=ex.getMessage();
                        if(ex instanceof SocketTimeoutException || ex instanceof ConnectException || ex instanceof NoRouteToHostException || ex instanceof SocketException)
                            message="Could not reach the bulb. Check Wi-Fi, its IP address and port. On the bulb hotspot, choose to stay connected without internet. On home Wi-Fi, turn off guest/client isolation.";
                        reply(id,Json.map("error",message==null?"The bulb connection was interrupted. Try connecting again.":message));
                    } catch(Exception ex) { reply(id,Json.map("error","The operation could not finish. Check Wi-Fi and try again.")); }
                }});
            } catch(RejectedExecutionException ignored) {}
        }
    }
    private void reply(final int id,final Object result) {
        if(closed) return;
        final String script="window.nativeResult("+id+","+Json.string(result)+");";
        runOnUiThread(new Runnable() { @Override public void run() { if(!closed) web.evaluateJavascript(script,null); }});
    }
    private Network wifi() throws IOException {
        ConnectivityManager manager=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        for(Network network:manager.getAllNetworks()) {
            NetworkCapabilities caps=manager.getNetworkCapabilities(network);
            if(caps!=null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return network;
        }
        throw new IOException("Connect your phone to home Wi-Fi or the bulb's Smitch Wi-Fi first. Mobile data alone cannot reach the bulb.");
    }
    private Object dispatch(Map<String,Object> data) throws Exception {
        String action=String.valueOf(data.get("op"));
        if(action.equals("info")) return Json.map("ip",preferences.getString("ip",""),"port",preferences.getInt("port",80),"version","1.0.0");
        if(action.equals("wifi")) {
            runOnUiThread(new Runnable() { @Override public void run() { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }});
            return Json.map("opened",true);
        }
        final Network network=wifi();
        BulbProtocol client=new BulbProtocol(network.getSocketFactory());
        if(action.equals("discover")) return discover(network,client);
        if(action.equals("connect")) {
            String ip=BulbProtocol.address(data.get("ip"));
            int port=BulbProtocol.integer(data.get("port"),1,65535,"Port");
            // A failed connection must invalidate the old session.
            connected=null; connectedNetwork=null;
            Map<String,Object> profile=client.identify(ip,port,2800);
            connected=profile; connectedNetwork=network;
            if(!ip.equals(BulbProtocol.HOTSPOT)) preferences.edit().putString("ip",ip).putInt("port",port).apply();
            return profile;
        }
        if(action.equals("light")) {
            if(connected==null || !network.equals(connectedNetwork)) throw new IllegalArgumentException("Connect to your bulb again after changing Wi-Fi.");
            if(!connected.get("mac").equals(data.get("mac"))) throw new IllegalArgumentException("Connect to this bulb again before using the controls.");
            client.send((String)connected.get("ip"),((Number)connected.get("port")).intValue(),BulbProtocol.command(connected,String.valueOf(data.get("action")),data.get("value")));
            return Json.map("sent",true);
        }
        if(action.equals("setup")) {
            if(!(data.get("ssid") instanceof String) || !(data.get("password") instanceof String)) throw new IllegalArgumentException("Enter the Wi-Fi name and password.");
            String ssid=(String)data.get("ssid"),password=(String)data.get("password");
            BulbProtocol.setupCommands(ssid,password,""); // Validate before contacting the device.
            Map<String,Object> profile=client.identify(BulbProtocol.HOTSPOT,80,2800);
            List<String> commands=BulbProtocol.setupCommands(ssid,password,(String)profile.get("model"));
            connected=null; connectedNetwork=null;
            for(String command:commands) { client.send(BulbProtocol.HOTSPOT,80,command); Thread.sleep(150); }
            return Json.map("sent",true);
        }
        throw new IllegalArgumentException("Unknown request.");
    }
    private Object discover(Network network,final BulbProtocol client) throws Exception {
        ConnectivityManager manager=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties properties=manager.getLinkProperties(network);
        if(properties==null) throw new IOException("Wi-Fi is still connecting. Wait a moment and try again.");
        String ip=null; int prefix=24;
        for(LinkAddress address:properties.getLinkAddresses()) if(address.getAddress() instanceof Inet4Address) {
            try { ip=BulbProtocol.address(address.getAddress().getHostAddress()); prefix=address.getPrefixLength(); break; }
            catch(IllegalArgumentException ignored) {}
        }
        if(ip==null) throw new IOException("This Wi-Fi network has no private IPv4 address. Use the bulb's hotspot instead.");
        String[] parts=ip.split("\\.");
        long host=0; for(String part:parts) host=(host<<8)|Integer.parseInt(part);
        int scope=Math.max(24,Math.min(32,prefix));
        long mask=(0xffffffffL<<(32-scope))&0xffffffffL, base=host&mask, end=base+(1L<<(32-scope));
        Set<String> candidates=new LinkedHashSet<String>();
        String saved=preferences.getString("ip",""); int savedPort=preferences.getInt("port",80);
        // Include the saved endpoint only when it belongs to the current Wi-Fi subnet.
        if(!saved.isEmpty()) {
            long last=0; for(String part:saved.split("\\.")) last=(last<<8)|Integer.parseInt(part);
            long actualMask=(0xffffffffL<<(32-Math.min(32,prefix)))&0xffffffffL;
            if((last&actualMask)==(host&actualMask)) candidates.add(saved+":"+savedPort);
        }
        long first=scope>=31?base:base+1, stop=scope>=31?end:end-1;
        for(long n=first;n<stop;n++) if(n!=host) candidates.add(((n>>24)&255)+"."+((n>>16)&255)+"."+((n>>8)&255)+"."+(n&255)+":80");
        ExecutorService pool=Executors.newFixedThreadPool(24);
        CompletionService<Map<String,Object>> completions=new ExecutorCompletionService<Map<String,Object>>(pool);
        List<Future<Map<String,Object>>> tasks=new ArrayList<Future<Map<String,Object>>>();
        Map<String,Map<String,Object>> found=new LinkedHashMap<String,Map<String,Object>>();
        try {
            for(final String target:candidates) tasks.add(completions.submit(new Callable<Map<String,Object>>() {
                @Override public Map<String,Object> call() {
                    try { String[] parts=target.split(":"); return client.identify(parts[0],Integer.parseInt(parts[1]),550); }
                    catch(Exception ignored) { return null; }
                }
            }));
            long deadline=System.nanoTime()+14000000000L;
            for(int i=0;i<tasks.size() && !closed;i++) {
                long remaining=deadline-System.nanoTime(); if(remaining<=0) break;
                Future<Map<String,Object>> task=completions.poll(remaining,TimeUnit.NANOSECONDS); if(task==null) break;
                Map<String,Object> profile=task.get(); if(profile!=null) found.put((String)profile.get("mac"),profile);
            }
        } finally { for(Future<?> task:tasks) task.cancel(true); pool.shutdownNow(); }
        return Json.map("devices",new ArrayList<Map<String,Object>>(found.values()),"scope",ip+"/"+scope,"limited",prefix<24);
    }
}
