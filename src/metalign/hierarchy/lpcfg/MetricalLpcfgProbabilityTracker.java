package metalign.hierarchy.lpcfg;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import metalign.hierarchy.Measure;
import metalign.hierarchy.lpcfg.MetricalLpcfgNonterminal.MetricalLpcfgLevel;
import metalign.utils.SmoothingUtils;

/**
 * A <code>MetricalLpcfgProbabilityTracker</code> keeps track of counts for a
 * {@link metalign.hierarchy.lpcfg.MetricalLpcfg}. Items can be added into these
 * maps using the add... methods, and probabilities can be retrieved thorugh the
 * get...Probability methods.
 * 
 * @author Andrew McLeod - 3 May, 2016
 */
public class MetricalLpcfgProbabilityTracker implements Serializable {
	/**
	 * Version 2
	 */
	private static final long serialVersionUID = 2L;

	/**
	 * The map used for modeling: p(nonterminal -- children | measure, nonterminal, head(nonterminal))
	 */
	private final Map<String, Map<String, Integer>> transitionMap;
	
	private Map<String, Map<Integer, Double>> transitionMapSmoothed = null;
	
	/**
	 * The map used for modeling: p(head(nonterminal) | measure, nonterminal, parentHeadLength)
	 */
	private final Map<String, Map<Double, Integer>> headMap;
	
	private Map<String, Map<Integer, Double>> headMapSmoothed = null;
	
	/**
	 * The map used for modeling: p(head(measure) | measure)
	 */
	private final Map<Measure, Map<Double, Integer>> measureHeadMap;
	
	private Map<Measure, Map<Integer, Double>> measureHeadMapSmoothed = null;
	
	/**
	 * Create a new empty probability tracker.
	 */
	public MetricalLpcfgProbabilityTracker() {
		transitionMap = new HashMap<String, Map<String, Integer>>();
		
		headMap = new HashMap<String, Map<Double, Integer>>();
		
		measureHeadMap = new HashMap<Measure, Map<Double, Integer>>();
	}
	
	/**
	 * Create a new probability tracker as a deep copy of the given one.
	 * 
	 * @param probabilities The probability tracker we want to be a deep copy of.
	 */
	private MetricalLpcfgProbabilityTracker(MetricalLpcfgProbabilityTracker probabilities) {
		transitionMap = new HashMap<String, Map<String, Integer>>();
		for (String key : probabilities.transitionMap.keySet()) {
			
			Map<String, Integer> conditionedMap = new HashMap<String, Integer>();
			for (String transition : probabilities.transitionMap.get(key).keySet()) {
				conditionedMap.put(transition, probabilities.transitionMap.get(key).get(transition));
			}
						
			transitionMap.put(key, conditionedMap);
		}
		
		headMap = new HashMap<String, Map<Double, Integer>>();
		for (String key : probabilities.headMap.keySet()) {
			
			Map<Double, Integer> conditionedMap = new HashMap<Double, Integer>();
			for (Double headLength : probabilities.headMap.get(key).keySet()) {
				conditionedMap.put(headLength, probabilities.headMap.get(key).get(headLength));
			}
		
			headMap.put(key, conditionedMap);
		}
		
		measureHeadMap = new HashMap<Measure, Map<Double, Integer>>();
		for (Measure measure : probabilities.measureHeadMap.keySet()) {
				
			Map<Double, Integer> measureHeadLengthMap = new HashMap<Double, Integer>();
			for (Double headLength : probabilities.measureHeadMap.get(measure).keySet()) {
				measureHeadLengthMap.put(headLength, probabilities.measureHeadMap.get(measure).get(headLength));
			}
			
			measureHeadMap.put(measure, measureHeadLengthMap);
		}
	}
	
