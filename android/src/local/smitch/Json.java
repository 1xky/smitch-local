package local.smitch;

import java.util.*;

/** Small JSON reader/writer shared by the bridge and wire protocol. */
public final class Json {
    private final String text;
    private int at;
    private Json(String text) { this.text = text; }
    public static Object parse(String text) {
        if (text == null || text.length() > 32768) throw new IllegalArgumentException("Invalid response size.");
        Json parser = new Json(text);
        Object value = parser.value(0);
        parser.space();
        if (parser.at != text.length()) throw new IllegalArgumentException("Invalid JSON response.");
        return value;
    }
    public static Map<String,Object> object(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) throw new IllegalArgumentException("Expected a JSON object.");
        return (Map<String,Object>) value;
    }
    public static Map<String,Object> map(Object... pairs) {
        Map<String,Object> out = new LinkedHashMap<String,Object>();
        for (int i=0;i<pairs.length;i+=2) out.put((String)pairs[i], pairs[i+1]);
        return out;
    }
    public static String string(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map) {
            StringBuilder out = new StringBuilder("{");
            for (Object item : ((Map<?,?>)value).entrySet()) {
                Map.Entry<?,?> entry = (Map.Entry<?,?>)item;
                if (out.length()>1) out.append(',');
                out.append(string(entry.getKey().toString())).append(':').append(string(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable) {
            StringBuilder out = new StringBuilder("[");
            for (Object item : (Iterable<?>)value) {
                if (out.length()>1) out.append(',');
                out.append(string(item));
            }
            return out.append(']').toString();
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch(c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c<32 || c=='\u2028' || c=='\u2029') out.append(String.format(Locale.US,"\\u%04x",(int)c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }
    private void space() { while(at<text.length() && Character.isWhitespace(text.charAt(at))) at++; }
    private char take() { if(at>=text.length()) throw new IllegalArgumentException("Incomplete JSON response."); return text.charAt(at++); }
    private Object value(int depth) {
        if(depth>24) throw new IllegalArgumentException("Response nesting is too deep.");
        space(); char c=take();
        if(c=='"') return quoted();
        if(c=='{' || c=='[') {
            boolean object=c=='{';
            Map<String,Object> map=new LinkedHashMap<String,Object>();
            List<Object> list=new ArrayList<Object>();
            space();
            if(at<text.length() && text.charAt(at)==(object?'}':']')) { at++; return object?map:list; }
            while(true) {
                if(object) {
                    space(); if(take()!='"') throw new IllegalArgumentException("Invalid JSON key.");
                    String key=quoted(); space(); if(take()!=':') throw new IllegalArgumentException("Invalid JSON object.");
                    map.put(key,value(depth+1));
                } else list.add(value(depth+1));
                space(); char next=take();
                if(next==(object?'}':']')) return object?map:list;
                if(next!=',') throw new IllegalArgumentException("Invalid JSON separator.");
            }
        }
        int start=at-1;
        while(at<text.length() && ",]} \r\n\t".indexOf(text.charAt(at))<0) at++;
        String token=text.substring(start,at);
        if(token.equals("true")) return Boolean.TRUE;
        if(token.equals("false")) return Boolean.FALSE;
        if(token.equals("null")) return null;
        if(!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("Invalid JSON value.");
        try { if(token.indexOf('.')>=0 || token.indexOf('e')>=0 || token.indexOf('E')>=0) return Double.valueOf(token); return Long.valueOf(token); }
        catch(NumberFormatException ex) { throw new IllegalArgumentException("Invalid JSON number."); }
    }
    private String quoted() {
        StringBuilder out=new StringBuilder();
        while(true) {
            char c=take();
            if(c=='"') return out.toString();
            if(c<32) throw new IllegalArgumentException("Invalid JSON string.");
            if(c=='\\') {
                c=take();
                switch(c) {
                    case 'n': c='\n'; break; case 'r': c='\r'; break; case 't': c='\t'; break;
                    case 'b': c='\b'; break; case 'f': c='\f'; break;
                    case 'u':
                        if(at+4>text.length()) throw new IllegalArgumentException("Invalid JSON escape.");
                        c=(char)Integer.parseInt(text.substring(at,at+4),16); at+=4; break;
                    case '"': case '\\': case '/': break;
                    default: throw new IllegalArgumentException("Invalid JSON escape.");
                }
            }
            out.append(c);
        }
    }
}
