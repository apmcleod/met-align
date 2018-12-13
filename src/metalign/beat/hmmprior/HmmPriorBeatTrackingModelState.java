package metalign.beat.hmmprior;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import metalign.beat.Beat;
import metalign.beat.BeatTrackingModelState;
import metalign.beat.hmm.HmmBeatTrackingModelParameters;
import metalign.generic.MidiModelState;
import metalign.hierarchy.Measure;
import metalign.utils.MathUtils;
import metalign.utils.MidiNote;

public class HmmPriorBeatTrackingModelState extends BeatTrackingModelState {
	private double downbeatLogProb;
	private double evennessLogProb;
	private double tempoLogProb;
	private double noteLogProb;
	
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
	public final HmmBeatTrackingModelParameters params;
	
	/**
	 * The downbeat priors for this model.
	 */
	private final DownbeatPriors priors;
	
	/**
	 * A LinkedList of those notes which we haven't assigned to a Beat yet.
	 */
	private final LinkedList<MidiNote> unusedNotes;
	
	public HmmPriorBeatTrackingModelState(HmmBeatTrackingModelParameters params, DownbeatPriors priors) {
		downbeatLogProb = 0.0;
		evennessLogProb = 0.0;
		tempoLogProb = 0.0;
		noteLogProb = 0.0;
		
		tatums = new ArrayList<Integer>();
		unusedNotes = new LinkedList<MidiNote>();
		previousTempo = 0;
		barCount = 0;
		
		this.params = params;
		this.priors = priors;
	}
	
	public HmmPriorBeatTrackingModelState(HmmPriorBeatTrackingModelState state) {
		downbeatLogProb = state.downbeatLogProb;
		evennessLogProb = state.evennessLogProb;
		tempoLogProb = state.tempoLogProb;
		noteLogProb = state.noteLogProb;
		
		tatums = new ArrayList<Integer>(state.tatums);
		params = state.params;
		priors = state.priors;
		unusedNotes = new LinkedList<MidiNote>(state.unusedNotes);
		previousTempo = state.previousTempo;
		barCount = state.barCount;
		
		setHierarchyState(state.hierarchyState);
		hierarchyState.setBeatState(this);
	}

