package metalign.beat.hmmprior;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import metalign.parsing.NoteListGenerator;
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
	 * @throws IOException 
	 */
	public void addNote(MidiNote note, double prior) throws IOException {
		if (notePriors.containsKey(note)) {
			throw new IOException("Warning: note added twice");
		}
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

	public static DownbeatPriors fromFile(File priorFile, NoteListGenerator nlg) throws IOException {
		DownbeatPriors priors = null;
		List<MidiNote> notes = nlg.getNoteList();
		
		BufferedReader br = new BufferedReader(new FileReader(priorFile));
		
		while (br.ready()) {
			if (priors == null) {
				priors = new DownbeatPriors(Double.parseDouble(br.readLine()));
				continue;
			}
			
			String[] split = br.readLine().split("\\s+");
			double start = Double.parseDouble(split[0]);
			double end = Double.parseDouble(split[1]);
			int pitch = Integer.parseInt(split[2]);
			double prior = Double.parseDouble(split[4]);
			
			priors.addNote(findMatchedNote(start, end, pitch, notes), prior);
		}
		
		br.close();
		
		return priors;
	}

	private static MidiNote findMatchedNote(double start, double end, int pitch, List<MidiNote> notes) throws IOException {
		double startMicros = start * 1000000;
		double endMicros = start * 1000000;
		
		for (MidiNote note : notes) {
			if (note.getPitch() == pitch && Math.abs(note.getOnsetTime() - startMicros) < 30000 &&
					Math.abs(note.getOffsetTime() - endMicros) < 30000) {
				return note;
			}
		}
		
		throw new IOException("Warning: Note match not found!");
	}
}
