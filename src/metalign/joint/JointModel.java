package metalign.joint;

import java.util.ArrayList;
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
	public JointBeam beam;
	
	public Map<VoiceSplittingModelState, List<VoiceSplittingModelState>> newVoiceStates;
	
	public Map<BeatTrackingModelState, Map<List<MidiNote>, TreeSet<BeatTrackingModelState>>> newBeatStates;
	
	private long previousTime = System.currentTimeMillis();
	
	/**
	 * Create a new JointModel based on a state with the given constituent states.
	 * 
	 * @param voice The voice splitting state to use.
	 * @param beat The beat tracking state to use.
	 * @param hierarchy The hierarchy detection state to use.
	 */
	public JointModel(VoiceSplittingModelState voice, BeatTrackingModelState beat, HierarchyModelState hierarchy) {
		beam = new JointBeam(hierarchy.getMeasureTypes());
		beam.add(new JointModelState(this, voice, beat, hierarchy));
	}

	@Override
	public void handleIncoming(List<MidiNote> notes) {
		setGlobalVariables();
		
		if (Main.LOG_STATUS) {
			printLog(notes);
		}
		
		// Branch for each hypothesis state
		for (JointModelState jms : beam.getOrderedStates(true)) {
			for (JointModelState nestedJms : jms.handleIncoming(notes)) {
				beam.add(nestedJms);
			}
			
			beam.fixForBeam();
		}
		
		beam.fixForVoiceBeam();
		
		if (((Main.SUPER_VERBOSE && Main.TESTING) || (MetricalLpcfgGeneratorRunner.VERBOSE && MetricalLpcfgGeneratorRunner.TESTING))) {
			System.out.println(notes + ": ");
			for (JointModelState jms : beam.getOrderedStates(false)) {
				System.out.println(jms.getVoiceState());
				System.out.println(jms.getBeatState());
				System.out.println(jms.getHierarchyState());
				
				if (Main.EVALUATOR != null) {
					System.out.println(Main.EVALUATOR.evaluate(jms));
				}
			}
			System.out.println();
		}
	}
	
	@Override
	public void close() {
		setGlobalVariables();
		
		if (Main.LOG_STATUS) {
			printLog(null);
		}
		
		// Close all states
		for (JointModelState jms : beam.getOrderedStates(true)) {
			for (JointModelState nestedJms : jms.close()) {
				beam.add(nestedJms);
			}
			
			beam.fixForBeam();
		}
		
		beam.fixForVoiceBeam();
	}
	
	/**
	 * Set the global static variables to new blank objects. They are: {@link #newVoiceStates},
	 * {@link #newBeatStates}, and {@link #startedStates}.
	 */
	public void setGlobalVariables() {
		newVoiceStates = new TreeMap<VoiceSplittingModelState, List<VoiceSplittingModelState>>();
		newBeatStates = new TreeMap<BeatTrackingModelState, Map<List<MidiNote>, TreeSet<BeatTrackingModelState>>>();
	}

	/**
	 * Print the logging info for this step.
	 * 
	 * @param notes The notes of the current step, or null if this comes from {@link #close()}.
	 */
	private void printLog(List<MidiNote> notes) {
		long newTime = System.currentTimeMillis();
		long timeDiff = newTime - previousTime;
		System.out.println("Time = " + timeDiff + "ms; Hypotheses = " + beam.totalSize() + "; " + (notes == null ? "Close" : ("Notes = " + notes)));
	}

	@Override
	public TreeSet<JointModelState> getHypotheses() {
		return beam.getOrderedStates(false);
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
		List<VoiceSplittingModelState> voiceHypotheses = new ArrayList<VoiceSplittingModelState>(beam.totalSize());
		
		for (JointModelState jointState : beam.getOrderedStates(false)) {
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
		List<BeatTrackingModelState> beatHypotheses = new ArrayList<BeatTrackingModelState>(beam.totalSize());
		
		for (JointModelState jointState : beam.getOrderedStates(false)) {
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
		List<HierarchyModelState> hierarchyHypotheses = new ArrayList<HierarchyModelState>(beam.totalSize());
		
		for (JointModelState jointState : beam.getOrderedStates(false)) {
			hierarchyHypotheses.add(jointState.getHierarchyState());
		}
		
		return hierarchyHypotheses;
	}
}