	@Override
	public TreeSet<HmmPriorBeatTrackingModelState> handleIncoming(List<MidiNote> notes) {
		TreeSet<HmmPriorBeatTrackingModelState> newStates = new TreeSet<HmmPriorBeatTrackingModelState>();
		
		if (notes.isEmpty()) {
			newStates.add(this);
			return newStates;
		}
		
		// Add all notes to unusedNotes
		for (MidiNote note : notes) {
			unusedNotes.add(note);
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

	@Override
	public TreeSet<HmmPriorBeatTrackingModelState> close() {
		TreeSet<HmmPriorBeatTrackingModelState> newStates = new TreeSet<HmmPriorBeatTrackingModelState>();
		
		if (unusedNotes.isEmpty()) {
			newStates.add(this);
			return newStates;
		}
		
		if (!tatums.isEmpty()) {
			for (HmmPriorBeatTrackingModelState state : addBar()) {
				newStates.addAll(state.close());
			}
		}
		
		return newStates;
	}

	/**
	 * Perform the first step of beat tracking. That is, create the initial hypothesis, etc.
	 * 
	 * @return The resulting states.
	 */
	private TreeSet<HmmPriorBeatTrackingModelState> initialStepLogic() {
		TreeSet<HmmPriorBeatTrackingModelState> newStates = new TreeSet<HmmPriorBeatTrackingModelState>();
		
		// Calculate variables
		Measure bar = hierarchyState.getMetricalMeasure();
		if (bar == null) {
			newStates.add(this);
			return newStates;
		}
		
		int beatsPerBar = bar.getBeatsPerMeasure();
		int subBeatsPerBeat = bar.getSubBeatsPerBeat();
		int tatumsPerSubBeat = hierarchyState.getSubBeatLength();
		int tatumsPerBeat = tatumsPerSubBeat * subBeatsPerBeat;
		int anacrusisSubBeats = hierarchyState.getAnacrusis();
		int anacrusisTatums = anacrusisSubBeats * tatumsPerSubBeat;
		
		int tatumsUntilDownbeat = anacrusisTatums != 0 ? anacrusisTatums :
				beatsPerBar * subBeatsPerBeat * tatumsPerSubBeat;
		int subBeatsUntilDownbeat = tatumsUntilDownbeat / tatumsPerSubBeat;
		int beatsUntilDownbeat = subBeatsUntilDownbeat / subBeatsPerBeat;
		
		int subBeatsUntilFirstBeat = subBeatsUntilDownbeat - beatsUntilDownbeat * subBeatsPerBeat;
		int tatumsUntilFirstSubBeat = tatumsUntilDownbeat - subBeatsUntilDownbeat * tatumsPerSubBeat;
		int tatumsUntilFirstBeat = tatumsUntilDownbeat - beatsUntilDownbeat * tatumsPerBeat;
		
		double minTimeUntilDownbeat = params.MINIMUM_TEMPO * tatumsUntilDownbeat / tatumsPerBeat;
		double maxTimeUntilDownbeat = params.MAXIMUM_TEMPO * tatumsUntilDownbeat / tatumsPerBeat;
		
		// Note timings
		int firstNoteTime = (int) unusedNotes.get(0).getOnsetTime();
		int secondToLastNoteTime = (int) unusedNotes.getLast().getOnsetTime();
		
		Iterator<MidiNote> noteIterator = unusedNotes.descendingIterator();
		while (noteIterator.hasNext()) {
			int tmpTime = (int) noteIterator.next().getOnsetTime();
			
			if (tmpTime != secondToLastNoteTime) {
				secondToLastNoteTime = tmpTime;
				break;
			}
		}
		
		int timeDifference = secondToLastNoteTime - firstNoteTime;
		
		// Too short
		if (timeDifference < minTimeUntilDownbeat) {
			newStates.add(this);
			// We set this probability in case it will drop out of the beam already.
			// This is the maximum possible initial tempo probability
			tempoLogProb = Math.log(MathUtils.getStandardNormal(0.0, 0.0, params.INITIAL_TEMPO_STD));
			return newStates;
		}
		
		// Too long
		if (timeDifference > maxTimeUntilDownbeat) {
			// Do not add this. We are too far already.
			return newStates;
		}
		
		// Place tatums until second to last note
		double timePerTatum = ((double) timeDifference) / tatumsUntilDownbeat;
		double timePerSubBeat = timePerTatum * tatumsPerSubBeat;
		double timePerBeat = timePerTatum * tatumsPerBeat;
		
		// We set this probability in case it will drop out of the beam already.
		// This is its maximum possible initial tempo probability
		tempoLogProb = Math.log(MathUtils.getStandardNormal(
				timePerBeat, params.INITIAL_TEMPO_MEAN, params.INITIAL_TEMPO_STD));
		newStates.add(this.deepCopy());
		
		
		
		// Beats
		Set<List<Integer>> beatTimesList = new HashSet<List<Integer>>();
		
		// Evenly spaced beats times
		List<Integer> evenBeatTimes = new ArrayList<Integer>(beatsUntilDownbeat + 1);
		double firstBeatTime = firstNoteTime + timePerTatum * tatumsUntilFirstBeat;
		for (int beat = 0; beat <= beatsUntilDownbeat; beat++) {
			evenBeatTimes.add((int) Math.round(firstBeatTime + beat * timePerBeat));
		}
		
		// Nudge beats, not including the first beat (if it is on the first note) or the last beat.
		beatTimesList.addAll(nudgeTimes(evenBeatTimes, tatumsUntilFirstBeat == 0 ? 1 : 0,
				evenBeatTimes.size() - 1, timePerTatum, params.MAGNETISM_BEAT));
		
		
		
		// Sub beats
		List<List<Integer>> subBeatTimesList = new ArrayList<List<Integer>>(beatTimesList.size());
		
		// Go through each beat placement hypothesis and place sub beats evenly
		for (List<Integer> beatTimes : beatTimesList) {
			// Place sub beats
			List<Integer> subBeatTimes = new ArrayList<Integer>(subBeatsUntilDownbeat + 1);
			subBeatTimesList.add(subBeatTimes);
			
			// Add filler times (will be overwritten)
			for (int i = 0; i <= subBeatsUntilDownbeat; i++) {
				subBeatTimes.add(-1);
			}
			
			// Add beats into sub beat list
			for (int i = 0; i < beatTimes.size(); i++) {
				subBeatTimes.set(subBeatsUntilFirstBeat + i * subBeatsPerBeat, beatTimes.get(i));
			}
			
			// Add sub beat times from any sub beats before the first beat
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
				
				timePerTatum = ((double) (finalTime - initialTime)) / tatumsPerBeat;
				timePerSubBeat = timePerTatum * tatumsPerSubBeat;
				
				for (int j = 1; j < subBeatsPerBeat; j++) {
					int time = (int) Math.round(initialTime + timePerSubBeat * j);
					subBeatTimes.set(initialIndex + j, time);
				}
			}
		}
		
		// Nudge sub beats
		Set<List<Integer>> nudgedSubBeatTimesList = new HashSet<List<Integer>>();
		
		// Nudge any sub beats before the first beat
		if (subBeatsUntilFirstBeat > 0) {
			for (List<Integer> subBeatTimes : subBeatTimesList) {
				nudgedSubBeatTimesList.addAll(nudgeTimes(
						subBeatTimes, 0, subBeatsUntilFirstBeat, timePerTatum * 2, params.MAGNETISM_SUB_BEAT));
			}
			subBeatTimesList = new ArrayList<List<Integer>>(nudgedSubBeatTimesList);
		}
		
		// Nudge sub beats in remaining beats
		for (int i = 0; i < beatsUntilDownbeat; i++) {
			int initialIndex = subBeatsUntilFirstBeat + i * subBeatsPerBeat;
			nudgedSubBeatTimesList = new HashSet<List<Integer>>();
			
			for (List<Integer> subBeatTimes : subBeatTimesList) {
				nudgedSubBeatTimesList.addAll(nudgeTimes(
						subBeatTimes,initialIndex + 1, initialIndex + subBeatsPerBeat, timePerTatum * 2,
						params.MAGNETISM_SUB_BEAT));
			}
			subBeatTimesList = new ArrayList<List<Integer>>(nudgedSubBeatTimesList);
		}
		
		
		
		// Tatums
		List<List<Integer>> tatumTimesList = new ArrayList<List<Integer>>(beatTimesList.size());
		
		for (List<Integer> subBeatTimes : subBeatTimesList) {
			// Place tatums evenly
			List<Integer> tatumTimes = new ArrayList<Integer>(tatumsUntilDownbeat + 1);
			tatumTimesList.add(tatumTimes);
			
			// Add place-holders (will be overwritten)
			for (int i = 0; i <= tatumsUntilDownbeat; i++) {
				tatumTimes.add(-1);
			}
			
			// Add sub beats into tatums list
			for (int i = 0; i < subBeatTimes.size(); i++) {
				tatumTimes.set(tatumsUntilFirstSubBeat + i * tatumsPerSubBeat, subBeatTimes.get(i));
			}
			
			// Add tatum times if they fall before the first sub beat
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
				
				timePerTatum = ((double) (finalTime - initialTime)) / tatumsPerSubBeat;
				
				for (int j = 1; j < tatumsPerSubBeat; j++) {
					int time = (int) Math.round(initialTime + timePerTatum * j);
					tatumTimes.set(initialIndex + j, time);
				}
			}
		}
		
		
		
		// Create new states
		for (List<Integer> tatumTimes : tatumTimesList) {
			HmmPriorBeatTrackingModelState newState = this.deepCopy();
			
			// Set initial tempo
			timePerTatum = ((double) (tatumTimes.get(tatumTimes.size() - 1) - tatumTimes.get(0))) /
					(tatumTimes.size() - 1);
			timePerBeat = timePerTatum * tatumsPerBeat;
			newState.previousTempo = timePerBeat;
			
			// Ensure within valid range (again)
			if (timePerBeat < params.MINIMUM_TEMPO || timePerBeat > params.MAXIMUM_TEMPO) {
				continue;
			}
			
			// Update tempo probability
			newState.tempoLogProb = Math.log(MathUtils.getStandardNormal(
					timePerBeat, params.INITIAL_TEMPO_MEAN, params.INITIAL_TEMPO_STD));
			
			// Update completed bar count
			if (anacrusisSubBeats == 0) {
				newState.barCount = 1;
			}
			
			// Beat spacing
			List<Integer> beatTimes = new ArrayList<Integer>(beatsUntilDownbeat + 1);
			for (int j = 0; j <= beatsUntilDownbeat; j++) {
				beatTimes.add(tatumTimes.get(tatumsUntilFirstBeat + j * tatumsPerBeat));
			}
			newState.updateSpacingProbability(beatTimes);
			
			// Sub beat spacing
			List<Integer> subBeatTimes = new ArrayList<Integer>(subBeatsUntilDownbeat + 1);
			for (int j = 0; j <= subBeatsUntilDownbeat; j++) {
				subBeatTimes.add(tatumTimes.get(tatumsUntilFirstSubBeat + j * tatumsPerSubBeat));
			}
			
			// Any pickup sub beats before the first beat
			newState.updateSpacingProbability(subBeatTimes.subList(0, subBeatsUntilFirstBeat));
			
			// Additional sub beats
			for (int j = 0; j < beatsUntilDownbeat; j++) {
				int initialIndex = subBeatsUntilFirstBeat + j * subBeatsPerBeat;
				newState.updateSpacingProbability(subBeatTimes.subList(
						initialIndex, initialIndex + subBeatsPerBeat + 1));
			}
			
			// Tatum spacing
			// Any tatum before the first sub beat
			newState.updateSpacingProbability(tatumTimes.subList(0, tatumsUntilFirstSubBeat));
			
			// ADditional tatums
			for (int j = 0; j < subBeatsUntilDownbeat; j++) {
				int initialIndex = tatumsUntilFirstSubBeat + j * tatumsPerSubBeat;
				newState.updateSpacingProbability(tatumTimes.subList(
						initialIndex, initialIndex + tatumsPerSubBeat + 1));
			}
			
			// Add tatum times into tatums
			newState.tatums.addAll(tatumTimes);
			
			// Update probabilities
			if (anacrusisSubBeats == 0) {
				newState.updateDownbeatProbability(tatumTimes.get(0));
			}
			newState.updateDownbeatProbability(tatumTimes.get(tatumTimes.size() - 1));
			
			// Get note probabilities and removed from unused notes list
			noteIterator = newState.unusedNotes.iterator();
			while (noteIterator.hasNext()) {
				int time = (int) noteIterator.next().getOnsetTime();
				
				if (time < newState.tatums.get(newState.tatums.size() - 1)) {
					newState.updateNoteProbability(time);
					noteIterator.remove();
					
				} else {
					break;
				}
			}
			
			// Add new state into tracking tree
			newStates.add(newState);
		}
		
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
		double nextBarTime = getLastTatumTime() + previousTempo * hierarchyState.getMetricalMeasure().getBeatsPerMeasure();
		
		return nextBarTime + previousTempo < unusedNotes.getLast().getOnsetTime();
	}
	
