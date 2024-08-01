package com.cryptofx.trading;
import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

public class BinanceTrader {
	
	static class Cryptography {
		private static String secretKey = System.getenv("SECRET_KEY");
		
		static String generateHMACSignature(String message) throws Exception {
			Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
			SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
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
		
	}
	
	
	static class JSONStuff {
		private static Gson gson = new Gson();
		
		static AssetBalanceData[] parseAssetBalanceDataFromJSON(String message) {
			return gson.fromJson(message, AssetBalanceData[].class);
		}
		
		static ExchangeInfo parseSymbolFiltersFromJSON(String message) {
			return gson.fromJson(message, ExchangeInfo.class);
		}
	}
	
	
	static class Mapping {
		private static HashSet<String> importedFiats = new HashSet<String>();
		private static HashSet<String> importedCryptos = new HashSet<String>();
		private static HashMap<String,Stream> streamNameToStream = new HashMap<String,Stream>();
		private static HashMap<Integer,Stream> streamMapValueToStream = new HashMap<Integer,Stream>();
		private static HashMap<String,AssetBalanceData> assetToAssetBalanceData = new HashMap<String,AssetBalanceData>();
		private static HashMap<String,HashSet<String>> cryptoToFiats = new HashMap<String,HashSet<String>>();
		private static HashMap<String,CryptoFXPair> cryptoFXPairNameToCryptoFXPair = new HashMap<String,CryptoFXPair>();
		private static HashMap<String,HashSet<CryptoFXPair>> cryptoToCryptoFXPairs = new HashMap<String,HashSet<CryptoFXPair>>();
		private static HashMap<String,HashSet<CryptoFXPair>> FXPairToCryptoFXPairs = new HashMap<String,HashSet<CryptoFXPair>>();
		
		static HashSet<String> getCryptos() {
			return importedCryptos;
		}
		
		static HashSet<String> getFiats() {
			return importedFiats;
		}
		
		static void setCryptos(String[] cryptoArray) {
			for (String crypto:cryptoArray) {
				importedCryptos.add(crypto);
			}
		}
		
		static void setFiats(String[] fiatArray) {
			for (String fiat:fiatArray) {
				importedCryptos.add(fiat);
			}
		}
		
	}
	
	
	static class DataStructures {
		private static MappedByteBuffer streamSharedMemory;
		private static Collection<AssetBalanceData> assetBalances = new HashSet<AssetBalanceData>();
		private static Collection<Stream> streams = new HashSet<Stream>();
		private static Collection<CryptoFXPair> cryptoFXPairs = new HashSet<CryptoFXPair>();
		
		static MappedByteBuffer createSharedMemory(String path, long size) {
			try (FileChannel fc = (FileChannel)Files.newByteChannel(new File(path).toPath(),
					EnumSet.of(
						StandardOpenOption.CREATE,
						
						StandardOpenOption.SPARSE,
						StandardOpenOption.WRITE,
						StandardOpenOption.READ)))
			{
				System.out.println("Connected to Shared Memory of size "+size);
				return fc.map(FileChannel.MapMode.READ_WRITE, 0, size);
			} catch( IOException ioe) {
				throw new RuntimeException(ioe);
			}
		}
		
