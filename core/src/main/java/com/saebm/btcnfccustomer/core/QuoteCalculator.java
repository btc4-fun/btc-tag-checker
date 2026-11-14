package com.saebm.btcnfccustomer.core;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Exact decimal/integer pricing. No binary floating point, network, or persisted inputs. */
public final class QuoteCalculator {
    public static final BigInteger MAX = new BigInteger("2100000000000000");
    private static final BigInteger MIN = BigInteger.valueOf(501);
    /**
     * The two published pricing models. CURRENT is the 1% acquisition model: the bitcoin rate
     * carries the markup once and the exchange's two fixed fees (a two-cent conversion charge and
     * a 423-satoshi transfer) are separate costs. LEGACY is the older 0.5% model, which has
     * neither fee; it is kept so a server that still serves the old table stays readable.
     */
    public enum Model { CURRENT, LEGACY }
    public final Model model;
    public final BigDecimal satoshiToman;
    public final BigInteger network, convert, transfer, materials, envelope, post, tag;
    private QuoteCalculator(Model model, BigDecimal sat, BigInteger network, BigInteger convert, BigInteger transfer,
                            BigInteger materials, BigInteger envelope, BigInteger post, BigInteger tag) {
        this.model = model; this.satoshiToman = sat; this.network = network;
        this.convert = convert; this.transfer = transfer; this.materials = materials;
        this.envelope = envelope; this.post = post; this.tag = tag;
    }
    static BigDecimal rate(String value, String maximum) {
        if (value == null || value.length() > 64 || !value.matches("[0-9]+(?:\\.[0-9]{1,18})?")) throw new IllegalArgumentException("Invalid rate");
        BigDecimal number = new BigDecimal(value);
        if (number.signum() <= 0 || number.compareTo(new BigDecimal(maximum)) > 0) throw new IllegalArgumentException("Invalid rate");
        return number;
    }
    private static BigInteger ceil(BigDecimal value) { return value.setScale(0, RoundingMode.CEILING).toBigIntegerExact(); }
    public static QuoteCalculator fromRaw(String btc, String usdt, String fee, boolean withTag) {
        return fromRaw(btc, usdt, fee, withTag, false, Model.CURRENT);
    }
    /** Hand delivery removes only the envelope and the postage; the card materials stay. */
    public static QuoteCalculator fromRaw(String btc, String usdt, String fee, boolean withTag, boolean inPerson) {
        return fromRaw(btc, usdt, fee, withTag, inPerson, Model.CURRENT);
    }
    /**
     * The model is always explicit: a caller that reads a payload must say which contract it saw,
     * so a missing fee is never silently treated as zero.
     */
    public static QuoteCalculator fromRaw(String btc, String usdt, String fee, boolean withTag, boolean inPerson, Model model) {
        if (model == null) throw new IllegalArgumentException("Invalid model");
        BigDecimal rawBtc = rate(btc, "1000000000000000"), rawUsdt = rate(usdt, "1000000000000000");
        // The acquisition markup is applied to the bitcoin rate ONCE. The tether rate is used as
        // received, because the dollar costs are divided by the already marked-up satoshi price.
        BigDecimal sat = model == Model.CURRENT
            ? rawBtc.multiply(new BigDecimal("1.01")).movePointLeft(8)
            : rawBtc.multiply(new BigDecimal("1.005")).movePointLeft(8);
        BigDecimal dollar = model == Model.CURRENT ? rawUsdt : rawUsdt.multiply(new BigDecimal("1.005"));
        BigInteger convert = model == Model.CURRENT
            ? dollar.multiply(new BigDecimal("0.02")).divide(sat, 0, RoundingMode.CEILING).toBigIntegerExact()
            : BigInteger.ZERO;
        // The exchange's own bitcoin transfer fee is fixed in bitcoin, so it is already satoshis
        // and never converted through a rate.
        BigInteger transfer = model == Model.CURRENT ? BigInteger.valueOf(423) : BigInteger.ZERO;
        return new QuoteCalculator(model, sat, ceil(rate(fee, "10000").multiply(new BigDecimal("141"))), convert, transfer,
            dollar.multiply(new BigDecimal("0.05")).divide(sat, 0, RoundingMode.CEILING).toBigIntegerExact(),
            inPerson ? BigInteger.ZERO : dollar.multiply(new BigDecimal("0.06")).divide(sat, 0, RoundingMode.CEILING).toBigIntegerExact(),
            inPerson ? BigInteger.ZERO : dollar.multiply(new BigDecimal("0.75")).divide(sat, 0, RoundingMode.CEILING).toBigIntegerExact(),
            withTag ? dollar.multiply(new BigDecimal("0.12")).divide(sat, 0, RoundingMode.CEILING).toBigIntegerExact() : BigInteger.ZERO);
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
        BigInteger subtotal=principal.add(network).add(convert).add(transfer).add(materials).add(envelope).add(post).add(tag);
        BigInteger operator=subtotal.multiply(BigInteger.valueOf(15)).add(BigInteger.valueOf(99)).divide(BigInteger.valueOf(100));
        BigInteger total=subtotal.add(operator), toman=ceil(new BigDecimal(total).multiply(satoshiToman));
        JsonObject row=new JsonObject();
        row.addProperty("principal_sats",principal); row.addProperty("btc",new BigDecimal(principal,8).toPlainString());
        row.addProperty("network_sats",network);
        // The two exchange fees exist only in the current contract; the legacy row keeps its old
        // shape instead of publishing zeros for costs that model does not charge.
        if(model==Model.CURRENT) {row.addProperty("convert_sats",convert);row.addProperty("transfer_sats",transfer);}
        row.addProperty("materials_sats",materials);
        row.addProperty("envelope_sats",envelope); row.addProperty("post_sats",post); row.addProperty("tag_sats",tag);
        row.addProperty("operator_sats",operator); row.addProperty("operator_toman",ceil(new BigDecimal(operator).multiply(satoshiToman))); row.addProperty("total_sats",total);
        row.addProperty("total_toman",toman); row.addProperty("total_rial",toman.multiply(BigInteger.TEN));
        row.addProperty("budget_toman",budget); row.addProperty("remainder_toman",budget==null?BigInteger.ZERO:budget.subtract(toman));
        return row;
    }
}
