package trading.binance;
import org.java_websocket.client.*;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.util.EnumSet;
import java.util.Map;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;


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
		GFG gfg = null;
		Gson gson = new Gson(); 
		gfg = gson.fromJson(message, GFG.class);
		processData(gfg);
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

	public void processData(GFG gfg) {
		try {
			FileWriter myWriter = new FileWriter(gfg.data.s+".csv",true);
				myWriter.write(Long.toString(System.currentTimeMillis())+","+gfg.data.a+","+gfg.data.A+","+gfg.data.b+","+gfg.data.B+'\n');
			myWriter.close();
		} catch (Exception e) {
		
		}
	}
	
	static MappedByteBuffer createSharedMemory(String path, long size) {
		try (FileChannel fc = (FileChannel)Files.newByteChannel(new File(path).toPath(),
				EnumSet.of(
					StandardOpenOption.CREATE,
					StandardOpenOption.SPARSE,
					StandardOpenOption.WRITE,
					StandardOpenOption.READ))) {
			return fc.map(FileChannel.MapMode.READ_WRITE, 0, size);
		}	catch( IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	public static void main(String[] args) throws URISyntaxException {
		System.out.println("V0.1");
		initialise();
		c = new BinanceDataStream_CSV(new URI(finalUri));
		c.connect();
	}
}
