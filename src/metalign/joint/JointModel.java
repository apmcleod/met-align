package metalign.joint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import metalign.Main;
import metalign.beat.BeatTrackingModelState;
import metalign.generic.MidiModel;
import metalign.hierarchy.HierarchyModelState;
import metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner;
import metalign.utils.MidiNote;
import metalign.voice.VoiceSplittingModelState;

/**
 * A <code>JointModel</code> is used to model and infer one of each type of model simultaneously as
 * a set, one step at a time.
 * 
 * @author Andrew McLeod - 8 Sept, 2015
 */
public class JointModel extends MidiModel {
	/**
	 * The hypothesis states at the current stage.
	 */
	private TreeSet<JointModelState> hypothesisStates;
	
	/**
	 * Have we started yet? False initially. Set to true the first call to {@link #handleIncoming(List)}.
	 */
	private boolean started;
	
	public Map<VoiceSplittingModelState, List<VoiceSplittingModelState>> newVoiceStates;
	
	public Map<BeatTrackingModelState, Map<List<MidiNote>, TreeSet<BeatTrackingModelState>>> newBeatStates;
	
	public TreeSet<JointModelState> startedStates;
	
	private long previousTime = System.currentTimeMillis();
	
	/**
	 * Create a new JointModel based on a state with the given constituent states.
	 * 
	 * @param voice The voice splitting state to use.
	 * @param beat The beat tracking state to use.
	 * @param hierarchy The hierarchy detection state to use.
	 */
	public JointModel(VoiceSplittingModelState voice, BeatTrackingModelState beat, HierarchyModelState hierarchy) {
		hypothesisStates = new TreeSet<JointModelState>();
		hypothesisStates.add(new JointModelState(this, voice, beat, hierarchy));
		started = false;
	}

	@Override
	public void handleIncoming(List<MidiNote> notes) {
		setGlobalVariables();
		
		if (!started) {
			started = true;
		}
		
		if (Main.LOG_STATUS) {
			printLog(notes);
		}
		
		TreeSet<JointModelState> newStates = new TreeSet<JointModelState>();
		
		// Branch for each hypothesis state
		for (JointModelState jms : hypothesisStates) {
			for (JointModelState nestedJms : jms.handleIncoming(notes)) {
				newStates.add(nestedJms);
				
				if (nestedJms.isStarted()) {
					// New state is started (has a bar)
					startedStates.add(nestedJms);
				}
			}
			
			fixForBeam(newStates);
		}
		
		fixForVoiceBeam(newStates);
		
		if (((Main.SUPER_VERBOSE && Main.TESTING) || (MetricalLpcfgGeneratorRunner.VERBOSE && MetricalLpcfgGeneratorRunner.TESTING))) {
			System.out.println(notes + ": ");
			for (JointModelState jms : newStates) {
				System.out.println(jms.getVoiceState());
				System.out.println(jms.getBeatState());
				System.out.println(jms.getHierarchyState());
				
				if (Main.EVALUATOR != null) {
					System.out.println(Main.EVALUATOR.evaluate(jms));
				}
			}
			System.out.println();
		}
		
		hypothesisStates = newStates;
	}
	
	@Override
	public void close() {
		setGlobalVariables();
		
		if (Main.LOG_STATUS) {
			printLog(null);
		}
		
		TreeSet<JointModelState> newStates = new TreeSet<JointModelState>();
		
		// Close all states
		for (JointModelState jms : hypothesisStates) {
			for (JointModelState nestedJms : jms.close()) {
				newStates.add(nestedJms);
				
				if (nestedJms.isStarted()) {
					// New state is started (has a bar)
					startedStates.add(nestedJms);
				}
			}
			
			fixForBeam(newStates);
		}
		
		fixForVoiceBeam(newStates);
		
		hypothesisStates = newStates;
	}
	
	/**
	 * Set the global static variables to new blank objects. They are: {@link #newVoiceStates},
	 * {@link #newBeatStates}, and {@link #startedStates}.
	 */
	private void setGlobalVariables() {
		newVoiceStates = new TreeMap<VoiceSplittingModelState, List<VoiceSplittingModelState>>();
		newBeatStates = new TreeMap<BeatTrackingModelState, Map<List<MidiNote>, TreeSet<BeatTrackingModelState>>>();
		startedStates = new TreeSet<JointModelState>();
	}

	/**
	 * Print the logging info for this step.
	 * 
	 * @param notes The notes of the current step, or null if this comes from {@link #close()}.
	 */
	private void printLog(List<MidiNote> notes) {
		long newTime = System.currentTimeMillis();
		long timeDiff = newTime - previousTime;
		System.out.println("Time = " + timeDiff + "ms; Hypotheses = " + hypothesisStates.size() + "; " + (notes == null ? "Close" : ("Notes = " + notes)));
	}

