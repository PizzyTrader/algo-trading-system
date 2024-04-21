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
import java.util.HashMap;
import java.util.Map;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

class Data {
	Long u;
	String s;	
	String b;	
	String B;	
	String a;	
	String A;	
	public Data() {
		this.u = (long) 0; 
		this.s= ""; 
		this.b= "";
		this.B= "";
		this.a= "";
		this.A= "";
	}
}

class StreamMessage {
	String stream;
	Data data;
	public StreamMessage() {
		this.stream = "";
		this.data = new Data();
	}
}

public class BinanceDataStream extends WebSocketClient{
	
	static String[] asset1 = {"ADA", "AVAX", "BNB", "BTC", "CHZ", "DOGE", "DOT", "ETH", "GALA", "LINK", "LTC", "MATIC", "SHIB", "SOL"};
	static String[] asset2 = {"TRY","EUR","BRL"};
	static String baseUri = "wss://stream.binance.com:9443/stream?streams=";
	
	static MappedByteBuffer shm = createSharedMemory("sharedMemory.dat", (20*asset1.length*asset1.length) + 8);

	static HashMap<String,Integer> symbolMemoryMap = new HashMap<String,Integer>();
	
	int flag = 1;
	int mapValue;
	float bidPriceFloat;
	float bidQuantityFloat;
	float askPriceFloat;
	float askQuantityFloat;
	
	public static String initialise(){
		int position = 0;
		String symbol;
		String[] streams = new String[(asset1.length*asset2.length)];
		String finalUri = baseUri;
		String streamString;
		for (String a1:asset1) {
			for (String a2:asset2) {
				streamString = a1.toLowerCase()+a2.toLowerCase();
				streams[position] = streamString;
				finalUri = finalUri+streamString+"@bookTicker/";
				position++;
			}
		}
		try {
			FileWriter myWriter = new FileWriter("symbolMap.csv");
			for (int i=0; i<streams.length;i++) {
				position = 8+(20*i);
				symbol = streams[i].toUpperCase();
				symbolMemoryMap.put(symbol,position);
				myWriter.write(symbol+","+position+'\n');
				shm.putLong(position,0);//orderId
				shm.putFloat(position+4,0);//bidPrice
				shm.putFloat(position+8,0);//bidQuant
				shm.putFloat(position+12,0);//askPrice
				shm.putFloat(position+16,0);//askQuant
			}
			myWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finalUri = (finalUri.substring(0, finalUri.length() - 1));
		System.out.println("initialised.");
		return finalUri;
	}

	public BinanceDataStream(URI serverUri, Draft draft) {
		super(serverUri, draft);
	}

	public BinanceDataStream(URI serverURI) {
		super(serverURI);
	}

	public BinanceDataStream(URI serverUri, Map<String, String> httpHeaders) {
		super(serverUri, httpHeaders);
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		System.out.println("Connecting...");
	}

	@Override
	public void onMessage(String message) {
		StreamMessage streamMessage = null;
		Gson gson = new Gson(); 
		streamMessage = gson.fromJson(message, StreamMessage.class);
		processData(streamMessage);
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		System.out.println(
				"Connection closed by " + (remote ? "remote peer" : "us") + " Code: " + code + " Reason: "+ reason);
	}

	@Override
	public void onError(Exception ex) {
		ex.printStackTrace();
	}

	public void processData(StreamMessage streamMessage) {
		mapValue = symbolMemoryMap.get(streamMessage.data.s);
		shm.putLong(mapValue,streamMessage.data.u);
		shm.putFloat(mapValue+4,Float.parseFloat(streamMessage.data.b));
		shm.putFloat(mapValue+8,Float.parseFloat(streamMessage.data.B));
		shm.putFloat(mapValue+12,Float.parseFloat(streamMessage.data.a));
		shm.putFloat(mapValue+16,Float.parseFloat(streamMessage.data.A));
		shm.putInt(0,1);
		shm.putInt(4,mapValue);
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
		String finalUri = initialise();
 		BinanceDataStream c = new BinanceDataStream(new URI(finalUri));
 		c.connect();
	}
}