	/**
	 * Add tatums to complete the current bar. This method recursively calls {@link #handleIncoming(List)}
	 * with an empty List as input on each newly created state, such that {@link #readyForNewBar()}
	 * should return false in all cases.
	 * 
	 * @return A Set of the states we have reached when adding tatums.
	 */
	private TreeSet<HmmPriorBeatTrackingModelState> addBar() {
		// Set up useful variables
		Measure bar = hierarchyState.getMetricalMeasure();
		int beatsPerBar = bar.getBeatsPerMeasure();
		double timePerTatum = previousTempo / bar.getSubBeatsPerBeat() / hierarchyState.getSubBeatLength();
		
		// Calculate previous and next downbeat times
		int lastTatumTime = getLastTatumTime();
		double estimatedDownbeatTime = getLastTatumTime() + beatsPerBar * previousTempo;
		
		
		// Branch
		TreeSet<HmmPriorBeatTrackingModelState> newStates = new TreeSet<HmmPriorBeatTrackingModelState>();
		
		for (int downbeatTime : getPossibleDownbeatTimes(estimatedDownbeatTime, previousTempo, timePerTatum)) {
			for (List<Integer> times : getNudgedTimes(lastTatumTime, downbeatTime)) {
				HmmPriorBeatTrackingModelState newState = this.deepCopy();
				newState.addBar(times);
				newStates.add(newState);
			}
		}

		return newStates;
	}

