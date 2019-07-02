package metalign.beat.hmm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import metalign.beat.Beat;
import metalign.beat.BeatTrackingModelState;
import metalign.generic.MidiModelState;
import metalign.hierarchy.Measure;
import metalign.utils.MathUtils;
import metalign.utils.MidiNote;

/**
 * An <code>HmmBeatTrackingModelState</code> is an hmm model which can be used to track
 * the beats of a live MIDI performance.
 * 
 * @author Andrew McLeod - 23 September, 2016
 */
public class HmmBeatTrackingModelState extends BeatTrackingModelState {
	/**
	 * The log probability of this sequence of beats having occurred.
	 */
	private double logProb;
	
	/**
	 * The tatums which we have found so far.
	 */
	private final List<Integer> tatums;
	
	/**
	 * The tempo of the previous bar.
	 */
	private double previousTempo;
	
	/**
	 * The number of full bars we've seen so far.
	 */
	private int barCount;
	
	/**
	 * The parameters for this model.
	 */
	private final HmmBeatTrackingModelParameters params;
	
	/**
	 * A LinkedList of those note times which we haven't assigned to a Beat yet.
	 */
	private final LinkedList<Integer> unusedNoteTimes;
	
	/**
	 * Create a new, empty, HmmBeatTrackingModelState.
	 */
	public HmmBeatTrackingModelState(HmmBeatTrackingModelParameters params) {
		logProb = 0.0;
		tatums = new ArrayList<Integer>();
		unusedNoteTimes = new LinkedList<Integer>();
		previousTempo = 0;
		barCount = 0;
		
		this.params = params;
	}
	
	/**
	 * Create a new state, a copy of the given one.
	 * 
	 * @param state The state whose copy we want.
	 */
	private HmmBeatTrackingModelState(HmmBeatTrackingModelState state) {
		tatums = new ArrayList<Integer>(state.tatums);
		logProb = state.logProb;
		params = state.params;
		unusedNoteTimes = new LinkedList<Integer>(state.unusedNoteTimes);
		previousTempo = state.previousTempo;
		barCount = state.barCount;
		
		setHierarchyState(state.hierarchyState);
		hierarchyState.setBeatState(this);
	}

	@Override
	public TreeSet<HmmBeatTrackingModelState> handleIncoming(List<MidiNote> notes) {
		TreeSet<HmmBeatTrackingModelState> newStates = new TreeSet<HmmBeatTrackingModelState>();
		
		if (notes.isEmpty()) {
			newStates.add(this);
			return newStates;
		}
		
		// Add all notes to unusedNotes
		for (MidiNote note : notes) {
			unusedNoteTimes.add((int) note.getOnsetTime());
		}
		
		if (tatums.isEmpty()) {
			// Special logic for first step
			newStates.addAll(initialStepLogic());
			
		} else if (readyForNewBar()) {
			// Not first step and ready for new bar
			newStates.addAll(addBar());
			
		} else {
			// Not first step, but not ready for new bar yet
			newStates.add(this);
		}
		
		return newStates;
	}

