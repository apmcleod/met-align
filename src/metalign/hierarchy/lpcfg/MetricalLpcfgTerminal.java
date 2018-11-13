package metalign.hierarchy.lpcfg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A <code>MetricalLpcfgTerminal</code> object represents a terminal symbol in the
 * rhythmic grammar. That is, any pattern of ties, notes, and rests which make
 * up an entire sub-beat in a given song's metrical structure. It is made up of a
 * List of {@link MetricalLpcfgQuantum}s.
 * 
 * @author Andrew McLeod - 24 February, 2016
 */
public class MetricalLpcfgTerminal implements MetricalLpcfgNode, Comparable<MetricalLpcfgTerminal>, Serializable {
	/**
	 * Version 3
	 */
	private static final long serialVersionUID = 3L;
	
	/**
	 * The pattern of quantums that make up this terminal, in unreduced form.
	 */
	private final List<MetricalLpcfgQuantum> quantums;
	
	/**
	 * The base length of this terminal, used to normalize in {@link #getHead()}.
	 */
	private final int baseLength;
	
	/**
	 * The head of this terminal.
	 */
	private final MetricalLpcfgHead head;
	
	/**
	 * Create a new terminal with just a single rest.
	 */
	public MetricalLpcfgTerminal() {
		this(Arrays.asList(new MetricalLpcfgQuantum[] {MetricalLpcfgQuantum.REST}));
	}
	
	/**
	 * Create a new MetricalGrammarTerminal with the given pattern and {@link #baseLength} of 1. This
	 * will convert the given pattern into reduced form before saving it.
	 * 
	 * @param subBeatQuantum The given pattern, in non-reduced form.
	 */
	public MetricalLpcfgTerminal(List<MetricalLpcfgQuantum> subBeatQuantum) {
		this(subBeatQuantum, 1);
	}

	/**
	 * Create a new MetricalGrammarTerminal with the given pattern and base length. This will convert
	 * the given pattern into reduced form before saving it.
	 * 
	 * @param beatQuantum The given pattern, in non-reduced form.
	 * @param baseLength {@link #baseLength}
	 */
	public MetricalLpcfgTerminal(List<MetricalLpcfgQuantum> beatQuantum, int baseLength) {
		if (!MetricalLpcfgGeneratorRunner.TESTING) {
			quantums = new ArrayList<MetricalLpcfgQuantum>(beatQuantum);
		} else {
			quantums = beatQuantum;
		}

		this.baseLength = baseLength;
		head = generateHead(quantums, baseLength);
	}
	
	/**
	 * Create a new MetricalGrammarTerminal, a shallow copy of the given one.
	 */
	private MetricalLpcfgTerminal(MetricalLpcfgTerminal terminal) {
		quantums = terminal.quantums;
		baseLength = terminal.baseLength;
		head = terminal.head;
	}
	
	/**
	 * Get whether this terminal contains any notes or not.
	 * 
	 * @return True if this terminal constins no notes (is all RESTS). False otherwise.
	 */
	@Override
	public boolean isEmpty() {
		return equals(new MetricalLpcfgTerminal());
	}
	
	@Override
	public boolean startsWithRest() {
		return quantums.get(0) == MetricalLpcfgQuantum.REST;
	}
	
	/**
	 * Get the head of this terminal.
	 * 
	 * @return The head of this terminal.
	 */
	public MetricalLpcfgHead getHead() {
		return head;
	}
	
