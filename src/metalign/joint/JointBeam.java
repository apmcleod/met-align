package metalign.joint;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.TreeSet;

import metalign.Main;
import metalign.voice.VoiceSplittingModelState;

public class JointBeam {

	private TreeSet<JointModelState> beam;
	private TreeSet<JointModelState> startedBeam;
	
	/**
	 * Make a new Joint Beam with the given measure types.
	 * @param measures
	 */
	public JointBeam() {
		beam = new TreeSet<JointModelState>();
		startedBeam = new TreeSet<JointModelState>();
	}
	
	/**
	 * Remove those hypotheses which are outside the top {@link Main#BEAM_SIZE}
	 * started hypotheses for each measure type.
	 */
	public void fixForBeam() {
		if (Main.BEAM_SIZE != -1) {
			
			// Remove down to the top beam size started hypotheses.
			while (startedBeam.size() > Main.BEAM_SIZE) {
				startedBeam.pollLast();
			}
				
			// Remove any hypotheses from the full beam outside of the least probable started hypothesis,
			// if the beam is full.
			if (startedBeam.size() == Main.BEAM_SIZE) {
				beam.tailSet(startedBeam.last(), false).clear();
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
			for (JointModelState jms : beam) {
				voiceStatesSet.add(jms.getVoiceState());
				orderedJointStates.add(jms);
			}
			beam.clear();
			
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
				beam.add(jms);
			}
		}
	}
	
	/**
	 * Get the worst score of a started hypothesis of the beam.
	 * 
	 * @return The worst score of any hypothesis in the beam. Or, negative infinity if
	 * the started beam is empty.
	 */
	public double getWorstScore() {
		try {
			return startedBeam.last().getScore();
		} catch (NoSuchElementException e) {
			return Double.NEGATIVE_INFINITY;
		}
	}
	
	/**
	 * Add the given state to the beam, with duplicate checking. That is,
	 * if there is a more-likely duplicate in the beam, do not add the new state.
	 * Otherwise, add the new state and remove any less likely duplicates from
	 * the beam.
	 * 
	 * @param state The state to add to the beam.
	 */
	public void add(JointModelState state) {
		// Duplicate checking
		Iterator<JointModelState> beamIterator = beam.iterator();
		
		while (beamIterator.hasNext()) {
			JointModelState jms = beamIterator.next();
			
			if (state.isDuplicateOf(jms)) {
				// Duplicate found
				
				if (state.getScore() <= jms.getScore()) {
					// The new state is less likely than its duplicate
					if (Main.SUPER_VERBOSE && Main.TESTING) {
						System.out.println("ELIMINATING (Duplicate): " + state);
					}
					return;
				}
				
				// The duplicate is less likely than the new state
				if (Main.SUPER_VERBOSE && Main.TESTING) {
					System.out.println("ELIMINATING (Duplicate): " + jms);
				}
				
				// Remove from the beam and the startedBeam
				beamIterator.remove();
				if (jms.isStarted()) {
					startedBeam.remove(jms);
				}
			}
		}
		
		// No more likely duplicate. We want to add the new state.
		beam.add(state);
	
		if (state.isStarted()) {
			startedBeam.add(state);
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
		
		toReturn.addAll(beam);
			
		if (remove) {
			beam.clear();
			startedBeam.clear();
		}
		
		return toReturn;
	}
	
	/**
	 * Get the number of hypotheses currently in the beam.
	 * 
	 * @return The number of hypotheses currently in the beam.
	 */
	public int size() {
		return beam.size();
	}
}
