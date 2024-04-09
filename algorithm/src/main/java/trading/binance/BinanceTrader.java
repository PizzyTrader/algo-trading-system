package trading.binance;
import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Scanner;

public class BinanceTrader {
	
	//static String[] fiatFXPairs = {"EURTRY", "EURBRL", "TRYBRL"};
	//static String[] cryptos = {"ADA","BTC","CHZ","SOL","DOT","LTC"};
	static String[] cryptos = {"ADA", "AVAX", "BNB", "BTC", "CHZ", "DOGE", "DOT", "ETH", "GALA", "LINK", "LTC", "MATIC", "SHIB", "SOL"};
	static String[] fiats = {"TRY","EUR","BRL"};
	static String[] fiatFXPairs = {"EURTRY", "EURBRL", "TRYBRL"};
	
	static float[] cryptoF1Data;
	static float[] cryptoF2Data;
	static float btc_cryptoF1Data[];
	static float btc_cryptoF2Data[];
	
	static float instantF1F2IER;
	static float instantF2F1IER;
	static float midF1F2IER;
	static float btc_midF1F2IER;
	
	static HashMap<String,float[]> symbolData = new HashMap<String,float[]>();
	static HashMap<Integer,String> symbolMemoryMap = new HashMap<Integer,String>();
	static HashMap<String,HashMap<String,float[]>> fiatFXData = new HashMap<String,HashMap<String,float[]>>();
	static HashMap<String,float[]> currentFXCryptos = new HashMap<String,float[]>();
	
	static MappedByteBuffer shm = createSharedMemory("sharedMemory.dat", (20*cryptos.length*fiats.length)+2);
	
	static MappedByteBuffer createSharedMemory(String path, long size) {
		try (FileChannel fc = (FileChannel)Files.newByteChannel(new File(path).toPath(),
				EnumSet.of(
						StandardOpenOption.CREATE,
						StandardOpenOption.SPARSE,
						StandardOpenOption.WRITE,
						StandardOpenOption.READ))) {

			return fc.map(FileChannel.MapMode.READ_WRITE, 0, size);
		} catch( IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	static void intialise() {
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
			
		}
		for (String f: fiatFXPairs) {
			HashMap<String,float[]> FXCryptos = new HashMap<String,float[]>();
			for (String c: cryptos) {
				float[] IERs = new float[3];
				FXCryptos.put(c,IERs);
			}
			fiatFXData.put(f,FXCryptos);
		}
	}
	
	static void readData() {
		short flag;
		float[] floatArray = new float[4];
		String streamPair;
		while (true) {
			flag = shm.getShort(0);
			if (flag == 1) {
				for(int i:symbolMemoryMap.keySet()) {
					streamPair = symbolMemoryMap.get(i);
					floatArray[0] = shm.getFloat(i+4); //bidPrice
					floatArray[1] = shm.getFloat(i+8); //bidQuant
					floatArray[2] = shm.getFloat(i+12); //askPrice
					floatArray[3] = shm.getFloat(i+16); //askQuant
					symbolData.put(streamPair,floatArray);
				}
				flag = 0;
				shm.putShort(0,flag);
				processData();
			}
		}
	}
	
	static void processData() {
		for (String p:symbolData.keySet()) {
			System.out.println(p);
			float[] f = symbolData.get(p);
			for(float fl:f) {
				System.out.print(Float.toString(fl)+",");
			}
			System.out.print('\n');
		}
	}
	
	public static void main(String[] args) {
		intialise();
		readData();
	}

}
