package metalign.time;

/**
 * A <code>KeySignature</code> stores the key signature of a song. That is, the key (represented by the
 * number of sharps in its signature), and whether it is major or minor.
 * 
 * @author Andrew McLeod - 11 Feb, 2015
 */
public class KeySignature {
	/**
	 * The number of sharps in this key signature. Negative numbers indicate the number of flats.
	 */
	private final int numSharps;
	
	/**
	 * True if this key is major. False for minor.
	 */
	private final boolean major;
	
	/**
	 * Create a default key signature (C major).
	 */
	public KeySignature() {
		this(new byte[] {0, 0});
	}

	/**
	 * Create a new Key Signature based on the given data array.
	 * 
	 * @param data data[0] is numSharps. data[1] is 0 for major, 1 for minor.
	 */
	public KeySignature(byte[] data) {
		numSharps = data[0];
		major = data[1] == 0;
	}

	/**
	 * Create a new key signature based on the given key number and major boolean.
	 * 
	 * @param keyNumber The positive offset from c to this key's tonic note.
	 * @param isMajor {@link #major}
	 */
	public KeySignature(int keyNumber, boolean isMajor) {
		major = isMajor;
		
		switch (keyNumber) {
			// C
			case 0:
				numSharps = isMajor ? 0 : -3;
				break;
				
			// D-flat
			case 1:
				numSharps = isMajor ? -5 : 4;
				break;
				
			// D
			case 2:
				numSharps = isMajor ? 2 : -1;
				break;
				
			// E-flat
			case 3:
				numSharps = isMajor ? -3 : 6;
				break;
				
			// E
			case 4:
				numSharps = isMajor ? 4 : 1;
				break;
			
			// F
			case 5:
				numSharps = isMajor ? -1 : -4;
				break;
				
			// F-sharp
			case 6:
				numSharps = isMajor ? 6 : 3;
				break;
				
			// G
			case 7:
				numSharps = isMajor ? 1 : -2;
				break;
				
			// A-flat
			case 8:
				numSharps = isMajor ? -4 : 5;
				break;
				
			// A
			case 9:
				numSharps = isMajor ? 3 : 0;
				break;
				
			// B-flat
			case 10:
				numSharps = isMajor ? -2 : -5;
				break;
				
			// B
			case 11:
				numSharps = isMajor ? 5 : 2;
				break;
				
			// Error
			default:
				numSharps = 0;
		}
	}

	/**
	 * Get the positive offset from C of the tonic note of this key.
	 *  
	 * @return The number of semitones between C and the next highest instance
	 * of the tonic of this key, on the range [0,11]
	 */
	public int getPositiveOffsetFromC() {
		int offset = (7 * numSharps) % 12;
		
		// Fix for minor keys
		if (!isMajor()) {
			offset-= 3;
		}
		
		while (offset < 0) {
			offset+= 12;
		}
		
		return offset;
	}
	
	/**
	 * Get the number of sharps in this key signature.
	 * 
	 * @return {@link #numSharps}
	 */
	public int getNumSharps() {
		return numSharps;
	}
	
	/**
	 * Return whether this key is major or not.
	 * 
	 * @return {@link #major}
	 */
	public boolean isMajor() {
		return major;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(3);
		sb.append(numSharps);
		
		if (!isMajor()) {
			sb.append('m');
		}
		
		return sb.toString();
	}
	
	@Override
	public int hashCode() {
		return getPositiveOffsetFromC() * (isMajor() ? -1 : 1);
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof KeySignature)) {
			return false;
		}
		
		KeySignature ks = (KeySignature) other;
		
		return getPositiveOffsetFromC() == ks.getPositiveOffsetFromC() && isMajor() == ks.isMajor();
	}
}
