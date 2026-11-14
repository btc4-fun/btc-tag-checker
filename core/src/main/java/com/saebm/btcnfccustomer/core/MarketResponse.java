package com.saebm.btcnfccustomer.core;

import com.google.gson.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validated server tables, with conservative server age plus monotonic local aging. */
public final class MarketResponse {
    public static final String PRICES="https://swapwallet.app/api/v1/market/prices";
    public static final String FEES="https://mempool.space/api/v1/fees/recommended";
    public final String fetchedAt;
    private final boolean serverFresh;
    private final long initialAge, received;
    private final Map<String,Option> options=new LinkedHashMap<>();
    public static final class Option {
        public final QuoteCalculator calculator;
        public final QuoteCalculator.Model model;
        public final String btcToman, usdtToman, feeRate;
        private final JsonArray rows;
        private Option(QuoteCalculator calculator, JsonObject table) {
            this.calculator=calculator; model=calculator.model; rows=table.getAsJsonArray("rows").deepCopy();
            btcToman=text(table.getAsJsonObject("rates"),"btc_toman");
            usdtToman=text(table.getAsJsonObject("rates"),"usdt_toman");
            feeRate=text(table.getAsJsonObject("assumptions"),"network_fee_rate");
        }
        public JsonArray rows() { return rows.deepCopy(); }
    }
    private MarketResponse(String fetchedAt, boolean fresh, long age, long received) {
        this.fetchedAt=fetchedAt;serverFresh=fresh;initialAge=age;this.received=received;
    }
    public Option option(String speed, boolean tag, boolean inPerson) {
        return options.get(speed + (tag ? "/with_tag" : "/no_tag") + (inPerson ? "_in_person" : ""));
    }
    /** Postal delivery is the default, so the two-argument form keeps its old meaning. */
    public Option option(String speed, boolean tag) { return option(speed, tag, false); }
    private long age(long now) { return now<received ? Long.MAX_VALUE : initialAge+(now-received); }
    public boolean fresh(long now) { return serverFresh && age(now)<60000; }
    public boolean usable(long now) { return age(now)<300000; }
    private static String text(JsonObject object,String key) {
        JsonElement e=object.get(key);
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected text");
        return e.getAsString();
    }
    private static BigInteger integer(JsonObject object,String key) {
        JsonElement e=object.get(key);
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.getAsString().matches("[0-9]{1,64}")) throw new IllegalArgumentException("Expected integer");
        return e.getAsBigInteger();
    }
    private static void equal(String expected,String actual) { if(!expected.equals(actual)) throw new IllegalArgumentException("Invalid pricing contract"); }
    private static void decimalEqual(BigDecimal expected,String actual) {
        if(actual.length()>64 || !actual.matches("[0-9]+(?:\\.[0-9]{1,29})?") || expected.compareTo(new BigDecimal(actual))!=0) throw new IllegalArgumentException("Invalid rate");
    }
    private static final String[] CURRENT_ROW_KEYS={"principal_sats","network_sats","convert_sats","transfer_sats","materials_sats","envelope_sats","post_sats","tag_sats","operator_sats","operator_toman","total_sats","total_toman","total_rial","remainder_toman"};
    private static final String[] LEGACY_ROW_KEYS={"principal_sats","network_sats","materials_sats","envelope_sats","post_sats","tag_sats","operator_sats","operator_toman","total_sats","total_toman","total_rial","remainder_toman"};
    private static Option validate(JsonObject table,String btc,String usdt,boolean tag,boolean hand) {
        JsonObject assumptions=table.getAsJsonObject("assumptions"),rates=table.getAsJsonObject("rates"),costs=table.getAsJsonObject("costs");
        // The two published models are told apart by the exchange fees themselves. A payload that
        // carries only one of the two keys is rejected instead of being read as a zero cost.
        boolean current=assumptions.has("convert_usd");
        if(current!=assumptions.has("transfer_sats")) throw new IllegalArgumentException("Invalid assumptions");
        QuoteCalculator.Model model=current?QuoteCalculator.Model.CURRENT:QuoteCalculator.Model.LEGACY;
        equal(current?"1":"0.5",text(assumptions,"exchange_fee_percent"));equal("15",text(assumptions,"operator_fee_percent"));
        equal("0.05",text(assumptions,"materials_usd"));equal("0.06",text(assumptions,"envelope_usd"));equal("0.75",text(assumptions,"post_usd"));equal("0.12",text(assumptions,"tag_usd"));
        if(current) {
            equal("0.02",text(assumptions,"convert_usd"));
            if(!integer(assumptions,"transfer_sats").equals(BigInteger.valueOf(423))) throw new IllegalArgumentException("Invalid assumptions");
        }
        if(!assumptions.get("in_person").isJsonPrimitive() || !assumptions.getAsJsonPrimitive("in_person").isBoolean() || assumptions.get("in_person").getAsBoolean()!=hand) throw new IllegalArgumentException("Invalid assumptions");
        if(!integer(assumptions,"transaction_vbytes").equals(BigInteger.valueOf(141)) || !assumptions.get("include_tag").isJsonPrimitive() || !assumptions.getAsJsonPrimitive("include_tag").isBoolean() || assumptions.get("include_tag").getAsBoolean()!=tag) throw new IllegalArgumentException("Invalid assumptions");
        QuoteCalculator calculator=QuoteCalculator.fromRaw(btc,usdt,text(assumptions,"network_fee_rate"),tag,hand,model);
        decimalEqual(new BigDecimal(btc).multiply(new BigDecimal(current?"1.01":"1.005")),text(rates,"btc_toman"));
        decimalEqual(current?new BigDecimal(usdt):new BigDecimal(usdt).multiply(new BigDecimal("1.005")),text(rates,"usdt_toman"));
        decimalEqual(calculator.satoshiToman,text(rates,"satoshi_toman"));
        if(!integer(costs,"network_sats").equals(calculator.network)||!integer(costs,"materials_sats").equals(calculator.materials)||!integer(costs,"envelope_sats").equals(calculator.envelope)||!integer(costs,"post_sats").equals(calculator.post)||!integer(costs,"tag_sats").equals(calculator.tag)) throw new IllegalArgumentException("Invalid costs");
        if(current) {
            if(!integer(costs,"convert_sats").equals(calculator.convert)||!integer(costs,"transfer_sats").equals(calculator.transfer)) throw new IllegalArgumentException("Invalid costs");
        } else if(costs.has("convert_sats")||costs.has("transfer_sats")) throw new IllegalArgumentException("Invalid costs");
        JsonArray rows=table.getAsJsonArray("rows");
        BigInteger firstCost=calculator.quote(BigInteger.valueOf(501),null).get("total_toman").getAsBigInteger();
        BigInteger step=BigInteger.valueOf(100000), nextBudget=firstCost.divide(step).add(BigInteger.ONE).multiply(step);
        int expectedCount=1;
        // Nonnegative and at most nineteen here; use the API-26 compatible conversion.
        if(nextBudget.compareTo(BigInteger.valueOf(3000000))<=0) expectedCount+=BigInteger.valueOf(3000000).subtract(nextBudget).divide(step).intValue()+1;
        if(rows.size()!=expectedCount) throw new IllegalArgumentException("Incomplete table");
        for(int i=0;i<rows.size();i++) {
            JsonObject row=rows.get(i).getAsJsonObject();
            BigInteger budget=i==0?null:nextBudget.add(step.multiply(BigInteger.valueOf(i-1)));
            JsonObject expected=budget==null?calculator.quote(BigInteger.valueOf(501),null):calculator.calculate(budget.toString(),true);
            for(String key:current?CURRENT_ROW_KEYS:LEGACY_ROW_KEYS) {
                if(!integer(row,key).equals(expected.get(key).getAsBigInteger())) throw new IllegalArgumentException("Invalid row");
            }
            equal(expected.get("btc").getAsString(),text(row,"btc"));
            if(budget==null ? !row.get("budget_toman").isJsonNull() : !budget.equals(integer(row,"budget_toman"))) throw new IllegalArgumentException("Invalid budget");
        }
        return new Option(calculator,table);
    }
    public static MarketResponse parse(String json,long wallNow,long monotonicNow) {
        try {
            JsonObject root=JsonParser.parseString(json).getAsJsonObject();
            String state=text(root,"status");if(!state.equals("fresh")&&!state.equals("stale")) throw new IllegalArgumentException("Unavailable");
            String fetched=text(root,"fetched_at");
            long age=Math.subtractExact(wallNow,OffsetDateTime.parse(fetched).toInstant().toEpochMilli());
            if(age<0||age>=300000) throw new IllegalArgumentException("Expired or future data");
            JsonObject source=root.getAsJsonObject("source");equal(PRICES,text(source,"prices"));equal(FEES,text(source,"fees"));
            String btc=text(root.getAsJsonObject("raw_rates"),"btc_toman"),usdt=text(root.getAsJsonObject("raw_rates"),"usdt_toman");
            validate(root,btc,usdt,false,false);
            MarketResponse response=new MarketResponse(fetched,state.equals("fresh"),age,monotonicNow);
            JsonObject catalog=root.getAsJsonObject("quote_options");
            for(String speed:new String[]{"economy","medium","fast"}) {
                try {
                    JsonObject choices=catalog.getAsJsonObject(speed);
                    Option noTag=validate(choices.getAsJsonObject("no_tag"),btc,usdt,false,false);
                    Option withTag=validate(choices.getAsJsonObject("with_tag"),btc,usdt,true,false);
                    equal(noTag.feeRate,withTag.feeRate);
                    response.options.put(speed+"/no_tag",noTag);response.options.put(speed+"/with_tag",withTag);
                    // Hand delivery is optional: an older server may omit it, so a missing
                    // pair must not fail the whole table.
                    try {
                        Option noTagHand=validate(choices.getAsJsonObject("no_tag_in_person"),btc,usdt,false,true);
                        Option withTagHand=validate(choices.getAsJsonObject("with_tag_in_person"),btc,usdt,true,true);
                        equal(noTagHand.feeRate,withTagHand.feeRate);
                        response.options.put(speed+"/no_tag_in_person",noTagHand);
                        response.options.put(speed+"/with_tag_in_person",withTagHand);
                    } catch(RuntimeException absent) { /* Hand delivery simply stays unavailable. */ }
                } catch(RuntimeException invalid) { if(speed.equals("economy")) throw invalid; }
            }
            return response;
        } catch(RuntimeException ex) { throw new IllegalArgumentException("Invalid market response"); }
    }
}
