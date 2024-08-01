package com.cryptofx.trading;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.Gson;

public class BinanceDataStreamer extends WebSocketClient{
	
	static String[] cryptos = {"ADA","APT","ARB","ATOM","AVAX","BCH","BNB","BTC","CHZ","DOGE","DOT","EGLD","ETH","FTM","GALA","GMT","GRT","ICP","LINK","LTC","MATIC","NEAR","OP","PEPE","RNDR","SHIB","SOL","SUI","TRX","USDT","VET","XLM","XRP"};
	static String[] fiats = {"EUR","TRY","ARS","BRL","COP","IDRT","PLN","RON","UAH","ZAR"};
	
	static String baseUri = "wss://stream.binance.com:9443/stream?streams=";
	static Gson gson = new Gson();
	static HttpClient httpClient = HttpClient.newHttpClient();
	
	static HashSet<BookTickerRequestData> initialBookTickerData = new HashSet<BookTickerRequestData>();
	static HashMap<String,Integer> streamSharedMemoryMapValues = new HashMap<String,Integer>();
	static HashMap<String,HashSet<String>> tradeableFiatCryptoPairs = new HashMap<String,HashSet<String>>();
	static MappedByteBuffer streamsSharedMemory;
	
	static int checkAndCreateStreamsList() {
		int i = 0;
		int printCount = 0;
		int checkedStreams = 0;
		int position = i;
		String streamSymbol = "";
		System.out.println("Checking streams. Please wait.");
		try {
			FileWriter myWriter = new FileWriter("streamsMap.csv");
			for(String fiat:fiats) {
				HashSet<String> tradeableCryptos = new HashSet<String>();
				for(String crypto:cryptos) {
					streamSymbol = crypto+fiat;
					try {
						HttpRequest request = HttpRequest
							.newBuilder()
							.uri(new URI("https://api.binance.com/api/v3/ticker/bookTicker?symbol="+streamSymbol))
							.version(HttpClient.Version.HTTP_2)
							.GET()
							.build();
						HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
						if (response.statusCode()==200) {
							BookTickerRequestData bookTickerData = gson.fromJson(response.body(), BookTickerRequestData.class);
							if(Float.parseFloat(bookTickerData.askPrice)>0) {
								System.out.print("#");
								tradeableCryptos.add(crypto);
								position = 8+(20*i);
								streamSharedMemoryMapValues.put(streamSymbol,position);
								myWriter.write(crypto+","+fiat+","+position+'\n');
								initialBookTickerData.add(bookTickerData);
								i++;
							} else {
								System.out.print("-");
							}
						} else {
							System.out.print(".");
						}
						printCount++;
						if (printCount == 10) {
							System.out.print('\n');
							printCount = 0;
						}
						checkedStreams++;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				tradeableFiatCryptoPairs.put(fiat,tradeableCryptos);
			}
			myWriter.close();
		} catch (Exception e) {
			
		}
		System.out.println("Finished checking. Found "+streamSharedMemoryMapValues.size()+" streams out of "+checkedStreams+".");
		return i;
	}
	
	static String generateURIString() {
		String finalURI = "wss://stream.binance.com:9443/stream?streams=";
		for (String symbol:streamSharedMemoryMapValues.keySet()) {
				finalURI += symbol.toLowerCase()+"@bookTicker/";
		}
		return (finalURI.substring(0, finalURI.length() - 1));
	}
	
	public BinanceDataStreamer(URI serverUri, Draft draft) {
		super(serverUri, draft);
	}
	
	public BinanceDataStreamer(URI serverURI) {
		super(serverURI);
	}
	
	public BinanceDataStreamer(URI serverUri, Map<String, String> httpHeaders) {
		super(serverUri, httpHeaders);
	}
	
	@Override
	public void onOpen(ServerHandshake handshakedata) {
		System.out.println("Connecting...");
	}
	
	@Override
	public void onMessage(String message) {
		BookTickerStreamMessage streamMessage = gson.fromJson(message, BookTickerStreamMessage.class);
		saveBookTickerDataToSharedMemory(streamMessage);
	}
	
	@Override
	public void onClose(int code, String reason, boolean remote) {
		System.out.println("Connection closed by " + (remote ? "remote peer" : "us") + " Code: " + code + " Reason: "+ reason);
	}
	
	@Override
	public void onError(Exception ex) {
		ex.printStackTrace();
	}
	
	public void saveBookTickerDataToSharedMemory(BookTickerStreamMessage streamMessage) {
		int mapValue = streamSharedMemoryMapValues.get(streamMessage.data.s);
		streamsSharedMemory.putLong(mapValue,streamMessage.data.u);
		streamsSharedMemory.putFloat(mapValue+4,Float.parseFloat(streamMessage.data.b));
		streamsSharedMemory.putFloat(mapValue+8,Float.parseFloat(streamMessage.data.B));
		streamsSharedMemory.putFloat(mapValue+12,Float.parseFloat(streamMessage.data.a));
		streamsSharedMemory.putFloat(mapValue+16,Float.parseFloat(streamMessage.data.A));
		streamsSharedMemory.putInt(0,1);
		streamsSharedMemory.putInt(4,mapValue);
	}
	
	public static void saveInitialBookTickerDataToSharedMemory(HashSet<BookTickerRequestData> bookTickerDataSet) {
		for(BookTickerRequestData bookTickerData:bookTickerDataSet) {
			int mapValue = streamSharedMemoryMapValues.get(bookTickerData.symbol);
			streamsSharedMemory.putLong(mapValue, 0);
			streamsSharedMemory.putFloat(mapValue+4,Float.parseFloat(bookTickerData.bidPrice));
			streamsSharedMemory.putFloat(mapValue+8,Float.parseFloat(bookTickerData.bidQty));
			streamsSharedMemory.putFloat(mapValue+12,Float.parseFloat(bookTickerData.askPrice));
			streamsSharedMemory.putFloat(mapValue+16,Float.parseFloat(bookTickerData.askQty));
			streamsSharedMemory.putInt(0,1);
			streamsSharedMemory.putInt(4,mapValue);
		}
	}
	
	static MappedByteBuffer createSharedMemory(String path, long size) {
		try (FileChannel fc = (FileChannel)Files.newByteChannel(new File(path).toPath(),
				EnumSet.of(
					StandardOpenOption.CREATE,
					StandardOpenOption.SPARSE,
					StandardOpenOption.WRITE,
					StandardOpenOption.READ))) {
			System.out.println("Created Shared Memory of size "+size);
			return fc.map(FileChannel.MapMode.READ_WRITE, 0, size);
		}	catch( IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	public static void main(String[] args) throws URISyntaxException {
		int numberOfStreams = checkAndCreateStreamsList();
		streamsSharedMemory = createSharedMemory("streamsData.dat",20*numberOfStreams+8);
		saveInitialBookTickerDataToSharedMemory(initialBookTickerData);
		BinanceDataStreamer streamer = new BinanceDataStreamer(new URI(generateURIString()));
		streamer.connect();
	}
}