	/**
	 * Perform the first step of beat tracking. That is, create the initial hypothesis, etc.
	 * 
	 * @return The resulting states.
	 */
	private TreeSet<HmmBeatTrackingModelState> initialStepLogic() {
		TreeSet<HmmBeatTrackingModelState> newStates = new TreeSet<HmmBeatTrackingModelState>();
		
		// Calculate variables
		Measure bar = hierarchyState.getMeasure();
		if (bar == null) {
			newStates.add(this);
			return newStates;
		}
		
		int beatsPerBar = bar.getBeatsPerBar();
		int subBeatsPerBeat = bar.getSubBeatsPerBeat();
		int tatumsPerSubBeat = hierarchyState.getSubBeatLength();
		int tatumsPerBeat = tatumsPerSubBeat * subBeatsPerBeat;
		int anacrusisSubBeats = hierarchyState.getAnacrusis();
		int anacrusisTatums = anacrusisSubBeats * tatumsPerSubBeat;
		
		int tatumsUntilDownbeat = anacrusisTatums == 0 ? beatsPerBar * subBeatsPerBeat * tatumsPerSubBeat : anacrusisTatums;
		int subBeatsUntilDownbeat = tatumsUntilDownbeat / tatumsPerSubBeat;
		int beatsUntilDownbeat = subBeatsUntilDownbeat / subBeatsPerBeat;
		
		int subBeatsUntilFirstBeat = subBeatsUntilDownbeat - beatsUntilDownbeat * subBeatsPerBeat;
		int tatumsUntilFirstSubBeat = tatumsUntilDownbeat - subBeatsUntilDownbeat * tatumsPerSubBeat;
		int tatumsUntilFirstBeat = tatumsUntilDownbeat - beatsUntilDownbeat * tatumsPerBeat;
		
		double minTimeUntilDownbeat = params.MINIMUM_TEMPO * tatumsUntilDownbeat / tatumsPerSubBeat / subBeatsPerBeat;
		double maxTimeUntilDownbeat = params.MAXIMUM_TEMPO * tatumsUntilDownbeat / tatumsPerSubBeat / subBeatsPerBeat;
		
		// Note timings
		int firstNoteTime = unusedNoteTimes.get(0);
		
		int secondToLastNoteTime = unusedNoteTimes.getLast();
		Iterator<Integer> noteTimeIterator = unusedNoteTimes.descendingIterator();
		while (noteTimeIterator.hasNext()) {
			int tmpTime = noteTimeIterator.next();
			
			if (tmpTime != secondToLastNoteTime) {
				secondToLastNoteTime = tmpTime;
				break;
			}
		}
		
		int thirdToLastNoteTime = secondToLastNoteTime;
		while (noteTimeIterator.hasNext()) {
			int tmpTime = noteTimeIterator.next();
			
			if (tmpTime != thirdToLastNoteTime) {
				thirdToLastNoteTime = tmpTime;
				break;
			}
		}
		
		int timeDifference = secondToLastNoteTime - firstNoteTime;
		
		// Too short
		if (timeDifference < minTimeUntilDownbeat) {
			newStates.add(this);
			logProb = Math.log(MathUtils.getStandardNormal(0.0, 0.0, params.INITIAL_TEMPO_STD));
			return newStates;
		}
		
		// Too long
		if (timeDifference > maxTimeUntilDownbeat) {
			// Perhaps there is no note on the first downbeat? Try lengths past the 3rd to last note...
			// TODO: Find lengths between 3rd to last note and 2nd to last note.
			
			// Do not add this. We are too far already.
			return newStates;
		}
		
		// Place tatums until second to last note
		double timePerTatum = ((double) timeDifference) / tatumsUntilDownbeat;
		double timePerSubBeat = timePerTatum * tatumsPerSubBeat;
		double timePerBeat = timePerTatum * tatumsPerBeat;
		
		logProb = Math.log(MathUtils.getStandardNormal(timePerBeat, params.INITIAL_TEMPO_MEAN, params.INITIAL_TEMPO_STD));
		newStates.add(this.deepCopy());
		
		// Move beats to any note within 1 sub beat, then nudge
		Set<List<Integer>> beatTimesList = new HashSet<List<Integer>>();
		
		// Evenly spaced beats times
		List<Integer> defaultBeatTimes = new ArrayList<Integer>(beatsUntilDownbeat + 1);
		defaultBeatTimes.add((int) Math.round(firstNoteTime + timePerTatum * tatumsUntilFirstBeat));
		for (int beat = 1; beat <= beatsUntilDownbeat; beat++) {
			defaultBeatTimes.add((int) Math.round(defaultBeatTimes.get(0) + beat * timePerBeat));
		}
		beatTimesList.add(defaultBeatTimes);
		
		Set<List<Integer>> shiftedBeatTimesList = new HashSet<List<Integer>>();
		// We can move the first beat
		if (tatumsUntilFirstBeat != 0) {
			for (int time : getCloseNotes(defaultBeatTimes.get(0), timePerSubBeat)) {
				// Don't want to shift to first note
				if (time == firstNoteTime) {
					continue;
				}
				
				// Shift and add
				for (List<Integer> list : beatTimesList) {
					if (time != list.get(0)) {
						List<Integer> shiftedBeatTimesListTmp = new ArrayList<Integer>(list);
						shiftedBeatTimesListTmp.set(0, time);
						shiftedBeatTimesList.add(shiftedBeatTimesListTmp);
					}
				}
			}
		}
		
		// Add all shifted to final list
		beatTimesList.addAll(shiftedBeatTimesList);
		
		// Move the rest of the beats (except the last one)
		for (int beat = 1; beat < beatsUntilDownbeat; beat++) {
			shiftedBeatTimesList.clear();
			for (int time : getCloseNotes(defaultBeatTimes.get(beat), timePerSubBeat)) {
				// Shift and add
				for (List<Integer> list : beatTimesList) {
					if (time != list.get(beat)) {
						List<Integer> shiftedBeatTimesListTmp = new ArrayList<Integer>(list);
						shiftedBeatTimesListTmp.set(beat, time);
						shiftedBeatTimesList.add(shiftedBeatTimesListTmp);
					}
				}
			}
			beatTimesList.addAll(shiftedBeatTimesList);
		}
		
		// Nudge beats
		Set<List<Integer>> nudgedBeatTimesList = new HashSet<List<Integer>>();
		for (List<Integer> beatTimes : beatTimesList) {
			nudgedBeatTimesList.addAll(nudgeTimes(beatTimes, 0, beatTimes.size(), timePerTatum, params.MAGNETISM_BEAT));
		}
		beatTimesList = new HashSet<List<Integer>>(nudgedBeatTimesList);
		
		List<List<Integer>> subBeatTimesList = new ArrayList<List<Integer>>(beatTimesList.size());
		List<List<Integer>> tatumTimesList = new ArrayList<List<Integer>>(beatTimesList.size());
		
		// Go through each beat placement hypothesis and place sub beats and tatums deterministically
		for (List<Integer> beatTimes : beatTimesList) {
			// Place sub beats
			List<Integer> subBeatTimes = new ArrayList<Integer>(subBeatsUntilDownbeat + 1);
			subBeatTimesList.add(subBeatTimes);
			
			for (int i = 0; i <= subBeatsUntilDownbeat; i++) {
				subBeatTimes.add(-1);
			}
			
			// Add beats into sub beat list
			for (int i = 0; i < beatTimes.size(); i++) {
				subBeatTimes.set(subBeatsUntilFirstBeat + i * subBeatsPerBeat, beatTimes.get(i));
			}
			
			// Add sub beat times
			if (subBeatsUntilFirstBeat > 0) {
				timePerTatum = (beatTimes.get(0) - firstNoteTime) / tatumsUntilFirstBeat;
				timePerSubBeat = timePerTatum * tatumsPerSubBeat;
				
				// Handle until first beat
				for (int i = 1; i <= subBeatsUntilFirstBeat; i++) {
					int time = (int) Math.round(beatTimes.get(0) - timePerSubBeat * i);
					subBeatTimes.set(subBeatsUntilFirstBeat - i, time);
				}
			}
			
			// Handle remaining beats
			for (int i = 0; i < beatsUntilDownbeat; i++) {
				int initialTime = beatTimes.get(i);
				int initialIndex = subBeatsUntilFirstBeat + i * subBeatsPerBeat;
				int finalTime = beatTimes.get(i + 1);
				
				timePerTatum = (finalTime - initialTime) / tatumsPerBeat;
				timePerSubBeat = timePerTatum * tatumsPerSubBeat;
				
				for (int j = 1; j < subBeatsPerBeat; j++) {
					int time = (int) Math.round(initialTime + timePerSubBeat * j);
					subBeatTimes.set(initialIndex + j, time);
				}
			}
		}
		
		Set<List<Integer>> nudgedSubBeatTimesList = new HashSet<List<Integer>>();
		
		// Handle first beat
		if (subBeatsUntilFirstBeat > 0) {
			for (List<Integer> subBeatTimes : subBeatTimesList) {
				nudgedSubBeatTimesList.addAll(nudgeTimes(subBeatTimes, 0, subBeatsUntilFirstBeat, timePerTatum * 2, params.MAGNETISM_SUB_BEAT));
			}
			subBeatTimesList = new ArrayList<List<Integer>>(nudgedSubBeatTimesList);
		}
		
		// Handle remaining beats
		for (int i = 0; i < beatsUntilDownbeat; i++) {
			int initialIndex = subBeatsUntilFirstBeat + i * subBeatsPerBeat;
			nudgedSubBeatTimesList = new HashSet<List<Integer>>();
			
			for (List<Integer> subBeatTimes : subBeatTimesList) {
				nudgedSubBeatTimesList.addAll(nudgeTimes(subBeatTimes, initialIndex + 1, initialIndex + subBeatsPerBeat, timePerTatum * 2, params.MAGNETISM_SUB_BEAT));
			}
			subBeatTimesList = new ArrayList<List<Integer>>(nudgedSubBeatTimesList);
		}
		
		for (List<Integer> subBeatTimes : subBeatTimesList) {
			// Place tatums
			List<Integer> tatumTimes = new ArrayList<Integer>(tatumsUntilDownbeat + 1);
			tatumTimesList.add(tatumTimes);
			for (int i = 0; i <= tatumsUntilDownbeat; i++) {
				tatumTimes.add(-1);
			}
			
			// Add sub beats into tatums list
			for (int i = 0; i < subBeatTimes.size(); i++) {
				tatumTimes.set(tatumsUntilFirstSubBeat + i * tatumsPerSubBeat, subBeatTimes.get(i));
			}
			
			// Add tatum times
			if (tatumsUntilFirstSubBeat > 0) {
				timePerTatum = (subBeatTimes.get(0) - firstNoteTime) / tatumsUntilFirstSubBeat;
				
				// Handle until first sub beat
				for (int i = 1; i <= tatumsUntilFirstSubBeat; i++) {
					int time = (int) Math.round(subBeatTimes.get(0) - timePerTatum * i);
					tatumTimes.set(tatumsUntilFirstSubBeat - i, time);
				}
			}
			
			// Handle remaining sub beats
			for (int i = 0; i < subBeatsUntilDownbeat; i++) {
				int initialTime = subBeatTimes.get(i);
				int initialIndex = tatumsUntilFirstSubBeat + i * tatumsPerSubBeat;
				int finalTime = subBeatTimes.get(i + 1);
				
				timePerTatum = (finalTime - initialTime) / tatumsPerSubBeat;
				
				for (int j = 1; j < tatumsPerSubBeat; j++) {
					int time = (int) Math.round(initialTime + timePerTatum * j);
					tatumTimes.set(initialIndex + j, time);
				}
			}
		}
		
		for (int i = 0; i < tatumTimesList.size(); i++) {
			HmmBeatTrackingModelState newState = this.deepCopy();
			newState.logProb = 0.0;
			
			List<Integer> tatumTimes = tatumTimesList.get(i);
			
			List<Integer> beatTimes = new ArrayList<Integer>(beatsUntilDownbeat + 1);
			for (int j = 0; j <= beatsUntilDownbeat; j++) {
				beatTimes.add(tatumTimes.get(tatumsUntilFirstBeat + j * tatumsPerBeat));
			}
			
			newState.addSpacingProbability(beatTimes);
			
			List<Integer> subBeatTimes = new ArrayList<Integer>(subBeatsUntilDownbeat + 1);
			for (int j = 0; j <= subBeatsUntilDownbeat; j++) {
				subBeatTimes.add(tatumTimes.get(tatumsUntilFirstSubBeat + j * tatumsPerSubBeat));
			}
			
			newState.addSpacingProbability(subBeatTimes.subList(0, subBeatsUntilFirstBeat));
			for (int j = 0; j < beatsUntilDownbeat; j++) {
				int initialIndex = subBeatsUntilFirstBeat + j * subBeatsPerBeat;
				newState.addSpacingProbability(subBeatTimes.subList(initialIndex, initialIndex + subBeatsPerBeat + 1));
			}
			
			newState.addSpacingProbability(tatumTimes.subList(0, tatumsUntilFirstSubBeat));
			for (int j = 0; j < subBeatsUntilDownbeat; j++) {
				int initialIndex = tatumsUntilFirstSubBeat + j * tatumsPerSubBeat;
				newState.addSpacingProbability(tatumTimes.subList(initialIndex, initialIndex + tatumsPerSubBeat + 1));
			}
			
			// Add tatum times into tatums
			for (int j = 0; j < tatumTimes.size(); j++) {
				int time = tatumTimes.get(j);
				newState.tatums.add(time);
			}
			
			// Get note probabilities and removed from unused notes list
			for (int j = 0; j < newState.unusedNoteTimes.size(); j++) {
				int time = newState.unusedNoteTimes.get(j);
				
				if (time < newState.tatums.get(newState.tatums.size() - 1)) {
					newState.addNoteError(time);
					newState.unusedNoteTimes.remove(j);
					j--;
					
				} else {
					break;
				}
			}
			
			timePerTatum = ((double) (tatumTimes.get(tatumTimes.size() - 1) - tatumTimes.get(0))) / (tatumTimes.size() - 1);
			timePerBeat = timePerTatum * tatumsPerBeat;
			newState.previousTempo = timePerBeat;
			
			if (timePerBeat >= params.MINIMUM_TEMPO && timePerBeat <= params.MAXIMUM_TEMPO) {
				newState.logProb += Math.log(MathUtils.getStandardNormal(timePerBeat, params.INITIAL_TEMPO_MEAN, params.INITIAL_TEMPO_STD));
				
				if (beatsUntilDownbeat == beatsPerBar) {
					newState.barCount = 1;
				}
				newStates.add(newState);
			}
		}
		
		// TODO: Place downbeat between 3rd to last note and 2nd to last note
		
		
		return newStates;
	}

