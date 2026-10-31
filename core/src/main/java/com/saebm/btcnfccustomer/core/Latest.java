package com.saebm.btcnfccustomer.core;
public final class Latest { private long generation; public synchronized long begin(){return ++generation;} public synchronized boolean accepts(long token){return token==generation;} public synchronized void invalidate(){generation++;} }
