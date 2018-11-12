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
	 * The pattern of quantums that make up this terminal, in fully reduced form.
	 */
	private final List<MetricalLpcfgQuantum> reducedPattern;
	
	/**
	 * The pattern of quantums that make up this terminal, in unreduced form.
	 */
	private final List<MetricalLpcfgQuantum> originalPattern;
	
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
			originalPattern = new ArrayList<MetricalLpcfgQuantum>(beatQuantum);
		} else {
			originalPattern = beatQuantum;
		}
		//reducedPattern = beatQuantum.size() == 0 ? new ArrayList<MetricalLpcfgQuantum>(0) : generateReducedPattern(originalPattern);
		reducedPattern = originalPattern;
		this.baseLength = baseLength;
		head = generateHead();
	}
	
	/**
	 * Create a new MetricalGrammarTerminal, a shallow copy of the given one.
	 */
	private MetricalLpcfgTerminal(MetricalLpcfgTerminal terminal) {
		reducedPattern = terminal.reducedPattern;
		originalPattern = terminal.originalPattern;
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
		return reducedPattern.isEmpty() || reducedPattern.get(0) == MetricalLpcfgQuantum.REST;
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
	private MetricalLpcfgHead generateHead() {
		int maxNoteLength = 0;
		int maxNoteIndex = 0;
		
		int currentNoteLength = 0;
		int currentNoteIndex = 0;
		
		for (int i = 0; i < originalPattern.size(); i++) {
			MetricalLpcfgQuantum quantum = originalPattern.get(i);
			if (quantum == MetricalLpcfgQuantum.ONSET || quantum == MetricalLpcfgQuantum.REST) {
				// Note ended
				if (currentNoteLength > maxNoteLength) {
					maxNoteLength = currentNoteLength;
					maxNoteIndex = currentNoteIndex;
				}
				
				currentNoteLength = 0;
				currentNoteIndex = i;
			}
			
			if (quantum == MetricalLpcfgQuantum.ONSET || quantum == MetricalLpcfgQuantum.TIE) {
				// Note continues
				currentNoteLength++;
			}
		}
		
		// Get final max as double
		if (currentNoteLength > maxNoteLength) {
			maxNoteLength = currentNoteLength;
			maxNoteIndex = currentNoteIndex;
		}
		
		double length = ((double) maxNoteLength) / originalPattern.size() * baseLength;
		
		return new MetricalLpcfgHead(length, ((double) maxNoteIndex) / originalPattern.size() * baseLength, originalPattern.get(maxNoteIndex) == MetricalLpcfgQuantum.TIE);
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
		if (reducedPattern.size() == 1) {
			return true;
		}
		
		// Starts ONSET TIE or TIE TIE or REST REST
		if ((reducedPattern.get(0) == MetricalLpcfgQuantum.ONSET && reducedPattern.get(1) == MetricalLpcfgQuantum.TIE) ||
				(reducedPattern.get(0) == reducedPattern.get(1) && reducedPattern.get(0) != MetricalLpcfgQuantum.ONSET)) {
			
			// Check if the rest are all equal
			for (int i = 2; i < reducedPattern.size(); i++) {
				if (reducedPattern.get(i) != reducedPattern.get(i - 1)) {
					return false;
				}
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Get the original unreduced pattern of this terminal.
	 * 
	 * @return {@link #originalPattern}
	 */
	public List<MetricalLpcfgQuantum> getOriginalPattern() {
		return originalPattern;
	}
	
	@Override
	public MetricalLpcfgTerminal getTerminal() {
		return this;
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
		return reducedPattern.equals(o.reducedPattern);
	}
	
	@Override
	public int hashCode() {
		return reducedPattern.hashCode();
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
		return reducedPattern.toString();
	}

	@Override
	public int compareTo(MetricalLpcfgTerminal o) {
		if (o == null) {
			return 1;
		}
		
		int result = reducedPattern.size() - o.reducedPattern.size();
		if (result != 0) {
			return result;
		}
		
		return Integer.compare(hashCode(), o.hashCode());
	}
}