	/**
	 * See if a new bar is ready to be added. That is, if we have gone far enough in time
	 * to complete it until the following downbeat.
	 * 
	 * Here, we are guaranteed to have seen at least the first bar already.
	 * 
	 * @return True if we are ready. False otherwise.
	 */
	private boolean readyForNewBar() {
		int beatsPerBar = hierarchyState.getMeasure().getBeatsPerBar();
		
		double nextBarTime = getLastTatumTime() + previousTempo * beatsPerBar;
		
		return nextBarTime + previousTempo < unusedNoteTimes.getLast();
	}
	
	/**
	 * Add tatums to complete the current bar. This method recursively calls {@link #handleIncoming(List)}
	 * with an empty List as input on each newly created state, such that {@link #readyForNewBar()}
	 * should return false in all cases.
	 * 
	 * @return A Set of the states we have reached when adding tatums.
	 */
	private TreeSet<HmmBeatTrackingModelState> addBar() {
		TreeSet<HmmBeatTrackingModelState> newStates = new TreeSet<HmmBeatTrackingModelState>();
		
		Measure bar = hierarchyState.getMeasure();
		int beatsPerBar = bar.getBeatsPerBar();
		int subBeatsPerBeat = bar.getSubBeatsPerBeat();
		int subBeatsPerBar = subBeatsPerBeat * beatsPerBar;
		int tatumsPerSubBeat = hierarchyState.getSubBeatLength();
		int tatumsPerBeat = tatumsPerSubBeat * subBeatsPerBeat;
		int tatumsPerBar = tatumsPerBeat * beatsPerBar;
		double timePerBeat = previousTempo;
		double timePerSubBeat = previousTempo / subBeatsPerBeat;
		double timePerTatum = timePerSubBeat / tatumsPerSubBeat;
		
		// Add times, initially 0
		List<List<Integer>> subBeatTimesList = new ArrayList<List<Integer>>();
		List<List<Integer>> tatumTimesList = new ArrayList<List<Integer>>();
		
		// Move beats to any note within 1 sub beat, then nudge
		Set<List<Integer>> beatTimesList = new HashSet<List<Integer>>();
		
		// Evenly spaced beats times
		List<Integer> defaultBeatTimes = new ArrayList<Integer>(beatsPerBar + 1);
		for (int beat = 0; beat <= beatsPerBar; beat++) {
			defaultBeatTimes.add((int) Math.round(getLastTatumTime() + beat * timePerBeat));
		}
		beatTimesList.add(defaultBeatTimes);
		
		// TODO: Shifting and nudging 1 beat at a time?
		
		// Shift beats to notes within a sub beat away
		Set<List<Integer>> shiftedBeatTimesList = new HashSet<List<Integer>>();
		
		// Move the rest of the beats (except the last one)
		for (int beat = 1; beat <= beatsPerBar; beat++) {
			for (int time : getCloseNotes(defaultBeatTimes.get(beat), timePerSubBeat)) {
				// Shift and add
				for (List<Integer> list : beatTimesList) {
					if (time != list.get(beat)) {
						List<Integer> shiftedBeatTimesListTmp = new ArrayList<Integer>(list);
						shiftedBeatTimesListTmp.set(beat, time);
						shiftedBeatTimesList.add(shiftedBeatTimesListTmp);
					}
				}
			}
		}
		
		// Add all shifted to final list
		beatTimesList.addAll(shiftedBeatTimesList);
		
		// Nudge beats
		Set<List<Integer>> nudgedBeatTimesList = new HashSet<List<Integer>>();
		for (List<Integer> beatTimes : beatTimesList) {
			nudgedBeatTimesList.addAll(nudgeTimes(beatTimes, 1, beatTimes.size(), timePerTatum, params.MAGNETISM_BEAT));
		}
		beatTimesList = new HashSet<List<Integer>>(nudgedBeatTimesList);
		
		// Add sub beats
		for (List<Integer> beatTimes : beatTimesList) {
			// Place sub beats
			List<Integer> subBeatTimes = new ArrayList<Integer>(subBeatsPerBar + 1);
			subBeatTimesList.add(subBeatTimes);
			
			for (int i = 0; i <= subBeatsPerBar; i++) {
				subBeatTimes.add(-1);
			}
			
			// Add beats into sub beat list
			for (int i = 0; i < beatTimes.size(); i++) {
				subBeatTimes.set(i * subBeatsPerBeat, beatTimes.get(i));
			}
			
			// Add sub beat times
			for (int i = 0; i < beatsPerBar; i++) {
				int initialTime = beatTimes.get(i);
				int initialIndex = i * subBeatsPerBeat;
				int finalTime = beatTimes.get(i + 1);
				
				timePerTatum = (finalTime - initialTime) / tatumsPerBeat;
				timePerSubBeat = timePerTatum * tatumsPerSubBeat;
				
				for (int j = 1; j < subBeatsPerBeat; j++) {
					int time = (int) Math.round(initialTime + timePerSubBeat * j);
					subBeatTimes.set(initialIndex + j, time);
				}
			}
		}
		
		// Nudge sub beats
		for (int i = 0; i < beatsPerBar; i++) {
			int initialIndex = i * subBeatsPerBeat;
			Set<List<Integer>> nudgedSubBeatTimesList = new HashSet<List<Integer>>();
			
			for (List<Integer> subBeatTimes : subBeatTimesList) {
				nudgedSubBeatTimesList.addAll(nudgeTimes(subBeatTimes, initialIndex + 1, initialIndex + subBeatsPerBeat, timePerTatum * 2, params.MAGNETISM_SUB_BEAT));
			}
			
			subBeatTimesList = new ArrayList<List<Integer>>(nudgedSubBeatTimesList);
		}
		
		// Add tatums
		for (List<Integer> subBeatTimes : subBeatTimesList) {
			// Place tatums
			List<Integer> tatumTimes = new ArrayList<Integer>(tatumsPerBar + 1);
			tatumTimesList.add(tatumTimes);
			for (int i = 0; i <= tatumsPerBar; i++) {
				tatumTimes.add(-1);
			}
			
			// Add sub beats into tatums list
			for (int i = 0; i < subBeatTimes.size(); i++) {
				tatumTimes.set(i * tatumsPerSubBeat, subBeatTimes.get(i));
			}
			
			// Add tatum times
			for (int i = 0; i < subBeatsPerBar; i++) {
				int initialTime = subBeatTimes.get(i);
				int initialIndex = i * tatumsPerSubBeat;
				int finalTime = subBeatTimes.get(i + 1);
				
				timePerTatum = (finalTime - initialTime) / tatumsPerSubBeat;
				
				for (int j = 1; j < tatumsPerSubBeat; j++) {
					int time = (int) Math.round(initialTime + timePerTatum * j);
					tatumTimes.set(initialIndex + j, time);
				}
			}
		}
		
		// Branch
		for (List<Integer> tatumTimes : tatumTimesList) {
			HmmBeatTrackingModelState newState = this.deepCopy();
			
			// TODO: Set bar and tatum number correctly? Now that we have it...
			// Add tatums into tatums list
			for (int i = 1; i < tatumTimes.size(); i++) {
				int time = tatumTimes.get(i);
				newState.tatums.add(time);
			}
			
			// Get note probabilities and removed from unused notes list
			for (int i = 0; i < newState.unusedNoteTimes.size(); i++) {
				int time = newState.unusedNoteTimes.get(i);
				
				if (time < newState.tatums.get(newState.tatums.size() - 1)) {
					newState.addNoteError(time);
					newState.unusedNoteTimes.remove(i);
					i--;
					
				} else {
					break;
				}
			}
			
			// Beat spacings
			List<Integer> beatTimes = new ArrayList<Integer>(beatsPerBar + 1);
			for (int i = 0; i < beatsPerBar + 1; i++) {
				beatTimes.add(tatumTimes.get(i * tatumsPerBeat));
			}
			newState.addSpacingProbability(beatTimes);
			
			// Sub beat spacings
			for (int beatNum = 0; beatNum < beatsPerBar; beatNum++) {
				List<Integer> subBeatTimes = new ArrayList<Integer>(subBeatsPerBeat + 1);
				for (int i = 0; i < subBeatsPerBeat + 1; i++) {
					subBeatTimes.add(tatumTimes.get(beatNum * tatumsPerBeat + i * tatumsPerSubBeat));
				}
				newState.addSpacingProbability(subBeatTimes);
			}
			
			// Tatum spacings
			for (int beatNum = 0; beatNum < beatsPerBar; beatNum++) {
				for (int subBeatNum = 0; subBeatNum < subBeatsPerBeat; subBeatNum++) {
					newState.addSpacingProbability(tatumTimes.subList(beatNum * tatumsPerBeat + subBeatNum * tatumsPerSubBeat, beatNum * tatumsPerBeat + subBeatNum * tatumsPerSubBeat + tatumsPerSubBeat + 1));
				}
			}
			
			timePerBeat = ((double) (beatTimes.get(beatsPerBar) - beatTimes.get(0))) / beatsPerBar;
			newState.updateTempoProbability(timePerBeat);
			newState.barCount++;
			newStates.add(newState);
		}

		return newStates;
	}

