package com.saebm.btcnfccustomer.core;
import java.util.*;
public final class Response {
 public long confirmedSats, pendingDeltaSats;
 public String serial,address,source,fetchedAt,nextAfterTxid;
 public List<Transaction> transactions;
 public static final class Transaction { public String txid; public long blockHeight,blockTime,deltaSats; }
 public static Response parse(String json) {
  try {
   if (json.length() > RequestPolicy.MAX_BODY) throw new IllegalArgumentException();
   com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(json));
   reader.setStrictness(com.google.gson.Strictness.STRICT);
   com.google.gson.JsonObject root = uniqueJson(reader, 0).getAsJsonObject();
   if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException();
   if (!text(root,"history_scope",32).equals("confirmed_only") || !text(root,"mempool_scope",32).equals("balance_only") || !text(root,"consistency",32).equals("non_atomic")) throw new IllegalArgumentException();
   Response p = new Response();
   p.serial=text(root,"serial",128); p.address=text(root,"address",128); p.source=text(root,"source",128); p.fetchedAt=text(root,"fetched_at",64);
   if (!BitcoinAddress.isValid(p.address) || !p.serial.matches(
       "(?=[A-Za-z0-9][A-Za-z0-9_\\-]{0,63}$)[A-Za-z0-9_\\-]*"
       + "[0-9]{2}[0-9ab][0-9a-u][0-9a-mz](?:[0-5][0-9][0-5][0-9]|[0-9a-zA-X]{2})[0-9a-z]+")) throw new IllegalArgumentException();
   java.time.Instant.parse(p.fetchedAt);
   p.confirmedSats=integer(root,"confirmed_sats"); p.pendingDeltaSats=integer(root,"pending_delta_sats");
   if(p.confirmedSats<0) throw new IllegalArgumentException();
   if(root.has("next_after_txid") && !root.get("next_after_txid").isJsonNull()) { p.nextAfterTxid=text(root,"next_after_txid",64); txid(p.nextAfterTxid); }
   com.google.gson.JsonArray rows=root.getAsJsonArray("transactions");
   if(rows.size()>100) throw new IllegalArgumentException();
   List<Transaction> txs=new ArrayList<>(); Set<String> seen=new HashSet<>();
   for(com.google.gson.JsonElement element:rows) {
    com.google.gson.JsonObject row=element.getAsJsonObject();
    if (!row.get("confirmed").isJsonPrimitive() || !row.getAsJsonPrimitive("confirmed").isBoolean() || !row.get("confirmed").getAsBoolean()) throw new IllegalArgumentException();
    Transaction tx=new Transaction(); tx.txid=text(row,"txid",64); txid(tx.txid);
    if(!seen.add(tx.txid)) throw new IllegalArgumentException();
    tx.blockHeight=integer(row,"block_height"); tx.blockTime=integer(row,"block_time"); tx.deltaSats=integer(row,"delta_sats");
    if(tx.blockHeight<0 || tx.blockTime<0) throw new IllegalArgumentException();
    txs.add(tx);
   }
   if(p.nextAfterTxid != null && (txs.isEmpty() || !p.nextAfterTxid.equals(txs.get(txs.size()-1).txid))) throw new IllegalArgumentException();
   p.transactions=Collections.unmodifiableList(txs); return p;
  } catch (Exception ex) { throw new IllegalArgumentException("Invalid server response"); }
 }
 private static com.google.gson.JsonElement uniqueJson(com.google.gson.stream.JsonReader reader, int depth) throws java.io.IOException {
  if(depth>16) throw new IllegalArgumentException();
  switch(reader.peek()) {
   case BEGIN_OBJECT:
    com.google.gson.JsonObject object=new com.google.gson.JsonObject(); reader.beginObject();
    while(reader.hasNext()) { String key=reader.nextName(); if(object.has(key)) throw new IllegalArgumentException(); object.add(key,uniqueJson(reader,depth+1)); }
    reader.endObject(); return object;
   case BEGIN_ARRAY:
    com.google.gson.JsonArray array=new com.google.gson.JsonArray();reader.beginArray();
    while(reader.hasNext()) { if(array.size()>=100) throw new IllegalArgumentException();array.add(uniqueJson(reader,depth+1)); }
    reader.endArray();return array;
   case STRING: return new com.google.gson.JsonPrimitive(reader.nextString());
   case NUMBER: return com.google.gson.JsonParser.parseString(reader.nextString());
   case BOOLEAN: return new com.google.gson.JsonPrimitive(reader.nextBoolean());
   case NULL: reader.nextNull();return com.google.gson.JsonNull.INSTANCE;
   default: throw new IllegalArgumentException();
  }
 }
 private static String text(com.google.gson.JsonObject row,String key,int max) {
  com.google.gson.JsonPrimitive p=row.getAsJsonPrimitive(key);
  if(!p.isString()) throw new IllegalArgumentException();
  String value=p.getAsString();
  if(value.isEmpty() || value.length()>max || value.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c)==Character.FORMAT)) throw new IllegalArgumentException();
  return value;
 }
 private static long integer(com.google.gson.JsonObject row,String key) {
  com.google.gson.JsonPrimitive p=row.getAsJsonPrimitive(key);
  if(!p.isNumber() || !p.getAsString().matches("-?(0|[1-9][0-9]*)")) throw new IllegalArgumentException();
  return Long.parseLong(p.getAsString());
 }
 private static void txid(String value) { if(!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(); }
 public static String signed(long value) { return value>=0 ? "+"+value : Long.toString(value); }
}