	/**
	 * Merge the probabilities and counts from the other tracker into this one.
	 * 
	 * @param other The probability tracker to merge into this one.
	 */
	public void merge(MetricalLpcfgProbabilityTracker other) {
		transitionMapSmoothed = null;
		headMapSmoothed = null;
		measureHeadMapSmoothed = null;
		
		for (String key : other.transitionMap.keySet()) {
			Map<String, Integer> nestedMap = transitionMap.get(key);
			if (nestedMap == null) {
				nestedMap = new HashMap<String, Integer>();
				transitionMap.put(key, nestedMap);
			}
			
			for (String nestedKey : other.transitionMap.get(key).keySet()) {
				Integer oldCountObject = nestedMap.get(nestedKey);
				int oldCount = oldCountObject == null ? 0 : oldCountObject;
				
				nestedMap.put(nestedKey, oldCount + other.transitionMap.get(key).get(nestedKey));
			}
		}
		
		for (String key : other.headMap.keySet()) {
			Map<Double, Integer> nestedMap = headMap.get(key);
			if (nestedMap == null) {
				nestedMap = new HashMap<Double, Integer>();
				headMap.put(key, nestedMap);
			}
			
			for (Double nestedKey : other.headMap.get(key).keySet()) {
				Integer oldCountObject = nestedMap.get(nestedKey);
				int oldCount = oldCountObject == null ? 0 : oldCountObject;
				
				nestedMap.put(nestedKey, oldCount + other.headMap.get(key).get(nestedKey));
			}
		}
		
		for (Measure key : other.measureHeadMap.keySet()) {
			Map<Double, Integer> nestedMap = measureHeadMap.get(key);
			if (nestedMap == null) {
				nestedMap = new HashMap<Double, Integer>();
				measureHeadMap.put(key, nestedMap);
			}
			
			for (Double nestedKey : other.measureHeadMap.get(key).keySet()) {
				Integer oldCountObject = nestedMap.get(nestedKey);
				int oldCount = oldCountObject == null ? 0 : oldCountObject;
				
				nestedMap.put(nestedKey, oldCount + other.measureHeadMap.get(key).get(nestedKey));
			}
		}
	}

	/**
	 * Add a new measure head length mapping. This models p(headLength | measure). It updates
	 * the {@link #measureHeadMap}.
	 * 
	 * @param measure The measure.
	 * @param head The head.
	 */
	public void addMeasureHead(Measure measure, MetricalLpcfgHead head) {
		double headLength = head.getLength();
		// Ensure sub map exists
		Map<Double, Integer> measureMapConditioned = measureHeadMap.get(measure);
		if (measureMapConditioned == null) {
			measureMapConditioned = new HashMap<Double, Integer>();
			measureHeadMap.put(measure, measureMapConditioned);	
		}
		
		// Get count
		Integer oldCount = measureMapConditioned.get(headLength);
		int newCount = 1;
		if (oldCount != null) {
			newCount = oldCount + 1;
		}
		
		// Add count to conditioned map
		measureMapConditioned.put(headLength, newCount);
		
		measureHeadMapSmoothed = null;
	}
	
