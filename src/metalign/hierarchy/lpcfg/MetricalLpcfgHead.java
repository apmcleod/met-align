package metalign.hierarchy.lpcfg;

import java.io.Serializable;

import metalign.utils.MathUtils;

/**
 * A <code>MetricalLpcfgHead</code> represents the head of a node in our lexicalized pcfg.
 * It contains information about the longest note lying under a node, that note's location
 * relative to the start of the beat, and whether that note begins from a tie. It's natural
 * ordering is first by decreasing {@link #length}, then by increasing {@link #startQuantum},
 * and finally by {@link #tiesIn}, where <code>false</code> comes first. 
 * 
 * @author Andrew McLeod - 5 May, 2016
 */
public class MetricalLpcfgHead implements Comparable<MetricalLpcfgHead>, Serializable {
	/**
	 * Version 1
	 */
	private static final long serialVersionUID = 1L;
	
	public static final MetricalLpcfgHead MAX_HEAD = new MetricalLpcfgHead(Integer.MAX_VALUE, 1, 0, 1, false);
	public static final MetricalLpcfgHead MIN_HEAD = new MetricalLpcfgHead();

	/**
	 * The length of this head, normalized so that a value of <code>1</code> represents
	 * the beat length.
	 */
	private final int lengthNumerator;
	private final int lengthDenominator;
	
	/**
	 * True if this head starts with a TIE. False otherwise.
	 * This can only be true if <code>{@link #startQuantum} == 0</code>.
	 */
	private final boolean tiesIn;
	
	/**
	 * The offset of the quantum which begins the first occurrence of a note of this head's length
	 * in the current node, normalized so that a value of <code>1</code> represents
	 * the beat length.
	 */
	private final int startQuantumNumerator;
	private final int startQuantumDenominator;
	
	/**
	 * Create a new empty head with {@link #length} 0, {@link #startQuantum} 0, and {@link #tiesIn} false.
	 */
	public MetricalLpcfgHead() {
		this(0, 1, 0, 1, false);
	}
	
	/**
	 * Create a new head with the given fields.
	 * 
	 * @param length {@link #length}
	 * @param startQuantum {@link #startQuantum}
	 * @param tiesIn {@link #tiesIn}
	 */
	public MetricalLpcfgHead(int lengthNum, int lengthDenom, int startQuantumNum, int startQuantumDenom, boolean tiesIn) {
		if (!MetricalLpcfgGeneratorRunner.LEXICALIZATION) {
			this.lengthNumerator = 0;
			this.lengthDenominator = 1;
			this.startQuantumNumerator = 0;
			this.startQuantumDenominator = 1;
			this.tiesIn = false;
			return;
		}
		
		int gcf = MathUtils.getGCF(lengthNum, lengthDenom);
		this.lengthNumerator = lengthNum / gcf;
		this.lengthDenominator = lengthDenom / gcf;
		
		gcf = MathUtils.getGCF(startQuantumNum, startQuantumDenom);
		this.startQuantumNumerator = startQuantumNum / gcf;
		this.startQuantumDenominator = startQuantumDenom / gcf;
		
		this.tiesIn = tiesIn;
	}
	
	/**
	 * Get the length of this head.
	 * 
	 * @return {@link #length}
	 */
	public double getLength() {
		return ((double) lengthNumerator) / lengthDenominator;
	}
	
	public double getStartQuantum() {
		return ((double) startQuantumNumerator) / startQuantumDenominator;
	}
	
	@Override
	public int compareTo(MetricalLpcfgHead other) {
		if (other == null) {
			return -1;
		}
		
		int result = Double.compare(other.getLength(), getLength());
		if (result != 0) {
			return result;
		}
		
		result = Double.compare(getStartQuantum(), other.getStartQuantum());
		if (result != 0) {
			return result;
		}
		
		return Boolean.compare(tiesIn, other.tiesIn);
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof MetricalLpcfgHead)) {
			return false;
		}
		
		MetricalLpcfgHead head = (MetricalLpcfgHead) other;
		
		return getLength() == head.getLength() && getStartQuantum() == head.getStartQuantum() && tiesIn == head.tiesIn;
	}
	
	@Override
	public int hashCode() {
		return Double.hashCode(getLength() + getStartQuantum() * 4 * (tiesIn ? -1 : 1));
	}
	
	@Override
	public String toString() {
		return lengthNumerator + (lengthDenominator == 1 ? "" : ("/" + lengthDenominator)) + "," + startQuantumNumerator +
				(startQuantumDenominator == 1 ? "" : ("/" + startQuantumDenominator)) + (tiesIn ? "t" : "");
	}
}
