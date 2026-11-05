package com.saebm.btcnfccustomer.core;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure Java QR classifier. It never logs or returns untrusted payloads. */
public final class QrPayload {
    public enum Kind { BITCOIN_ADDRESS, BITCOIN_URI, ALLOWLISTED_HTTPS_URL, UNTRUSTED, INVALID }
    public static final class Result { public final Kind kind; public final String value; private Result(Kind k,String v){kind=k;value=v;} }
    private static final Set<String> HOSTS=new HashSet<>(Arrays.asList("btc.saeb.id.ir","btc4.fun","bitcoin4.fun","b.tc4fun.ir"));
    private QrPayload() { }
    public static Result classify(String payload) {
        if(payload==null||payload.isEmpty()||payload.length()>2048||!payload.equals(payload.trim())
                ||payload.codePoints().anyMatch(c -> Character.isISOControl(c)||Character.getType(c)==Character.FORMAT)) return new Result(Kind.INVALID,null);
        String value=payload.trim();
        if(BitcoinAddress.isValid(value)) return new Result(Kind.BITCOIN_ADDRESS,value);
        if(value.regionMatches(true,0,"bitcoin:",0,8)) { String body=value.substring(8); int q=body.indexOf('?'); String address=q<0?body:body.substring(0,q); if(BitcoinAddress.isValid(address)&&safeQuery(q<0?"":body.substring(q+1))) return new Result(Kind.BITCOIN_URI,address); return new Result(Kind.INVALID,null); }
        try { URI u=URI.create(value); if(!"https".equalsIgnoreCase(u.getScheme())) return new Result(Kind.UNTRUSTED,null); String host=u.getHost(); if(host==null||u.getPort()!=-1||u.getUserInfo()!=null) return new Result(Kind.UNTRUSTED,null); host=host.toLowerCase(Locale.ROOT); if(!HOSTS.contains(host)) return new Result(Kind.UNTRUSTED,null); return new Result(Kind.ALLOWLISTED_HTTPS_URL,u.toString()); } catch(IllegalArgumentException e){ return new Result(Kind.INVALID,null); }
    }
    private static boolean safeQuery(String query) { if(query.isEmpty()) return true; Set<String> seen=new HashSet<>(); for(String part:query.split("&",-1)){int eq=part.indexOf('=');if(eq<=0||eq!=part.lastIndexOf('='))return false;String key=part.substring(0,eq);String val=part.substring(eq+1);if(!Arrays.asList("amount","label","message").contains(key)||!seen.add(key))return false;try{String decoded=URLDecoder.decode(val, StandardCharsets.UTF_8.name());if(decoded.length()>256||decoded.indexOf('#')>=0||decoded.indexOf('&')>=0||decoded.indexOf('=')>=0)return false;if(key.equals("amount")&&!decoded.matches("[0-9]+(?:\\.[0-9]{1,8})?"))return false;}catch(Exception e){return false;}}return true; }
}