	/**
	 * Get all of the possible nudged times of the given base downbeat times.
	 * 
	 * @param prev The time of the downbeat of the previous bar.
	 * @param next The time of the downbeat of the upcoming bar.
	 * 
	 * @return A List of all possible times given the anchor downbeat times, which
	 * will not change.
	 */
	private List<List<Integer>> getNudgedTimes(int prev, int next) {
		// Set up useful variables
		Measure bar = hierarchyState.getMetricalMeasure();
		int beatsPerBar = bar.getBeatsPerMeasure();
		int subBeatsPerBeat = bar.getSubBeatsPerBeat();
		int tatumsPerSubBeat = hierarchyState.getSubBeatLength();
		int tatumsPerBar = tatumsPerSubBeat * subBeatsPerBeat * beatsPerBar;
		
		// Add even beats
		List<Integer> evenBeats = getEvenTimes(prev, next, beatsPerBar);
		
		// Nudge beats
		double nudgeWindow = ((double) (next - prev)) / tatumsPerBar;
		Set<List<Integer>> nudgedBeats = new HashSet<List<Integer>>(
				nudgeTimes(evenBeats, 1, beatsPerBar, nudgeWindow, params.MAGNETISM_BEAT));
		
		// Nudge sub beats
		Set<List<Integer>> nudgedSubBeats = new HashSet<List<Integer>>();
		for (List<Integer> beats : nudgedBeats) {
			nudgedSubBeats.addAll(getNextDivisionNudgedTimes(
					beats, subBeatsPerBeat, nudgeWindow, params.MAGNETISM_SUB_BEAT));
		}
		
		// For each sub beat
		List<List<Integer>> tatums = new ArrayList<List<Integer>>();
		for (List<Integer> subBeats : nudgedSubBeats) {
			// Add even tatums
			tatums.add(getEvenTimes(subBeats, tatumsPerSubBeat));
		}
		
		return tatums;
	}

