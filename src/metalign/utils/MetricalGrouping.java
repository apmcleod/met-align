package metalign.utils;

/**
 * A <code>MetricalGrouping</code> represents a single grouping in the MetricalTree of a piece,
 * containing its start time and its end time. Two groupings are considered equal if both their
 * start times and their end times are within {@link Evaluation#BEAT_EPSILON} of each other.
 * <br>
 * This class implements Comparable, and is sorted first by its start time, and then its end time.
 * Thus, it can be used in a TreeSet.
 * 
 * @author Andrew McLeod - 2 June, 2017
 */
public class MetricalGrouping implements Comparable<MetricalGrouping> {
	/**
	 * The start time of this grouping.
	 */
	private final long startTime;
	
	/**
	 * The end time of this grouping.
	 */
	private final long endTime;
	
	/**
	 * Create a new grouping with the given start and end time.
	 * 
	 * @param start {@link #startTime}
	 * @param end {@link #endTime}
	 */
	public MetricalGrouping(long start, long end) {
		startTime = start;
		endTime = end;
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MetricalGrouping)) {
			return false;
		}
		
		MetricalGrouping grouping = (MetricalGrouping) o;
		return Math.abs(grouping.startTime - startTime) <= Evaluation.BEAT_EPSILON && Math.abs(grouping.endTime - endTime) <= Evaluation.BEAT_EPSILON;
	}

	@Override
	public int compareTo(MetricalGrouping o) {
		if (o == null) {
			return -1;
		}
		
		if (Math.abs(o.startTime - startTime) > Evaluation.BEAT_EPSILON) {
			return Long.compare(o.startTime, startTime);
		}
		
		if (Math.abs(o.endTime - endTime) > Evaluation.BEAT_EPSILON) {
			return Long.compare(o.endTime, endTime);
		}
		
		return 0;
	}
	
	@Override
	public String toString() {
		return (startTime + "-" + endTime);
	}
}
