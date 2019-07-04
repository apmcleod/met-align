package metalign.harmony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import metalign.beat.Beat;
import metalign.hierarchy.Measure;
import metalign.hierarchy.lpcfg.MetricalLpcfgNode;
import metalign.hierarchy.lpcfg.MetricalLpcfgNonterminal;
import metalign.hierarchy.lpcfg.MetricalLpcfgTerminal;
import metalign.hierarchy.lpcfg.MetricalLpcfgTree;

public class ChordProbabilityTracker {

	private Map<Measure, Map<String, Double>> counts;
	private Map<Measure, Map<String, Double>> changes;
	
	public ChordProbabilityTracker() {
		counts = new HashMap<Measure, Map<String, Double>>();
		changes = new HashMap<Measure, Map<String, Double>>();
	}
	
	public void addBar(List<MetricalLpcfgTree> trees, List<Beat> changeBeats) {
		if (trees.size() == 0) {
			return;
		}
		
		Measure measure = trees.get(0).getMeasure().getMeasure();
		double value = 1.0 / trees.size();
		
		// Check downbeat
		for (MetricalLpcfgTree tree : trees) {
			
			// Check for change
			boolean change = false;
			for (Beat changeBeat : changeBeats) {
				if (changeBeat.isDownbeat()) {
					change = true;
					break;
				}
			}
			
			// Go through each key, updating trackers
			updateTrackers(measure, tree.getMeasure(), value, new Beat(0, 0, 0, 0, 0L), changeBeats);
		}
		
		// Check beats (including downbeat)
		for (MetricalLpcfgTree tree : trees) {
			int beatNum = 0;
			
			for (MetricalLpcfgNode node : tree.getMeasure().getChildren()) {
				MetricalLpcfgNonterminal beatNode = (MetricalLpcfgNonterminal) node;
				
				// Go through each key, updating trackers
				updateTrackers(measure, beatNode, value, new Beat(0, beatNum, 0, 0, 0L), changeBeats);
				
				// Check sub beats (including beats)
				int subBeatNum = 0;
				for (MetricalLpcfgNode subBeatNode : beatNode.getChildren()) {
					if (subBeatNode instanceof MetricalLpcfgTerminal) {
						// TODO
					} else {
						// TODO
					}
					
					subBeatNum++;
				}
				
				beatNum++;
			}
		}
	}
	
	private void updateTrackers(Measure measure, MetricalLpcfgNonterminal node, double value, Beat beat, List<Beat> changeBeats) {
		Map<String, Double> thisCounts = counts.get(measure);
		Map<String, Double> thisChanges = changes.get(measure);
		
		// Check for change
		boolean change = false;
		for (Beat changeBeat : changeBeats) {
			if (changeBeat.getBeat() == beat.getBeat() &&
					changeBeat.getSubBeat() == beat.getSubBeat() && changeBeat.getTatum() == beat.getTatum()) {
				change = true;
				break;
			}
		}
		
		for (String key : getKeys(node)) {
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
	}
	
	private List<String> getKeys(MetricalLpcfgNonterminal node) {
		List<String> keys = new ArrayList<String>();
		
		switch (node.getLevel()) {
		case BEAT:
			keys.add("BEAT");
			keys.add(node.getTypeString());
			break;
			
		case BAR:
			keys.add("BAR");
			keys.add(((MetricalLpcfgNonterminal) node.getChildren().get(0)).getTypeString() + "_BAR");
			break;
			
		case SUB_BEAT:
			keys.add("SUBBEAT");
			keys.add(node.getTypeString());
			break;
		}
		
		return keys;
	}
}
