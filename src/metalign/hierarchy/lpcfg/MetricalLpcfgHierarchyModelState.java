package metalign.hierarchy.lpcfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import metalign.Main;
import metalign.generic.MidiModelState;
import metalign.hierarchy.HierarchyModelState;
import metalign.hierarchy.Measure;
import metalign.utils.MidiNote;
import metalign.voice.Voice;

/**
 * A <code>GrammarHierarchyModelState</code> is used to generate hierarchy hypotheses
 * using a grammar.
 *
 * @author Andrew McLeod - 29 February, 2016
 */
public class MetricalLpcfgHierarchyModelState extends HierarchyModelState {

	/**
	 * A <code>MetricalLpcfgMatch</code> is an enum indicating which type of match a
	 * {@link MetricalLpcfgHierarchyModelState} has matched.
	 *
	 * @author Andrew McLeod - 12 May, 2016
	 */
	public enum MetricalLpcfgMatch {
		/**
		 * A sub beat level match.
		 */
		SUB_BEAT,

		/**
		 * A beat level match.
		 */
		BEAT,

		/**
		 * A wrong match.
		 */
		WRONG;
	}

	public static double LOCAL_WEIGHT = 0.5;

	/**
	 * Object to save tree log probabilities so as not to regenerate every time.
	 */
	public static List<List<Map<List<MetricalLpcfgQuantum>, Double>>> treeMap = new ArrayList<List<Map<List<MetricalLpcfgQuantum>, Double>>>();


	/**
	 * The length of the terminals which we are looking for.
	 */
	private final int subBeatLength;

	/**
	 * The length of the anacrusis in this state, measured in quantums.
	 */
	private final int anacrusisLength;

	/**
	 * The measure type we are looking for.
	 */
	private final Measure measure;

	/**
	 * The grammar object we will use for probabilities.
	 */
	private final MetricalLpcfg grammar;

	/**
	 * The local grammar for this hierarchy model state.
	 */
	private final MetricalLpcfg localGrammar;

	/**
	 * The log probability of this hypothesis.
	 */
	private double logProbability;

	/**
	 * The log probability of this hypothesis from its local grammar.
	 */
	private double localLogProb;

	/**
	 * The measure number of the next subbeat to be shifted onto the stack.
	 */
	private int measureNum;

	/**
	 * The number of measures we have used in this model.
	 */
	private int measuresUsed;

	/**
	 * The index of the first beat of the next measure.
	 */
	private int nextMeasureIndex;

	/**
	 * A List of whether each given voice has begun yet. This is used to throw out anacrusis measures.
	 */
	private final List<Boolean> hasBegun;

	/**
	 * A List of any notes which have not yet been completely added to a Tree, divided by voice.
	 */
	private final List<List<MidiNote>> unfinishedNotes;

	/**
	 * Notes which we have yet to check for beat and sub beat matches.
	 */
	private final List<List<MidiNote>> notesToCheck;

	/**
	 * A List of a Queue per voice of notes to check for beat matching.
	 */
	private final List<List<MidiNote>> notesToCheckBeats;

	/**
	 * The number of times a sub beat match has been found.
	 */
	private int subBeatMatches;

	/**
	 * The number of times a beat match has been found.
	 */
	private int beatMatches;

	/**
	 * The number of times a mismatch has been found.
	 */
	private int wrongMatches;

	/**
	 * Create a new state based on the given grammar. The {@link #measure} and {@link #subBeatLength} fields
	 * will be null and 0 respectively, and appropriate settings for these will be returned by the first call
	 * to {@link #handleIncoming(List)}. This allows all possible states to be generated by a single one without
	 * having to manually create each one each time.
	 *
	 * @param grammar {@link #grammar}
	 */
	public MetricalLpcfgHierarchyModelState(MetricalLpcfg grammar) {
		subBeatLength = 0;
		anacrusisLength = 0;
		measure = null;
		this.grammar = grammar;

		subBeatMatches = Main.USE_CONGRUENCE ? 0 : 1;
		beatMatches = Main.USE_CONGRUENCE ? 0 : 1;;
		wrongMatches = 0;

		localGrammar = new MetricalLpcfg();

		logProbability = 0.0;
		localLogProb = 0.0;

		measureNum = 0;
		nextMeasureIndex = 0;
		measuresUsed = 0;

		unfinishedNotes = new ArrayList<List<MidiNote>>();
		notesToCheck = new ArrayList<List<MidiNote>>();
		notesToCheckBeats = new ArrayList<List<MidiNote>>();
		hasBegun = new ArrayList<Boolean>();
	}

