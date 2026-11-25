package local.smitch;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.SocketFactory;

/** Stock My Smitch 2.9.7 TCP commands, without account or cloud dependencies. */
public final class BulbProtocol {
    public static final String HOTSPOT="192.168.4.1";
    private final SocketFactory sockets;
    public BulbProtocol(SocketFactory sockets) { this.sockets=sockets; }
    public static String address(Object input) {
        if(!(input instanceof String)) throw new IllegalArgumentException("Enter the bulb's local IPv4 address.");
        String[] parts=((String)input).trim().split("\\.",-1);
        if(parts.length!=4) throw new IllegalArgumentException("Enter a local IPv4 address, such as 192.168.1.25.");
        int[] n=new int[4];
        for(int i=0;i<4;i++) {
            if(!parts[i].matches("[0-9]{1,3}")) throw new IllegalArgumentException("Enter a valid IPv4 address.");
            n[i]=Integer.parseInt(parts[i]);
            if(n[i]>255) throw new IllegalArgumentException("Enter a valid IPv4 address.");
        }
        if(!(n[0]==10 || n[0]==192&&n[1]==168 || n[0]==172&&n[1]>=16&&n[1]<=31 || n[0]==169&&n[1]==254))
            throw new IllegalArgumentException("Use the bulb's private local network address.");
        return n[0]+"."+n[1]+"."+n[2]+"."+n[3];
    }
    public static int integer(Object value,int min,int max,String label) {
        if(!(value instanceof Number)) throw new IllegalArgumentException(label+" must be a number.");
        double number=((Number)value).doubleValue(); int n=((Number)value).intValue();
        if(number!=n || n<min || n>max) throw new IllegalArgumentException(label+" must be between "+min+" and "+max+".");
        return n;
    }
    public Map<String,Object> identify(String ip,int port,int timeout) throws IOException {
        Map<String,Object> profile=exchange(address(ip),port,"0001xx03",true,timeout);
        profile.put("ip",ip); profile.put("port",port); return profile;
    }
    public void send(String ip,int port,String command) throws IOException { exchange(address(ip),port,command,false,2500); }
    private Map<String,Object> exchange(String ip,int port,String command,boolean query,int timeout) throws IOException {
        if(port<1 || port>65535) throw new IllegalArgumentException("Invalid port.");
        try(Socket socket=sockets.createSocket()) {
            socket.connect(new InetSocketAddress(ip,port),timeout);
            socket.setSoTimeout(timeout);
            socket.getOutputStream().write((command+"\r").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            if(!query) return null;
            long deadline=System.nanoTime()+timeout*1000000L;
            ByteArrayOutputStream data=new ByteArrayOutputStream(); byte[] buffer=new byte[2048];
            while(data.size()<16384 && System.nanoTime()<deadline) {
                socket.setSoTimeout(Math.max(1,(int)((deadline-System.nanoTime())/1000000L)));
                int count;
                try { count=socket.getInputStream().read(buffer,0,Math.min(buffer.length,16384-data.size())); }
                catch(SocketTimeoutException ex) { break; }
                if(count<0) break;
                data.write(buffer,0,count);
                String text=new String(data.toByteArray(),StandardCharsets.UTF_8).replace('\0',' ').trim();
                Map<String,Object> profile=parseReport(text);
                if(profile!=null) return profile;
                for(String frame:text.split("[\\r\\n]+")) { profile=parseReport(frame); if(profile!=null) return profile; }
            }
            throw new IOException("The address answered, but did not return a recognized Smitch bulb status.");
        }
    }
    public static Map<String,Object> parseReport(String raw) {
        try {
            Map<String,Object> report=Json.object(raw);
            Object modelObject=report.get("model"), macObject=report.get("mac_address");
            if(!(modelObject instanceof String) || !(macObject instanceof String)) return null;
            String model=(String)modelObject, mac=(String)macObject;
            if(!mac.replace(":","").replace("-","").matches("[0-9a-fA-F]{12}")) return null;
            String lower=model.toLowerCase(Locale.US); boolean white=lower.startsWith("white bulb");
            Map<String,Object> values=new LinkedHashMap<String,Object>(); Object rows=report.get("values");
            if(rows instanceof List && !((List)rows).isEmpty() && ((List)rows).get(0) instanceof Map) values=(Map<String,Object>)((List)rows).get(0);
            boolean rgb=!white && (lower.contains("rgb") || lower.contains("color bulb") || values.containsKey("r")&&values.containsKey("g")&&values.containsKey("b"));
            if(!white && !rgb && !lower.contains("bulb")) return null;
            Integer temp=number(values,0,6500,"temp","temperature");
            boolean cct=lower.contains("cct") || white&&!lower.contains(" sc") || temp!=null&&temp>0;
            Integer r=number(values,0,255,"r"),g=number(values,0,255,"g"),b=number(values,0,255,"b");
            Integer power=number(values,0,1,"powerState","power_state","power_status","power");
            String color=r!=null&&g!=null&&b!=null?String.format(Locale.US,"#%02x%02x%02x",r,g,b):null;
            return Json.map("model",model.substring(0,Math.min(80,model.length())),"mac",mac.toUpperCase(Locale.US),"white",white,"rgb",rgb,"cct",cct,
                "power",power,"brightness",number(values,0,100,"brightness"),"temperature",temp!=null&&temp>=2700?temp:null,"color",color);
        } catch(IllegalArgumentException ex) { return null; }
    }
    private static Integer number(Map<String,Object> values,int min,int max,String... keys) {
        for(String key:keys) {
            Object value=values.get(key);
            if(value instanceof Boolean && min==0&&max==1) return ((Boolean)value)?1:0;
            if(value instanceof String && ((String)value).matches("[0-9]{1,5}")) value=Long.valueOf((String)value);
            try { if(value instanceof Number) return integer(value,min,max,key); } catch(IllegalArgumentException ignored) {}
        }
        return null;
    }
    public static String command(Map<String,Object> profile,String action,Object value) {
        if(action.equals("power")) {
            if(!(value instanceof Boolean)) throw new IllegalArgumentException("Choose On or Off.");
            return "0001xx09xxx"+(((Boolean)value)?"1":"0");
        }
        if(action.equals("brightness")) return String.format(Locale.US,"0001xx%s%03d",Boolean.TRUE.equals(profile.get("white"))?"02":"07",integer(value,5,100,"Brightness"));
        if(action.equals("temperature")) {
            if(!Boolean.TRUE.equals(profile.get("cct"))) throw new IllegalArgumentException("This bulb has not reported adjustable white temperature.");
            return "0001xx"+(Boolean.TRUE.equals(profile.get("white"))?"01":"10")+integer(value,2700,6500,"Temperature");
        }
        if(action.equals("color")) {
            if(!Boolean.TRUE.equals(profile.get("rgb"))) throw new IllegalArgumentException("This bulb has not reported color support.");
            if(!(value instanceof String) || !((String)value).matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Choose a valid color.");
            String hex=(String)value;
            return String.format(Locale.US,"0001xx01%03d%03d%03d",Integer.parseInt(hex.substring(1,3),16),Integer.parseInt(hex.substring(3,5),16),Integer.parseInt(hex.substring(5,7),16));
        }
        throw new IllegalArgumentException("Unknown light control.");
    }
    public static List<String> setupCommands(String ssid,String password,String model) {
        int ssidBytes=ssid.getBytes(StandardCharsets.UTF_8).length, passBytes=password.getBytes(StandardCharsets.UTF_8).length;
        if(ssidBytes<1 || ssidBytes>32 || ssid.matches("(?s).*[\\x00-\\x1f\\x7f].*")) throw new IllegalArgumentException("Wi-Fi name must be 1 to 32 bytes with no control characters.");
        if(passBytes<8 || passBytes>63 || password.matches("(?s).*[\\x00-\\x1f\\x7f].*")) throw new IllegalArgumentException("Wi-Fi password must be 8 to 63 bytes with no control characters.");
        List<String> out=new ArrayList<String>();
        out.add("0000xx01"+ssid); out.add("0000xx02"+password); out.add("0000xx05SMTH");
        if(model.equalsIgnoreCase("white bulb")) out.add("0000xx0S11");
        out.add("0000xx03"); return out;
    }
}