	/**
	 * Update tempo and add tempo probability.
	 * 
	 * @param newTempo The new tempo
	 */
	private void updateTempoProbability(double newTempo) {
		logProb += Math.log(MathUtils.getStandardNormal(0.0, (newTempo - previousTempo) / previousTempo, params.TEMPO_PERCENT_CHANGE_STD));
		
		previousTempo = newTempo;
	}

	/**
	 * Measure the evenness of the given list of times and add that probability into {@link #logProb}.
	 * 
	 * @param times The List of times whose evenness we want to measure.
	 */
	private void addSpacingProbability(List<Integer> times) {
		if (times.size() <= 2) {
			return;
		}
		
		logProb += getSpacingLogProbability(times, params);
	}
	
	/**
	 * Get the log probability of the given spacing times.
	 * 
	 * @param times The times
	 * @param params The parameters
	 * @return The log probability.
	 */
	private static double getSpacingLogProbability(List<Integer> times, HmmBeatTrackingModelParameters params) {
		if (times.size() <= 2) {
			return 0.0;
		}
		
		double std = 0.0;
		
		double sum = 0.0;
		double sumSquared = 0.0;
			
		for (int i = 1; i < times.size(); i++) {
			double element = times.get(i) - times.get(i - 1);
			sum += element;
			sumSquared += element * element;
		}
			
		double mean = sum / (times.size() - 1);
		double variance = sumSquared / (times.size() - 1) - mean * mean;
		std = Math.sqrt(variance);
		
		double percentStd = std / mean;
		double prob = percentStd < params.BEAT_SPACING_MEAN ?
				MathUtils.getStandardNormal(0.0, 0.0, params.BEAT_SPACING_STD) :
					MathUtils.getStandardNormal(params.BEAT_SPACING_MEAN, percentStd, params.BEAT_SPACING_STD);
				
		return Math.log(prob / params.BEAT_SPACING_NORM_FACTOR);
	}

