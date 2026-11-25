package local.smitch;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.SocketFactory;

public final class ProtocolTest {
    private static int checks;
    private static void check(boolean ok,String description) { checks++; if(!ok) throw new AssertionError(description); }
    private static void equal(Object a,Object b) { check(Objects.equals(a,b),"Expected "+a+", got "+b); }
    private static void rejects(Runnable action) { boolean rejected=false; try {action.run();} catch(IllegalArgumentException ex){rejected=true;} check(rejected,"Expected input rejection"); }
    public static void main(String[] args) throws Exception {
        final String rgb="{\"model\":\"RGB CCT Bulb\",\"mac_address\":\"AA:BB:CC:DD:EE:FF\",\"values\":[{\"r\":255,\"g\":20,\"b\":0,\"brightness\":65,\"temperature\":4000,\"powerState\":true}]}";
        Map<String,Object> color=BulbProtocol.parseReport(rgb);
        equal(Boolean.TRUE,color.get("rgb")); equal(Boolean.TRUE,color.get("cct")); equal(1,color.get("power")); equal(65,color.get("brightness")); equal("#ff1400",color.get("color"));
        Map<String,Object> white=BulbProtocol.parseReport("{\"model\":\"White Bulb\",\"mac_address\":\"aabbccddeeff\",\"values\":[{\"brightness\":\"42\",\"temp\":\"2700\",\"power\":0}]}");
        equal(Boolean.TRUE,white.get("white")); equal(Boolean.FALSE,white.get("rgb")); equal(42,white.get("brightness")); equal(2700,white.get("temperature"));
        equal("0001xx09xxx1",BulbProtocol.command(color,"power",true)); equal("0001xx09xxx0",BulbProtocol.command(white,"power",false));
        equal("0001xx07005",BulbProtocol.command(color,"brightness",5)); equal("0001xx02100",BulbProtocol.command(white,"brightness",100));
        equal("0001xx106500",BulbProtocol.command(color,"temperature",6500)); equal("0001xx012700",BulbProtocol.command(white,"temperature",2700));
        equal("0001xx01255001000",BulbProtocol.command(color,"color","#ff0100"));
        equal("0001xx01000255000",BulbProtocol.command(color,"color","#00ff00"));
        rejects(new Runnable(){public void run(){BulbProtocol.command(color,"brightness",4);}});
        rejects(new Runnable(){public void run(){BulbProtocol.command(color,"brightness",101);}});
        rejects(new Runnable(){public void run(){BulbProtocol.command(color,"brightness",20.5);}});
        rejects(new Runnable(){public void run(){BulbProtocol.command(white,"color","#ffffff");}});
        rejects(new Runnable(){public void run(){BulbProtocol.command(color,"power",1);}});
        rejects(new Runnable(){public void run(){BulbProtocol.command(color,"color","#ffffff\r0000xx03");}});
        equal("192.168.4.1",BulbProtocol.address("192.168.4.1")); equal("10.0.0.9",BulbProtocol.address("10.0.0.9")); equal("172.16.1.1",BulbProtocol.address("172.16.1.1"));
        for(final String ip:Arrays.asList("8.8.8.8","127.0.0.1","192.168.300.1","172.32.1.1","example.com","192.168.4.1\r0001xx09xxx1")) rejects(new Runnable(){public void run(){BulbProtocol.address(ip);}});
        equal(null,BulbProtocol.parseReport("{\"model\":\"HTTP server\",\"mac_address\":\"AABBCCDDEEFF\"}"));
        equal(null,BulbProtocol.parseReport("{\"model\":\"RGB Bulb\",\"mac_address\":\"wrong\"}"));
        equal(null,BulbProtocol.parseReport("HTTP/1.1 200 OK"));
        equal(null,BulbProtocol.parseReport("{\"model\":"));
        equal(Arrays.asList("0000xx01Test WiFi","0000xx02password123","0000xx05SMTH","0000xx0S11","0000xx03"),BulbProtocol.setupCommands("Test WiFi","password123","White Bulb"));
        equal(4,BulbProtocol.setupCommands("Test WiFi","password123","RGB Bulb").size());
        rejects(new Runnable(){public void run(){BulbProtocol.setupCommands("a\r0000xx03","password123","");}});
        rejects(new Runnable(){public void run(){BulbProtocol.setupCommands("WiFi","bad\rpassword","");}});
        rejects(new Runnable(){public void run(){BulbProtocol.setupCommands("WiFi","short","");}});
        rejects(new Runnable(){public void run(){BulbProtocol.setupCommands("12345678901234567890123456789012345","password123","");}});
        rejects(new Runnable(){public void run(){BulbProtocol.setupCommands("WiFi","éééééééééééééééééééééééééééééééé","");}});
        String escaped=Json.string(Json.map("string","quote\" line\n nul\0 ü \u2028", "list",Arrays.asList(1,true,null)));
        equal("quote\" line\n nul\0 ü \u2028",Json.object(escaped).get("string"));
        rejects(new Runnable(){public void run(){Json.parse("[1,]");}});
        rejects(new Runnable(){public void run(){Json.parse("{\"x\":NaN}");}});
        rejects(new Runnable(){public void run(){Json.parse("{} trailing");}});

        // Exercise the actual wire reader/writer through simulated, fragmented socket streams.
        // This works in restricted environments that prohibit even loopback connections.
        final List<String> captured=Collections.synchronizedList(new ArrayList<String>());
        SocketFactory factory=new SocketFactory(){
            int next;
            @Override public Socket createSocket() throws IOException {
                final int index=next++;
                final ByteArrayOutputStream output=new ByteArrayOutputStream();
                final byte[] data=(index==0?rgb+"\r\n":index==2?"not a bulb\r":"").getBytes("UTF-8");
                return new Socket(){
                    @Override public void connect(SocketAddress endpoint,int timeout) {equal("192.168.4.1",((InetSocketAddress)endpoint).getAddress().getHostAddress());equal(80,((InetSocketAddress)endpoint).getPort());check(timeout>0,"Bounded connect timeout");}
                    @Override public void setSoTimeout(int timeout){check(timeout>0,"Bounded read timeout");}
                    @Override public OutputStream getOutputStream(){return output;}
                    @Override public InputStream getInputStream(){
                        if(input==null) input=index==3?new InputStream(){public int read()throws IOException{throw new SocketTimeoutException();}}:
                            new ByteArrayInputStream(data){@Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(7,len));}};
                        return input;
                    }
                    InputStream input;
                    @Override public synchronized void close(){try{captured.add(output.toString("UTF-8"));}catch(Exception ex){throw new AssertionError(ex);}}
                };
            }
            @Override public Socket createSocket(String h,int p)throws IOException{throw new IOException();}
            @Override public Socket createSocket(String h,int p,InetAddress a,int l)throws IOException{throw new IOException();}
            @Override public Socket createSocket(InetAddress a,int p)throws IOException{throw new IOException();}
            @Override public Socket createSocket(InetAddress a,int p,InetAddress l,int lp)throws IOException{throw new IOException();}
        };
        BulbProtocol client=new BulbProtocol(factory);
        Map<String,Object> identified=client.identify("192.168.4.1",80,1800);
        equal("AA:BB:CC:DD:EE:FF",identified.get("mac")); equal("192.168.4.1",identified.get("ip")); equal(80,identified.get("port"));
        client.send("192.168.4.1",80,BulbProtocol.command(identified,"power",false));
        boolean failed=false; try{client.identify("192.168.4.1",80,500);}catch(IOException ex){failed=true;} check(failed,"Reject non-bulb TCP response");
        failed=false;try{client.identify("192.168.4.1",80,500);}catch(IOException ex){failed=true;}check(failed,"Handle read timeout");
        equal(Arrays.asList("0001xx03\r","0001xx09xxx0\r","0001xx03\r","0001xx03\r"),captured);
        System.out.println("PASS: "+checks+" protocol, validation, provisioning and simulated socket checks.");
    }
}