	/**
	 * Create a new state based on the given grammar, measure, and terminal length, and as a partial
	 * deep copy of the given state.
	 *
	 * @param state The old state we want this new one to be a deep copy of.
	 * @param grammar {@link #grammar}
	 * @param measure {@link #measure}
	 * @param terminalLength {@link #subBeatLength}
	 * @param anacrusisLength {@link #anacrusisLength}
	 */
	public MetricalLpcfgHierarchyModelState(MetricalLpcfgHierarchyModelState state, MetricalLpcfg grammar, Measure measure, int terminalLength, int anacrusisLength) {
		this.subBeatLength = terminalLength;
		this.anacrusisLength = anacrusisLength;
		this.measure = measure;
		this.grammar = grammar;

		subBeatMatches = state.subBeatMatches;
		beatMatches = state.beatMatches;
		wrongMatches = state.wrongMatches;

		logProbability = state.logProbability;
		localLogProb = state.localLogProb;

		measureNum = state.measureNum;
		measuresUsed = state.measuresUsed;
		nextMeasureIndex = state.nextMeasureIndex;
		if (measure != null && nextMeasureIndex == 0) {
			if (anacrusisLength != 0) {
				nextMeasureIndex = anacrusisLength * subBeatLength;
				measureNum = -1;

			} else {
				nextMeasureIndex = subBeatLength * measure.getBeatsPerMeasure() * measure.getSubBeatsPerBeat();
			}
		}

		unfinishedNotes = new ArrayList<List<MidiNote>>();
		for (List<MidiNote> voice : state.unfinishedNotes) {
			unfinishedNotes.add(new ArrayList<MidiNote>(voice));
		}

		notesToCheckBeats = new ArrayList<List<MidiNote>>();
		for (List<MidiNote> voice : state.notesToCheckBeats) {
			notesToCheckBeats.add(new ArrayList<MidiNote>(voice));
		}

		notesToCheck = new ArrayList<List<MidiNote>>();
		for (List<MidiNote> voice : state.notesToCheck) {
			notesToCheck.add(new ArrayList<MidiNote>(voice));
		}

		hasBegun = new ArrayList<Boolean>(state.hasBegun);

		localGrammar = state.localGrammar.shallowCopy();

		setVoiceState(state.voiceState);
		setBeatState(state.beatState.deepCopy());
		beatState.setHierarchyState(this);
	}

	/**
	 * Create a new state which is a deep copy of the given one (when necessary).
	 *
	 * @param state The state whose deep copy we want.
	 */
	private MetricalLpcfgHierarchyModelState(MetricalLpcfgHierarchyModelState state) {
		this(state, state.grammar, state.measure, state.subBeatLength, state.anacrusisLength);
	}

	/**
	 * Add a new voice at the given index into our tracking lists. Used in case a note
	 * from a new voice is removed by the trill optimisation.
	 *
	 * @param index
	 */
	public void addNewVoiceIndex(int index) {
		hasBegun.add(index, Boolean.FALSE);
		unfinishedNotes.add(index, new ArrayList<MidiNote>());

		if (!matches(MetricalLpcfgMatch.BEAT)) {
			notesToCheckBeats.add(index, new ArrayList<MidiNote>());
		}

		if (!isFullyMatched()) {
			notesToCheck.add(index, new ArrayList<MidiNote>());
		}
	}

	@Override
	public TreeSet<MetricalLpcfgHierarchyModelState> handleIncoming(List<MidiNote> notes) {
		TreeSet<MetricalLpcfgHierarchyModelState> newStates = new TreeSet<MetricalLpcfgHierarchyModelState>();

		if (notes.isEmpty()) {
			newStates.add(this);
			return newStates;
		}

		// Update for any new voices
		addNewVoices(notes);

		// Branch
		if (measure == null) {
			newStates.addAll(getAllFirstStepBranches());

		} else {
			boolean parsed = false;
			while (beatState.getNumTatums() > nextMeasureIndex) {
				parseStep();
				parsed = true;
			}

			if (parsed && !isFullyMatched()) {
				updateMatchType();
			}

			if (!isWrong()) {
				newStates.add(this);

			} else if (Main.SUPER_VERBOSE) {
				System.out.println("ELIMINATING (Match):" + beatState + this);
			}
		}

		return newStates;
	}