	/**
	 * Get a collection of all of the possible nudged times of the next division down the hierarchy.
	 * 
	 * @param times The times of the pulses at some level of the hierarchy tree.
	 * @param divisions The number of divisions to make between each element in times.
	 * @param nudgeWindow The window width within which to nudge.
	 * @param magnetism The magnetism to use to nudge.
	 * 
	 * @return A Collection of every possible List of times at the next level down the hierarchy
	 * from the given times. Each List in the returned Collection will contain the values from
	 * the given times List, with (divisions - 1) extra times between each of them.
	 */
	private Collection<List<Integer>> getNextDivisionNudgedTimes(List<Integer> times, int divisions,
			double nudgeWindow, double magnetism) {
		
		// All of the possible nudged locations of the current times
		List<List<Integer>> nudgedTimes = new ArrayList<List<Integer>>();
		nudgedTimes.add(getEvenTimes(times, divisions));
		
		// Nudge within each existing boundary
		for (int i = 1; i < times.size(); i++) {
			int initialIndex = (i - 1) * divisions;
			
			// Stores every possible version of times, nudged between the boundaries
			Set<List<Integer>> nudgedTimesTmp = new HashSet<List<Integer>>();
			
			for (List<Integer> subBeats : nudgedTimes) {
				nudgedTimesTmp.addAll(nudgeTimes(
						subBeats, initialIndex + 1, initialIndex + divisions, nudgeWindow, magnetism));
			}
			
			// Save each beat into the current List, to iterate through next beat
			nudgedTimes = new ArrayList<List<Integer>>(nudgedTimesTmp);
		}
		
		return nudgedTimes;
	}

	/**
	 * Get evenly-spaced times, the next level down from the given times.
	 * 
	 * @param times The times of the pulses at some level of the hierarchy tree.
	 * @param divisions The number of evenly-spaced divisions to make between each element in times.
	 *  
	 * @return A List of pulses at the next level down the hierarchy tree, where each value from
	 * the given times List is interspersed with (divisions - 1) evenly-spaced times.
	 */
	private List<Integer> getEvenTimes(List<Integer> times, int divisions) {
		List<Integer> evenTimes = new ArrayList<Integer>((times.size() - 1) * divisions + 1);
		
		// Go through each set of boundaries
		for (int i = 1; i < times.size(); i++) {
			evenTimes.addAll(getEvenTimes(times.get(i - 1), times.get(i), divisions));
			
			// Remove the last beat added (it will be added in the next loop)
			evenTimes.remove(evenTimes.size() - 1);
		}
		// Add the final boundary
		evenTimes.add(times.get(times.size() - 1));
		
		return evenTimes;
	}

