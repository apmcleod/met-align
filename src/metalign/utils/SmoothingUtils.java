package metalign.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A static class of some static utility functions that perform Good-Turing smoothing as in
 * Church et al (1991).
 * 
 * @author Andrew McLeod - 25 March, 2016
 */
public class SmoothingUtils {
	/**
	 * A private constructor since this class should never be instantiated.
	 */
	private SmoothingUtils() {}
	
	/**
	 * Return the Good-Turing smoothed probability values for our counts.
	 * 
	 * @param frequencyCounts A Map of the frequency of an object to the number of objects with that frequency.
	 * @param count The total count of all objects.
	 * @return A Map of the Good-Turing smoothed probability values for each frequency.
	 */
	public static Map<Integer, Double> getGoodTuringSmoothing(Map<Integer, Integer> frequencyCounts, int count) {
		Map<Integer, Double> goodTuringProbabilities = new TreeMap<Integer, Double>();
		
		List<Integer> frequencies = new ArrayList<Integer>(frequencyCounts.keySet());
		List<Integer> frequencyCountsList = new ArrayList<Integer>(frequencyCounts.values());
		
		int size = frequencies.size();
		
		// zValues for regression
		List<Double> zValues = new ArrayList<Double>(size);
		for (int i = 0; i < size; i++) {
			int prev = i == 0 ? 0 : frequencies.get(i - 1);
			int next = i == size - 1 ? 2 * frequencies.get(i) - prev : frequencies.get(i + 1);
			
			zValues.add(((double) frequencyCountsList.get(i)) / (0.5 * (next - prev)));
		}
		
		// Calculate regression
		double[] x = new double[size];
		double[] y = new double[size];
		for (int i = 0; i < size; i++) {
			x[i] = Math.log(frequencies.get(i));
			y[i] = Math.log(zValues.get(i));
		}
		
		double[] regression = linearRegression(x, y);
		if (Double.isNaN(regression[0])) {
			regression = new double[] {-1.0, x[0] + y[0]};
		}
		
		// Get adjusted counts
		List<Double> goodTuringCounts = new ArrayList<Double>(size);
		for (int i = 0; i < size; i++) {
			int frequency = frequencies.get(i);
			double goodTuringCount = (frequency + 1.0) * getFrequencyCountEstimate(regression, frequency + 1) / getFrequencyCountEstimate(regression, frequency);
			goodTuringCounts.add(goodTuringCount);
		}
		
		// Normalize adjusted counts
		int firstCount = frequencyCountsList.get(0);
		double gtTotalCount = firstCount;
		for (int i = 0; i < size; i++) {
			gtTotalCount += goodTuringCounts.get(i) * frequencyCountsList.get(i);
		}
		
		// Calculate final probabilities
		double gtZeroProb = firstCount / gtTotalCount / count;
		goodTuringProbabilities.put(0, gtZeroProb);
		for (int i = 0; i < size; i++) {
			goodTuringProbabilities.put(frequencies.get(i), goodTuringCounts.get(i) / gtTotalCount);
		}
		
		return goodTuringProbabilities;
	}
	
	/**
	 * Get the frequency count estimate of the given frequency based on the values from the regression
	 * from a log-log plot.
	 * 
	 * @param regression The slope (index 0) and intercept (index 1) of the regression line (from a
	 * log-log plot).
	 * @param frequency The frequency whose count we want.
	 * @return The count of the given frequency from the regression line.
	 */
	private static double getFrequencyCountEstimate(double[] regression, int frequency) {
		return Math.exp(regression[0] * Math.log(frequency) + regression[1]);
	}
	
	/**
	 * Perform linear regression given x and y coordinates.
	 * <br>
	 * This algorithm was adapted from http://introcs.cs.princeton.edu/java/97data/LinearRegression.java.html
	 * 
	 * @param x The x coordinates of our points.
	 * @param y The y coordinates of our points.
	 * @return A length 2 array containing the slope at index 0 and the intercept at index 1.
	 */
	private static double[] linearRegression(double[] x, double[] y) {
        double[] results = new double[2];
        if (x.length != y.length) {
        	return results;
        }

        // first pass: compute xbar and ybar
        double sumx = 0.0;
        double sumy = 0.0;
        for (int i = 0; i < x.length; i++) {
            sumx += x[i];
            sumy += y[i];
        }
        
        double xbar = sumx / x.length;
        double ybar = sumy / x.length;

        // second pass: compute summary statistics
        double xxbar = 0.0;
        double xybar = 0.0;
        for (int i = 0; i < x.length; i++) {
            xxbar += (x[i] - xbar) * (x[i] - xbar);
            xybar += (x[i] - xbar) * (y[i] - ybar);
        }
        
        double slope = xybar / xxbar;
        double intercept = ybar - slope * xbar;
        
        results[0] = slope;
        results[1] = intercept;
        return results;
	}
	
	/**
	 * Add one to the count of the given frequency in the given Map.
	 * 
	 * @param frequencyCounts A Map of the frequency of an object to the number of objects with that frequency.
	 * We will increment one in this Map.
	 * @param frequency The frequency whose count we want to increment.
	 */
	public static void addFrequency(Map<Integer, Integer> frequencyCounts, int frequency) {
		Integer frequencyCount = frequencyCounts.get(frequency);
		
		int newCount = frequencyCount == null ? 1 : frequencyCount + 1;
		frequencyCounts.put(frequency, newCount);
	}
	
	/**
	 * Get the frequency counts of the counts in the given Collection.
	 * 
	 * @param counts The Collection whose frequency counts we want.
	 * @return A Map of a count to the frequency of that count in the given Collection.
	 */
	public static Map<Integer, Integer> getFrequencyMap(Collection<Integer> counts) {
		Map<Integer, Integer> frequencyCounts = new HashMap<Integer, Integer>();
		
		for (Integer count : counts) {
			addFrequency(frequencyCounts, count);
		}
		
		return frequencyCounts;
	}
	
	/**
	 * Get the sum of the counts in the given collection.
	 * 
	 * @param counts A Collection of counts which we want to sum.
	 * @return A sum of the counts in the given Collection.
	 */
	public static int getTotalCount(Collection<Integer> counts) {
		int total = 0;
		
		for (int count : counts) {
			total += count;
		}
		
		return total;
	}
}