		static int getNumberOfStreamsFromCSV(String CSVFileName) {
			File readFile = new File(CSVFileName);
			Scanner myReader;
			int numberOfImportedStreams = 0;
			try {
				myReader = new Scanner(readFile,"UTF-8");
				while (myReader.hasNextLine()) {
					numberOfImportedStreams++;
					myReader.nextLine();
				}
				myReader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return numberOfImportedStreams;
		}
		
		static void importStreamMapValuesFromCSV(String CSVFileName) {
			File readFile = new File(CSVFileName);
			Scanner myReader;
			String[] cryptoFiatAndMapValue = new String[3];
			HashSet<String> checkedAssets = new HashSet<String>();
			
			try {
				for(AssetBalanceData a:HTTPStuff.getAssetBalanceData()) {
					assetBalances.add(a);
					Mapping.assetToAssetBalanceData.put(a.asset, a);
					checkedAssets.add(a.asset);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			try {
				myReader = new Scanner(readFile,"UTF-8");
				cryptoFiatAndMapValue = myReader.nextLine().split(",");
				while (myReader.hasNextLine()) {
					cryptoFiatAndMapValue = myReader.nextLine().split(",");
					
					String crypto = cryptoFiatAndMapValue[0];
					Mapping.importedCryptos.add(crypto);
					
					String fiat = cryptoFiatAndMapValue[1];
					Mapping.importedFiats.add(fiat);
					
					Stream newStream = new Stream(crypto, fiat, Integer.parseInt(cryptoFiatAndMapValue[2]), DataStructures.readBookTickerDataFromSharedMemory(Integer.parseInt(cryptoFiatAndMapValue[2])), HTTPStuff.getFiltersForStream(crypto+fiat));
					streams.add(newStream);
					Mapping.streamMapValueToStream.put(newStream.streamMapValue, newStream);
					Mapping.streamNameToStream.put(newStream.streamName, newStream);
					
					if (checkedAssets.add(crypto)) {
						AssetBalanceData newAssetBalance = new AssetBalanceData(crypto);
						assetBalances.add(newAssetBalance);
						Mapping.assetToAssetBalanceData.put(crypto, newAssetBalance);
					}
					if (checkedAssets.add(fiat)) {
						AssetBalanceData newAssetBalance = new AssetBalanceData(fiat);
						assetBalances.add(newAssetBalance);
						Mapping.assetToAssetBalanceData.put(fiat, newAssetBalance);
					}
					
					HashSet<String> toAdd = Mapping.cryptoToFiats.getOrDefault(crypto, new HashSet<String>());
					toAdd.add(newStream.fiat);
					Mapping.cryptoToFiats.put(crypto,toAdd);
				}
				myReader.close();
			} catch (IOException e) {
				System.out.println("IO Exception...");
				e.printStackTrace();
				System.exit(-1);
			}
			
		}
		
		static void initialiseFXPairs() {
			for (Map.Entry<String,HashSet<String>> entry: Mapping.cryptoToFiats.entrySet()) {
				String[] fiatArray = new String[entry.getValue().size()];
				int c = 0;
				
				if(entry.getValue().size()>1) {
					
					for (String fiat:entry.getValue()) {
						fiatArray[c] = fiat;
						c++;
					}
					
					for (int i=0; i<fiatArray.length-1;i++) {
						
						for (int j=i+1; j<fiatArray.length;j++) {
							CryptoFXPair newFXPair = new CryptoFXPair(entry.getKey(), Mapping.streamNameToStream.get(entry.getKey()+fiatArray[i]),Mapping.streamNameToStream.get(entry.getKey()+fiatArray[j]),Calculations.calculateFXRatesArray(Mapping.streamNameToStream.get(entry.getKey()+fiatArray[i]).bookTickerStreamData,Mapping.streamNameToStream.get(entry.getKey()+fiatArray[j]).bookTickerStreamData));
							
							cryptoFXPairs.add(newFXPair);
							
							Mapping.cryptoFXPairNameToCryptoFXPair.put(entry.getKey()+fiatArray[i]+fiatArray[j], newFXPair);
							
							HashSet<CryptoFXPair> cryptoFXPairsForCrypto = Mapping.cryptoToCryptoFXPairs.getOrDefault(entry.getKey(), new HashSet<CryptoFXPair>());
							cryptoFXPairsForCrypto.add(newFXPair);
							Mapping.cryptoToCryptoFXPairs.put(entry.getKey(), cryptoFXPairsForCrypto);

							HashSet<CryptoFXPair> cryptoFXPairsForFXPair = Mapping.FXPairToCryptoFXPairs.getOrDefault(fiatArray[i]+fiatArray[j], new HashSet<CryptoFXPair>());
							cryptoFXPairsForFXPair.add(newFXPair);
							Mapping.FXPairToCryptoFXPairs.put(fiatArray[i]+fiatArray[j], cryptoFXPairsForFXPair);
						}
						
					}
				}
			}
		}
		
		static void removeUntradeableCryptoFXPairs() {
			int numberOfRemovedCryptoFXPairs = 0;
			HashSet<CryptoFXPair> allToRemove = new HashSet<CryptoFXPair>();
			for(Map.Entry<String,HashSet<CryptoFXPair>> entry:Mapping.FXPairToCryptoFXPairs.entrySet()) {
				if(entry.getValue().size()==1) {
					CryptoFXPair toRemove = entry.getValue().iterator().next();
					allToRemove.add(toRemove);
				}
			}
			for(CryptoFXPair remove:allToRemove) {
				cryptoFXPairs.remove(remove);
				Mapping.cryptoFXPairNameToCryptoFXPair.remove(remove.crypto+remove.FXPair);
				
				HashSet<CryptoFXPair> cryptoFXPairsForFXPair = Mapping.FXPairToCryptoFXPairs.get(remove.FXPair);
				cryptoFXPairsForFXPair.remove(remove);
				if(cryptoFXPairsForFXPair.size()==0) {
					Mapping.FXPairToCryptoFXPairs.remove(remove.FXPair);
				}
				
				HashSet<CryptoFXPair> cryptoFXPairsForCrypto = Mapping.cryptoToCryptoFXPairs.get(remove.crypto);
				cryptoFXPairsForCrypto.remove(remove);
				
				numberOfRemovedCryptoFXPairs++;
			}
			System.out.println("Removed "+numberOfRemovedCryptoFXPairs+" CryptoFX Pairs");
		}
		
		static float[] readBookTickerDataFromSharedMemory(int mapValue){
			float[] bookTickerData = new float[4];
			
			bookTickerData[0] = streamSharedMemory.getFloat(mapValue+4); //bidPrice
			bookTickerData[1] = streamSharedMemory.getFloat(mapValue+8); //bidQuant
			bookTickerData[2] = streamSharedMemory.getFloat(mapValue+12); //askPrice
			bookTickerData[3] = streamSharedMemory.getFloat(mapValue+16); //askQuant
			streamSharedMemory.putInt(0,0);
			return bookTickerData;
		}
		
		static int getFlagValue() {
			return streamSharedMemory.getInt(0);
		}
		
		static int getLastUpdatedMapValue() {
			return streamSharedMemory.getInt(4);
		}
		
		static Collection<Stream> getStreams() {
			return streams;
		}
		
	}
	
	
	static class HTTPStuff {
		private static String APIKey = System.getenv("API_KEY");
		private static HttpClient httpClient = HttpClient.newHttpClient();
		
		static String createSignedQuery(String requestQuery) throws Exception {
			return requestQuery+"&signature="+Cryptography.generateHMACSignature(requestQuery);
		}
		
		static HttpResponse<String> newOrder(String symbol, String side, String type, String timeInForce, String quantity, String price) throws Exception {
			String requestQuery = "symbol="+symbol+"&side="+side+"&type="+type+"&timeInForce="+timeInForce+"&quantity="+quantity+"&price="+price+"&timestamp"+Long.toString(Instant.now().toEpochMilli());
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/api/v3/order/test?"+createSignedQuery(requestQuery)))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.POST(BodyPublishers.noBody())
					.build();
			return httpClient.send(request, BodyHandlers.ofString());
		}

		static HttpResponse<String> newMakerOrder(String symbol, String side, String quantity, String price) throws Exception {
			String requestQuery = "symbol="+symbol+"&side="+side+"&type=LIMIT&timeInForce=GTC"+"&quantity="+quantity+"&price="+price+"&timestamp"+Long.toString(Instant.now().toEpochMilli());
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/api/v3/order/test?"+createSignedQuery(requestQuery)))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.POST(BodyPublishers.noBody())
					.build();
			return httpClient.send(request, BodyHandlers.ofString());
		}
		
		static HttpResponse<String> cancelOrder(String symbol,String orderId) throws Exception {
			String requestQuery = "symbol="+symbol+"&orderId="+orderId+"&timestamp"+Long.toString(Instant.now().toEpochMilli());
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/api/v3/order?"+createSignedQuery(requestQuery)))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.DELETE()
					.build();
			return httpClient.send(request, BodyHandlers.ofString());
		}
		
		static HttpResponse<String> cancelAllOrders(String symbol) throws Exception {
			String requestQuery = "symbol="+symbol+"&timestamp"+Long.toString(Instant.now().toEpochMilli());
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/api/v3/openOrders?"+createSignedQuery(requestQuery)))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.DELETE()
					.build();
			return httpClient.send(request, BodyHandlers.ofString());
		}
		
		static HttpResponse<String> getUserAsset() throws Exception {
			String requestQuery = "&timestamp="+Long.toString(Instant.now().toEpochMilli());
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/sapi/v3/asset/getUserAsset?"+createSignedQuery(requestQuery)))
					.header("X-MBX-APIKEY",APIKey)
					.version(HttpClient.Version.HTTP_2)
					.POST(BodyPublishers.noBody())
					.build();
			return httpClient.send(request, BodyHandlers.ofString());
		}
		
		static HttpResponse<String> getExchangeInfo(String streamName) throws Exception {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(new URI("https://api.binance.com/api/v3/exchangeInfo?symbol="+streamName))
					.version(HttpClient.Version.HTTP_2)
					.GET()
					.build();
			return httpClient.send(request, BodyHandlers.ofString());
		}
		
		static AssetBalanceData[] getAssetBalanceData() throws Exception {
			HttpResponse<String> response = getUserAsset();
			
			if(response.statusCode()!=200) {
				System.out.println("getBalanceData Error");
				System.out.println(response.body());
				throw new Exception("Failed getBalanceData");
			}
			return JSONStuff.parseAssetBalanceDataFromJSON(getUserAsset().body());
		}
		
		static StreamFilter getFiltersForStream(String streamName) {
			HttpResponse<String> response;
			StreamFilter resultFilter = new StreamFilter();
			
			try {
				response = getExchangeInfo(streamName);
				if (response.statusCode()==200) {
					for(Filter f:JSONStuff.parseSymbolFiltersFromJSON(response.body()).symbols.get(0).filters) {
						if (f.filterType.equals("PRICE_FILTER")) {
							resultFilter.minPrice = getNumberOfDecimalPlacesForFilter(f.minPrice);
							resultFilter.tickSize = getNumberOfDecimalPlacesForFilter(f.tickSize);
						} else if (f.filterType.equals("LOT_SIZE")) {
							resultFilter.minQty = getNumberOfDecimalPlacesForFilter(f.minQty);
						}
					}
				} else {
					System.out.print("Exchange Info Error");
					System.out.print(response.body());
				}
			} catch (Exception e) {
				System.out.println("Error: Get Filters");
				e.printStackTrace();
			}
			return resultFilter;
		}
		
		static int getNumberOfDecimalPlacesForFilter(String minimumValue) {
			int a = minimumValue.indexOf('1');
			int b = minimumValue.indexOf('.');
			
			return (a-b<0)?0:a-b;
		}
		
	}
	
	
	static class Calculations { 
		
		//Strategy Calculations
		static float calculateInstantFXRate(float[] primaryFiatStreamData, float[] secondaryFiatStreamData) {
			return (float) secondaryFiatStreamData[0]/primaryFiatStreamData[2];
		}

		static float calculateInstantInverseFXRate(float[] primaryFiatStreamData, float[] secondaryFiatStreamData) {
			return (float) secondaryFiatStreamData[2]/primaryFiatStreamData[0];
		}

		static float calculateMidFXRate(float[] primaryFiatStreamData, float[] secondaryFiatStreamData) {
			return (float) (secondaryFiatStreamData[0]+secondaryFiatStreamData[2])/(primaryFiatStreamData[0]+primaryFiatStreamData[2]);
		}
		
		static float[] calculateFXRatesArray(float[] primaryFiatStreamData, float[] secondaryFiatStreamData) {
			float[] result = {calculateInstantFXRate(primaryFiatStreamData, secondaryFiatStreamData),calculateInstantInverseFXRate(primaryFiatStreamData, secondaryFiatStreamData),calculateMidFXRate(primaryFiatStreamData, secondaryFiatStreamData)};
			return result;
		}
		
		//Misc. Calculations
		static float round(float number, int scale) {
			int pow = 10;
			for (int i = 1; i < scale; i++)
				pow *= 10;
			float tmp = number * pow;
			return ( (float) ( (int) ((tmp - (int) tmp) >= 0.5f ? tmp + 1 : tmp) ) ) / pow;
		}
		
		static float calculateMinimumValueForDecimalPlaces(int numberOfDecimalPlaces) {
			float pow = 10;
			for (int i = 1; i < numberOfDecimalPlaces; i++) {
				pow /= 10;
			}
			return pow;
		}
		
	}
	
	static class Display {
		static void printDataStructures(){
			System.out.println("---ASSETS---"+DataStructures.assetBalances.size());
			for (AssetBalanceData a:DataStructures.assetBalances) {
				System.out.println(a.asset+" "+a.free);
			}
			System.out.println("---STREAMS---"+DataStructures.streams.size());
			for (Stream s:DataStructures.streams) {
				System.out.println(s.streamName+" "+s.streamMapValue);
			}
			System.out.println("---CRYPTOFX---"+DataStructures.cryptoFXPairs.size());
			for (CryptoFXPair c:DataStructures.cryptoFXPairs) {
				System.out.println(c.crypto+" "+c.FXPair);
			}
		}
		
		static void printCryptoFXPairs() {
			System.out.println("---CRYPTOFX---"+Mapping.cryptoFXPairNameToCryptoFXPair.size());
			for (CryptoFXPair c:Mapping.cryptoFXPairNameToCryptoFXPair.values()) {
				System.out.println(c.crypto+" "+c.FXPair+": "+c.FXRates[0]+","+c.FXRates[1]+" "+c.FXRates[2]);
			}
		}

		static void printCryptoFXPairsByFXPair() {
			System.out.println("---CRYPTOFX BY FX---"+Mapping.FXPairToCryptoFXPairs.size());
			for (Map.Entry<String,HashSet<CryptoFXPair>> entry:Mapping.FXPairToCryptoFXPairs.entrySet()) {
				System.out.println("__["+entry.getKey()+"]__");
				for(CryptoFXPair c:entry.getValue()) {
					System.out.println(c.crypto+" "+c.FXPair+": "+c.FXRates[0]+", "+c.FXRates[1]+", "+c.FXRates[2]);
				}
			}
		}
	}
	
	public static void main(String[] args) {
		String[] fiatsToTrade = {"EUR","TRY","ARS","BRL","COP","IDRT","PLN","RON","UAH","ZAR"};
		String[] cryptosToTrade = {"ADA","APT","ARB","ATOM","AVAX","BCH","BNB","BTC","CHZ","DOGE","DOT","EGLD","ETH","FTM","GALA","GMT","GRT","ICP","LINK","LTC","MATIC","NEAR","OP","PEPE","RNDR","SHIB","SOL","SUI","TRX","USDT","VET","XLM","XRP"};
		int numberOfStreams = DataStructures.getNumberOfStreamsFromCSV("streamsMap.csv");
		
		
		System.out.println(numberOfStreams);
		DataStructures.streamSharedMemory = DataStructures.createSharedMemory("streamsData.dat", 20*numberOfStreams+8);
		
		System.out.println("Importing Streams.");
		DataStructures.importStreamMapValuesFromCSV("streamsMap.csv");
		
		DataStructures.initialiseFXPairs();
		DataStructures.removeUntradeableCryptoFXPairs();
		
		Display.printCryptoFXPairsByFXPair();
		
	}
}	
