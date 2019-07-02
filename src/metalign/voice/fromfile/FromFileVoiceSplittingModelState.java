package metalign.voice.fromfile;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javax.sound.midi.InvalidMidiDataException;

import metalign.generic.MidiModelState;
import metalign.parsing.EventParser;
import metalign.parsing.MidiEventParser;
import metalign.utils.MidiNote;
import metalign.voice.Voice;
import metalign.voice.VoiceSplittingModelState;

/**
 * A <code>FromFileVoiceSplittingModelState</code> generates a list of {@link Voice}s, the one
 * directly generated from the underlying MIDI using the {@link MidiEventParser#getGoldStandardVoices()}
 * method.
 * 
 * @author Andrew McLeod - 2 Dec, 2015
 */
public class FromFileVoiceSplittingModelState extends VoiceSplittingModelState {
	
	/**
	 * A List of the Voices present in this state, generated from the MIDI directly.
	 */
	private List<Voice> voices;
	
	/**
	 * The most recent time for which we have seen a note onset so far, initially 0.
	 */
	private long mostRecentTime;
	
	/**
	 * Creates a new object, generating {@link #voices} directly from the
	 * {@link EventParser#getGoldStandardVoices()} method.
	 * 
	 * @param ep The EventParser we will get the gold standard voices from.
	 * @throws InvalidMidiDataException 
	 */
	public FromFileVoiceSplittingModelState(EventParser ep) throws InvalidMidiDataException {
		mostRecentTime = 0;
		
		voices = new ArrayList<Voice>();
		for (List<MidiNote> voice : ep.getGoldStandardVoices()) {
			Voice newVoice = null;
			
			// Initialize
			if (!voice.isEmpty()) {
				newVoice = new Voice(voice.get(0));
				
				// Chain
				for (int i = 1; i < voice.size(); i++) {
					newVoice = new Voice(voice.get(i), newVoice);
					if (voice.get(i).getOffsetTime() == 0L) {
						throw new InvalidMidiDataException("No offset found for note " + voice.get(i));
					}
				}
				
				voices.add(newVoice);
			}
		}
	}

	@Override
	public List<Voice> getVoices() {
		List<Voice> currentVoices = new ArrayList<Voice>();
		
		for (Voice voice : voices) {
			while (voice != null && voice.getMostRecentNote().getOnsetTime() > mostRecentTime) {
				voice = voice.getPrevious();
			}
			
			if (voice != null) {
				currentVoices.add(voice);
			}
		}
		
		return currentVoices;
	}

	@Override
	public TreeSet<FromFileVoiceSplittingModelState> handleIncoming(List<MidiNote> notes) {
		TreeSet<FromFileVoiceSplittingModelState> newState = new TreeSet<FromFileVoiceSplittingModelState>();
		mostRecentTime = notes.get(0).getOnsetTime();
		newState.add(this);
		return newState;
	}
	
	@Override
	public TreeSet<FromFileVoiceSplittingModelState> close() {
		TreeSet<FromFileVoiceSplittingModelState> newState = new TreeSet<FromFileVoiceSplittingModelState>();
		mostRecentTime++;
		newState.add(this);
		return newState;
	}

	@Override
	public double getScore() {
		return 0.0;
	}
	
	@Override
	public boolean isDuplicateOf(VoiceSplittingModelState state) {
		return state instanceof FromFileVoiceSplittingModelState;
	}

	@Override
	public int compareTo(MidiModelState other) {
		if (!(other instanceof FromFileVoiceSplittingModelState)) {
			return -1;
		}
		
		return 0;
		
//		FromFileVoiceSplittingModelState o = (FromFileVoiceSplittingModelState) other;
//		
//		int result = Long.compare(mostRecentTime, o.mostRecentTime);
//		if (result != 0) {
//			return result;
//		}
//		
//		result = voices.size() - o.voices.size();
//		if (result != 0) {
//			return result;
//		}
//		
//		for (int i = 0; i < voices.size(); i++) {
//			result = voices.get(i).compareTo(o.voices.get(i));
//			if (result != 0) {
//				return result;
//			}
//		}
//		
//		return 0;
	}

}
