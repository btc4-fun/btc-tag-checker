package com.saebm.btcnfccustomer.core;
import java.io.*;
import java.net.*;
public final class RequestPolicy {
 public static final int MAX_BODY = 262144;
 public static final int CONNECT_MS = 5000, READ_MS = 5000, TOTAL_MS = 15000;
 /** The site's own printed-serial shape; the app never invents a looser one. */
 public static final String SERIAL_PATTERN =
  "(?=[A-Za-z0-9][A-Za-z0-9_\\-]{0,63}$)[A-Za-z0-9_\\-]*"
  + "[0-9]{2}[0-9ab][0-9a-u][0-9a-mz](?:[0-5][0-9][0-5][0-9]|[0-9a-zA-X]{2})[0-9a-z]+";
 public static URL url(String uid, String cursor) throws MalformedURLException {
  if (uid == null || !uid.matches("(?:[0-9A-F]{8}|[0-9A-F]{14}|[0-9A-F]{20})")) throw new IllegalArgumentException("Invalid UID");
  if (cursor != null && !cursor.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid cursor");
  return new URL("https://btc4.fun/api/tags/" + uid + (cursor == null ? "" : "?after_txid=" + cursor));
 }
 public static boolean validSerial(String serial) { return serial != null && serial.matches(SERIAL_PATTERN); }
 /** Printed serial on the envelope, never the raw NFC UID. */
 public static URL walletUrl(String serial, boolean balance) throws MalformedURLException {
  if (!validSerial(serial)) throw new IllegalArgumentException("Invalid serial");
  return new URL("https://btc4.fun/api/wallets/" + serial + (balance ? "?balance=1" : ""));
 }
 public static URL addressUrl(String address, boolean balance) throws MalformedURLException {
  if (!BitcoinAddress.isValid(address)) throw new IllegalArgumentException("Invalid address");
  return new URL("https://btc4.fun/api/addresses/" + address + (balance ? "?balance=1" : ""));
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