	/**
	 * Nudge the times in the given List towards notes from {@link #unusedNoteTimes}.
	 * 
	 * @param times The List of times we want to nudge.
	 * @param from the first index we want to nudge, inclusive.
	 * @param to The last index we want to nudge, exclusive.
	 * @param timePerTatum The average time per tatum in this bar (before nudging).
	 * @param strength The strength of the nudge. A value from 0.0-1.0, representing the proportion.
	 * of time to move the note.
	 * 
	 * @return The possible resulting times lists, in a List.
	 */
	private List<List<Integer>> nudgeTimes(List<Integer> times, int from, int to, double timePerTatum, double strength) {
		List<List<Integer>> nudgedTimes = new ArrayList<List<Integer>>();
		nudgedTimes.add(new ArrayList<Integer>(times));
		
		// Go through each beat to nudge
		for (int toNudgeIndex = from; toNudgeIndex < to; toNudgeIndex++) {
			List<List<Integer>> newNudgedTimes = new ArrayList<List<Integer>>();
			
			// Go through each previous nudge hypothesis
			for (int prevTimesIndex = 0; prevTimesIndex < nudgedTimes.size(); prevTimesIndex++) {
				List<Integer> possibleNudgedTimes = nudgeTime(nudgedTimes.get(prevTimesIndex).get(toNudgeIndex), timePerTatum, strength);
				
				// Go through each resulting nudge location
				for (int i = 0; i < possibleNudgedTimes.size(); i++) {
					newNudgedTimes.add(new ArrayList<Integer>(nudgedTimes.get(prevTimesIndex)));
					newNudgedTimes.get(newNudgedTimes.size() - 1).set(toNudgeIndex, possibleNudgedTimes.get(i));
				}
			}
			
			nudgedTimes = newNudgedTimes;
		}
		
		return nudgedTimes;
	}

