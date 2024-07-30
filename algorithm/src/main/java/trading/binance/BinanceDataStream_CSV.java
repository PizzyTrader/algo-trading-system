package trading.binance;
import org.java_websocket.client.*;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.*;
import java.io.FileWriter;
import java.net.*;
import java.util.Map;

public class BinanceDataStream_CSV extends WebSocketClient{
	
	static String[] asset1 = {"ADA", "AVAX", "BNB", "BTC", "CHZ", "DOGE", "DOT", "ETH", "GALA", "LINK", "LTC", "MATIC", "SHIB", "SOL"};
	static String[] asset2 = {"TRY","EUR","BRL"};
	static String baseUri = "wss://stream.binance.com:9443/stream?streams=";
	
	static BinanceDataStream_CSV c;
	static String finalUri;
	static URI uri;
	short flag = 1;
	float bidPriceFloat;
	float bidQuantityFloat;
	float askPriceFloat;
	float askQuantityFloat;
	
	public static void initialise(){
		int position = 0;
		String[] streams = new String[(asset1.length*asset2.length)];
		finalUri = baseUri;
		String streamString;
		for (String a1:asset1) {
			for (String a2:asset2) {
				streamString = a1.toLowerCase()+a2.toLowerCase();
				streams[position] = streamString;
				finalUri = finalUri+streamString+"@bookTicker/";
				position++;
			}
		}
		finalUri = (finalUri.substring(0, finalUri.length() - 1));
	}

	public BinanceDataStream_CSV(URI serverUri, Draft draft) {
		super(serverUri, draft);
	}

	public BinanceDataStream_CSV(URI serverURI) {
		super(serverURI);
	}

	public BinanceDataStream_CSV(URI serverUri, Map<String, String> httpHeaders) {
		super(serverUri, httpHeaders);
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		System.out.println("Connecting...");
	}

	@Override
	public void onMessage(String message) {
		BookTickerStreamMessage BookTickerStreamMessage = null;
		Gson gson = new Gson(); 
		BookTickerStreamMessage = gson.fromJson(message, BookTickerStreamMessage.class);
		processData(BookTickerStreamMessage);
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		System.out.println("Connection closed by " + (remote ? "remote peer" : "us") + " Code: " + code + " Reason: "+ reason+'\n'+"Trying to reconnect...");
		try {
			c = new BinanceDataStream_CSV(new URI(finalUri));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		c.connect();
	}

	@Override
	public void onError(Exception ex) {
		ex.printStackTrace();
	}

	public void processData(BookTickerStreamMessage BookTickerStreamMessage) {
		try {
			FileWriter myWriter = new FileWriter(BookTickerStreamMessage.data.s+".csv",true);
				myWriter.write(Long.toString(System.currentTimeMillis())+","+BookTickerStreamMessage.data.a+","+BookTickerStreamMessage.data.A+","+BookTickerStreamMessage.data.b+","+BookTickerStreamMessage.data.B+'\n');
			myWriter.close();
		} catch (Exception e) {
		
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		System.out.println("V0.1");
		initialise();
		c = new BinanceDataStream_CSV(new URI(finalUri));
		c.connect();
	}
}
