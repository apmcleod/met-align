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
	 * The log probability of a downbeat not occurring on a note.
	 */
	private final double restPrior;
	
	/**
	 * A map containing, for each note, the log probability of it being on a downbeat.
	 */
	private final Map<MidiNote, Double> notePriors;
	
	/**
	 * Create a new prior object with the given prior.
	 * 
	 * @param restPrior The probability of a downbeat not occurring on a note.
	 * (NOT the log probability).
	 */
	public DownbeatPriors(double restPrior) {
		this.restPrior = Math.log(restPrior);
		notePriors = new HashMap<MidiNote, Double>();
	}
	
	/**
	 * Add a note to this prior.
	 * 
	 * @param note The note to add.
	 * @param prior The probability of a downbeat being on the given note. (NOT the log probability).
	 * @throws IOException The note already has a prior probability.
	 */
	public void addNote(MidiNote note, double prior) throws IOException {
		if (notePriors.containsKey(note)) {
			throw new IOException("Warning: note added twice");
		}
		
		notePriors.put(note, Math.log(prior));
	}
	
	/**
	 * Get the log probability of a downbeat being on a rest.
	 * 
	 * @return {@link #restPrior}
	 */
	public double getRestPrior() {
		return restPrior;
	}
	
	/**
	 * Get the log probability of a downbeat being on a given note.
	 * 
	 * @param note The note whose downbeat probability we want.
	 * @return The log probability of a downbeat being on the given note. Returns -INF.
	 */
	public double getPrior(MidiNote note) {
		try {
			return notePriors.get(note) * (1 - restPrior);
		} catch (NullPointerException e) {
			return Double.NEGATIVE_INFINITY;
		}
	}

	/**
	 * Parse and return a new DownbeatPriors object from the given file.
	 * 
	 * @param priorFile The file to parse the new object from.
	 * @param nlg The NoteListGenerator containing the notes with which we need to match the
	 * parsed priors.
	 * @return The newly created DownbeatPriors object.
	 * @throws IOException
	 */
	public static DownbeatPriors fromFile(File priorFile, NoteListGenerator nlg) throws IOException {
		DownbeatPriors priors = null;
		List<MidiNote> notes = nlg.getNoteList();
		
		BufferedReader br = new BufferedReader(new FileReader(priorFile));
		
		while (br.ready()) {
			// The first line should be p(rest)
			if (priors == null) {
				priors = new DownbeatPriors(Double.parseDouble(br.readLine()));
				continue;
			}
			
			// The other lines should be "start end pitch p(downbeat)"
			String[] split = br.readLine().split("\\s+");
			double start = Double.parseDouble(split[0]);
			double end = Double.parseDouble(split[1]);
			int pitch = Integer.parseInt(split[2]);
			double prior = Double.parseDouble(split[3]);
			
			priors.addNote(start, end, pitch, notes, prior);
		}
		
		br.close();
		
		return priors;
	}

	/**
	 * Find the matching note for the parsed values and add it to this object.
	 * 
	 * @param start The onset time of the note, in seconds, parsed from the python output.
	 * @param end The offset time of the note, in seconds, parsed from the python output.
	 * @param pitch The pitch of the note, parsed from the python output.
	 * @param notes A List of all of the notes of the song we are generating the prior of.
	 * @param prior The prior downbeat probability, parsed from the python output.
	 * 
	 * @throws IOException If a matching note was not found. Probably this is due to using
	 * a different MIDI file to generate the python output vs. parsing it here with Java.
	 */
	private void addNote(double start, double end, int pitch, List<MidiNote> notes, double prior) throws IOException {
		// Start and end are given in seconds, but we measure them in microseconds
		double startMicros = start * 1000000;
		
		// We don't use this because pretty_midi matches offsets to the most recent onset, while we do the opposite.
		// This breaks offset checking with overlapping notes on the same pitch.
		// Luckily, we only really care about onset time.
		@SuppressWarnings("unused")
		double endMicros = end * 1000000;
		
		// Find a match
		for (MidiNote note : notes) {
			// Check if this note has already been matched
			if (notePriors.containsKey(note)) {
				continue;
			}
			
			// Check if this note matches
			if (note.getPitch() == pitch && Math.abs(note.getOnsetTime() - startMicros) < 10000) {
				addNote(note, prior);
				return;
			}
		}
		
		throw new IOException("Warning: Note match not found! s=" + start + " e=" + end + " p=" + pitch);
	}
}
