package com.cryptofx.trading;

import java.util.ArrayList;

class BookTickerStreamMessage {
	String stream;
	BookTickerStreamData data;
	public BookTickerStreamMessage() {
		this.stream = "";
		this.data = new BookTickerStreamData();
	}
}

class BookTickerStreamData {
	Long u;
	String s;	
	String b;	
	String B;	
	String a;	
	String A;	
	public BookTickerStreamData() {
		this.u = (long) 0; 
		this.s= ""; 
		this.b= "";
		this.B= "";
		this.a= "";
		this.A= "";
	}
}

class BookTickerRequestData {
	String symbol;
	String bidPrice;
	String bidQty;
	String askPrice;
	String askQty;
}

class AssetBalanceData {
	String asset;
	String free;
	String locked;
	String freeze;
	String withdrawing;
	String ipoable;
	String btcValuation;
	public AssetBalanceData(String asset) {
		this.asset = asset;
		this.free = "";
		this.locked = "";
		this.freeze = "";
		this.withdrawing = "";
		this.ipoable = "";
		this.btcValuation = "";
	}
}

class ExchangeInfo {
	String timezone;
	float serverTime;
	ArrayList<RateLimit> rateLimits = new ArrayList<RateLimit>();
	ArrayList<Object> exchangeFilters = new ArrayList<Object>();
	ArrayList<Symbol> symbols = new ArrayList<Symbol>();
}

class RateLimit {
	String rateLimitType;
	String interval;
	int intervalNum;
	int limit;
}

class Symbol {
	String symbol;
	String status;
	String baseAsset;
	float baseAssetPrecision;
	String quoteAsset;
	float quotePrecision;
	float quoteAssetPrecision;
	float baseCommissionPrecision;
	float quoteCommissionPrecision;
	ArrayList<String> orderTypes = new ArrayList<String>();
	boolean icebergAllowed;
	boolean ocoAllowed;
	boolean otoAllowed;
	boolean quoteOrderQtyMarketAllowed;
	boolean allowTrailingStop;
	boolean cancelReplaceAllowed;
	boolean isSpotTradingAllowed;
	boolean isMarginTradingAllowed;
	ArrayList<Filter> filters = new ArrayList<Filter>();
	ArrayList<Object> permissions = new ArrayList<Object>();
	ArrayList<ArrayList<String>> permissionSets = new ArrayList<ArrayList<String>>();
	String defaultSelfTradePreventionMode;
	ArrayList<String> allowedSelfTradePreventionModes = new ArrayList<String>();
}

class Filter {
	String filterType;
	String minPrice;
	String minQty;
	String tickSize;
}
