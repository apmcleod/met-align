package metalign.hierarchy.lpcfg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A <code>MetricalLpcfgNonterminal</code> is an internal node in a metrical lpcfg tree.
 * 
 * @author Andrew McLeod - 25 April, 2016
 */
public class MetricalLpcfgNonterminal implements MetricalLpcfgNode, Serializable {
	/**
	 * Version 2
	 */
	private static final long serialVersionUID = 2L;
	
	/**
	 * An enum representing the type of a {@link MetricalLpcfgNonterminal}
	 * 
	 * @author Andrew McLeod - 26 April, 2016
	 */
	public enum MetricalLpcfgType {
		/**
		 * This is {@link MetricalLpcfgMeasure} (which should be at the head of the tree).
		 */
		MEASURE,
		
		/**
		 * This node's head length is less than none of its siblings' and greater than at least
		 * one of its siblings'.
		 */
		STRONG,
		
		/**
		 * This node's head length is less than at least one of its siblings'.
		 */
		WEAK,
		
		/**
		 * This node's head length is equals to all of its siblings'.
		 */
		EVEN;
	}
	
	/**
	 * An enum representing the level of a {@link MetricalLpcfgNonterminal}
	 * 
	 * @author Andrew McLeod - 26 April, 2016
	 */
	public enum MetricalLpcfgLevel {
		/**
		 * This node is at the measure level of a tree.
		 */
		MEASURE,
		
		/**
		 * This node is at the beat level of a tree.
		 */
		BEAT,
		
		/**
		 * This node is at the sub beat level of a tree.
		 */
		SUB_BEAT;
	}
	
	/**
	 * The type of this node.
	 */
	private MetricalLpcfgType type;
	
	/**
	 * The level of this node.
	 */
	private final MetricalLpcfgLevel level;

	/**
	 * A List of the children of this node.
	 */
	private final List<MetricalLpcfgNode> children;
	
	/**
	 * Create a new non-terminal, initially of {@link #type} {@link MetricalLpcfgType#EVEN}.
	 * 
	 * @param level {@link #level}
	 */
	public MetricalLpcfgNonterminal(MetricalLpcfgLevel level) {
		type = MetricalLpcfgType.EVEN;
		this.level = level;
		children = new ArrayList<MetricalLpcfgNode>();
	}
	
	@Override
	public boolean isEmpty() {
		for (MetricalLpcfgNode child : children) {
			if (!child.isEmpty()) {
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public boolean startsWithRest() {
		return children.isEmpty() || children.get(0).startsWithRest();
	}

	/**
	 * Get the children of this non-terminal.
	 * 
	 * @return {@link #children}
	 */
	public List<MetricalLpcfgNode> getChildren() {
		return children;
	}
	
	/**
	 * Add a new node as a child of this non-terminal. The node will be added at
	 * the end of the current child list.
	 * 
	 * @param child The new child node to add to {@link #children}
	 */
	public void addChild(MetricalLpcfgNode child) {
		children.add(child);
	}
	
	/**
	 * Set the {@link #type} to the given value.
	 * 
	 * @param type {@link #type}
	 */
	public void setType(MetricalLpcfgType type) {
		this.type = type;
	}
	
	/**
	 * Get the String of the type of this node.
	 * 
	 * @return {@link #type}'s toString.
	 */
	public String getTypeString() {
		return (type == null ? "NULL" : type.toString()) + "_" + level.toString();
	}
	
	/**
	 * Get the level of this non-terminal.
	 * 
	 * @return {@link #level}
	 */
	public MetricalLpcfgLevel getLevel() {
		return level;
	}
	
	/**
	 * Save terminal for faster computation.
	 */
	private MetricalLpcfgTerminal terminal = null;
	
	@Override
	public MetricalLpcfgTerminal getTerminal() {
		if (terminal != null) {
			return terminal;
		}
		
		List<MetricalLpcfgQuantum> quantumsList = new ArrayList<MetricalLpcfgQuantum>();
		
		for (MetricalLpcfgNode child : children) {
			for (MetricalLpcfgQuantum quantum : child.getTerminal().getOriginalPattern()) {
				quantumsList.add(quantum);
			}
		}
		
		terminal = new MetricalLpcfgTerminal(quantumsList, getLength());
		return terminal;
	}
	
	/**
	 * Get the transition String of this non-terminal.
	 * 
	 * @return The transition String of this non-terminal.
	 */
	public String getTransitionString() {
		List<String> childrenList = new ArrayList<String>(children.size());
		
		for (MetricalLpcfgNode node : children) {
			if (node instanceof MetricalLpcfgTerminal) {
				childrenList.add(node.toString());
				
			} else {
				childrenList.add(((MetricalLpcfgNonterminal) node).getTypeString());
			}
		}
		
		return childrenList.toString();
	}
	
	/**
	 * Get the head of this non-terminal. This is calculated recursively as the max of the heads
	 * of all of its children with onsets shifted, or a length 0 head if it has no children.
	 * 
	 *  @return The head of this non-terminal.
	 */
	@Override
	public MetricalLpcfgHead getHead() {
		return getTerminal().getHead();
	}
	
	/**
	 * Fix the {@link #type}s of all of the {@link #children} of this non-terminal. If a child is
	 * a terminal node, do nothing to it.
	 */
	public void fixChildrenTypes() {
		MetricalLpcfgHead min = new MetricalLpcfgHead(Double.MAX_VALUE, 0, false);
		MetricalLpcfgHead max = new MetricalLpcfgHead();
		
		for (MetricalLpcfgNode child : children) {
			MetricalLpcfgHead childHead = child.getHead();
			
			if (min.compareTo(childHead) < 0) {
				min = childHead;
			}
			
			if (max.compareTo(childHead) > 0) {
				max = childHead;
			}
		}
		
		if (max.equals(min)) {
			// EVEN
			for (MetricalLpcfgNode child : children) {
				if (child instanceof MetricalLpcfgNonterminal) {
					((MetricalLpcfgNonterminal) child).setType(MetricalLpcfgType.EVEN);
				}
			}
			
		} else {
			// Strong (max) and weak (other)
			for (MetricalLpcfgNode child : children) {
				if (child instanceof MetricalLpcfgNonterminal) {
					((MetricalLpcfgNonterminal) child).setType(child.getHead().equals(max) ?
							MetricalLpcfgType.STRONG : MetricalLpcfgType.WEAK);
				}
			}
		}
	}
	
	@Override
	public int getLength() {
		int length = 0;
		
		for (MetricalLpcfgNode child : children) {
			length += child.getLength();
		}
		
		return length;
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MetricalLpcfgNonterminal)) {
			return false;
		}
		
		MetricalLpcfgNonterminal node = (MetricalLpcfgNonterminal) o;
		
		return node.type == type && node.level == level && node.children.equals(children);
	}
	
	@Override
	public String toStringPretty(int depth, String tab) {
		StringBuilder sb = new StringBuilder();
		
		for (int i = 0; i < depth; i++) {
			sb.append(tab);
		}
		
		sb.append(getTypeString());
		sb.append('(').append(getHead()).append(")\n");
		
		depth++;
		for (MetricalLpcfgNode child : children) {
			sb.append(child.toStringPretty(depth, tab)).append('\n');
		}
		
		if (children.size() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		
		return sb.toString();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append(getTypeString());
		sb.append('(').append(getHead()).append(") ").append(children);
		
		return sb.toString();
	}
}
