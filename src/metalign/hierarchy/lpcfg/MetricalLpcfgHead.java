package metalign.hierarchy.lpcfg;

import java.io.Serializable;

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
	
	public static final MetricalLpcfgHead MAX_HEAD = new MetricalLpcfgHead(Double.MAX_VALUE, 0, false);
	public static final MetricalLpcfgHead MIN_HEAD = new MetricalLpcfgHead();

	/**
	 * The length of this head, normalized so that a value of <code>1.0</code> represents
	 * the sub beat length.
	 */
	private final double length;
	
	/**
	 * True if this head starts with a TIE. False otherwise.
	 * This can only be true if <code>{@link #startQuantum} == 0</code>.
	 */
	private final boolean tiesIn;
	
	/**
	 * The offset of the quantum which begins the first occurrence of a note of this head's length
	 * in the current node, normalized so that a value of <code>1.0</code> represents
	 * the sub beat length.
	 */
	private final double startQuantum;
	
	/**
	 * Create a new empty head with {@link #length} 0, {@link #startQuantum} 0, and {@link #tiesIn} false.
	 */
	public MetricalLpcfgHead() {
		this(0.0, 0.0, false);
	}
	
	/**
	 * Create a new head with the given fields.
	 * 
	 * @param length {@link #length}
	 * @param startQuantum {@link #startQuantum}
	 * @param tiesIn {@link #tiesIn}
	 */
	public MetricalLpcfgHead(double length, double startQuantum, boolean tiesIn) {
		if (!MetricalLpcfgGeneratorRunner.LEXICALIZATION) {
			this.length = 0;
			this.startQuantum = 0;
			this.tiesIn = false;
			return;
		}
		
		this.length = length;
		this.startQuantum = startQuantum;
		this.tiesIn = tiesIn;
	}
	
	/**
	 * Get the length of this head.
	 * 
	 * @return {@link #length}
	 */
	public double getLength() {
		return length;
	}
	
	@Override
	public int compareTo(MetricalLpcfgHead other) {
		if (other == null) {
			return -1;
		}
		
		int result = Double.compare(other.length, length);
		if (result != 0) {
			return result;
		}
		
		result = Double.compare(startQuantum, other.startQuantum);
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
		
		return length == head.length && startQuantum == head.startQuantum && tiesIn == head.tiesIn;
	}
	
	@Override
	public int hashCode() {
		return Double.hashCode(length + startQuantum * 4 * (tiesIn ? -1 : 1));
	}
	
	@Override
	public String toString() {
		return length + "," + startQuantum + (tiesIn ? "t" : "");
	}
}
