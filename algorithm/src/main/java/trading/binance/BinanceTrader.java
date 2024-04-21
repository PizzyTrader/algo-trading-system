package trading.binance;
import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Scanner;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

class BalancesMessage {
	
}

public class BinanceTrader {
	
	static String APIKey = System.getenv("API_KEY");
	static String secretKey = System.getenv("SECRET_KEY");
	
	static String[] cryptos = {"ADA","BTC","CHZ","SOL","DOT","LTC"};
	//static String[] cryptos = {"ADA", "AVAX", "BNB", "BTC", "CHZ", "DOGE", "DOT", "ETH", "GALA", "LINK", "LTC", "MATIC", "SHIB", "SOL"};
	static String[] fiats = {"TRY","EUR","BRL"};
	static String[] fiatFXPairs = {"EURTRY", "EURBRL", "TRYBRL"};
	
	static MappedByteBuffer shm;
	static HashMap<Integer,String> symbolMemoryMap = new HashMap<Integer,String>();
	static HashMap<String,HashMap<String,float[]>> fiatFXData = new HashMap<String,HashMap<String,float[]>>();
	static HashMap<String,float[]> symbolData = new HashMap<String,float[]>();
	static HashMap<String,Float> balances = new HashMap<String,Float>();
	
	static HttpClient client = HttpClient.newHttpClient();
	