	/**
	 * Get a List of all of the first step branches we could make out of this state. That is,
	 * given that we currently do not have a measure, assign all possible measures and terminalLengths
	 * to new copies and return them in a List.
	 *
	 * @return A List of all possible first step branches out of this state.
	 */
	private List<MetricalLpcfgHierarchyModelState> getAllFirstStepBranches() {
		List<MetricalLpcfgHierarchyModelState> newStates = new ArrayList<MetricalLpcfgHierarchyModelState>();

		// Add measure hypotheses
		for (int subBeatLength = 1; subBeatLength <= 8; subBeatLength++) {
			if (Main.SUB_BEAT_LENGTH != -1 && Main.SUB_BEAT_LENGTH != subBeatLength) {
				continue;
			}

			for (Measure measure : grammar.getMeasures()) {

				int subBeatsPerMeasure = measure.getBeatsPerMeasure() * measure.getSubBeatsPerBeat();
				for (int anacrusisLength = 0; anacrusisLength < subBeatsPerMeasure; anacrusisLength++) {

					measure = new Measure(measure.getBeatsPerMeasure(), measure.getSubBeatsPerBeat(), subBeatLength, anacrusisLength);
					MetricalLpcfgHierarchyModelState newState =
							new MetricalLpcfgHierarchyModelState(this, grammar, measure, subBeatLength, anacrusisLength);
					newState.updateMatchType();

					// This hypothesis could match the first note, and is ready to parse
					if (!newState.isWrong()) {

						// Parse
						while (newState.beatState.getNumTatums() > newState.nextMeasureIndex) {
							newState.parseStep();
						}

						if (!newState.isFullyMatched()) {
							newState.updateMatchType();
						}

						if (!newState.isWrong()) {
							newStates.add(newState);

							if (Main.SUPER_VERBOSE) {
								System.out.println("Adding " + newState);
							}
						}
					}
				}
			}
		}

		return newStates;
	}

	@Override
	public TreeSet<MetricalLpcfgHierarchyModelState> close() {
		while (nextMeasureIndex <= beatState.getNumTatums()) {
			parseStep();
		}

		// Branch
		TreeSet<MetricalLpcfgHierarchyModelState> newStates = new TreeSet<MetricalLpcfgHierarchyModelState>();

		if (!isFullyMatched()) {
			updateMatchType();
		}

		if (!isWrong() && isFullyMatched()) {
			/*for (MetricalLpcfgTree tree : localGrammar.getTrees()) {
				double logProb = localGrammar.getTreeLogProbability(tree);
				localEntropy -= logProb * Math.exp(logProb);
			}*/
			newStates.add(this);

		} else if (Main.SUPER_VERBOSE) {
			System.out.println("ELIMINATING (No match): " + this);
		}

		return newStates;
	}

	/**
	 * Perform a single parse step. That is, make a tree from {@link #unfinishedNotes},
	 * add it and its probability to our model, and then rmove any finished notes.
	 */
	private void parseStep() {
		boolean measureUsed = false;

		if (measuresUsed == 0) {
			logProbability = 0.0;
			localLogProb = 0.0;
		}

		for (int voiceIndex = 0; voiceIndex < unfinishedNotes.size(); voiceIndex++) {
			List<MidiNote> voice = unfinishedNotes.get(voiceIndex);

			List <MetricalLpcfgQuantum> quantums = MetricalLpcfgTreeFactory.makeQuantumList(
					voice, beatState.getBeatTimes(), measure, subBeatLength, anacrusisLength,
					measureNum, hasBegun.get(voiceIndex));

			boolean isEmpty = true;
			for (MetricalLpcfgQuantum quantum : quantums) {
				if (quantum != MetricalLpcfgQuantum.REST) {
					isEmpty = false;
					break;
				}
			}

			if (!isEmpty) {
				if (!hasBegun.get(voiceIndex)) {
					hasBegun.set(voiceIndex, Boolean.TRUE);

					if (quantums.get(0) == MetricalLpcfgQuantum.REST) {
						continue;
					}
				}

				measureUsed = true;
				int beatsPerMeasure = measure.getBeatsPerMeasure();
				int subBeatsPerBeat = measure.getSubBeatsPerBeat();
				while (treeMap.size() <= beatsPerMeasure) {
					treeMap.add(new ArrayList<Map<List<MetricalLpcfgQuantum>, Double>>());
				}

				while (treeMap.get(beatsPerMeasure).size() <= subBeatsPerBeat) {
					treeMap.get(beatsPerMeasure).add(new HashMap<List<MetricalLpcfgQuantum>, Double>());
				}

				Map<List<MetricalLpcfgQuantum>, Double> nestedTreeMap = treeMap.get(beatsPerMeasure).get(subBeatsPerBeat);

				MetricalLpcfgTree tree = null;
				Double logProb = nestedTreeMap.get(quantums);
				if (logProb == null) {
					tree = MetricalLpcfgTreeFactory.makeTree(quantums, beatsPerMeasure, subBeatsPerBeat);
					logProb = grammar.getTreeLogProbability(tree);
					nestedTreeMap.put(quantums, logProb);
				}
				logProbability += logProb;


				if (LOCAL_WEIGHT != 0.0) {
					if (tree == null) {
						tree = MetricalLpcfgTreeFactory.makeTree(quantums, beatsPerMeasure, subBeatsPerBeat);
					}

					if (!localGrammar.getTrees().isEmpty()) {
						localLogProb += localGrammar.getTreeLogProbability(tree);
					}

					localGrammar.addTree(tree);
				}
			}
		}

		removeFinishedNotes();
		nextMeasureIndex += subBeatLength * measure.getBeatsPerMeasure() * measure.getSubBeatsPerBeat();
		measureNum++;

		if (measureUsed) {
			measuresUsed++;
		}
	}

