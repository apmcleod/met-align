package metalign.joint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import metalign.Main;
import metalign.beat.BeatTrackingModelState;
import metalign.generic.MidiModelState;
import metalign.hierarchy.HierarchyModelState;
import metalign.hierarchy.lpcfg.MetricalLpcfgHierarchyModelState;
import metalign.utils.MidiNote;
import metalign.voice.Voice;
import metalign.voice.VoiceSplittingModelState;

/**
 * A <code>JointModelState</code> is the state used to model and infer one of each type of model
 * simultaneously as a set, one step at a time.
 * 
 * @author Andrew McLeod - 8 Sept, 2015
 */
public class JointModelState extends MidiModelState {
	
	private final JointModel jointModel;
	
	/**
	 * The VoiceSplittingModel to use in our joint model.
	 */
	private VoiceSplittingModelState voiceState;
	
	/**
	 * The BeatTrackingModel to use in our joint model.
	 */
	private BeatTrackingModelState beatState;
	
	/**
	 * The HierarchyModel to use in our joint model.
	 */
	private HierarchyModelState hierarchyState;
	
	/**
	 * Create a new JointModelState with the given constituent states.
	 * 
	 * @param voice {@link #voiceState}
	 * @param beat {@link #beatState}
	 * @param hierarchy {@link #hierarchyState}
	 */
	public JointModelState(JointModel jm, VoiceSplittingModelState voice, BeatTrackingModelState beat, HierarchyModelState hierarchy) {
		jointModel = jm;
		
		voiceState = voice;
		
		beatState = beat;
		beatState.setHierarchyState(hierarchy);
		
		hierarchyState = hierarchy;
		hierarchyState.setVoiceState(voiceState);
		hierarchyState.setBeatState(beatState);
	}

	/**
	 * Create a new ModelState on the given HierarchyModelState. The {@link #voiceState} and the
	 * {@link #beatState} will be loaded from the given HierarchyModelState.
	 * 
	 * @param h {@link #hierarchyState}
	 */
	private JointModelState(JointModel jm, HierarchyModelState h) {
		this(jm, h.getVoiceState(), h.getBeatState().deepCopy(), h.deepCopy());
	}

	@Override
	public double getScore() {
		return voiceState.getScore() + beatState.getScore() + hierarchyState.getScore();
	}

