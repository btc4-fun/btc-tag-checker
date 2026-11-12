package com.saebm.btcnfccustomer.core;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Exact decimal/integer pricing. No binary floating point, network, or persisted inputs. */
public final class QuoteCalculator {
    public static final BigInteger MAX = new BigInteger("2100000000000000");
    private static final BigInteger MIN = BigInteger.valueOf(501);
    public final BigDecimal satoshiToman;
    public final BigInteger network, materials, post, tag;
    private QuoteCalculator(BigDecimal sat, BigInteger network, BigInteger materials, BigInteger post, BigInteger tag) {
        this.satoshiToman = sat; this.network = network; this.materials = materials; this.post = post; this.tag = tag;
    }
    static BigDecimal rate(String value, String maximum) {
        if (value == null || value.length() > 64 || !value.matches("[0-9]+(?:\\.[0-9]{1,18})?")) throw new IllegalArgumentException("Invalid rate");
        BigDecimal number = new BigDecimal(value);
        if (number.signum() <= 0 || number.compareTo(new BigDecimal(maximum)) > 0) throw new IllegalArgumentException("Invalid rate");
        return number;
    }
    private static BigInteger ceil(BigDecimal value) { return value.setScale(0, RoundingMode.CEILING).toBigIntegerExact(); }
    public static QuoteCalculator fromRaw(String btc, String usdt, String fee, boolean withTag) {
        BigDecimal sat = rate(btc,"1000000000000000").multiply(new BigDecimal("1.005")).movePointLeft(8);
        BigDecimal dollar = rate(usdt,"1000000000000000").multiply(new BigDecimal("1.005"));
        return new QuoteCalculator(sat, ceil(rate(fee,"10000").multiply(new BigDecimal("141"))),
            dollar.multiply(new BigDecimal("0.11")).divide(sat,0,RoundingMode.CEILING).toBigIntegerExact(),
            dollar.multiply(new BigDecimal("0.75")).divide(sat,0,RoundingMode.CEILING).toBigIntegerExact(),
            withTag ? dollar.multiply(new BigDecimal("0.12")).divide(sat,0,RoundingMode.CEILING).toBigIntegerExact() : BigInteger.ZERO);
    }
    public JsonObject calculate(String input, boolean budget) {
        if (input == null || !input.matches("[1-9][0-9]{0,31}")) throw new IllegalArgumentException("Invalid integer input");
        BigInteger value = new BigInteger(input);
        if (!budget) return quote(value,null);
        if (quote(MIN,null).get("total_toman").getAsBigInteger().compareTo(value)>0) throw new IllegalArgumentException("Insufficient budget");
        BigInteger low=MIN, high=MAX;
        while (low.compareTo(high)<0) {
            BigInteger mid=low.add(high).add(BigInteger.ONE).divide(BigInteger.valueOf(2));
            if (quote(mid,null).get("total_toman").getAsBigInteger().compareTo(value)<=0) low=mid;
            else high=mid.subtract(BigInteger.ONE);
        }
        return quote(low,value);
    }
    public JsonObject quote(BigInteger principal, BigInteger budget) {
        if (principal.compareTo(MIN)<0 || principal.compareTo(MAX)>0) throw new IllegalArgumentException("Principal outside supply bounds");
        BigInteger subtotal=principal.add(network).add(materials).add(post).add(tag);
        BigInteger operator=subtotal.multiply(BigInteger.valueOf(15)).add(BigInteger.valueOf(99)).divide(BigInteger.valueOf(100));
        BigInteger total=subtotal.add(operator), toman=ceil(new BigDecimal(total).multiply(satoshiToman));
        JsonObject row=new JsonObject();
        row.addProperty("principal_sats",principal); row.addProperty("btc",new BigDecimal(principal,8).toPlainString());
        row.addProperty("network_sats",network); row.addProperty("materials_sats",materials);
        row.addProperty("post_sats",post); row.addProperty("tag_sats",tag);
        row.addProperty("operator_sats",operator); row.addProperty("operator_toman",ceil(new BigDecimal(operator).multiply(satoshiToman))); row.addProperty("total_sats",total);
        row.addProperty("total_toman",toman); row.addProperty("total_rial",toman.multiply(BigInteger.TEN));
        row.addProperty("budget_toman",budget); row.addProperty("remainder_toman",budget==null?BigInteger.ZERO:budget.subtract(toman));
        return row;
    }
}