	/**
	 * Remove those hypotheses which are outside the top {@link Main#BEAM_SIZE}
	 * finished hypotheses.
	 * 
	 * @param newStates The ordered set of hypotheses.
	 */
	private void fixForBeam(TreeSet<JointModelState> newStates) {
		if (Main.BEAM_SIZE != -1) {
			
			// Remove down to the top beam size finished hypotheses.
			while (startedStates.size() > Main.BEAM_SIZE) {
				startedStates.pollLast();
			}
			
			if (startedStates.size() == Main.BEAM_SIZE) {
				newStates.tailSet(startedStates.last(), false).clear();
			}
		}
	}
	
	/**
	 * Comparator used to remove down to voice beam.
	 */
	private static final Comparator<JointModelState> orderJointStatesByVoiceStateFirst = new Comparator<JointModelState>() {
		@Override
		public int compare(JointModelState o1, JointModelState o2) {
			int result = o1.getVoiceState().compareTo(o2.getVoiceState());
			if (result != 0) {
				return result;
			}
			
			return o1.compareTo(o2);
		}
	};

	/**
	 * Remove those hypotheses outside of the top {@link Main#VOICE_BEAM_SIZE} voiceState scores
	 * which are also outside of the top {@link Main#BEAM_SIZE} overall scores. (Ties are kept in)
	 * 
	 * @param newStates The ordered set of hypotheses.
	 */
	private void fixForVoiceBeam(TreeSet<JointModelState> newStates) {
		// Remove those outside of voice beam if we are still at least 2 * normal beam
		if (Main.VOICE_BEAM_SIZE != -1 && Main.BEAM_SIZE != -1 && newStates.size() > Main.BEAM_SIZE) {
			TreeSet<VoiceSplittingModelState> voiceStatesSet = new TreeSet<VoiceSplittingModelState>();
			TreeSet<JointModelState> orderedJointStates = new TreeSet<JointModelState>(orderJointStatesByVoiceStateFirst);
			
			// Add all voice states into an ordered list
			for (JointModelState jms : newStates) {
				voiceStatesSet.add(jms.getVoiceState());
				orderedJointStates.add(jms);
			}
			
			// No beam necessary
			if (voiceStatesSet.size() <= Main.VOICE_BEAM_SIZE) {
				return;
			}
			
			// Remove down voice states to voice beam size
			while (voiceStatesSet.size() > Main.VOICE_BEAM_SIZE) {
				voiceStatesSet.pollLast();
			}
			
			// Removed down joint states
			while (orderedJointStates.last().getVoiceState() != voiceStatesSet.last() && orderedJointStates.size() > Main.BEAM_SIZE) {
				orderedJointStates.pollLast();
			}
			
			// Move to newStates
			newStates.clear();
			newStates.addAll(orderedJointStates);
		}
	}

	@Override
	public TreeSet<JointModelState> getHypotheses() {
		return hypothesisStates;
	}
	
	/**
	 * Get an ordered List of the {@link VoiceSplittingModelState}s which are currently the top hypotheses
	 * for this joint model. These may not be sorted in order by their own scores, but they are given in
	 * order of the underlying {@link JointModelState}'s scores.
	 * 
	 * @return An ordered List of the {@link VoiceSplittingModelState}s which are currently the top hypotheses
	 * for this joint model.
	 */
	public List<? extends VoiceSplittingModelState> getVoiceHypotheses() {
		List<VoiceSplittingModelState> voiceHypotheses = new ArrayList<VoiceSplittingModelState>(hypothesisStates.size());
		
		for (JointModelState jointState : hypothesisStates) {
			voiceHypotheses.add(jointState.getVoiceState());
		}
		
		return voiceHypotheses;
	}
	
	/**
	 * Get an ordered List of the {@link BeatTrackingModelState}s which are currently the top hypotheses
	 * for this joint model. These may not be sorted in order by their own scores, but they are given in
	 * order of the underlying {@link JointModelState}'s scores.
	 * 
	 * @return An ordered List of the {@link BeatTrackingModelState}s which are currently the top hypotheses
	 * for this joint model.
	 */
	public List<? extends BeatTrackingModelState> getBeatHypotheses() {
		List<BeatTrackingModelState> beatHypotheses = new ArrayList<BeatTrackingModelState>(hypothesisStates.size());
		
		for (JointModelState jointState : hypothesisStates) {
			beatHypotheses.add(jointState.getBeatState());
		}
		
		return beatHypotheses;
	}
	
	/**
	 * Get an ordered List of the {@link HierarchyModelState}s which are currently the top hypotheses
	 * for this joint model. These may not be sorted in order by their own scores, but they are given in
	 * order of the underlying {@link JointModelState}'s scores.
	 * 
	 * @return An ordered List of the {@link HierarchyModelState}s which are currently the top hypotheses
	 * for this joint model.
	 */
	public List<? extends HierarchyModelState> getHierarchyHypotheses() {
		List<HierarchyModelState> hierarchyHypotheses = new ArrayList<HierarchyModelState>(hypothesisStates.size());
		
		for (JointModelState jointState : hypothesisStates) {
			hierarchyHypotheses.add(jointState.getHierarchyState());
		}
		
		return hierarchyHypotheses;
	}
}
