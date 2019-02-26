package metalign.utils;

import java.util.List;

/**
 * A static class of some utility functions that may need to used from multiple places.
 * 
 * @author Andrew McLeod
 */
public class MathUtils {
	/**
	 * A private constructor since this class should never be instantiated.
	 */
	private MathUtils() {}
	
	/**
	 * Evaluate a Gaussian window function with the given mean range and standard deviation. The formula is:
	 * 
	 * G(m1, m2, s) = e ^ [(-1 / 2) * ([m2 - m1] / s) ^ 2]
	 * 
	 * @param mean1 The low end of the mean range.
	 * @param mean2 The high end of the mean range.
	 * @param std The standard deviation.
	 * @return The value of the Gaussian window function.
	 */
	public static double gaussianWindow(double mean1, double mean2, double std) {
		double fraction = (mean2 - mean1) / std;
		double exponent = - (fraction * fraction) / 2.0;
		return Math.exp(exponent);
	}
	
	private static final double NORMAL_FACTOR = 1.0 / Math.sqrt(2 * Math.PI);
	
	/**
	 * Get the standard normal value given the x, mean, and standard deviation.
	 * 
	 * @param x The value we want to measure.
	 * @param mean The mean of the distribution.
	 * @param std The standard deviation of the distribution.
	 * 
	 * @return The standard normal value of the given x.
	 */
	public static double getStandardNormal(double x, double mean, double std) {
		double z = (x - mean) / std;
		double exp = Math.exp(-0.5 * z * z);
		
		return NORMAL_FACTOR * exp;
	}

	/**
	 * Get the index of the first occurrence of the maximum value in the given array.
	 * 
	 * @param array The array whose max we will find.
	 * @return The index of the first occurrence of the maximum value of the given array. Or,
	 * -1 if the maximum vlaue is {@link Double#NEGATIVE_INFINITY} or the array has length 0.
	 */
	public static int getMaxIndex(double[] array) {
		double maxVal = Double.NEGATIVE_INFINITY;
		int maxIndex = -1;
		
		for (int i = 0; i < array.length; i++) {
			if (array[i] > maxVal) {
				maxVal = array[i];
				maxIndex = i;
			}
		}
		
		return maxIndex;
	}
	
	/**
	 * Get the greatest common factor of two integers.
	 * 
	 * @param a The first integer.
	 * @param b The second integer.
	 * @return Their greatest common factor.
	 */
	public static int getGCF(int a, int b) {
	   if (b == 0) {
		   return a;
	   }
		   
	   return getGCF(b, a % b);
	}
	
	/**
	 * Get the index from list of the closest value to the given value.
	 * 
	 * @param allTimes The list to search, sorted in increasing order.
	 * @param value The value we are looking for.
	 * @param start The index to start searching from.
	 * 
	 * @return The index of the closest value to the given value in the given array.
	 */
	public static int linearSearch(List<Long> allTimes, long value, int start) {
		long min = Math.abs(allTimes.get(start) - value);
		
		for (int i = start + 1; i < allTimes.size(); i++) {
			long diff = Math.abs(allTimes.get(i) - value);
			
			if (diff < min) {
				min = diff;
				
			} else {
				// Once we start getting farther away from val, we know we are past it and the min was the previous step.
				return i - 1;
			}
		}
		
		return allTimes.size() - 1;
	}
	
	/**
	 * Get the f-measure given TP, FP, and FN counts.
	 * 
	 * @param truePositives TP
	 * @param falsePositives FP
	 * @param falseNegatives FN
	 * @return The F-1 score, or 0.0 if the result would be NaN.
	 */
	public static double getF1(int truePositives, int falsePositives, int falseNegatives) {
		double precision = ((double) truePositives) / (truePositives + falsePositives);
		double recall = ((double) truePositives) / (truePositives + falseNegatives);
		
		double f1 = 2.0 * recall * precision / (recall + precision);
		return Double.isNaN(f1) ? 0.0 : f1;
	}
}
