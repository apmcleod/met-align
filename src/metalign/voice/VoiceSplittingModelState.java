package metalign.voice;

import java.util.List;
import java.util.TreeSet;

import metalign.Main;
import metalign.generic.MidiModelState;
import metalign.utils.MidiNote;

/**
 * A <code>VoiceSplittingModelState</code> is a {@link MidiModelState} which contains
 * a List of {@link Voice}s into which the incoming MIDI data has been split. In
 * order to get these voices, the {@link #getVoices()} method should be called.
 * 
 * @author Andrew McLeod - 4 Sept, 2015
 */
public abstract class VoiceSplittingModelState extends MidiModelState {
	/**
	 * Gets the Voices which are contained by this state currently.
	 * 
	 * @return A List of the Voices contained by this State.
	 */
	public abstract List<Voice> getVoices();
	
	@Override
	public abstract TreeSet<? extends VoiceSplittingModelState> handleIncoming(List<MidiNote> notes);
	
	@Override
	public abstract TreeSet<? extends VoiceSplittingModelState> close();
	
	/**
	 * Test if we should remove the given note based on {@link Main#MIN_NOTE_LENGTH}.
	 * 
	 * @param note The note we want to check for removal.
	 * @return True if it should be removed. False otherwise.
	 */
	public boolean shouldRemove(MidiNote note) {
		// Keep all notes
		if (Main.MIN_NOTE_LENGTH == -1) {
			return false;
		}
		
		for (Voice voice : getVoices()) {
			// Find correct voice
			if (voice.getMostRecentNote().equals(note)) {
				Voice previous = voice.getPrevious();
				if (previous == null) {
					return false;
				}
				
				MidiNote previousNote = previous.getMostRecentNote();
				if (previousNote == null) {
					return false;
				}
				
				// Check IOI b/w previous note and this note
				if (note.getOnsetTime() - previousNote.getOnsetTime() < Main.MIN_NOTE_LENGTH) {
					return true;
				}
				return false;
			}
		}
		
		return false;
	}
	
	/**
	 * Decide whether the given state is a duplicate of this one.
	 * 
	 * @param state The state we want to check for a duplicate.
	 * @return True if the states are duplicates. False otherwise.
	 */
	public abstract boolean isDuplicateOf(VoiceSplittingModelState state);
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		
		for (Voice voice : getVoices()) {
			sb.append(voice).append(',');
		}
		
		sb.setCharAt(sb.length() - 1, ']');
		return sb.toString();
	}
}