	/**
	 * Generate the head of this terminal, called once in the constructor.
	 * 
	 * @return The head of this terminal.
	 */
	public static MetricalLpcfgHead generateHead(List<MetricalLpcfgQuantum> quantum, int baseLength) {
		int maxNoteLength = 0;
		int maxNoteIndex = 0;
		
		int currentNoteLength = 0;
		int currentNoteIndex = 0;
		
		for (int i = 0; i < quantum.size(); i++) {
			switch (quantum.get(i)) {
				case ONSET:
					if (currentNoteLength > maxNoteLength) {
						maxNoteLength = currentNoteLength;
						maxNoteIndex = currentNoteIndex;
					}
					
					currentNoteLength = 1;
					currentNoteIndex = i;
					break;
					
				case REST:
					if (currentNoteLength > maxNoteLength) {
						maxNoteLength = currentNoteLength;
						maxNoteIndex = currentNoteIndex;
					}
					
					currentNoteLength = 0;
					break;
					
				case TIE:
					currentNoteLength++;
					break;
			}
		}
		
		// Get final max as double
		if (currentNoteLength > maxNoteLength) {
			maxNoteLength = currentNoteLength;
			maxNoteIndex = currentNoteIndex;
		}
		
		double length = ((double) maxNoteLength) / quantum.size() * baseLength;
		
		return new MetricalLpcfgHead(length, ((double) maxNoteIndex) / quantum.size() * baseLength, quantum.get(maxNoteIndex) == MetricalLpcfgQuantum.TIE);
	}
	
	@Override
	public int getLength() {
		return baseLength;
	}
	
	/**
	 * Return if this pattern could be reduced to length 1. That is, if it is either all the same
	 * or an onset followed by all ties.
	 * 
	 * @return True if this pattern could be reduced to one. False otherwise.
	 */
	public boolean reducesToOne() {
		// Length 1 anyways
		if (quantums.size() == 1) {
			return true;
		}
		
		// Starts ONSET TIE or TIE TIE or REST REST
		if ((quantums.get(0) == MetricalLpcfgQuantum.ONSET && quantums.get(1) == MetricalLpcfgQuantum.TIE) ||
				(quantums.get(0) == quantums.get(1) && quantums.get(0) != MetricalLpcfgQuantum.ONSET)) {
			
			// Check if the rest are all equal
			for (int i = 2; i < quantums.size(); i++) {
				if (quantums.get(i) != quantums.get(i - 1)) {
					return false;
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	public static boolean reducesToOne(List<MetricalLpcfgQuantum> quantums) {
		// Starts ONSET TIE or TIE TIE or REST REST
		if ((quantums.get(0) == MetricalLpcfgQuantum.ONSET && quantums.get(1) == MetricalLpcfgQuantum.TIE) ||
				(quantums.get(0) == quantums.get(1) && quantums.get(0) != MetricalLpcfgQuantum.ONSET)) {
			
			// Check if the rest are all equal
			for (int i = 2; i < quantums.size(); i++) {
				if (quantums.get(i) != quantums.get(i - 1)) {
					return false;
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	@Override
	public List<MetricalLpcfgQuantum> getQuantum() {
		return quantums;
	}
	
	/**
	 * Return a copy of this terminal.
	 * 
	 * @return A copy of this terminal.
	 */
	@Override
	public MetricalLpcfgTerminal deepCopy() {
		return new MetricalLpcfgTerminal(this);
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof MetricalLpcfgTerminal)) {
			return false;
		}
		
		MetricalLpcfgTerminal o = (MetricalLpcfgTerminal) other;
		return quantums.equals(o.quantums);
	}
	
	@Override
	public int hashCode() {
		return quantums.hashCode();
	}
	
	/**
	 * Get the recursive String of this terminal. That is, the one that shows probabilities.
	 * 
	 * @return The recursive String of this terminal.
	 */
	public String toStringPretty(int depth, String tab) {
		StringBuilder sb = new StringBuilder();
		
		for (int i = 0; i < depth; i++) {
			sb.append(tab);
		}
		
		sb.append(toString());
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return quantums.toString();
	}

	@Override
	public int compareTo(MetricalLpcfgTerminal o) {
		if (o == null) {
			return 1;
		}
		
		int result = quantums.size() - o.quantums.size();
		if (result != 0) {
			return result;
		}
		
		return Integer.compare(hashCode(), o.hashCode());
	}
}