	/**
	 * Remove measure head length mapping. This models p(headLength | measure). It updates
	 * the {@link #measureHeadMap}.
	 * 
	 * @param measure The measure.
	 * @param head The head.
	 * @throws MetricalLpcfgElementNotFoundException If the measure head to be removed is not found.
	 */
	public void removeMeasureHead(Measure measure, MetricalLpcfgHead head) throws MetricalLpcfgElementNotFoundException {
		double headLength = head.getLength();
		
		// Ensure sub map exists
		Map<Double, Integer> measureMapConditioned = measureHeadMap.get(measure);
		if (measureMapConditioned == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		// Get count
		Integer oldCount = measureMapConditioned.get(headLength);
		if (oldCount == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		int newCount = oldCount - 1;
		if (newCount != 0) {
			measureMapConditioned.put(headLength, newCount);
			
		} else {
			// We need to remove the measure head
			measureMapConditioned.remove(headLength);
			
			if (measureMapConditioned.isEmpty()) {
				// We need to remove the whole conditioned map
				measureHeadMap.remove(measure);
			}
		}
		
		measureHeadMapSmoothed = null;
	}

	/**
	 * Gets the probability p(headLength | measure).
	 * 
	 * @param measure The measure.
	 * @param head The head.
	 * @return p(headLength | measure)
	 */
	public double getMeasureHeadProbability(Measure measure, MetricalLpcfgHead head) {
		if (measureHeadMapSmoothed == null) {
			smooth();
		}
		
		double headLength = head.getLength();
		Map<Double, Integer> measureMapConditioned = measureHeadMap.get(measure);
		if (measureMapConditioned == null) {
			return 0.0;
		}
		
		Integer count = measureMapConditioned.get(headLength);
		if (count == null) {
			count = 0;
		}
		
		return Math.log(measureHeadMapSmoothed.get(measure).get(count));
	}

	/**
	 * Add a new transition mapping. This models p(transition | measure, type, headLength). It updates
	 * {@link #transitionMap}.
	 * 
	 * @param measure The measure.
	 * @param typeString The type.
	 * @param head The head.
	 * @param transitionString The transition String.
	 * @param level The level to use for back-off probabilities.
	 */
	public void addTransition(Measure measure, String typeString, MetricalLpcfgHead head, String transitionString,
			MetricalLpcfgLevel level) {
		String key = encode(measure, typeString, head);
		String backoffKey = encodeBackoff(measure, typeString, head, level);
		
		// Ensure measure map exists
		Map<String, Integer> transitionMapConditioned = transitionMap.get(key);
		if (transitionMapConditioned == null) {
			transitionMapConditioned = new HashMap<String, Integer>();
			transitionMap.put(key, transitionMapConditioned);
		}
		
		// Ensure backoff measure map exists
		Map<String, Integer> transitionMapBackoffConditioned = transitionMap.get(backoffKey);
		if (transitionMapBackoffConditioned == null) {
			transitionMapBackoffConditioned = new HashMap<String, Integer>();
			transitionMap.put(backoffKey, transitionMapBackoffConditioned);
		}
		
		// Get count
		Integer oldCount = transitionMapConditioned.get(transitionString);
		int newCount = 1;
		if (oldCount != null) {
			newCount = oldCount + 1;
		}
		
		// Get backoff count
		Integer oldCountBackoff = transitionMapBackoffConditioned.get(transitionString);
		int newCountBackoff = 1;
		if (oldCountBackoff != null) {
			newCountBackoff = oldCountBackoff + 1;
		}
		
		// Add count to conditioned maps
		transitionMapConditioned.put(transitionString, newCount);
		transitionMapBackoffConditioned.put(transitionString, newCountBackoff);
		
		transitionMapSmoothed = null;
	}
	
	/**
	 * Remove a transition mapping. This models p(transition | measure, type, headLength). It updates
	 * {@link #transitionMap}.
	 * 
	 * @param measure The measure.
	 * @param typeString The type.
	 * @param head The head.
	 * @param transitionString The transition String.
	 * @param level The level to use for back-off probabilities.
	 * @throws MetricalLpcfgElementNotFoundException If the transition to be removed is not found.
	 */
	public void removeTransition(Measure measure, String typeString, MetricalLpcfgHead head, String transitionString,
			MetricalLpcfgLevel level) throws MetricalLpcfgElementNotFoundException {
		String key = encode(measure, typeString, head);
		String backoffKey = encodeBackoff(measure, typeString, head, level);
		
		// Ensure measure map exists
		Map<String, Integer> transitionMapConditioned = transitionMap.get(key);
		if (transitionMapConditioned == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		// Ensure backoff measure map exists
		Map<String, Integer> transitionMapBackoffConditioned = transitionMap.get(backoffKey);
		if (transitionMapBackoffConditioned == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		// Get count
		Integer oldCount = transitionMapConditioned.get(transitionString);
		if (oldCount == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		int newCount = oldCount - 1;
		if (newCount != 0) {
			transitionMapConditioned.put(transitionString, newCount);
			
		} else {
			// We need to delete the key
			transitionMapConditioned.remove(transitionString);
			
			if (transitionMapConditioned.isEmpty()) {
				// We need to remove the whole conditioned map
				transitionMap.remove(key);
			}
		}
		
		// Get backoff count
		Integer oldCountBackoff = transitionMapBackoffConditioned.get(transitionString);
		if (oldCountBackoff == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		int newCountBackoff = oldCountBackoff - 1;
		if (newCountBackoff != 0) {
			transitionMapBackoffConditioned.put(transitionString, newCountBackoff);
			
		} else {
			// We need to delete the key
			transitionMapBackoffConditioned.remove(transitionString);
			
			if (transitionMapBackoffConditioned.isEmpty()) {
				// We need to remove the whole conditioned map
				transitionMap.remove(backoffKey);
			}
		}
		
		transitionMapSmoothed = null;
	}
	
	/**
	 * Gets the probability p(transition | measure, type, headLength).
	 * 
	 * @param measure The measure.
	 * @param typeString The type.
	 * @param head The head.
	 * @param transitionString The transition String.
	 * @param level The level to use in case we need to back off.
	 * @return p(transition | type, headLength)
	 */
	public double getTransitionProbability(Measure measure, String typeString, MetricalLpcfgHead head, String transitionString,
			MetricalLpcfgLevel level) {
		if (transitionMapSmoothed == null) {
			smooth();
		}
		
		String key = encode(measure, typeString, head);
		Map<String, Integer> transitionMapConditioned = transitionMap.get(key);
		
		double logProbability = 0.0;
		
		// Get main probability
		Integer count = 0;
		if (transitionMapConditioned != null) {
			count = transitionMapConditioned.get(transitionString);
			if (count == null) {
				count = 0;
			}
			
			logProbability = Math.log(transitionMapSmoothed.get(key).get(count)); 
		}
		
		// Get backoff probability if needed
		if (count == 0) {
			String backoffKey = encodeBackoff(measure, typeString, head, level);
			Map<String, Integer> transitionMapBackoffConditioned = transitionMap.get(backoffKey);
			
			if (transitionMapBackoffConditioned != null) {
				count = transitionMapBackoffConditioned.get(transitionString);
				if (count == null) {
					count = 0;
				}
				
				logProbability += Math.log(transitionMapSmoothed.get(backoffKey).get(count));
			}
		}
		
		return logProbability;
	}

	/**
	 * Add a new head mapping. This models p(headLength | measure, type, parentHeadLength). It updates
	 * {@link #headMap}.
	 * 
	 * @param measure The measure.
	 * @param typeString The type.
	 * @param parentHead The parent's head.
	 * @param head The head.
	 * @param level The level to use for back-off probabilities.
	 */
	public void addHead(Measure measure, String typeString, MetricalLpcfgHead parentHead, MetricalLpcfgHead head,
			MetricalLpcfgLevel level) {
		String key = encode(measure, typeString, parentHead);
		String backoffKey = encodeBackoff(measure, typeString, parentHead, level);
		double headLength = head.getLength();
		
		// Ensure measure map exists
		Map<Double, Integer> headMapConditioned = headMap.get(key);
		if (headMapConditioned == null) {
			headMapConditioned = new HashMap<Double, Integer>();
			headMap.put(key, headMapConditioned);
		}
		
		// Ensure backoff map exists
		Map<Double, Integer> headMapBackoffConditioned = headMap.get(backoffKey);
		if (headMapBackoffConditioned == null) {
			headMapBackoffConditioned = new HashMap<Double, Integer>();
			headMap.put(backoffKey, headMapBackoffConditioned);
		}
		
		// Get count
		Integer oldCount = headMapConditioned.get(headLength);
		int newCount = 1;
		if (oldCount != null) {
			newCount = oldCount + 1;
		}
		
		// Get count
		Integer oldCountBackoff = headMapBackoffConditioned.get(headLength);
		int newCountBackoff = 1;
		if (oldCountBackoff != null) {
			newCountBackoff = oldCountBackoff + 1;
		}
		
		// Add count to conditioned maps
		headMapConditioned.put(headLength, newCount);
		headMapBackoffConditioned.put(headLength, newCountBackoff);
		
		headMapSmoothed = null;
	}

	/**
	 * Remove a head mapping. This models p(headLength | measure, type, parentHeadLength). It updates
	 * {@link #headMap}.
	 * 
	 * @param measure The measure.
	 * @param typeString The type.
	 * @param parentHead The parent's head.
	 * @param head The head.
	 * @param level The level to use for back-off probabilities.
	 * @throws MetricalLpcfgElementNotFoundException If the head to be removed is not found.
	 */
	public void removeHead(Measure measure, String typeString, MetricalLpcfgHead parentHead, MetricalLpcfgHead head,
			MetricalLpcfgLevel level) throws MetricalLpcfgElementNotFoundException {
		String key = encode(measure, typeString, parentHead);
		String backoffKey = encodeBackoff(measure, typeString, parentHead, level);
		double headLength = head.getLength();
		
		// Ensure measure map exists
		Map<Double, Integer> headMapConditioned = headMap.get(key);
		if (headMapConditioned == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		// Ensure backoff map exists
		Map<Double, Integer> headMapBackoffConditioned = headMap.get(backoffKey);
		if (headMapBackoffConditioned == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		// Get count
		Integer oldCount = headMapConditioned.get(headLength);
		if (oldCount == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		int newCount = oldCount - 1;
		if (newCount != 0) {
			headMapConditioned.put(headLength, newCount);
			
		} else {
			// We need to delete the key
			headMapConditioned.remove(headLength);
			
			if (headMapConditioned.isEmpty()) {
				// We need to remove the whole conditioned map
				headMap.remove(key);
			}
		}
		
		// Get backoff count
		Integer oldCountBackoff = headMapBackoffConditioned.get(headLength);
		if (oldCountBackoff == null) {
			throw new MetricalLpcfgElementNotFoundException((MetricalLpcfgTree) null);
		}
		
		int newCountBackoff = oldCountBackoff - 1;
		if (newCountBackoff != 0) {
			headMapBackoffConditioned.put(headLength, newCountBackoff);
			
		} else {
			// We need to delete the key
			headMapBackoffConditioned.remove(headLength);
			
			if (headMapBackoffConditioned.isEmpty()) {
				// We need to remove the whole conditioned map
				headMap.remove(backoffKey);
			}
		}
		
		headMapSmoothed = null;
	}

	/**
	 * Gets the probability p(headLength | measure, type, parentHeadLength).
	 * 
	 * @param measure The measure.
	 * @param typeString The type.
	 * @param parentHead The parent's head.
	 * @param head The head.
	 * @param level The level to use in case we need to back off.
	 * @return p(headLength | type, parentHeadLength)
	 */
	public double getHeadProbability(Measure measure, String typeString, MetricalLpcfgHead parentHead, MetricalLpcfgHead head,
			MetricalLpcfgLevel level) {
		if (headMapSmoothed == null) {
			smooth();
		}
		
		String key = encode(measure, typeString, parentHead);
		double headLength = head.getLength();
		Map<Double, Integer> headMapConditioned = headMap.get(key);
		
		double logProbability = 0.0;
		
		// Get main probability
		Integer count = 0;
		if (headMapConditioned != null) {
			count = headMapConditioned.get(headLength);
			if (count == null) {
				count = 0;
			}
			
			logProbability = Math.log(headMapSmoothed.get(key).get(count)); 
		}
		
		// Get backoff probability if needed
		if (count == 0) {
			String backoffKey = encodeBackoff(measure, typeString, parentHead, level);
			Map<Double, Integer> headMapBackoffConditioned = headMap.get(backoffKey);
			
			if (headMapBackoffConditioned != null) {
				count = headMapBackoffConditioned.get(headLength);
				if (count == null) {
					count = 0;
				}
				
				logProbability += Math.log(headMapSmoothed.get(backoffKey).get(count));
			}
		}
		
		return logProbability;
	}
	
	/**
	 * Get the set of measures contained within this grammar.
	 * 
	 * @return The Set of the measure types within this grammar.
	 */
	public Set<Measure> getMeasures() {
		return measureHeadMap.keySet();
	}
	
	/**
	 * Get a deep copy of this probability tracker.
	 * 
	 * @return A deep copy of this probability tracker.
	 */
	public MetricalLpcfgProbabilityTracker deepCopy() {
		return new MetricalLpcfgProbabilityTracker(this);
	}
	
	@Override
	public String toString() {
		return transitionMap + "\n" + headMap + "\n" + measureHeadMap;
	}
	
	/**
	 * Calculate and save Good-Turing smoothing
	 */
	public void smooth() {
		transitionMapSmoothed = new HashMap<String, Map<Integer, Double>>();
		for (String key : transitionMap.keySet()) {
			
			Map<Integer, Double> smoothed = SmoothingUtils.getGoodTuringSmoothing(
					SmoothingUtils.getFrequencyMap(transitionMap.get(key).values()),
					SmoothingUtils.getTotalCount(transitionMap.get(key).values()));
			
			transitionMapSmoothed.put(key, smoothed);
		}
		
		headMapSmoothed = new HashMap<String, Map<Integer, Double>>();
		for (String key : headMap.keySet()) {
			
			Map<Integer, Double> smoothed = SmoothingUtils.getGoodTuringSmoothing(
					SmoothingUtils.getFrequencyMap(headMap.get(key).values()),
					SmoothingUtils.getTotalCount(headMap.get(key).values()));
			
			headMapSmoothed.put(key, smoothed);
		}
		
		measureHeadMapSmoothed = new HashMap<Measure, Map<Integer, Double>>();
		for (Measure key : measureHeadMap.keySet()) {
			
			Map<Integer, Double> smoothed = SmoothingUtils.getGoodTuringSmoothing(
					SmoothingUtils.getFrequencyMap(measureHeadMap.get(key).values()),
					SmoothingUtils.getTotalCount(measureHeadMap.get(key).values()));
			
			measureHeadMapSmoothed.put(key, smoothed);
		}
	}
	
	/**
	 * Encode the given measure, typeString, and head into a single String key. This is done so
	 * that we don't need huge nested Maps.
	 * 
	 * @param measure The measure we want to encode.
	 * @param typeString The typeString to encode.
	 * @param head The head to encode.
	 * @return The encoded String.
	 */
	private static String encode(Measure measure, String typeString, MetricalLpcfgHead head) {
		StringBuilder sb = new StringBuilder();
		
		sb.append(measure).append(';');
		sb.append(typeString).append(';');
		sb.append(head.getLength());
		
		return sb.toString();
	}
	
	/**
	 * Encode the given measure's backup type (based on the level), typeString, and headLength into
	 * a single String key. This is done so we don't need huge nested Maps.
	 * 
	 * @param measure The measure whose feature to encode.
	 * @param typeString The typeString to encode.
	 * @param head The head to encode.
	 * @param level The level that decides which feature of the given measure to encode. 
	 * @return The encoded String.
	 */
	private static String encodeBackoff(Measure measure, String typeString, MetricalLpcfgHead head, MetricalLpcfgLevel level) {
		String measureKey = "";

		switch (level) {
			case SUB_BEAT:
				measureKey = "SSB";
				break;
				
			case BEAT:
				measureKey = measure.getSubBeatsPerBeat() + "SB";
				break;
				
			case BAR:
				measureKey = measure.getBeatsPerBar() + "B";
				break;
		}
		
		return encode(measureKey, typeString, head);
	}
	
	/**
	 * Encode the given measureKey, typeString, and headLength into a String. This is done
	 * so we can use a single String key instead of large nested Maps.
	 * 
	 * @param measureKey The measureKey to encode.
	 * @param typeString The typeString to encode.
	 * @param head The head to encode.
	 * @return The encoded String.
	 */
	private static String encode(String measureKey, String typeString, MetricalLpcfgHead head) {
		StringBuilder sb = new StringBuilder();
		
		sb.append(measureKey).append(';');
		sb.append(typeString).append(';');
		sb.append(head.getLength());
		
		return sb.toString();
	}
}
