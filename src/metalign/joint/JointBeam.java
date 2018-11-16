package metalign.joint;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeSet;

import metalign.Main;
import metalign.hierarchy.Measure;
import metalign.voice.VoiceSplittingModelState;

public class JointBeam {

	private Map<Measure, TreeSet<JointModelState>> beam;
	private Map<Measure, TreeSet<JointModelState>> startedBeam;
	
	/**
	 * Make a new Joint Beam with the given measure types.
	 * @param measures
	 */
	public JointBeam(Iterable<Measure> measures) {
		beam = new HashMap<Measure, TreeSet<JointModelState>>();
		startedBeam = new HashMap<Measure, TreeSet<JointModelState>>();
		
		for (Measure measure : measures) {
			beam.put(measure, new TreeSet<JointModelState>());
			startedBeam.put(measure, new TreeSet<JointModelState>());
		}
	}
	
	/**
	 * Remove those hypotheses which are outside the top {@link Main#BEAM_SIZE}
	 * started hypotheses for each measure type.
	 */
	public void fixForBeam() {
		if (Main.BEAM_SIZE != -1) {
			
			for (Measure key : startedBeam.keySet()) {
				TreeSet<JointModelState> measure = startedBeam.get(key);
				
				// Remove down to the top beam size started hypotheses.
				while (measure.size() > Main.BEAM_SIZE) {
					measure.pollLast();
				}
				
				// Remove any hypotheses from the full beam outside of the least probable started hypothesis,
				// if the beam is full.
				if (startedBeam.size() == Main.BEAM_SIZE) {
					beam.get(key).tailSet(measure.last(), false).clear();
				}
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
	 * <br>
	 * This method does not fix the started beam (because it is called after we no longer need the started beam).
	 */
	public void fixForVoiceBeam() {
		// Remove those outside of voice beam if we are still at least 2 * normal beam
		if (Main.VOICE_BEAM_SIZE != -1 && Main.BEAM_SIZE != -1 && beam.size() > Main.BEAM_SIZE) {
			TreeSet<VoiceSplittingModelState> voiceStatesSet = new TreeSet<VoiceSplittingModelState>();
			TreeSet<JointModelState> orderedJointStates = new TreeSet<JointModelState>(orderJointStatesByVoiceStateFirst);
			
			// Add all voice states into an ordered list
			for (TreeSet<JointModelState> measure : beam.values()) {
				for (JointModelState jms : measure) {
					voiceStatesSet.add(jms.getVoiceState());
					orderedJointStates.add(jms);
				}
				
				measure.clear();
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
			
			// Move to beam
			for (JointModelState jms : orderedJointStates) {
				addWithoutStarted(jms);
			}
		}
	}
	
	/**
	 * Get the worst score of a started hypothesis of the given measure's beam.
	 * 
	 * @param metricalMeasure The measure type whose beam we want.
	 * @return The worst score of any hypothesis in that beam.
	 */
	public double getWorstScore(Measure measure) {
		try {
			return startedBeam.get(measure).last().getScore();
		} catch (NoSuchElementException e) {
			return Double.NEGATIVE_INFINITY;
		}
	}
	
	/**
	 * Add the given state to the beam.
	 * 
	 * @param state The state to add to the beam.
	 */
	public void addWithoutStarted(JointModelState state) {
		Measure measure = state.getHierarchyState().getMetricalMeasure();
		
		beam.get(measure).add(state);
	}
	
	/**
	 * Add the given state to the beam.
	 * 
	 * @param state The state to add to the beam.
	 */
	public void add(JointModelState state) {
		Measure measure = state.getHierarchyState().getMetricalMeasure();
		
		try {
			beam.get(measure).add(state);
		
			if (state.isStarted()) {
				startedBeam.get(measure).add(state);
			}
		} catch (NullPointerException e) {
			for (TreeSet<JointModelState> set : beam.values()) {
				set.add(state);
				break;
			}
		}
	}
	
	/**
	 * Get all of the states in the beam, ordered by probability (most probable first).
	 * 
	 * @param remove If true, this will clear the beam once it returns the states.
	 * @return The states in the beam, ordered by probability.
	 */
	public TreeSet<JointModelState> getOrderedStates(boolean remove) {
		TreeSet<JointModelState> toReturn = new TreeSet<JointModelState>();
		
		for (TreeSet<JointModelState> measure : beam.values()) {
			toReturn.addAll(measure);
			
			if (remove) {
				measure.clear();
			}
		}
		
		if (remove) {
			for (TreeSet<JointModelState> measure : startedBeam.values()) {
				measure.clear();
			}
		}
		
		return toReturn;
	}
	
	/**
	 * Get the number of hypotheses currently in the given measure's beam.
	 * 
	 * @param The measure we want to check.
	 * @return The number of hypotheses currently in the given measure's beam.
	 */
	public int size(Measure measure) {
		try {
			return beam.get(measure).size();
		} catch (NullPointerException e) {
			return totalSize();
		}
	}
	
	/**
	 * Get the number of hypotheses currently in the beam.
	 * 
	 * @return The number of hypotheses currently in the beam.
	 */
	public int totalSize() {
		int size = 0;
		
		for (TreeSet<JointModelState> measure : beam.values()) {
			size += measure.size();
		}
		
		return size;
	}
}
