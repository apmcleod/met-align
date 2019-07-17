package metalign.harmony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import metalign.beat.Beat;
import metalign.hierarchy.Measure;
import metalign.hierarchy.lpcfg.MetricalLpcfgNode;
import metalign.hierarchy.lpcfg.MetricalLpcfgNonterminal;
import metalign.hierarchy.lpcfg.MetricalLpcfgNonterminal.MetricalLpcfgLevel;
import metalign.hierarchy.lpcfg.MetricalLpcfgQuantum;
import metalign.hierarchy.lpcfg.MetricalLpcfgTerminal;
import metalign.hierarchy.lpcfg.MetricalLpcfgTree;

public class ChordBeatTransitionProbabilityTracker {

	/**
	 * For each measure type, the number of possible changes we could have had.
	 * <br>
	 * If there are 2 trees for a single bar, and 1 has a strong downbeat while
	 * the other has a weak downbeat, they each count for 0.5 of a change.
	 */
	private Map<Measure, Map<String, Double>> counts;
	
	/**
	 * For each measure type, how many changes there are for each key.
	 * <br>
	 * If there are 2 trees for a single bar, and 1 has a strong downbeat while
	 * the other has a weak downbeat, they each count for 0.5 of a change.
	 */
	private Map<Measure, Map<String, Double>> changes;
	
	/**
	 * Create a new empty tracker.
	 */
	public ChordBeatTransitionProbabilityTracker() {
		counts = new HashMap<Measure, Map<String, Double>>();
		changes = new HashMap<Measure, Map<String, Double>>();
	}
	
	/**
	 * Add a bars worth of chord changes to the probability trackers.
	 * 
	 * @param measure The measure type of this bar.
	 * @param trees The trees created in this bar.
	 * @param changeBeats The beats on which there was a chord change within this bar.
	 */
	public void addBar(Measure measure, List<MetricalLpcfgTree> trees, List<Beat> changeBeats) {
		// First, do generic (non-strength-based) changes
		double downbeatValue = 0.0;
		double beatValue = 0.0;
		double subBeatValue = 0.0;
		double tatumValue = 0.0;
		
		boolean[] beats = new boolean[measure.getBeatsPerBar()];
		
		// Check all changes
		for (Beat beat : changeBeats) {
			if (beat.isDownbeat()) {
				downbeatValue++;
				beats[0] = true;
				
			} else if (beat.isBeat()) {
				beatValue++;
				if (beat.getBeat() >= beats.length) {
					System.err.println("Beat Error. Found: " + beat + " in measure " + measure);
				} else {
					beats[beat.getBeat()] = true;
				}
				
			} else if (beat.isSubBeat()) {
				subBeatValue++;
				
			} else {
				tatumValue++;
			}
		}
		
		// Update maps
		updateMaps(measure, "BAR", downbeatValue == 1, 1);
		updateMaps(measure, "BEAT", true, beatValue);
		updateMaps(measure, "BEAT", false, measure.getBeatsPerBar() - 1 - beatValue);
		updateMaps(measure, "SUB_BEAT", true, subBeatValue);
		updateMaps(measure, "SUB_BEAT", false, measure.getBeatsPerBar() * (measure.getSubBeatsPerBeat() - 1) - subBeatValue);
		updateMaps(measure, "TATUM", true, tatumValue);
		
		updateMaps(measure, beats.length + Arrays.toString(beats), true, 1);
		
		// Next, check rhythmic-based changes
		if (trees.size() == 0) {
			return;
		}
		
		// Value per tree
		double value = 1.0 / trees.size();
		
		
		// Check rhythmic-based strengths
		for (MetricalLpcfgTree tree : trees) {
			// Check downbeat
			updateTrackers(measure, tree.getMeasure(), value, new Beat(0, 0, 0, 0, 0L), changeBeats);
			
			int beatNum = 0;
			
			// Check beats (including downbeat)
			for (MetricalLpcfgNode node : tree.getMeasure().getChildren()) {
				MetricalLpcfgNonterminal beatNode = (MetricalLpcfgNonterminal) node;
				
				updateTrackers(measure, beatNode, value, new Beat(0, beatNum, 0, 0, 0L), changeBeats);
				
				// If this beat leads directly to  a terminal, create a non-terminal out of it
				// So that we can check its sub-beats.
				if (beatNode.getChildren().get(0) instanceof MetricalLpcfgTerminal) {
					MetricalLpcfgNonterminal newBeatNode = new MetricalLpcfgNonterminal(MetricalLpcfgLevel.BEAT);
					
					List<MetricalLpcfgQuantum> quantums = new ArrayList<MetricalLpcfgQuantum>(measure.getSubBeatsPerBeat());
					
					switch (beatNode.getChildren().get(0).getQuantum().get(0)) {
						case ONSET:
							quantums.add(MetricalLpcfgQuantum.ONSET);
							// Fall through
						case TIE:
							while (quantums.size() < measure.getSubBeatsPerBeat()) {
								quantums.add(MetricalLpcfgQuantum.TIE);
							}
							break;
							
						case REST:
							while (quantums.size() < measure.getSubBeatsPerBeat()) {
								quantums.add(MetricalLpcfgQuantum.REST);
							}
							break;
					}
					
					// Add new terminal sub beats to this new beat
					for (int i = 0; i < measure.getSubBeatsPerBeat(); i++) {
						MetricalLpcfgNonterminal nonTerminal = new MetricalLpcfgNonterminal(MetricalLpcfgLevel.SUB_BEAT);
						nonTerminal.addChild(new MetricalLpcfgTerminal(quantums.subList(i, i + 1), 1, measure.getSubBeatsPerBeat()));
						newBeatNode.addChild(nonTerminal);
					}
					
					// Set strengths and make active
					newBeatNode.fixChildrenTypes();
					beatNode = newBeatNode;
				}
				
				// Check sub beats (including beats)
				int subBeatNum = 0;
				for (MetricalLpcfgNode subBeatNode : beatNode.getChildren()) {
					updateTrackers(measure, (MetricalLpcfgNonterminal) subBeatNode, value,
							new Beat(0, beatNum, subBeatNum, 0, 0L), changeBeats);
					
					subBeatNum++;
				}
				
				beatNum++;
			}
		}
	}
	
