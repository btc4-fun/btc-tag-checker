package com.saebm.btcnfccustomer;
import com.saebm.btcnfccustomer.core.RequestPolicy;
import com.saebm.btcnfccustomer.core.Response;
import com.saebm.btcnfccustomer.core.Uid;
import org.junit.Test;
import static org.junit.Assert.*;
/** Exercises the core dependency as resolved by the Android debug unit-test variant. */
public class CustomerVariantTest {
 @Test public void debugVariantUsesReadOnlyFixedEndpointContract() throws Exception {
  assertEquals("https://btc4.fun/api/tags/00000001", RequestPolicy.url(Uid.format(new byte[]{0,0,0,1}),null).toString());
  assertEquals("-1",Response.signed(-1));
 }
}