	/**
	 * Get the possible nudged time. That is, the average time of notes close to this beat, either using:
	 * all notes close enough, the closest note, or no notes.
	 * 
	 * @param time The tatum's original time.
	 * @param timePerTatum The time per tatum.
	 * @param strength The strength of the nudge. A value from 0.0-1.0, representing the proportion.
	 * @return The possible nudged times.
	 */
	private List<Integer> nudgeTime(int time, double timePerTatum, double strength) {
		List<Integer> notes = getCloseNotes(time, timePerTatum);
		List<Integer> nudgedTimes = new ArrayList<Integer>();
		nudgedTimes.add(time);
		
		if (notes.isEmpty()) {
			return nudgedTimes;
		}
		
		// Get closest time
		int smallestDiff = Integer.MAX_VALUE;
		for (int i = 0; i < notes.size(); i++) {
			int diff = notes.get(i) - time;
			
			if (Math.abs(diff) < Math.abs(smallestDiff)) {
				smallestDiff = diff;
			}
		}
		int nudgedTime = (int) Math.round(time + smallestDiff * strength);
		if (!nudgedTimes.contains(nudgedTime)) {
			nudgedTimes.add(nudgedTime);
		}
		
		// Get average time of close notes
		if (notes.size() > 1) {
			double avg = notes.get(0);
			for (int j = 1; j < notes.size(); j++) {
				avg += notes.get(j);
			}
			avg /= notes.size();
		
			double diff = avg - time;
			
			if (strength == 0.5) {
				nudgedTimes.clear();
			}
			
			nudgedTime = (int) Math.round(time + diff * strength);
			if (!nudgedTimes.contains(nudgedTime)) {
				nudgedTimes.add(nudgedTime);
			}
		}
		
		return nudgedTimes;
	}

