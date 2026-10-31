package com.saebm.btcnfccustomer.core;
import java.io.*;
import java.net.*;
public final class RequestPolicy {
 public static final int MAX_BODY = 262144;
 public static final int CONNECT_MS = 5000, READ_MS = 5000, TOTAL_MS = 15000;
 public static URL url(String uid, String cursor) throws MalformedURLException {
  if (uid == null || !uid.matches("(?:[0-9A-F]{8}|[0-9A-F]{14}|[0-9A-F]{20})")) throw new IllegalArgumentException("Invalid UID");
  if (cursor != null && !cursor.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid cursor");
  return new URL("https://btc4.fun/api/tags/" + uid + (cursor == null ? "" : "?after_txid=" + cursor));
 }
 public static String read(InputStream in, int max, long deadline) throws IOException {
  ByteArrayOutputStream out = new ByteArrayOutputStream();
  byte[] buffer = new byte[4096];
  while (true) {
   if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new java.net.SocketTimeoutException();
   int count = in.read(buffer, 0, Math.min(buffer.length, max - out.size() + 1));
   if (System.nanoTime() >= deadline) throw new java.net.SocketTimeoutException();
   if (count < 0) break;
   if (out.size() + count > max) throw new IOException("Response too large");
   out.write(buffer, 0, count);
  }
  return java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString();
 }
 public static int retrySeconds(String value) {
  if (value == null || !value.matches("[0-9]{1,9}")) return 20;
  try { return Math.min(3600, Integer.parseInt(value)); } catch (NumberFormatException ex) { return 20; }
 }
}
