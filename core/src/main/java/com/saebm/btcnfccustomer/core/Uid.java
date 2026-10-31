package com.saebm.btcnfccustomer.core;
public final class Uid {
 private Uid() {}
 public static String format(byte[] raw) {
  if (raw == null || (raw.length != 4 && raw.length != 7 && raw.length != 10)) throw new IllegalArgumentException("Unsupported UID length");
  StringBuilder value = new StringBuilder();
  for (byte b : raw) { value.append("0123456789ABCDEF".charAt((b & 255) >>> 4)); value.append("0123456789ABCDEF".charAt(b & 15)); }
  return value.toString();
 }
}