	/**
	 * Get the times of the notes close to a given time.
	 * 
	 * @param time
	 * @param window
	 * @return A List containing the times of notes close in time to this tatum.
	 */
	private List<Integer> getCloseNotes(int time, double window) {
		List<Integer> times = new ArrayList<Integer>();
		for (int noteTime : unusedNoteTimes) {
			if (Math.abs(noteTime - time) <= window / 2) {
				times.add(noteTime);
			}
		}
		
		return times;
	}

	/**
	 * Add the note error to {@link #logProb} for a note at the given time.
	 * 
	 * @param time The time for which to add the note error.
	 */
	private void addNoteError(int time) {
		int closestTatum = getClosestTatumToTime(time, tatums);
		
		logProb += Math.log(MathUtils.getStandardNormal(0, Math.abs(closestTatum - time), params.NOTE_STD));
	}

	/**
	 * Get the tatum which is closest to the given time.
	 * 
	 * @param time The time which we are searching for.
	 * @return The closest tatum time to the given note in time.
	 */
	private static int getClosestTatumToTime(int time, List<Integer> tatums) {
		double minDiff = Double.MAX_VALUE;
		int minIndex = -1;
		
		for (int i = tatums.size() - 1; i >= 0; i--) {
			double diff = Math.abs(tatums.get(i) - time);
			
			if (diff < minDiff) {
				minDiff = diff;
				minIndex = i;
				
			} else {
				return tatums.get(minIndex);
			}
		}
		
		// No tatums found
		return minIndex == -1 ? 0 : tatums.get(minIndex);
	}
	
