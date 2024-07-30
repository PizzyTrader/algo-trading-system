package trading.binance;


class StreamFilter {
	int minPrice;
	int minQty;
	int tickSize;
}

class CryptoFXPair {
	String crypto;
	Stream primaryStream;
	Stream secondaryStream;
	String FXPair;
	float[] FXRates;
	
	public CryptoFXPair(String crypto, Stream primaryStream, Stream secondaryStream, float[] FXRates) {
		this.crypto = crypto;
		this.primaryStream = primaryStream;
		this.secondaryStream = secondaryStream;
		this.FXRates = FXRates;
		this.FXPair = primaryStream.fiat+secondaryStream.fiat;
	}
}

class Stream {
	String crypto;
	String fiat;
	String streamName;
	int streamMapValue;
	float[] bookTickerStreamData;
	StreamFilter filters;
	
	public Stream(String crypto, String fiat, int streamMapValue, float[] bookTickerStreamData, StreamFilter filters) {
		this.crypto = crypto;
		this.fiat = fiat;
		this.streamName = crypto + fiat;
		this.streamMapValue = streamMapValue;
		this.bookTickerStreamData = bookTickerStreamData;
		this.filters = filters;
	}
}
