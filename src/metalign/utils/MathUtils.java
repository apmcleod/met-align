package metalign.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import metalign.beat.Beat;

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
	 * Get the two beats from the given List closest to the given time.
	 * This is just a binary search but with beats, plus it returns two Beats.
	 * 
	 * @param time The time we want the two closest beats.
	 * @param beats A List of the Beats to search.
	 * @return A List of the two Beats closest to the given time.
	 */
	public static List<Beat> getBeatsAroundTime(long time, List<Beat> beats) {
		int min = 0;
		int max = beats.size() - 1;
		
		while (min <= max) {
			int mid = (min + max) / 2;
			if (mid == 0) {
				return beats.subList(min, max + 1);
			}
			if (beats.get(mid).getTime() > time && beats.get(mid - 1).getTime() <= time) {
				return beats.subList(mid - 1, mid + 1);
				
			} else if (beats.get(mid).getTime() <= time) {
				min = mid + 1;
				
			} else {
				max = mid - 1;
			}
		}
		
		List<Beat> beatsAroundTime = new ArrayList<Beat>(2);
		beatsAroundTime.add(beats.get(beats.size() - 1));
		beatsAroundTime.add(beats.get(beats.size() - 1));
		return beatsAroundTime;
	}

	/**
	 * Get the index of the time prior to the given time.
	 * This is just a binary search.
	 * 
	 * @param time The time prior to which we want the index. 
	 * @param beats The times of the beats.
	 * @return The index just prior to the given time.
	 */
	public static int getFirstIndexAroundTime(long time, List<Integer> beats) {
		int search = Collections.binarySearch(beats, (int) time);
		
		if (search < 0) {
			search = -(search + 1);
			
			if (search != 0) {
				search--;
			}
		}
		
		return search;
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
