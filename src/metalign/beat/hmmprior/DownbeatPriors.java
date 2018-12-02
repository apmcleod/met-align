package metalign.beat.hmmprior;

import java.util.HashMap;
import java.util.Map;

import metalign.utils.MidiNote;

public class DownbeatPriors {
	
	/**
	 * The probability of a downbeat not occurring on a note.
	 */
	private final double restPrior;
	
	/**
	 * A map containing, for each note, the probability of it being on a downbeat.
	 */
	private final Map<MidiNote, Double> notePriors;
	
	/**
	 * Create a new prior object with the given prior.
	 * 
	 * @param restPrior The probability of a downbeat not occurring on a note.
	 */
	public DownbeatPriors(double restPrior) {
		this.restPrior = restPrior;
		notePriors = new HashMap<MidiNote, Double>();
	}
	
	/**
	 * Add a note to this prior.
	 * 
	 * @param note The note to add.
	 * @param prior The probability of a downbeat being on the given note.
	 */
	public void addNote(MidiNote note, double prior) {
		notePriors.put(note, prior);
	}
	
	/**
	 * Get the probability of a downbeat being on a rest.
	 * 
	 * @return {@link #restPrior}
	 */
	public double getRestPrior() {
		return restPrior;
	}
	
	/**
	 * Get the probability of a downbeat being on a given note.
	 * 
	 * @param note The note whose downbeat probability we want.
	 * @return The probability of a downbeat being on the given note. Returns 0 if the note
	 * is not found.
	 */
	public double getPrior(MidiNote note) {
		try {
			return notePriors.get(note) * (1 - restPrior);
		} catch (NullPointerException e) {
			return 0.0;
		}
	}
}