	/**
	 * Remove any notes which are entirely finished from {@link #unfinishedNotes}.
	 */
	private void removeFinishedNotes() {
		for (List<MidiNote> voice : unfinishedNotes) {
			int i = 0;
			for (i = 0; i < voice.size(); i++) {
				MidiNote note = voice.get(i);

				if (note.getOffsetTime() >= beatState.getBeatTimes().get(beatState.getBeatTimes().size() - 2)) {
					break;
				}
			}
			voice.subList(0, i).clear();
		}
	}

	/**
	 * Add new voices into our tracking lists.
	 *
	 * @param notes The new notes that were just added.
	 */
	private void addNewVoices(List<MidiNote> notes) {
		for (int voiceIndex = 0; voiceIndex < voiceState.getVoices().size(); voiceIndex++) {
			Voice voice = voiceState.getVoices().get(voiceIndex);
			MidiNote note = voice.getMostRecentNote();

			if (notes.contains(note)) {

				notes.remove(note);

				if (voice.isNew(note.getOnsetTime())) {
					// This was a new voice
					List<MidiNote> newNote = new ArrayList<MidiNote>();
					newNote.add(note);
					try {
						unfinishedNotes.add(voiceIndex, newNote);
					} catch (IndexOutOfBoundsException e) {
						System.err.println("An error occurred. Probably, a voice/track has multiple notes onset at the same time (" +
										   note.getOnsetTime() + " microseconds). The file should be run through my voice splitter " +
										   "first. See the README.");
						System.exit(1);
					}
					hasBegun.add(voiceIndex, Boolean.FALSE);

					if (!matches(MetricalLpcfgMatch.BEAT)) {
						notesToCheckBeats.add(voiceIndex, new ArrayList<MidiNote>(newNote));
					}

					if (!isFullyMatched()) {
						newNote = new ArrayList<MidiNote>();
						newNote.add(note);
						notesToCheck.add(voiceIndex, newNote);
					}

				} else {
					unfinishedNotes.get(voiceIndex).add(note);

					if (!matches(MetricalLpcfgMatch.BEAT)) {
						notesToCheckBeats.get(voiceIndex).add(note);
					}

					if (!isFullyMatched()) {
						notesToCheck.get(voiceIndex).add(note);
					}
				}
			}
		}

		// PROBLEM!!
		if (!notes.isEmpty()) {
			for (MidiNote note : notes) {
				for (int voiceIndex = 0; voiceIndex < voiceState.getVoices().size(); voiceIndex++) {
					Voice voice = voiceState.getVoices().get(voiceIndex);

					if (voice.getNotes().contains(note)) {
						if (voice.isNew(note.getOnsetTime())) {
							// This was a new voice
							List<MidiNote> newNote = new ArrayList<MidiNote>();
							newNote.add(note);
							unfinishedNotes.add(voiceIndex, newNote);
							hasBegun.add(voiceIndex, Boolean.FALSE);

							if (!matches(MetricalLpcfgMatch.BEAT)) {
								notesToCheckBeats.add(voiceIndex, new ArrayList<MidiNote>(newNote));
							}

							if (!isFullyMatched()) {
								newNote = new ArrayList<MidiNote>();
								newNote.add(note);
								notesToCheck.add(voiceIndex, newNote);
							}

						} else {
							unfinishedNotes.get(voiceIndex).add(note);

							if (!matches(MetricalLpcfgMatch.BEAT)) {
								notesToCheckBeats.get(voiceIndex).add(note);
							}

							if (!isFullyMatched()) {
								notesToCheck.get(voiceIndex).add(note);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Go through the {@link #notesToCheck} Queue for any notes that have finished and update
	 * the match for any that have.
	 */
	private void updateMatchType() {
		if (beatState.getNumTatums() == 0) {
			return;
		}

		// Check for conglomerate beat matches
		for (int voiceIndex = 0; !isWrong() && !matches(MetricalLpcfgMatch.BEAT) && voiceIndex < notesToCheckBeats.size(); voiceIndex++) {
			while (checkConglomerateBeatMatch(notesToCheckBeats.get(voiceIndex)));

			// Empty notesToCheckBeats if done
			if (matches(MetricalLpcfgMatch.BEAT)) {
				notesToCheckBeats.clear();
			}
		}

		int lastTime = beatState.getLastTatumTime();

		for (List<MidiNote> voice : notesToCheck) {

			MidiNote nextNote = voice.isEmpty() ? null : voice.get(0);
			int noteIndex = 0;
			for (noteIndex = 0; !isWrong() && !isFullyMatched() &&
					nextNote != null && nextNote.getOffsetTime() <= lastTime;) {

				MidiNote note = nextNote;
				nextNote = ++noteIndex < voice.size() ? voice.get(noteIndex) : null;

				updateMatchType(note, nextNote);
			}

			voice.subList(0, noteIndex).clear();
		}
	}

	/**
	 * Check for a conglomerate beat match. That is, a match where some number of notes add up to a beat exactly
	 * with no leading, trailing, or interspersed rests.
	 *
	 * @param notes The unchecked notes in the voice we want to check.
	 * @return True if we need to continue checking this voice. That is, if this model is not wrong yet, hasn't yet
	 * been matched at the beat level, and if we were able to check the beat beginning with the previous first note.
	 * False otherwise.
	 */
	private boolean checkConglomerateBeatMatch(List<MidiNote> notes) {
		if (notes.isEmpty()) {
			return false;
		}

		int numNotesToRemove = 0;
		int beatLength = subBeatLength * measure.getSubBeatsPerBeat();

		List<Integer> beats = beatState.getBeatTimes();

		int lastBeatTactus = beats.size() - 1;
		lastBeatTactus -= anacrusisLength * subBeatLength;

		if (anacrusisLength % measure.getSubBeatsPerBeat() != 0) {
			lastBeatTactus += subBeatLength * (measure.getSubBeatsPerBeat() - (anacrusisLength % measure.getSubBeatsPerBeat()));
		}
		int lastBeatNum = lastBeatTactus / beatLength;

		// First note
		Iterator<MidiNote> iterator = notes.iterator();
		MidiNote firstNote = iterator.next();
		numNotesToRemove++;
		MidiNote nextNote = iterator.hasNext() ? iterator.next() : null;
		if (Main.EXTEND_NOTES && nextNote == null) {
			return false;
		}

		int startBeatIndex = firstNote.getOnsetBeatIndex(beats);
		// Cut this note at the onset of the next note if they overlap
		int endBeatIndex = firstNote.overlaps(nextNote) || Main.EXTEND_NOTES ? nextNote.getOnsetBeatIndex(beats) : firstNote.getOffsetBeatIndex(beats);

		int startTactus = startBeatIndex;
		int endTactus = endBeatIndex;

		int noteLengthTacti = Math.max(1, endTactus - startTactus);
		startTactus -= anacrusisLength * subBeatLength;
		endTactus = startTactus + noteLengthTacti;

		// Add up possible partial beat at the start
		if (anacrusisLength % measure.getSubBeatsPerBeat() != 0) {
			startTactus += subBeatLength * (measure.getSubBeatsPerBeat() - (anacrusisLength % measure.getSubBeatsPerBeat()));
		}
		int beatOffset = startTactus % beatLength;
		int firstBeatNum = startTactus / beatLength;

		// First note's beat hasn't finished yet
		if (firstBeatNum == lastBeatNum) {
			return false;
		}

		// First note doesn't begin on a beat
		if (beatOffset != 0) {
			notes.remove(0);
			return nextNote != null;
		}

		// Tracking array
		MetricalLpcfgQuantum[] quantums = new MetricalLpcfgQuantum[beatLength + 1];
		Arrays.fill(quantums, MetricalLpcfgQuantum.REST);

		// Add first note into tracking array
		quantums[beatOffset] = MetricalLpcfgQuantum.ONSET;
		for (int tactus = beatOffset + 1; tactus < noteLengthTacti && tactus < quantums.length; tactus++) {
			quantums[tactus] = MetricalLpcfgQuantum.TIE;
		}

		while (nextNote != null) {
			MidiNote note = nextNote;
			numNotesToRemove++;
			nextNote = iterator.hasNext() ? iterator.next() : null;
			if (Main.EXTEND_NOTES && nextNote == null) {
				break;
			}

			startBeatIndex = note.getOnsetBeatIndex(beats);
			// Cut off at beginning of next note if one exists and they overlap
			endBeatIndex = note.overlaps(nextNote) || Main.EXTEND_NOTES ? nextNote.getOnsetBeatIndex(beats) : note.getOffsetBeatIndex(beats);

			startTactus = startBeatIndex;
			endTactus = endBeatIndex;

			noteLengthTacti = Math.max(1, endTactus - startTactus);
			startTactus -= anacrusisLength * subBeatLength;
			endTactus = startTactus + noteLengthTacti;

			// Add up possible partial beat at the start
			if (anacrusisLength % measure.getSubBeatsPerBeat() != 0) {
				startTactus += subBeatLength * (measure.getSubBeatsPerBeat() - (anacrusisLength % measure.getSubBeatsPerBeat()));
			}
			beatOffset = startTactus % beatLength;
			int beatNum = startTactus / beatLength;

			if (beatNum != firstBeatNum) {
				if (beatOffset == 0) {
					quantums[beatLength] = MetricalLpcfgQuantum.ONSET;
				}

				numNotesToRemove--;
				break;
			}

			// Add note into tracking array
			quantums[beatOffset] = MetricalLpcfgQuantum.ONSET;
			for (int tactus = beatOffset + 1; tactus - beatOffset < noteLengthTacti && tactus < quantums.length; tactus++) {
				quantums[tactus] = MetricalLpcfgQuantum.TIE;
			}
		}

		// Some note tied over the beat boundary, no match
		if (quantums[beatLength] == MetricalLpcfgQuantum.TIE) {
			notes.subList(0, numNotesToRemove).clear();
			return nextNote != null;
		}

		// Get the onsets of this quantum
		List<Integer> onsets = new ArrayList<Integer>();
		for (int tactus = 0; tactus < beatLength; tactus++) {
			MetricalLpcfgQuantum quantum = quantums[tactus];

			// There's a REST in this quantum, no match
			if (quantum == MetricalLpcfgQuantum.REST) {
				notes.subList(0, numNotesToRemove).clear();
				return nextNote != null;
			}

			if (quantum == MetricalLpcfgQuantum.ONSET) {
				onsets.add(tactus);
			}
		}

		// Get the lengths of the notes of this quantum
		List<Integer> lengths = new ArrayList<Integer>(onsets.size());
		for (int i = 1; i < onsets.size(); i++) {
			lengths.add(onsets.get(i) - onsets.get(i - 1));
		}
		lengths.add(beatLength - onsets.get(onsets.size() - 1));

		// Only 1 note, no match
		if (lengths.size() == 1) {
			notes.subList(0, numNotesToRemove).clear();
			return nextNote != null;
		}

		for (int i = 1; i < lengths.size(); i++) {
			// Some lengths are different, match
			if (lengths.get(i) != lengths.get(i - 1)) {
				addMatch(MetricalLpcfgMatch.BEAT);
				notes.subList(0, numNotesToRemove).clear();
				return false;
			}
		}

		// All note lengths were the same, no match
		notes.subList(0, numNotesToRemove).clear();
		return nextNote != null;
	}

	/**
	 * Update the match for the given note.
	 *
	 * @param note The note we need to check for a match.
	 */
	private void updateMatchType(MidiNote note, MidiNote nextNote) {
		if (Main.EXTEND_NOTES && nextNote == null) {
			return;
		}
		List<Integer> beats = beatState.getBeatTimes();
		int startBeatIndex = note.getOnsetBeatIndex(beats);
		// Cut off note at next onset, if it overlaps
		int endBeatIndex = note.overlaps(nextNote) || Main.EXTEND_NOTES ? nextNote.getOnsetBeatIndex(beats) : note.getOffsetBeatIndex(beats);

		int startTactus = startBeatIndex;
		int endTactus = endBeatIndex;

		int noteLengthTacti = Math.max(1, endTactus - startTactus);
		startTactus -= anacrusisLength * subBeatLength;
		endTactus = startTactus + noteLengthTacti;

		int prefixStart = startTactus;
		int middleStart = startTactus;
		int postfixStart = endTactus;

		int prefixLength = 0;
		int middleLength = noteLengthTacti;
		int postfixLength = 0;

		int beatLength = subBeatLength * measure.getSubBeatsPerBeat();

		// Reinterpret note given matched levels
		if (matches(MetricalLpcfgMatch.SUB_BEAT) && startTactus / subBeatLength != (endTactus - 1) / subBeatLength) {
			// Interpret note as sub beats

			int subBeatOffset = startTactus % subBeatLength;
			int subBeatEndOffset = endTactus % subBeatLength;

			// Prefix
			if (subBeatOffset != 0) {
				prefixLength = subBeatLength - subBeatOffset;
			}

			// Middle fix
			middleStart += prefixLength;
			middleLength -= prefixLength;

			// Postfix
			postfixStart -= subBeatEndOffset;
			postfixLength += subBeatEndOffset;

			// Middle fix
			middleLength -= postfixLength;

		} else if (matches(MetricalLpcfgMatch.BEAT) && startTactus / beatLength != (endTactus - 1) / beatLength) {
			// Interpret note as beats

			// Add up possible partial beat at the start
			if (anacrusisLength % measure.getSubBeatsPerBeat() != 0) {
				int diff = subBeatLength * (measure.getSubBeatsPerBeat() - (anacrusisLength % measure.getSubBeatsPerBeat()));
				startTactus += diff;
				endTactus += diff;
			}
			int beatOffset = (startTactus + subBeatLength * (anacrusisLength % measure.getSubBeatsPerBeat())) % beatLength;
			int beatEndOffset = (endTactus + subBeatLength * (anacrusisLength % measure.getSubBeatsPerBeat())) % beatLength;

			// Prefix
			if (beatOffset != 0) {
				prefixLength = beatLength - beatOffset;
			}

			// Middle fix
			middleStart += prefixLength;
			middleLength -= prefixLength;

			// Postfix
			postfixStart -= beatEndOffset;
			postfixLength += beatEndOffset;

			// Middle fix
			middleLength -= postfixLength;
		}

		// Prefix checking
		if (prefixLength != 0) {
			updateMatchType(prefixStart, prefixLength);
		}

		// Middle checking
		if (!isFullyMatched() && !isWrong() && middleLength != 0) {
			updateMatchType(middleStart, middleLength);
		}

		// Postfix checking
		if (!isFullyMatched() && !isWrong() && postfixLength != 0) {
			updateMatchType(postfixStart, postfixLength);
		}
	}

	/**
	 * Update the match for a note with the given start tactus and length.
	 *
	 * @param startTactus The tactus at which this note begins, normalized to where 0
	 * is the beginning of the full anacrusis measure, if any exists.
	 * @param noteLengthTacti The length and tacti of the note.
	 */
	private void updateMatchType(int startTactus, int noteLengthTacti) {
		int beatLength = subBeatLength * measure.getSubBeatsPerBeat();
		int measureLength = beatLength * measure.getBeatsPerMeasure();

		int subBeatOffset = startTactus % subBeatLength;
		int beatOffset = startTactus % beatLength;
		int measureOffset = startTactus % measureLength;

		if (matches(MetricalLpcfgMatch.SUB_BEAT)) {
			// Matches sub beat (and not beat)

			if (noteLengthTacti < subBeatLength) {
				// Note is shorter than a sub beat

			} else if (noteLengthTacti == subBeatLength) {
				// Note is exactly a sub beat

			} else if (noteLengthTacti < beatLength) {
				// Note is between a sub beat and a beat in length

				// Can only happen when the beat is divided in 3, but this is 2 sub beats
				addMatch(MetricalLpcfgMatch.WRONG);

			} else if (noteLengthTacti == beatLength) {
				// Note is exactly a beat in length

				// Must match exactly
				addMatch(beatOffset == 0 ? MetricalLpcfgMatch.BEAT : MetricalLpcfgMatch.WRONG);

			} else {
				// Note is greater than a beat in length

				if (noteLengthTacti % beatLength != 0) {
					// Not some multiple of the beat length
					addMatch(MetricalLpcfgMatch.WRONG);
				}
			}

		} else if (matches(MetricalLpcfgMatch.BEAT)) {
			// Matches beat (and not sub beat)

			if (noteLengthTacti < subBeatLength) {
				// Note is shorter than a sub beat

				if (subBeatLength % noteLengthTacti != 0 || subBeatOffset % noteLengthTacti != 0) {
					// Note doesn't divide sub beat evenly
					addMatch(MetricalLpcfgMatch.WRONG);
				}

			} else if (noteLengthTacti == subBeatLength) {
				// Note is exactly a sub beat

				// Must match sub beat exactly
				addMatch(subBeatOffset == 0 ? MetricalLpcfgMatch.SUB_BEAT : MetricalLpcfgMatch.WRONG);

			} else if (noteLengthTacti < beatLength) {
				// Note is between a sub beat and a beat in length

				// Wrong if not aligned with beat at onset or offset
				if (beatOffset != 0 && beatOffset + noteLengthTacti != beatLength) {
					addMatch(MetricalLpcfgMatch.WRONG);
				}

			} else if (noteLengthTacti == beatLength) {
				// Note is exactly a beat in length

			} else {
				// Note is longer than a beat in length

			}

		} else {
			// Matches neither sub beat nor beat

			if (noteLengthTacti < subBeatLength) {
				// Note is shorter than a sub beat

				if (subBeatLength % noteLengthTacti != 0 || subBeatOffset % noteLengthTacti != 0) {
					// Note doesn't divide sub beat evenly
					addMatch(MetricalLpcfgMatch.WRONG);
				}

			} else if (noteLengthTacti == subBeatLength) {
				// Note is exactly a sub beat

				// Must match sub beat exactly
				addMatch(subBeatOffset == 0 ? MetricalLpcfgMatch.SUB_BEAT : MetricalLpcfgMatch.WRONG);

			} else if (noteLengthTacti < beatLength) {
				// Note is between a sub beat and a beat in length

				// Wrong if not aligned with beat at onset or offset
				if (beatOffset != 0 && beatOffset + noteLengthTacti != beatLength) {
					addMatch(MetricalLpcfgMatch.WRONG);
				}

			} else if (noteLengthTacti == beatLength) {
				// Note is exactly a beat in length

				// Must match beat exactly
				addMatch(beatOffset == 0 ? MetricalLpcfgMatch.BEAT : MetricalLpcfgMatch.WRONG);

			} else {
				// Note is greater than a beat in length

				if (measureLength % noteLengthTacti != 0 || measureOffset % noteLengthTacti != 0 ||
						beatOffset != 0 || noteLengthTacti % beatLength != 0) {
					// Note doesn't divide measure evenly, or doesn't match a beat
					addMatch(MetricalLpcfgMatch.WRONG);
				}
			}
		}
	}

	/**
	 * Return whether this model has been fully matched yet. That is, whether it has been
	 * matched at both the beat level and the sub beat level.
	 *
	 * @return True if this model has been fully matched. False otherwise.
	 */
	private boolean isFullyMatched() {
		return subBeatMatches > 0 && beatMatches > 0;
	}

	/**
	 * Return whether this model has been designated as wrong yet.
	 *
	 * @return True if this model is wrong. False otherwise.
	 */
	private boolean isWrong() {
		return wrongMatches >= 5;
	}

	/**
	 * Add the given matchType to this model.
	 *
	 * @param matchType The match type we want to add to this model.
	 */
	public void addMatch(MetricalLpcfgMatch matchType) {
		switch (matchType) {
			case SUB_BEAT:
				subBeatMatches++;
				break;

			case BEAT:
				beatMatches++;
				break;

			case WRONG:
				wrongMatches++;
		}
	}

	/**
	 * Check if this model matches the given match type.
	 *
	 * @param matchType The match type we want to check our model against.
	 * @return True if our model matches the given match type. False otherwise.
	 */
	private boolean matches(MetricalLpcfgMatch matchType) {
		switch (matchType) {
			case SUB_BEAT:
				return subBeatMatches > 0;

			case BEAT:
				return beatMatches > 0;

			case WRONG:
				return isWrong();
		}

		return false;
	}

	@Override
	public int getSubBeatLength() {
		return subBeatLength;
	}

	@Override
	public int getAnacrusis() {
		return anacrusisLength;
	}

	@Override
	public int getBarCount() {
		return measuresUsed;
	}

	/**
	 * Get the local grammar of this model.
	 *
	 * @return {@link #localGrammar}
	 */
	public MetricalLpcfg getLocalGrammar() {
		return localGrammar;
	}

	@Override
	public Measure getMetricalMeasure() {
		return measure;
	}

	@Override
	public MetricalLpcfgHierarchyModelState deepCopy() {
		return new MetricalLpcfgHierarchyModelState(this);
	}

	@Override
	public double getScore() {
		return logProbability + LOCAL_WEIGHT * localLogProb;
	}

	@Override
	public boolean isDuplicateOf(HierarchyModelState state) {
		if (!(state instanceof MetricalLpcfgHierarchyModelState)) {
			return false;
		}

		MetricalLpcfgHierarchyModelState lpcfg = (MetricalLpcfgHierarchyModelState) state;

		return measure.equals(lpcfg.measure) && subBeatLength == lpcfg.subBeatLength && anacrusisLength == lpcfg.anacrusisLength;
	}

	@Override
	public int compareTo(MidiModelState other) {
		if (!(other instanceof MetricalLpcfgHierarchyModelState)) {
			return -1;
		}

		MetricalLpcfgHierarchyModelState o = (MetricalLpcfgHierarchyModelState) other;

		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}

		result = Integer.compare(subBeatLength, o.subBeatLength);
		if (result != 0) {
			return result;
		}

		result = Integer.compare(anacrusisLength, o.anacrusisLength);
		if (result != 0) {
			return result;
		}

		if (measure != null) {
			result = measure.compareTo(o.measure);
			if (result != 0) {
				return result;
			}
		}

		if (voiceState.compareTo(o.voiceState) == 0
				&& beatState.compareToNoRecurse(o.beatState) == 0) {
			return 0;
		}
		return 1;
	}

	@Override
	public int compareToNoRecurse(MidiModelState other) {
		if (!(other instanceof MetricalLpcfgHierarchyModelState)) {
			return -1;
		}

		MetricalLpcfgHierarchyModelState o = (MetricalLpcfgHierarchyModelState) other;

		int result = Double.compare(o.getScore(), getScore());
		if (result != 0) {
			return result;
		}

		result = Integer.compare(subBeatLength, o.subBeatLength);
		if (result != 0) {
			return result;
		}

		result = Integer.compare(anacrusisLength, o.anacrusisLength);
		if (result != 0) {
			return result;
		}

		if (measure != null) {
			result = measure.compareTo(o.measure);
			if (result != 0) {
				return result;
			}
		}

		return 0;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append(measure).append(" length=").append(subBeatLength).append(" anacrusis=").append(anacrusisLength);
		sb.append(" Score=").append(logProbability).append(" + ").append(localLogProb).append(" = ").append(getScore());

		return sb.toString();
	}
}