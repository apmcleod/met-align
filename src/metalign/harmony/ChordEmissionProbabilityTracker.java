package metalign.harmony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import metalign.harmony.Chord.ChordQuality;
import metalign.utils.MidiNote;

public class ChordEmissionProbabilityTracker {
	
	/**
	 * A Map containing, for each ChordQuality, a list of the notes emitted during chords of that type
	 * (relative to the root, where root is pitch 0).
	 */
	private final Map<ChordQuality, List<Integer>> emissionCounts;
	
	/**
	 * Create a new empty Probability Tracker.
	 */
	public ChordEmissionProbabilityTracker() {
		emissionCounts = new HashMap<ChordQuality, List<Integer>>();
	}
	
	/**
	 * Add a new note to this tracker under the given chord.
	 * 
	 * @param chord The chord.
	 * @param note The note to add.
	 */
	public void addNote(Chord chord, MidiNote note) {
		int relativePitch = (note.getPitch() - chord.root + 12) % 12;
		
		// Add new List to Map if unseen quality
		if (!emissionCounts.containsKey(chord.quality)) {
			List<Integer> counts = new ArrayList<Integer>(12);
			for (int i = 0; i < 12; i++) {
				counts.add(0);
			}
			emissionCounts.put(chord.quality, counts);
		}
		
		// Increment count by 1
		emissionCounts.get(chord.quality).set(relativePitch, emissionCounts.get(chord.quality).get(relativePitch) + 1);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		for (ChordQuality quality : emissionCounts.keySet()) {
			sb.append(quality).append('\n');
			
			int sum = 0;
			for (int count : emissionCounts.get(quality)) {
				sum += count;
			}
			
			for (int i = 0; i < 12; i++) {
				sb.append(i).append(": ").append(100.0 * emissionCounts.get(quality).get(i) / sum).append("%\n");
			}
		}
		
		return sb.toString();
	}

}
