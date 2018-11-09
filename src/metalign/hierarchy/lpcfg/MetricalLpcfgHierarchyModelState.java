package metalign.hierarchy.lpcfg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import metalign.Main;
import metalign.beat.hmm.HmmBeatTrackingModelState;
import metalign.generic.MidiModelState;
import metalign.hierarchy.HierarchyModelState;
import metalign.hierarchy.Measure;
import metalign.utils.MathUtils;
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
	
	public static double GLOBAL_WEIGHT = 2.0 / 3.0;
	
	/**
	 * Object to save tree log probabilities so as not to regenerate every time.
	 */
	public static List<List<Map<List<MetricalLpcfgQuantum>, Double>>> treeMap = new ArrayList<List<Map<List<MetricalLpcfgQuantum>, Double>>>();
	
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
	
	private double alignmentLogProbability;
	
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
	 * The time of the most recent tatum, per voice.
	 */
	private final List<List<Integer>> previous;
	
	/**
	 * The quantums of the previous bar in each voice.
	 */
	private final List<List<MetricalLpcfgQuantum>> previousBarQuantum;
	
	/**
	 * The index of the previous onset in each voice.
	 */
	private final List<Integer> previousOnset;
	
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
		anacrusisLength = 0;
		measure = null;
		this.grammar = grammar;
		
		subBeatMatches = 0;
		beatMatches = 0;
		wrongMatches = 0;
		
		localGrammar = new MetricalLpcfg();
		
		logProbability = 0.0;
		localLogProb = 0.0;
		alignmentLogProbability = 0.0;
		
		measureNum = 0;
		nextMeasureIndex = 0;
		measuresUsed = 0;
		
		unfinishedNotes = new ArrayList<List<MidiNote>>();
		hasBegun = new ArrayList<Boolean>();
		previous = new ArrayList<List<Integer>>();
		previousBarQuantum = new ArrayList<List<MetricalLpcfgQuantum>>();
		previousOnset = new ArrayList<Integer>();
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
	public MetricalLpcfgHierarchyModelState(MetricalLpcfgHierarchyModelState state, MetricalLpcfg grammar, Measure measure, int anacrusisLength) {
		this.anacrusisLength = anacrusisLength;
		this.measure = measure;
		this.grammar = grammar;
		
		subBeatMatches = state.subBeatMatches;
		beatMatches = state.beatMatches;
		wrongMatches = state.wrongMatches;
		
		logProbability = state.logProbability;
		localLogProb = state.localLogProb;
		alignmentLogProbability = state.alignmentLogProbability;
		
		measureNum = state.measureNum;
		measuresUsed = state.measuresUsed;
		nextMeasureIndex = state.nextMeasureIndex;
		if (measure != null && nextMeasureIndex == 0) {
			if (anacrusisLength != 0) {
				nextMeasureIndex = anacrusisLength;
				measureNum = -1;
				
			} else {
				nextMeasureIndex = measure.getBeatsPerBar() * measure.getSubBeatsPerBeat();
			}
		}
		
		unfinishedNotes = new ArrayList<List<MidiNote>>();
		for (List<MidiNote> voice : state.unfinishedNotes) {
			unfinishedNotes.add(new ArrayList<MidiNote>(voice));
		}
		
		previous = new ArrayList<List<Integer>>();
		for (List<Integer> voice : state.previous) {
			previous.add(new ArrayList<Integer>(voice));
		}
		
		previousBarQuantum = new ArrayList<List<MetricalLpcfgQuantum>>();
		for (List<MetricalLpcfgQuantum> voice : state.previousBarQuantum) {
			previousBarQuantum.add(new ArrayList<MetricalLpcfgQuantum>(voice));
		}
		
		hasBegun = new ArrayList<Boolean>(state.hasBegun);
		previousOnset = new ArrayList<Integer>(state.previousOnset);
		
		localGrammar = state.localGrammar.deepCopy();
		
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
		this(state, state.grammar, state.measure, state.anacrusisLength);
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
		
		List<Integer> voicePrevious = new ArrayList<Integer>();
		voicePrevious.add(Integer.MIN_VALUE);
		previous.add(index, voicePrevious);
		
		previousBarQuantum.add(index, new ArrayList<MetricalLpcfgQuantum>());
		previousOnset.add(index, Integer.MAX_VALUE);
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
			while (beatState.getNumTatums() > nextMeasureIndex) {
				parseStep();
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
		for (Measure measure : grammar.getMeasures()) {
			
			int subBeatsPerMeasure = measure.getBeatsPerBar() * measure.getSubBeatsPerBeat();
			for (int anacrusisLength = 0; anacrusisLength < subBeatsPerMeasure; anacrusisLength++) {
				
				measure = new Measure(measure.getBeatsPerBar(), measure.getSubBeatsPerBeat());
				MetricalLpcfgHierarchyModelState newState =
						new MetricalLpcfgHierarchyModelState(this, grammar, measure, anacrusisLength);
				
				// This hypothesis could match the first note, and is ready to parse
				while (newState.beatState.getNumTatums() > newState.nextMeasureIndex) {
					newState.parseStep();
				}
				
				if (!newState.isWrong()) {
					newStates.add(newState);
					
					if (Main.SUPER_VERBOSE) {
						System.out.println("Adding " + newState);
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
			
		if (!isWrong() && isFullyMatched()) {
			newStates.add(this);
				
		} else if (Main.SUPER_VERBOSE) {
			System.out.println("ELIMINATING (No match): " + this);
		}
		
		return newStates;
	}
	
	/**
	 * Perform a single parse step. That is, make a tree from {@link #unfinishedNotes},
	 * add it and its probability to our model, and then remove any finished notes.
	 */
	private void parseStep() {
		boolean measureUsed = false;
		
		if (measuresUsed == 0) {
			logProbability = 0.0;
			localLogProb = 0.0;
		}
		
		for (int voiceIndex = 0; voiceIndex < unfinishedNotes.size(); voiceIndex++) {
			List<MidiNote> voiceNotes = unfinishedNotes.get(voiceIndex);
			List<Integer> voicePrevious = previous.get(voiceIndex);
			
			List<List<Integer>> alignmentDiffs = new ArrayList<List<Integer>>();
			alignmentDiffs.add(new ArrayList<Integer>());
			
			List<List<MetricalLpcfgQuantum>> quantumLists = MetricalLpcfgTreeFactory.makeQuantumLists(
					voiceNotes, voicePrevious, beatState.getBeatTimes(), measure, anacrusisLength,
					measureNum, hasBegun.get(voiceIndex), alignmentDiffs);
			
			double minTotalLogProb = Double.NEGATIVE_INFINITY;
			double minAlignmentLogProb = 0.0;
			double minGlobalTreeLogProb = 0.0;
			double minLocalTreeLogProb = 0.0;
			boolean minIsEmpty = false;
			boolean minUseTree = false;
			int minPrevious = Integer.MIN_VALUE;
			MetricalLpcfgTree minTree = null;
			List<MetricalLpcfgQuantum> minQuantums = null;
			
			// Try each hypothesis list, and use the one with the greatest probability according to the grammar and the alignment.
			for (int i = 0; i < quantumLists.size(); i++) {
				List<MetricalLpcfgQuantum> quantums = quantumLists.get(i);
				List<Integer> alignments = alignmentDiffs.get(i);
			
				double alignmentLogProb = getAlignmentLogProbability(alignments);
				double globalTreeLogProb = 0.0;
				double localTreeLogProb = 0.0;
				boolean useTree = false;
				MetricalLpcfgTree tree = null;
				
				boolean isEmpty = true;
				for (MetricalLpcfgQuantum quantum : quantums) {
					if (quantum != MetricalLpcfgQuantum.REST) {
						isEmpty = false;
						break;
					}
				}
				
				if (!isEmpty && quantums.size() == measure.getBeatsPerBar() * measure.getSubBeatsPerBeat() * 12 &&
						(hasBegun.get(voiceIndex) || quantums.get(0) != MetricalLpcfgQuantum.REST)) {
					useTree = true;
					int beatsPerMeasure = measure.getBeatsPerBar();
					int subBeatsPerBeat = measure.getSubBeatsPerBeat();
					while (treeMap.size() <= beatsPerMeasure) {
						treeMap.add(new ArrayList<Map<List<MetricalLpcfgQuantum>, Double>>());
					}
					
					while (treeMap.get(beatsPerMeasure).size() <= subBeatsPerBeat) {
						treeMap.get(beatsPerMeasure).add(new HashMap<List<MetricalLpcfgQuantum>, Double>());
					}
					
					Map<List<MetricalLpcfgQuantum>, Double> nestedTreeMap = treeMap.get(beatsPerMeasure).get(subBeatsPerBeat);
					
					Double logProb = nestedTreeMap.get(quantums);
					if (logProb == null) {
						tree = MetricalLpcfgTreeFactory.makeTree(quantums, beatsPerMeasure, subBeatsPerBeat);
						logProb = grammar.getTreeLogProbability(tree);
						nestedTreeMap.put(quantums, logProb);
					}
					globalTreeLogProb = logProb;
					
					
					if (GLOBAL_WEIGHT != 1.0) {
						if (tree == null) {
							tree = MetricalLpcfgTreeFactory.makeTree(quantums, beatsPerMeasure, subBeatsPerBeat);
						}
						
						if (!localGrammar.getTrees().isEmpty()) {
							localTreeLogProb = localGrammar.getTreeLogProbability(tree);
						}
					}
				}
				
				double totalLogProb = alignmentLogProb + GLOBAL_WEIGHT * globalTreeLogProb + (1.0 - GLOBAL_WEIGHT) * localTreeLogProb;
				
				if (totalLogProb > minTotalLogProb) {
					minTotalLogProb = totalLogProb;
					minAlignmentLogProb = alignmentLogProb;
					minGlobalTreeLogProb = globalTreeLogProb;
					minLocalTreeLogProb = localTreeLogProb;
					minQuantums = quantums;
					minIsEmpty = isEmpty;
					minUseTree = useTree;
					minPrevious = voicePrevious.get(voiceIndex);
				}
			}
			
			// Here we have the min of everything. Add them in.
			alignmentLogProbability += minAlignmentLogProb;
			logProbability += minGlobalTreeLogProb;
			localLogProb += minLocalTreeLogProb;
			
			if (!minIsEmpty) {
				hasBegun.set(voiceIndex, Boolean.TRUE);
			}
			
			updateMatch(previousBarQuantum.get(voiceIndex), minQuantums.get(0), voiceIndex);
			
			previousBarQuantum.set(voiceIndex, minQuantums);
			voicePrevious.clear();
			voicePrevious.add(minPrevious);
			
			if (minUseTree) {
				measureUsed = true;
				
				if (GLOBAL_WEIGHT != 1.0) {
					if (minTree == null) {
						minTree = MetricalLpcfgTreeFactory.makeTree(minQuantums, measure.getBeatsPerBar(), measure.getSubBeatsPerBeat());
					}
					
					localGrammar.addTree(minTree);
				}
			}
		}
		
		removeFinishedNotes();
		nextMeasureIndex += measure.getBeatsPerBar() * measure.getSubBeatsPerBeat();
		measureNum++;
		
		if (measureUsed) {
			measuresUsed++;
		}
	}
	
	private double getAlignmentLogProbability(List<Integer> alignments) {
		if (!(beatState instanceof HmmBeatTrackingModelState)) {
			return 0.0;
		}
		HmmBeatTrackingModelState bs = (HmmBeatTrackingModelState) beatState;
		
		double logProb = 0.0;
		
		for (int diff : alignments) {
			logProb += Math.log(MathUtils.getStandardNormal(0, diff, bs.params.NOTE_STD));
		}
		
		return logProb;
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
					unfinishedNotes.add(voiceIndex, newNote);
					
					hasBegun.add(voiceIndex, Boolean.FALSE);
					
					List<Integer> voicePrevious = new ArrayList<Integer>();
					voicePrevious.add(Integer.MIN_VALUE);
					previous.add(voiceIndex, voicePrevious);
					
					previousBarQuantum.add(voiceIndex, new ArrayList<MetricalLpcfgQuantum>());
					previousOnset.add(voiceIndex, Integer.MAX_VALUE);
					
				} else {
					unfinishedNotes.get(voiceIndex).add(note);
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
							
							List<Integer> voicePrevious = new ArrayList<Integer>();
							voicePrevious.add(Integer.MIN_VALUE);
							previous.add(voiceIndex, voicePrevious);
							
							previousBarQuantum.add(voiceIndex, new ArrayList<MetricalLpcfgQuantum>());
							previousOnset.add(voiceIndex, Integer.MAX_VALUE);
							
						} else {
							unfinishedNotes.get(voiceIndex).add(note);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Update this hypothesis's match type based on the current bar's quantum list, and the first
	 * quantum of the next bar.
	 * 
	 * @param quantums A list of quantums of the current bar, or an empty list if we are approaching
	 * the first one.
	 * @param next The first quantum of the following bar.
	 */
	private void updateMatch(List<MetricalLpcfgQuantum> quantums, MetricalLpcfgQuantum next, int voiceIndex) {
		if (isFullyMatched() || quantums.isEmpty()) {
			return;
		}
		
		// Front-pad with rests
		List<MetricalLpcfgQuantum> newQuantums = new ArrayList<MetricalLpcfgQuantum>(measure.getBeatsPerBar() * measure.getSubBeatsPerBeat() * 12);
		for (int i = 0; i < measure.getBeatsPerBar() * measure.getSubBeatsPerBeat() * 12 - quantums.size(); i++) {
			newQuantums.add(MetricalLpcfgQuantum.REST);
		}
		newQuantums.addAll(quantums);
		quantums = newQuantums;
		
		if (!matches(MetricalLpcfgMatch.BEAT)) {
			checkConglomerateBeatMatch(quantums, next);
		}
		
		if (!isFullyMatched()) {
			checkMatch(quantums, next, previousOnset.get(voiceIndex));
		}
		
		if (!isFullyMatched()) {
			int lastIndex = quantums.lastIndexOf(MetricalLpcfgQuantum.ONSET);
			
			if (lastIndex == -1) {
				// No onsets here
				if (previousOnset.get(voiceIndex) != Integer.MAX_VALUE && next == MetricalLpcfgQuantum.TIE) {
					// We are tied into, and we tie out. Just decrement previousOnset
					previousOnset.set(voiceIndex, previousOnset.get(voiceIndex) - quantums.size());
					
				} else {
					// We either have no notes, or our tie has ended
					previousOnset.set(voiceIndex, Integer.MAX_VALUE);
				}
				
			} else {
				// We have an onset
				if (next == MetricalLpcfgQuantum.TIE) {
					// We tie out
					previousOnset.set(voiceIndex, lastIndex);
					
				} else {
					// We don't tie out
					previousOnset.set(voiceIndex, Integer.MAX_VALUE);
				}
			}
		}
	}
	
	/**
	 * Check for a conglomerate beat match. That is, a match where some number of notes add up to a beat exactly
	 * with no leading, trailing, or interspersed rests, and where the notes aren't all of equal length.
	 * 
	 * @param quantums The current bar's quantums.
	 * @param next The first quantum of the following bar.
	 */
	private void checkConglomerateBeatMatch(List<MetricalLpcfgQuantum> quantums, MetricalLpcfgQuantum next) {
		int beatLength = quantums.size() / measure.getBeatsPerBar();
		
		for (int beat = 0; beat < measure.getBeatsPerBar(); beat++) {
			List<MetricalLpcfgQuantum> beatQuantums = quantums.subList(beat * beatLength, (beat + 1) * beatLength);
			MetricalLpcfgQuantum beatNext = beat == measure.getBeatsPerBar() - 1 ? next : quantums.get((beat + 1) * beatLength);
			
			// This starts with an onset, and has no rests, and the following quantum is either a beat or a rest.
			if (beatQuantums.get(0) == MetricalLpcfgQuantum.ONSET &&
					(!beatQuantums.contains(MetricalLpcfgQuantum.REST) && beatNext != MetricalLpcfgQuantum.TIE)) {
				
				// Here we have a potential match, assuming the notes aren't all of identical length
				int prevIndex = 0;
				int nextIndex = 0;
				int prevLength = 0;
				
				while ((nextIndex = beatQuantums.subList(nextIndex + 1, beatQuantums.size()).indexOf(MetricalLpcfgQuantum.ONSET)) != -1) {
					int length = nextIndex - prevIndex;
					
					if (prevLength != 0 && prevLength != length) {
						addMatch(MetricalLpcfgMatch.BEAT);
						return;
					}
					
					prevLength = length;
					prevIndex = nextIndex;
				}
				
				nextIndex = beatQuantums.size();
				
				int length = nextIndex - prevIndex;
				
				if (prevLength != 0 && prevLength != length) {
					addMatch(MetricalLpcfgMatch.BEAT);
				}
			}
		}
	}
	
	/**
	 * Update the match for the given note.
	 * 
	 * @param note The note we need to check for a match.
	 */
	private void checkMatch(List<MetricalLpcfgQuantum> quantums, MetricalLpcfgQuantum next, int prevOnset) {
		List<Integer> onsetTimes = new ArrayList<Integer>();
		List<Integer> offsetTimes = new ArrayList<Integer>();
		onsetTimes.add(prevOnset);
		
		boolean inOnset = true;
		for (int i = 0; i < quantums.size(); i++) {
			switch (quantums.get(i)) {
				case REST:
					if (inOnset) {
						offsetTimes.add(i);
						inOnset = false;
					}
					break;
					
				case ONSET:
					if (inOnset) {
						offsetTimes.add(i);
					}
					onsetTimes.add(i);
					inOnset = true;
					break;
					
				case TIE:
			}
		}
		
		switch (next) {
			case REST:
				if (inOnset) {
					offsetTimes.add(quantums.size());
				}
				break;
				
			case ONSET:
				offsetTimes.add(quantums.size());
				break;
				
			case TIE:
		}
		
		if (offsetTimes.size() < onsetTimes.size()) {
			offsetTimes.add(Integer.MAX_VALUE);
		}
		
		int subBeatLength = quantums.size() / measure.getBeatsPerBar() / measure.getSubBeatsPerBeat();
		
		for (int i = 0; i < onsetTimes.size(); i++) {
			int startTactus = onsetTimes.get(i);
			int endTactus = offsetTimes.get(i);
			
			while (startTactus < 0 || endTactus < 0) {
				startTactus += quantums.size();
				endTactus += quantums.size();
			}
			
			if (startTactus == Integer.MAX_VALUE || endTactus == Integer.MAX_VALUE) {
				continue;
			}
			
			int noteLengthTacti = Math.max(1, endTactus - startTactus);
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
				updateMatchType(prefixStart, prefixLength, subBeatLength);
			}
			
			// Middle checking
			if (!isFullyMatched() && !isWrong() && middleLength != 0) {
				updateMatchType(middleStart, middleLength, subBeatLength);
			}
			
			// Postfix checking
			if (!isFullyMatched() && !isWrong() && postfixLength != 0) {
				updateMatchType(postfixStart, postfixLength, subBeatLength);
			}
		}
	}

	/**
	 * Update the match for a note with the given start tactus and length.
	 * 
	 * @param startIndex The index at which this note begins, where 0 is the beginning of the bar.
	 * @param noteLength The length of the note in quantums.
	 * @param subBeatLength The length of a sub beat in quantums.
	 */
	private void updateMatchType(int startIndex, int noteLength, int subBeatLength) {
		int beatLength = subBeatLength * measure.getSubBeatsPerBeat();
		int measureLength = beatLength * measure.getBeatsPerBar();
		
		int subBeatOffset = startIndex % subBeatLength;
		int beatOffset = startIndex % beatLength;
		int measureOffset = startIndex % measureLength;
		
		if (matches(MetricalLpcfgMatch.SUB_BEAT)) {
			// Matches sub beat (and not beat)
			
			if (noteLength < subBeatLength) {
				// Note is shorter than a sub beat
				
			} else if (noteLength == subBeatLength) {
				// Note is exactly a sub beat
				
			} else if (noteLength < beatLength) {
				// Note is between a sub beat and a beat in length
				
				// Can only happen when the beat is divided in 3, but this is 2 sub beats
				addMatch(MetricalLpcfgMatch.WRONG);
				
			} else if (noteLength == beatLength) {
				// Note is exactly a beat in length
				
				// Must match exactly
				addMatch(beatOffset == 0 ? MetricalLpcfgMatch.BEAT : MetricalLpcfgMatch.WRONG);
				
			} else {
				// Note is greater than a beat in length
				
				if (noteLength % beatLength != 0) {
					// Not some multiple of the beat length
					addMatch(MetricalLpcfgMatch.WRONG);	
				}
			}
			
		} else if (matches(MetricalLpcfgMatch.BEAT)) {
			// Matches beat (and not sub beat)
			
			if (noteLength < subBeatLength) {
				// Note is shorter than a sub beat
				
				if (subBeatLength % noteLength != 0 || subBeatOffset % noteLength != 0) {
					// Note doesn't divide sub beat evenly
					addMatch(MetricalLpcfgMatch.WRONG);
				}
				
			} else if (noteLength == subBeatLength) {
				// Note is exactly a sub beat
				
				// Must match sub beat exactly
				addMatch(subBeatOffset == 0 ? MetricalLpcfgMatch.SUB_BEAT : MetricalLpcfgMatch.WRONG);
				
			} else if (noteLength < beatLength) {
				// Note is between a sub beat and a beat in length
				
				// Wrong if not aligned with beat at onset or offset
				if (beatOffset != 0 && beatOffset + noteLength != beatLength) {
					addMatch(MetricalLpcfgMatch.WRONG);
				}
				
			} else if (noteLength == beatLength) {
				// Note is exactly a beat in length
				
			} else {
				// Note is longer than a beat in length
				
			}
			
		} else {
			// Matches neither sub beat nor beat
			
			if (noteLength < subBeatLength) {
				// Note is shorter than a sub beat
				
				if (subBeatLength % noteLength != 0 || subBeatOffset % noteLength != 0) {
					// Note doesn't divide sub beat evenly
					addMatch(MetricalLpcfgMatch.WRONG);
				}
				
			} else if (noteLength == subBeatLength) {
				// Note is exactly a sub beat
				
				// Must match sub beat exactly
				addMatch(subBeatOffset == 0 ? MetricalLpcfgMatch.SUB_BEAT : MetricalLpcfgMatch.WRONG);
				
			} else if (noteLength < beatLength) {
				// Note is between a sub beat and a beat in length
				
				// Wrong if not aligned with beat at onset or offset
				if (beatOffset != 0 && beatOffset + noteLength != beatLength) {
					addMatch(MetricalLpcfgMatch.WRONG);
				}
				
			} else if (noteLength == beatLength) {
				// Note is exactly a beat in length
				
				// Must match beat exactly
				addMatch(beatOffset == 0 ? MetricalLpcfgMatch.BEAT : MetricalLpcfgMatch.WRONG);
				
			} else {
				// Note is greater than a beat in length
				
				if (measureLength % noteLength != 0 || measureOffset % noteLength != 0 ||
						beatOffset != 0 || noteLength % beatLength != 0) {
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
	public Measure getMeasure() {
		return measure;
	}

	@Override
	public MetricalLpcfgHierarchyModelState deepCopy() {
		return new MetricalLpcfgHierarchyModelState(this);
	}

	@Override
	public double getScore() {
		return alignmentLogProbability + GLOBAL_WEIGHT * logProbability + (1.0 - GLOBAL_WEIGHT) * localLogProb;
	}
	
	@Override
	public boolean isDuplicateOf(HierarchyModelState state) {
		if (!(state instanceof MetricalLpcfgHierarchyModelState)) {
			return false;
		}
		
		MetricalLpcfgHierarchyModelState lpcfg = (MetricalLpcfgHierarchyModelState) state;
		
		return measure.equals(lpcfg.measure) && anacrusisLength == lpcfg.anacrusisLength;
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
		
		sb.append(measure).append(" anacrusis=").append(anacrusisLength);
		sb.append(" Score=").append(alignmentLogProbability).append(" + ")
			.append(GLOBAL_WEIGHT * logProbability).append(" + ")
			.append((1.0 - GLOBAL_WEIGHT) * localLogProb).append(" = ")
			.append(getScore());
		
		return sb.toString();
	}
}