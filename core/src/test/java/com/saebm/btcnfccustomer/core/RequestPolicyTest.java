package com.saebm.btcnfccustomer.core;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class RequestPolicyTest {
 @Test public void fixedOriginAndExactIdentifiers() throws Exception {
  assertEquals("https://btc4.fun/api/tags/00017F80", RequestPolicy.url("00017F80", null).toString());
  String tx = "a".repeat(64);
  assertEquals("https://btc4.fun/api/tags/00000000000001?after_txid="+tx, RequestPolicy.url("00000000000001", tx).toString());
  assertEquals("https://btc4.fun/api/tags/00000000000000000001", RequestPolicy.url("00000000000000000001", null).toString());
  for(String uid : new String[]{"00017f80", " 00017F80", "00017F80/", "0011", "", "00017F80?x=y"}) assertThrows(IllegalArgumentException.class, () -> RequestPolicy.url(uid,null));
  assertThrows(IllegalArgumentException.class, () -> RequestPolicy.url("00017F80","../"));
 }
 @Test public void serialAndAddressUrlsStayOnTheFixedOrigin() throws Exception {
  assertEquals("https://btc4.fun/api/wallets/26b1z00001?balance=1", RequestPolicy.walletUrl("26b1z00001", true).toString());
  assertEquals("https://btc4.fun/api/wallets/26b1z00001", RequestPolicy.walletUrl("26b1z00001", false).toString());
  String address = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";
  assertEquals("https://btc4.fun/api/addresses/" + address + "?balance=1", RequestPolicy.addressUrl(address, true).toString());
  assertEquals("https://btc4.fun/api/addresses/" + address, RequestPolicy.addressUrl(address, false).toString());
  // The site's own shape admits any prefix, so a raw UID like 00017F80 is a legal serial there too.
  assertTrue(RequestPolicy.validSerial("00017F80"));
  for (String serial : new String[]{"", "TEST-ONLY", "26b1z00001/", " 26b1z00001", "26b1z00001?balance=1", "../", "x".repeat(65)}) {
   assertThrows(IllegalArgumentException.class, () -> RequestPolicy.walletUrl(serial, true));
   assertFalse(RequestPolicy.validSerial(serial));
  }
  for (String bad : new String[]{"", "bc1qtestonly", "bc1qzmtrqsfuaf6l6kkcsseumq26ukaphfj9skkug6", address + "/"}) {
   assertThrows(IllegalArgumentException.class, () -> RequestPolicy.addressUrl(bad, true));
  }
 }
 @Test public void rawUidLengthsAreNotNormalized() {
  for(int length : new int[]{0,1,3,5,6,8,9,11}) assertThrows(IllegalArgumentException.class, () -> Uid.format(new byte[length]));
  assertThrows(IllegalArgumentException.class, () -> Uid.format(null));
 }
 @Test public void responseBodyHasHardBoundAndDeadline() throws Exception {
  assertEquals("abc", RequestPolicy.read(new ByteArrayInputStream("abc".getBytes()), 3, System.nanoTime()+1_000_000_000L));
  assertThrows(IOException.class, () -> RequestPolicy.read(new ByteArrayInputStream(new byte[4]), 3, System.nanoTime()+1_000_000_000L));
  assertThrows(IOException.class, () -> RequestPolicy.read(new ByteArrayInputStream(new byte[0]), 3, 0));
  assertThrows(IOException.class, () -> RequestPolicy.read(new ByteArrayInputStream(new byte[]{(byte)0xc0,(byte)0xaf}), 3, System.nanoTime()+1_000_000_000L));
 }
 @Test public void retryAfterIsDisplayOnlyAndBounded() {
  assertEquals(20, RequestPolicy.retrySeconds(null));
  assertEquals(20, RequestPolicy.retrySeconds("garbage"));
  assertEquals(0, RequestPolicy.retrySeconds("0"));
  assertEquals(3600, RequestPolicy.retrySeconds("99999"));
  assertEquals(20, RequestPolicy.retrySeconds("-1"));
 }
}