	@SuppressWarnings("unchecked")
	@Override
	public TreeSet<JointModelState> handleIncoming(List<MidiNote> notes) {
		TreeSet<JointModelState> newStates = new TreeSet<JointModelState>();
		
		// Check if we even need to compute anything
		boolean beamFull = Main.BEAM_SIZE != -1 && jointModel.startedStates.size() >= Main.BEAM_SIZE;
		if (beamFull && getScore() < jointModel.startedStates.last().getScore()) {
			return newStates;
		}
		
		
		// Voice state branching with check for pre-computed values
		List<VoiceSplittingModelState> newVoiceStates = jointModel.newVoiceStates.get(voiceState);
		
		if (newVoiceStates == null) {
			newVoiceStates = new ArrayList<VoiceSplittingModelState>(voiceState.handleIncoming(notes));
			
			jointModel.newVoiceStates.put(voiceState, newVoiceStates);
		}
		
		
		// Beat state branching with check for pre-computed values for initial step
		List<TreeSet<BeatTrackingModelState>> newBeatStates = new ArrayList<TreeSet<BeatTrackingModelState>>();
		List<List<MidiNote>> newNotesLists = new ArrayList<List<MidiNote>>();
		
		for (VoiceSplittingModelState voiceState : newVoiceStates) {
			
			// This falls outside the main beam, we can skip it.
			if (beamFull && jointModel.startedStates.last().getScore() >= voiceState.getScore() + beatState.getScore() + beatState.getHierarchyState().getScore()) {
				newNotesLists.add(new ArrayList<MidiNote>());
				newBeatStates.add(new TreeSet<BeatTrackingModelState>());
				continue;
			}
			
			BeatTrackingModelState beatStateCopy = beatState.deepCopy();
			beatStateCopy.setHierarchyState(beatState.getHierarchyState());
			
			// Calculate new notes (in case of -m)
			List<MidiNote> newNotes = new ArrayList<MidiNote>(notes.size());
			for (MidiNote note : notes) {
				if (!voiceState.shouldRemove(note)) {
					newNotes.add(note);
				}
			}
			newNotesLists.add(newNotes);
			
			// Branch to save computation of initial step
			if (beatStateCopy.isStarted()) {
				newBeatStates.add((TreeSet<BeatTrackingModelState>) beatStateCopy.handleIncoming(newNotes));
				
			} else {
				// Initial step
				Map<List<MidiNote>, TreeSet<BeatTrackingModelState>> newBeatStatesMap = jointModel.newBeatStates.get(beatStateCopy);
				if (newBeatStatesMap == null) {
					newBeatStatesMap = new HashMap<List<MidiNote>, TreeSet<BeatTrackingModelState>>();
					jointModel.newBeatStates.put(beatState, newBeatStatesMap);
				}
				
				TreeSet<BeatTrackingModelState> branchedStates = newBeatStatesMap.get(newNotes);
				if (branchedStates == null) {
					branchedStates = (TreeSet<BeatTrackingModelState>) beatStateCopy.handleIncoming(newNotes);
					newBeatStatesMap.put(newNotes, branchedStates);
				}
				
				newBeatStates.add(branchedStates);
			}
		}
		
		
		// Hierarchy state branching
		for (int i = 0; i < newBeatStates.size(); i++) {
			
			VoiceSplittingModelState newVoiceState = newVoiceStates.get(i);
			TreeSet<BeatTrackingModelState> beatStateSet = newBeatStates.get(i);
			List<MidiNote> newNotes = newNotesLists.get(i);

			TreeSet<JointModelState> newStatesTmp = new TreeSet<JointModelState>();
			
			// Go through each voice beat pair and branch on it
			for (BeatTrackingModelState beatState : beatStateSet) {
				
				// Main Beam is full and score is not possibly better than any of them
				if (beamFull && jointModel.startedStates.last().getScore() >= newVoiceState.getScore() + beatState.getScore() + beatState.getHierarchyState().getScore()) {
					if (Main.SUPER_VERBOSE && Main.TESTING) {
						System.out.println("ELIMINATING (Joint Beam): " + newVoiceState + beatState + beatState.getHierarchyState());
					}
					continue;
				}
				
				HierarchyModelState hierarchyStateCopy = hierarchyState.deepCopy();
				hierarchyStateCopy.setVoiceState(newVoiceState);
				hierarchyStateCopy.setBeatState(beatState);
				beatState.setHierarchyState(hierarchyStateCopy);
				
				// Special case to add new voice to hierarchy note trackers if the note has been removed
				if (hierarchyStateCopy instanceof MetricalLpcfgHierarchyModelState && newNotes.size() < notes.size()) {
					for (int j = 0; j < newVoiceState.getVoices().size(); j++) {
						Voice voice = newVoiceState.getVoices().get(j);
						
						boolean needToFix = true;
						for (MidiNote note : voice.getNotes()) {
							if (!notes.contains(note)) {
								// This voice is not new
								needToFix = false;
								break;
							}
							
							if (newNotes.contains(note)) {
								// This voice may be new, but it will get seen by the hierarchy
								needToFix = false;
								break;
							}
						}
						
						if (needToFix) {
							((MetricalLpcfgHierarchyModelState) hierarchyStateCopy).addNewVoiceIndex(j);
						}
					}
				}
				
				// Branching with duplicate checking for less memory usage (because duplicates have the same voice state)
				for (HierarchyModelState hms : hierarchyStateCopy.handleIncoming(newNotes)) {
					addWithDuplicateCheck(hms, newStatesTmp);
				}
			}
			
			newStates.addAll(newStatesTmp);
		}
		
		return newStates;
	}

	@SuppressWarnings("unchecked")
	@Override
	public TreeSet<JointModelState> close() {
		TreeSet<JointModelState> newStates = new TreeSet<JointModelState>();
		
		// Check if we even need to compute anything
		boolean beamFull = Main.BEAM_SIZE != -1 && jointModel.startedStates.size() >= Main.BEAM_SIZE;
		if (beamFull && getScore() < jointModel.startedStates.last().getScore()) {
			return newStates;
		}
		
		// Voice statej
		List<VoiceSplittingModelState> newVoiceStates = jointModel.newVoiceStates.get(voiceState);
		if (newVoiceStates == null) {
			newVoiceStates = new ArrayList<VoiceSplittingModelState>(voiceState.close());
			jointModel.newVoiceStates.put(voiceState, newVoiceStates);
		}
		
		// Beat states
		List<TreeSet<BeatTrackingModelState>> newBeatStates = new ArrayList<TreeSet<BeatTrackingModelState>>();
		for (VoiceSplittingModelState voiceState : newVoiceStates) {
			// This falls outside the main beam, we can skip it.
			if (beamFull && jointModel.startedStates.last().getScore() >= voiceState.getScore() + beatState.getScore() + beatState.getHierarchyState().getScore()) {
				newBeatStates.add(new TreeSet<BeatTrackingModelState>());
				continue;
			}
			
			BeatTrackingModelState beatStateCopy = beatState.deepCopy();
			beatStateCopy.setHierarchyState(beatState.getHierarchyState());
			
			newBeatStates.add((TreeSet<BeatTrackingModelState>) beatStateCopy.close());
		}
		
		// Hierarchy states
		for (int i = 0; i < newBeatStates.size(); i++) {
			VoiceSplittingModelState newVoiceState = newVoiceStates.get(i);
			TreeSet<BeatTrackingModelState> beatStateSet = newBeatStates.get(i);
			
			TreeSet<JointModelState> newStatesTmp = new TreeSet<JointModelState>();
			
			for (BeatTrackingModelState beatState : beatStateSet) {
				// Main Beam is full and score is not possibly better than any of them
				if (beamFull && jointModel.startedStates.last().getScore() >= newVoiceState.getScore() + beatState.getScore() + beatState.getHierarchyState().getScore()) {
					if (Main.SUPER_VERBOSE && Main.TESTING) {
						System.out.println("ELIMINATING (Joint Beam): " + newVoiceState + beatState + beatState.getHierarchyState());
					}
					continue;
				}
				
				HierarchyModelState hierarchyStateCopy = hierarchyState.deepCopy();
				hierarchyStateCopy.setVoiceState(newVoiceState);
				hierarchyStateCopy.setBeatState(beatState);
				beatState.setHierarchyState(hierarchyStateCopy);
				
				// Branching with duplicate checking for less memory usage
				for (HierarchyModelState hms : hierarchyStateCopy.close()) {
					addWithDuplicateCheck(hms, newStatesTmp);
				}
			}
			
			newStates.addAll(newStatesTmp);
		}
				
		return newStates;
	}
	