	/**
	 * Update the trackers ({@link #changes} and {@link #counts}). This method searches changeBeats
	 * for any change, and then calls {@link #updateMaps(Measure, String, boolean, double)}.
	 * 
	 * @param measure The measure type.
	 * @param node The current node.
	 * @param value The value to add to the trackers.
	 * @param beat The beat on which this node lies.
	 * @param changeBeats The beats on which there was a chord change.
	 */
	private void updateTrackers(Measure measure, MetricalLpcfgNonterminal node, double value, Beat beat, List<Beat> changeBeats) {
		// Check for change
		boolean change = false;
		for (Beat changeBeat : changeBeats) {
			if (changeBeat.getBeat() == beat.getBeat() &&
					changeBeat.getSubBeat() == beat.getSubBeat() && changeBeat.getTatum() == beat.getTatum()) {
				if (!beat.isDownbeat()) {
					change = true;
				}
				change = true;
				break;
			}
		}
		
		for (String key : getKeys(node, beat, measure)) {
			updateMaps(measure, key, change, value);
		}
	}
	
	/**
	 * Update the tracking maps ({@link #changes} and {@link #counts}). You shouldn't call this directly.
	 * Rather, use {@link #updateTrackers(Measure, MetricalLpcfgNonterminal, double, Beat, List)},
	 * which calls this after calculating key and change.
	 * 
	 * @param measure The measure type this is.
	 * @param key The key we are looking for in the map.
	 * @param change Whether there was a change here.
	 * @param value The value to add to the maps.
	 */
	private void updateMaps(Measure measure, String key, boolean change, double value) {
		if (!counts.containsKey(measure)) {
			counts.put(measure, new HashMap<String, Double>());
			changes.put(measure, new HashMap<String, Double>());
		}
		
		Map<String, Double> thisCounts = counts.get(measure);
		Map<String, Double> thisChanges = changes.get(measure);
		
		if (thisCounts.containsKey(key)) {
			thisCounts.put(key, thisCounts.get(key) + value);
			
			if (change) {
				thisChanges.put(key, thisChanges.get(key) + value);
			}
			
		} else {
			thisCounts.put(key, value);
			thisChanges.put(key, change ? value : 0.0); 
		}
	}
	
	/**
	 * Get the strength-based key of a given nonterminal node.
	 * 
	 * @param node The non-terminal node whose key we want.
	 * @return The type string (level and strength) of the given node. Or, the strength of the 1st beat
	 * plus "_BAR" if this is a bar node.
	 */
	private List<String> getKeys(MetricalLpcfgNonterminal node, Beat beat, Measure measure) {
		List<String> keys = new ArrayList<String>();
		
		switch (node.getLevel()) {
		case BEAT:
			keys.add("BEAT_" + node.getTypeString());
			keys.add("BEAT_" + beat.getBeat() + "/" + measure.getBeatsPerBar());
			keys.add("BEAT_" + beat.getBeat() + "/" + measure.getBeatsPerBar() + "_" + node.getTypeString());
			break;
			
		case BAR:
			keys.add("BAR_" + ((MetricalLpcfgNonterminal) node.getChildren().get(0)).getTypeString());
			break;
			
		case SUB_BEAT:
			keys.add("SUB_BEAT_" + node.getTypeString());
			keys.add("SUB_BEAT_" + beat.getBeat() + "," + beat.getSubBeat() + "/" + measure.getSubBeatsPerBeat());
			keys.add("SUB_BEAT_" + beat.getBeat() + "," + beat.getSubBeat() + "/" + measure.getSubBeatsPerBeat() + "_" + node.getTypeString());
			break;
			
		default:
			break;
		}
		
		return keys;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		Set<String> internalKeys = new TreeSet<String>();
		
		for (Measure measure : changes.keySet()) {
			internalKeys.addAll(changes.get(measure).keySet());
		}
		
		for (String internalKey : internalKeys) {
			double count = 0.0;
			double change = 0.0;
			
			for (Measure measure : changes.keySet()) {
				if (changes.get(measure).containsKey(internalKey)) {
					count += counts.get(measure).get(internalKey);
					change += changes.get(measure).get(internalKey);
				}
			}
			
			sb.append(internalKey).append(": ").append(change).append(" / ").append(count).append(" = ").append(change / count).append('\n');
		}
		
		return sb.toString();
	}
}
