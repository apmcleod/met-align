package metalign.hierarchy.lpcfg;

/**
 * A <code>MetricalGrammarTerminalQuantum</code> represents the lowest level within a
 * {@link MetricalLpcfgTerminal}, and can either be a rest, a tie, or an onset.
 * 
 * @author Andrew McLeod - 24 February, 2016
 */
public enum MetricalLpcfgQuantum {
	/**
	 * A rest. No notes are played at this time.
	 */
	REST,
	
	/**
	 * A note onset. A new note begins at this time.
	 */
	ONSET,
	
	/**
	 * A tie. A note is being played at this time, but its onset was earlier.
	 * This must be preceded by 0 or more other TIEs, preceded again by an ONSET.
	 */
	TIE;
}