	/**
	 * Add a new JMS given by the hms to the newStates temporary TreeSet, with a duplicate check.
	 * 
	 * @param hms The hms used to generate the new JMS.
	 * @param newStatesTmp The TreeSet to add this to.
	 */
	private void addWithDuplicateCheck(HierarchyModelState hms, TreeSet<JointModelState> newStatesTmp) {
		JointModelState jms = new JointModelState(jointModel, hms);
		JointModelState duplicate = null;
		
		for (JointModelState state : newStatesTmp) {
			if (state.isDuplicateOf(jms)) {
				duplicate = state;
				break;
			}
		}
		
		if (duplicate != null) {
			if (duplicate.getScore() < jms.getScore()) {
				newStatesTmp.add(jms);
				newStatesTmp.remove(duplicate);
				
				if (Main.SUPER_VERBOSE && Main.TESTING) {
					System.out.println("ELIMINATING (Duplicate): " + duplicate);
				}
				
			} else {
				if (Main.SUPER_VERBOSE && Main.TESTING) {
					System.out.println("ELIMINATING (Duplicate): " + jms);
				}
			}
			
		} else {
			newStatesTmp.add(jms);
		}
	}
	
	/**
	 * Get the number of bars which we have gone through so far.
	 * 
	 * @return The number of bars we have gone through so far.
	 */
	public int getBarCount() {
		return Math.min(beatState.getBarCount(), hierarchyState.getBarCount());
	}
	
	/**
	 * Get the VoiceSplittingModelState currently in this JointModelState.
	 * 
	 * @return {@link #voiceState}
	 */
	public VoiceSplittingModelState getVoiceState() {
		return voiceState;
	}
	
	/**
	 * Get the BeatTrackingModelState currently in this JointModelState.
	 * 
	 * @return {@link #beatState}
	 */
	public BeatTrackingModelState getBeatState() {
		return beatState;
	}
	
	/**
	 * Get the HierarchyModelState currently in this JointModelState.
	 * 
	 * @return {@link #hierarchyState}
	 */
	public HierarchyModelState getHierarchyState() {
		return hierarchyState;
	}
	
	/**
	 * Decide whether the given state is a duplicate of this one.
	 * 
	 * @param state The state we want to check for a duplicate.
	 * @return True if the states are duplicates. False otherwise.
	 */
	private boolean isDuplicateOf(JointModelState state) {
		if (Main.NUM_FROM_FILE == 0) {
			return beatState.isDuplicateOf(state.beatState) && hierarchyState.isDuplicateOf(state.hierarchyState);
		}
		
		return voiceState.isDuplicateOf(state.voiceState) && beatState.isDuplicateOf(state.beatState) && hierarchyState.isDuplicateOf(state.hierarchyState);
	}
	
	/**
	 * Return if this state is started yet. That is, if it has completed enough to be removed.
	 * 
	 * @return True if it has started, false otherwise.
	 */
	public boolean isStarted() {
		return getBarCount() > 0;
	}

	@Override
	public int compareTo(MidiModelState other) {
		if (!(other instanceof JointModelState)) {
			return 1;
		}
		
		JointModelState o = (JointModelState) other;
		
		// Larger scores first
		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		result = Double.compare(voiceState.getScore(), o.voiceState.getScore());
		if (result != 0) {
			return result;
		}
		
		result = Double.compare(beatState.getScore(), o.beatState.getScore());
		if (result != 0) {
			return result;
		}
		
		result = Double.compare(hierarchyState.getScore(), o.hierarchyState.getScore());
		if (result != 0) {
			return result;
		}
		
		result = voiceState.compareTo(o.voiceState);
		if (result != 0) {
			return result;
		}
		
		result = beatState.compareToNoRecurse(o.beatState);
		if(result != 0) {
			return result;
		}
		
		return hierarchyState.compareToNoRecurse(o.hierarchyState);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("{");
		
		sb.append(voiceState).append(";");
		sb.append(beatState).append(";");
		sb.append(hierarchyState).append("}");
		
		sb.append('=').append(getScore());
		
		return sb.toString();
	}
}
