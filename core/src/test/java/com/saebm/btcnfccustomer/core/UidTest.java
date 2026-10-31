package com.saebm.btcnfccustomer.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class UidTest {
 @Test public void preservesRawOrderLeadingZerosAndUnsignedBytes() { assertEquals("00017F80", Uid.format(new byte[]{0,1,127,(byte)128})); }
}