	@Override
	public int getLastTatumTime() {
		return tatums.isEmpty() ? -1 : tatums.get(tatums.size() - 1);
	}

	@Override
	public TreeSet<HmmBeatTrackingModelState> close() {
		TreeSet<HmmBeatTrackingModelState> newStates = new TreeSet<HmmBeatTrackingModelState>();
		
		if (unusedNoteTimes.isEmpty()) {
			newStates.add(this);
			return newStates;
		}
		
		if (!tatums.isEmpty()) {
			for (HmmBeatTrackingModelState state : addBar()) {
				newStates.addAll(state.close());
			}
		}
		
		return newStates;
	}
	
	@Override
	public HmmBeatTrackingModelState deepCopy() {
		return new HmmBeatTrackingModelState(this);
	}
	
	@Override
	public List<Integer> getBeatTimes() {
		return tatums;
	}
	
	@Override
	public int getNumTatums() {
		return tatums.size();
	}
	
	@Override
	public List<Beat> getBeats() {
		List<Beat> beats = new ArrayList<Beat>(tatums.size());
		
		for (int i = 0; i < tatums.size(); i++) {
			beats.add(new Beat(0, i, tatums.get(i), tatums.get(i)));
		}
		
		return beats;
	}

	@Override
	public double getScore() {
		return logProb;
	}
	
	@Override
	public int getBarCount() {
		return barCount;
	}
	
	@Override
	public boolean isDuplicateOf(BeatTrackingModelState state) {
		if (!(state instanceof HmmBeatTrackingModelState)) {
			return false;
		}
		
		HmmBeatTrackingModelState hmm = (HmmBeatTrackingModelState) state;
		
		return Math.abs(previousTempo - hmm.previousTempo) < params.DIFF_MIN &&
				Math.abs(getLastTatumTime() - hmm.getLastTatumTime()) < params.DIFF_MIN;
	}

	@Override
	public int compareTo(MidiModelState other) {
		if (!(other instanceof HmmBeatTrackingModelState)) {
			return -1;
		}
		
		HmmBeatTrackingModelState o = (HmmBeatTrackingModelState) other;
		
		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		result = tatums.size() - o.tatums.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < tatums.size(); i++) {
			result = tatums.get(i).compareTo(o.tatums.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		result = unusedNoteTimes.size() - o.unusedNoteTimes.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < unusedNoteTimes.size(); i++) {
			result = unusedNoteTimes.get(i).compareTo(o.unusedNoteTimes.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		result = Double.compare(previousTempo, o.previousTempo);
		if (result != 0) {
			return result;
		}
		
		return hierarchyState.compareToNoRecurse(o.hierarchyState);
		//return 0;
	}
	
	@Override
	public int compareToNoRecurse(MidiModelState other) {
		if (!(other instanceof HmmBeatTrackingModelState)) {
			return -1;
		}
		
		HmmBeatTrackingModelState o = (HmmBeatTrackingModelState) other;
		
		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		result = tatums.size() - o.tatums.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < tatums.size(); i++) {
			result = tatums.get(i).compareTo(o.tatums.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		result = unusedNoteTimes.size() - o.unusedNoteTimes.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < unusedNoteTimes.size(); i++) {
			result = unusedNoteTimes.get(i).compareTo(o.unusedNoteTimes.get(i));
			if (result != 0) {
				return result;
			}
		}
		
		result = Double.compare(previousTempo, o.previousTempo);
		if (result != 0) {
			return result;
		}
		
		return 0;
	}
}