	static void createSharedMemory(String path, long size) {
		try (FileChannel fc = (FileChannel)Files.newByteChannel(new File(path).toPath(),
				EnumSet.of(
						StandardOpenOption.CREATE,
						StandardOpenOption.SPARSE,
						StandardOpenOption.WRITE,
						StandardOpenOption.READ))) {

			shm = fc.map(FileChannel.MapMode.READ_WRITE, 0, size);
		} catch( IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	static void createSymbolMemoryMap() {
		try {
			File readFile = new File("symbolMap.csv");
			Scanner myReader = new Scanner(readFile,"UTF-8");
			String symbolMapData = "";
			String[] cleaner = new String[2];
			while (myReader.hasNextLine()) {
				symbolMapData = myReader.nextLine();
				cleaner = symbolMapData.split(",");
				symbolMemoryMap.put(Integer.parseInt(cleaner[1]),cleaner[0]);
			}
			myReader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void createFiatFXData() {
		for (String f: fiatFXPairs) {
			HashMap<String,float[]> FXCryptos = new HashMap<String,float[]>();
			for (String c: cryptos) {
				float[] IERs = new float[1];
				FXCryptos.put(c,IERs);
				float[] symbolFloat1 = new float[4];
				float[] symbolFloat2 = new float[4];
				symbolData.put(c+f.substring(0,3),symbolFloat1);
				symbolData.put(c+f.substring(3),symbolFloat2);
			}
			fiatFXData.put(f,FXCryptos);
		}
	}
	
	static void createBalances(){
		for (String fiat:fiats) {
			balances.put(fiat, (float) 0);
		}
		for (String crypto:cryptos) {
			balances.put(crypto, (float) 0);
		}
	}
	
	static void startBot() {
		int flag;
		int latestMapValue;
		Long timeThresh = (long) 10;
		Long startTimeEpoch = Instant.now().getEpochSecond();
		Long currentTimeEpoch = startTimeEpoch;
		while (true) {
			currentTimeEpoch = Instant.now().getEpochSecond();
			flag = shm.getInt(0);
			latestMapValue = shm.getInt(4);
			if (flag == 1) {
				readData(latestMapValue);
				processData();
				strategy();
			}
			if (currentTimeEpoch-startTimeEpoch>timeThresh) {
				startTimeEpoch=currentTimeEpoch;
				displayData();
			}
		}
	}
	
	static void initialiseData() {
		System.out.println("Initialising Streams...");
		Boolean ready = false;
		while (ready == false) {
			for(int m:symbolMemoryMap.keySet()) {
				readData(m);
			}
			ready = true;
			for(float[] data:symbolData.values()) {
				for (float f:data) {
					if(f<=0) {
						ready = false;
					}
				}
			}
		}
		System.out.println("All Streams Initialised!");
	}

	static void readData(int mapValue) {
		String bookTickerSymbol = symbolMemoryMap.get(mapValue);
		if (symbolData.keySet().contains(bookTickerSymbol)) {
			float[] bookTickerData = symbolData.get(bookTickerSymbol);
			bookTickerData[0] = shm.getFloat(mapValue+4); //bidPrice
			bookTickerData[1] = shm.getFloat(mapValue+8); //bidQuant
			bookTickerData[2] = shm.getFloat(mapValue+12); //askPrice
			bookTickerData[3] = shm.getFloat(mapValue+16); //askQuant
			symbolData.put(bookTickerSymbol,bookTickerData);
			shm.putInt(0,0);
		}
	}
	
	static void processData() {
		for(String fiatFX:fiatFXData.keySet()) {
			HashMap<String,float[]> cryptoIER = fiatFXData.get(fiatFX);
			String fx1 = fiatFX.substring(0,3);
			String fx2 = fiatFX.substring(3);
			for(String c:cryptos) {
				float[] IERs = cryptoIER.get(c);
				float[] cryptoFX1Data = symbolData.get(c+fx1);
				float[] cryptoFX2Data = symbolData.get(c+fx2);
				float cryptoFX1FX2MidPrice = (cryptoFX2Data[0]+cryptoFX2Data[2])/(cryptoFX1Data[0]+cryptoFX1Data[2]);
				IERs[0]=cryptoFX1FX2MidPrice;
				cryptoIER.put(c, IERs);
			}
			fiatFXData.put(fiatFX, cryptoIER);
		}
	}
	
	static void strategy() {
		try {
			updateBalances();
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			float[] symbolDataArray = symbolData.get("BTCEUR");
			float midPrice = (symbolDataArray[0]+symbolDataArray[2])/2;
			createOrder("BTCEUR","BUY","LIMIT","GTC","0.0001",Float.toString(round(midPrice,2)));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void updateBalances() throws Exception {
		System.out.println("updateBal");
		try {
			Instant instant = Instant.now();
			String data = "timestamp="+Long.toString(instant.toEpochMilli());
			String signature = generateHMACSignature(data, secretKey);
			String dataFinal = data+"&signature="+signature;
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/sapi/v3/asset/getUserAsset?"+dataFinal))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.POST(BodyPublishers.noBody())
					.build();
			HttpClient client = HttpClient.newHttpClient();
			
			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
			
			System.out.println(response.body());
			
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
	
	static void createOrder(String symbol,String side, String type, String timeInForce,String quantity,String price) throws Exception {
		System.out.println("creatOrder");
		try {
			Instant instant = Instant.now();
			String data = "symbol="+symbol+"&side="+side+"&type="+type+"&timeInForce="+timeInForce+"&quantity="+quantity+"&price="+price+"&timestamp="+Long.toString(instant.toEpochMilli());
			String signature = generateHMACSignature(data, secretKey);
			String dataFinal = data+"&signature="+signature;
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/api/v3/order/test?"+dataFinal))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.POST(BodyPublishers.noBody())
					.build();
			
			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
			
			System.out.println(response.body());
			
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
	
	static void cancelAllOrders(String symbol) throws Exception {
		try {
			Instant instant = Instant.now();
			String data = "symbol="+symbol+"&timestamp="+Long.toString(instant.toEpochMilli());
			String signature = generateHMACSignature(data, secretKey);
			String dataFinal = data+"&signature="+signature;
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/api/v3/openOrders?"+dataFinal))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.DELETE()
					.build();
			
			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
			
			System.out.println(response.body());
			
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
	
	static void cancelOrder(String symbol,String orderId) throws Exception {
		try {
			Instant instant = Instant.now();
			String data = "symbol="+symbol+"&orderId="+orderId+"&timestamp="+Long.toString(instant.toEpochMilli());
			String signature = generateHMACSignature(data, secretKey);
			String dataFinal = data+"&signature="+signature;
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/api/v3/order?"+dataFinal))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.DELETE()
					.build();
			
			HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
			
			System.out.println(response.body());
			
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
	
	static void displayData() {
		System.out.println("----SYMBOL DATA----");
		for (String s:symbolData.keySet()) {
			float[] sData = symbolData.get(s);
			String sDataDisplay = "";
			for (float sDataFloat:sData) {
				sDataDisplay=sDataDisplay+", "+sDataFloat;
			}
			sDataDisplay = sDataDisplay.substring(2);
			sDataDisplay = "["+sDataDisplay+"]";
			System.out.println(s+": "+sDataDisplay);
		}
		System.out.println();
		System.out.println("----FX IERs----");
		for (String fx:fiatFXData.keySet()) {
			HashMap<String,float[]> cryptoIER = fiatFXData.get(fx);
			String cIerDisplay = "";
			for (String c:cryptoIER.keySet()) {
				float[] cIerData = cryptoIER.get(c);
				cIerDisplay=cIerDisplay+", "+c+": "+cIerData[0];
			}
			cIerDisplay= cIerDisplay.substring(2);
			cIerDisplay= "["+cIerDisplay+"]";
			System.out.println("-"+fx+"-");
			System.out.println(cIerDisplay);
			System.out.println();
		}
	}
	
	static String generateHMACSignature(String message, String secret) throws Exception {
		Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
		SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
		hmacSHA256.init(secretKeySpec);
		byte[] signatureBytes = hmacSHA256.doFinal(message.getBytes());
		return encodeHexString(signatureBytes);
	}
	
	static String encodeHexString(byte[] byteArray) {
		StringBuffer hexStringBuffer = new StringBuffer();
		for (int i = 0; i < byteArray.length; i++) {
			hexStringBuffer.append(byteToHex(byteArray[i]));
		}
		return hexStringBuffer.toString();
	}
	
	static String byteToHex(byte num) {
		char[] hexDigits = new char[2];
		hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
		hexDigits[1] = Character.forDigit((num & 0xF), 16);
		return new String(hexDigits);
	}
	
	static float round(float number, int scale) {
		int pow = 10;
		for (int i = 1; i < scale; i++)
			pow *= 10;
		float tmp = number * pow;
		return ( (float) ( (int) ((tmp - (int) tmp) >= 0.5f ? tmp + 1 : tmp) ) ) / pow;
	}
	
	public static void main(String[] args) {
		createFiatFXData();
		createSymbolMemoryMap();
		createSharedMemory("sharedMemory.dat", (20*symbolMemoryMap.size())+8);
		initialiseData();
		startBot();
	}

}
