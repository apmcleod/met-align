package metalign.hierarchy.lpcfg;

import java.io.Serializable;

import metalign.hierarchy.Measure;

/**
 * A <code>MetricalLpcfgMeasure</code> is a type of {@link MetricalLpcfgNonterminal}, which
 * represents the {@link Measure} type of the tree.
 * 
 * @author Andrew McLeod - 25 April, 2016
 */
public class MetricalLpcfgMeasure extends MetricalLpcfgNonterminal implements Serializable {
	/**
	 * Version 1
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The measure of this tree structure.
	 */
	private final Measure measure;
	
	/**
	 * Create a new MetricalLpcfgMeasure node from the given Measure type.
	 * 
	 * @param measure {@link #measure}
	 */
	public MetricalLpcfgMeasure(Measure measure) {
		super(MetricalLpcfgLevel.MEASURE);
		setType(MetricalLpcfgType.MEASURE);
		
		this.measure = measure;
	}

	/**
	 * Create a new measure node as a copy of the given one.
	 * 
	 * @param measure The measure node which we want a copy of.
	 */
	private MetricalLpcfgMeasure(MetricalLpcfgMeasure measure) {
		super(measure);
		
		this.measure = new Measure(measure.measure.getBeatsPerBar(), measure.measure.getSubBeatsPerBeat());
	}
	
	/**
	 * Get the Measure type of this node.
	 * 
	 * @return {@link #measure}
	 */
	public Measure getMeasure() {
		return measure;
	}
	
	/**
	 * Get the type String of this measure.
	 * 
	 * @return The toString of the {@link #measure}.
	 */
	@Override
	public String getTypeString() {
		return measure.toString();
	}

	@Override
	public MetricalLpcfgMeasure deepCopy() {
		return new MetricalLpcfgMeasure(this);
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MetricalLpcfgMeasure)) {
			return false;
		}
		
		MetricalLpcfgMeasure measure = (MetricalLpcfgMeasure) o;
		
		return measure.measure.equals(this.measure) && measure.getChildren().equals(getChildren());
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append(measure).append(' ').append(getChildren());
		
		return sb.toString();
	}
	
	@Override
	public String toStringPretty(int depth, String tab) {
		StringBuilder sb = new StringBuilder();
		
		for (int i = 0; i < depth; i++) {
			sb.append(tab);
		}
		
		sb.append(measure);
		sb.append('(').append(getHead()).append(")\n");
		
		depth++;
		for (MetricalLpcfgNode child : getChildren()) {
			sb.append(child.toStringPretty(depth, tab)).append('\n');
		}
		
		if (getChildren().size() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		
		return sb.toString();
	}
}