	/**
	 * Get a List of evenly-spaced times between the given first and last boundaries.
	 * 
	 * @param first The first boundary time. The first element in the returned List.
	 * @param last The last boundary time. The last element in the returned List.
	 * @param divisions The number of divisions to add into the returned List.
	 * 
	 * @return A List of evenly-spaced times. First, then (divisions - 1) evenly-spaced
	 * times, then last.
	 */
	private List<Integer> getEvenTimes(int first, int last, int divisions) {
		List<Integer> evenTimes = new ArrayList<Integer>(divisions + 1);
		
		evenTimes.add(first);
		double diff = ((double) (last - first)) / divisions;
		for (int i = 1; i < divisions; i++) {
			evenTimes.add((int) Math.round(first + i * diff));
		}
		evenTimes.add(last);
		
		return evenTimes;
	}

	/**
	 * Get a Set (so no duplicates) of all of the possible downbeat times within the given window.
	 * For the given time, as well as each onset time of a note from {@link #unusedNotes} whose
	 * onset lies within the given window size from the given time, we first get all possible nudges
	 * from that time, using {@link #nudgeTime(int, double, double)}, with BEAT_MAGNETISM as
	 * the magnetism, and nudgeWindow as the nudge window.
	 * 
	 * @param time The initial downbeat time. Returned values will be centered around this time.
	 * @param window The maximum distance from the given time where a return should be.
	 * @param nudgeWindow The window size to nudge any guesses.
	 * 
	 * @return A Set of all of the possible downbeat times with the given settings.
	 */
	private Set<Integer> getPossibleDownbeatTimes(double time, double window, double nudgeWindow) {
		Set<Integer> times = new HashSet<Integer>();
		times.addAll(nudgeTime((int) Math.round(time), nudgeWindow, params.MAGNETISM_BEAT));
		
		for (MidiNote note : unusedNotes) {
			if (Math.abs(note.getOnsetTime() - time) <= window) {
				times.addAll(nudgeTime((int) note.getOnsetTime(), nudgeWindow, params.MAGNETISM_BEAT));
			}
		}
		
		return times;
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
				Set<Integer> possibleNudgedTimes = nudgeTime(nudgedTimes.get(prevTimesIndex).get(toNudgeIndex), timePerTatum, strength);
				
				// Go through each resulting nudge location
				for (int time : possibleNudgedTimes) {
					newNudgedTimes.add(new ArrayList<Integer>(nudgedTimes.get(prevTimesIndex)));
					newNudgedTimes.get(newNudgedTimes.size() - 1).set(toNudgeIndex, time);
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
	 * @param window The time per tatum.
	 * @param strength The strength of the nudge. A value from 0.0-1.0, representing the proportion.
	 * @return The possible nudged times.
	 */
	private Set<Integer> nudgeTime(int time, double window, double strength) {
		List<Integer> notes = getCloseNotes(time, window);
		Set<Integer> nudgedTimes = new HashSet<Integer>();
		nudgedTimes.add(time);
		
		if (notes.isEmpty()) {
			return nudgedTimes;
		}
		
		// Nudge to each note within the window
		// Get closest time
		int smallestDiff = Integer.MAX_VALUE;
		for (int noteTime : notes) {
			int diff = noteTime - time;
			
			if (Math.abs(diff) < Math.abs(smallestDiff)) {
				smallestDiff = diff;
			}
		}
		int nudgedTime = (int) Math.round(time + smallestDiff * strength);
		nudgedTimes.add(nudgedTime);
		
		
		// Get average time of close notes
		if (notes.size() > 1) {
			double avg = notes.get(0);
			for (int j = 1; j < notes.size(); j++) {
				avg += notes.get(j);
			}
			avg /= notes.size();
		
			double diff = avg - time;
			
			if (strength == params.MAGNETISM_SUB_BEAT) {
				nudgedTimes.clear();
			}
			
			nudgedTimes.add((int) Math.round(time + diff * strength));
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
		for (MidiNote note : unusedNotes) {
			if (Math.abs(note.getOnsetTime() - time) <= window / 2) {
				times.add((int) note.getOnsetTime());
			}
		}
		
		return times;
	}

	/**
	 * Add a list of tatums as a new bar. This is used for all bars EXCEPT the initial one.
	 * This method also calculates all probabilities and adds them to this state's {@link #logProb}.
	 * 
	 * @param tatumTimes The tatums of the new bar which we will add to {@link #tatums}.
	 */
	private void addBar(List<Integer> tatumTimes) {
		// Helpful variables
		Measure bar = hierarchyState.getMetricalMeasure();
		int beatsPerBar = bar.getBeatsPerMeasure();
		int subBeatsPerBeat = bar.getSubBeatsPerBeat();
		int tatumsPerSubBeat = hierarchyState.getSubBeatLength();
		int tatumsPerBeat = tatumsPerSubBeat * subBeatsPerBeat;
		
		// Add tatums into tatums list
		tatums.addAll(tatumTimes.subList(1, tatumTimes.size()));
		
		// Get note probabilities and removed from unused notes list
		Iterator<MidiNote> noteIterator = unusedNotes.iterator();
		while (noteIterator.hasNext()) {
			int time = (int) noteIterator.next().getOnsetTime();
			
			if (time < tatums.get(tatums.size() - 1)) {
				updateNoteProbability(time);
				noteIterator.remove();
				
			} else {
				break;
			}
		}
		
		// Beat spacings
		List<Integer> beatTimes = new ArrayList<Integer>(beatsPerBar + 1);
		for (int i = 0; i < beatsPerBar + 1; i++) {
			beatTimes.add(tatumTimes.get(i * tatumsPerBeat));
		}
		updateSpacingProbability(beatTimes);
		
		// Sub beat spacings
		for (int beatNum = 0; beatNum < beatsPerBar; beatNum++) {
			List<Integer> subBeatTimes = new ArrayList<Integer>(subBeatsPerBeat + 1);
			for (int i = 0; i < subBeatsPerBeat + 1; i++) {
				subBeatTimes.add(tatumTimes.get(beatNum * tatumsPerBeat + i * tatumsPerSubBeat));
			}
			updateSpacingProbability(subBeatTimes);
		}
		
		// Tatum spacings
		for (int beatNum = 0; beatNum < beatsPerBar; beatNum++) {
			for (int subBeatNum = 0; subBeatNum < subBeatsPerBeat; subBeatNum++) {
				updateSpacingProbability(tatumTimes.subList(beatNum * tatumsPerBeat + subBeatNum * tatumsPerSubBeat, beatNum * tatumsPerBeat + subBeatNum * tatumsPerSubBeat + tatumsPerSubBeat + 1));
			}
		}
		
		// Update downbeat and tempo probabilities
		updateDownbeatProbability(tatumTimes.get(tatumTimes.size() - 1));
		updateTempoProbability(((double) (beatTimes.get(beatsPerBar) - beatTimes.get(0))) / beatsPerBar);
		barCount++;
	}

	/**
	 * Get the probability of a downbeat at the given time, using {@link #priors}.
	 * 
	 * @param time The time whose downbeat probability we want.
	 */
	private void updateDownbeatProbability(int time) {
		if (priors == null) {
			return;
		}
		
		double maxProb = Double.NEGATIVE_INFINITY;
		
		for (MidiNote note : unusedNotes) {
			if (Math.abs(note.getOnsetTime() - time) < 35000) {
				maxProb = Math.max(maxProb, priors.getPrior(note));
			}
		}
		
		downbeatLogProb += Math.log(maxProb == Double.NEGATIVE_INFINITY ? priors.getRestPrior() : maxProb);
	}

	/**
	 * Update tempo and add tempo probability.
	 * 
	 * @param newTempo The new tempo
	 */
	private void updateTempoProbability(double newTempo) {
		tempoLogProb += Math.log(MathUtils.getStandardNormal(0.0, (newTempo - previousTempo) / previousTempo, params.TEMPO_PERCENT_CHANGE_STD));
		
		previousTempo = newTempo;
	}

	/**
	 * Measure the evenness of the given list of times and add that probability into {@link #logProb}.
	 * 
	 * @param times The List of times whose evenness we want to measure.
	 */
	private void updateSpacingProbability(List<Integer> times) {
		if (times.size() <= 2) {
			return;
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
				
		evennessLogProb += Math.log(prob / params.BEAT_SPACING_NORM_FACTOR);
	}
	
	/**
	 * Add the note error to {@link #logProb} for a note at the given time.
	 * 
	 * @param time The time for which to add the note error.
	 */
	private void updateNoteProbability(int time) {
		int closestTatum = getClosestTatumToTime(time, tatums);
		
		noteLogProb += Math.log(MathUtils.getStandardNormal(0, Math.abs(closestTatum - time), params.NOTE_STD));
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
	public List<Beat> getBeats() {
		List<Beat> beats = new ArrayList<Beat>(tatums.size());
		
		for (int i = 0; i < tatums.size(); i++) {
			beats.add(new Beat(0, i, tatums.get(i), tatums.get(i)));
		}
		
		return beats;
	}

	@Override
	public List<Integer> getBeatTimes() {
		return tatums;
	}

	@Override
	public int getLastTatumTime() {
		return tatums.isEmpty() ? -1 : tatums.get(tatums.size() - 1);
	}

	@Override
	public int getNumTatums() {
		return tatums.size();
	}

	@Override
	public int getBarCount() {
		return barCount;
	}

	@Override
	public boolean isDuplicateOf(BeatTrackingModelState state) {
		if (!(state instanceof HmmPriorBeatTrackingModelState)) {
			return false;
		}
		
		HmmPriorBeatTrackingModelState hmm = (HmmPriorBeatTrackingModelState) state;
		
		return Math.abs(previousTempo - hmm.previousTempo) < params.DIFF_MIN &&
				Math.abs(getLastTatumTime() - hmm.getLastTatumTime()) < params.DIFF_MIN;
	}

	@Override
	public HmmPriorBeatTrackingModelState deepCopy() {
		return new HmmPriorBeatTrackingModelState(this);
	}
	
	@Override
	public int compareTo(MidiModelState other) {
		int result = Double.compare(other.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		if (!(other instanceof HmmPriorBeatTrackingModelState)) {
			return -1;
		}
		
		HmmPriorBeatTrackingModelState o = (HmmPriorBeatTrackingModelState) other;
		
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
		
		result = unusedNotes.size() - o.unusedNotes.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < unusedNotes.size(); i++) {
			result = unusedNotes.get(i).compareTo(o.unusedNotes.get(i));
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
		int result = Double.compare(other.getScore(), getScore());
		if (result != 0) {
			return result;
		}
		
		if (!(other instanceof HmmPriorBeatTrackingModelState)) {
			return -1;
		}
		
		HmmPriorBeatTrackingModelState o = (HmmPriorBeatTrackingModelState) other;
		
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
		
		result = unusedNotes.size() - o.unusedNotes.size();
		if (result != 0) {
			return result;
		}
		
		for (int i = 0; i < unusedNotes.size(); i++) {
			result = unusedNotes.get(i).compareTo(o.unusedNotes.get(i));
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

	@Override
	public double getScore() {
		return tempoLogProb + evennessLogProb + downbeatLogProb + noteLogProb;
	}
	
	@Override
	public String getScoreString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append(" Tempo=").append(tempoLogProb);
		sb.append(" Evenness=").append(evennessLogProb);
		sb.append(" Downbeat=").append(downbeatLogProb);
		sb.append(" Note=").append(noteLogProb);
		
		return sb.toString();
	}

	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		
		for (Beat beat : getBeats()) {
			sb.append(beat).append(',');
		}
		
		sb.setCharAt(sb.length() - 1, ']');
		
		sb.append(" Tempo=").append(tempoLogProb);
		sb.append(" Evenness=").append(evennessLogProb);
		sb.append(" Downbeat=").append(downbeatLogProb);
		sb.append(" Note=").append(noteLogProb);
		return sb.toString();
	}
}
