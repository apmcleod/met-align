package metalign.hierarchy.lpcfg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import metalign.utils.MathUtils;

/**
 * A <code>MetricalLpcfgNonterminal</code> is an internal node in a metrical lpcfg tree.
 * 
 * @author Andrew McLeod - 25 April, 2016
 */
public class MetricalLpcfgNonterminal implements MetricalLpcfgNode, Serializable {
	/**
	 * Version 3
	 */
	private static final long serialVersionUID = 3L;
	
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
	
	/**
	 * Create a new non-terminal as a deep copy of the given one.
	 * 
	 * @param nonterminal The non-terminal we want a deep copy of.
	 */
	public MetricalLpcfgNonterminal(MetricalLpcfgNonterminal nonterminal) {
		type = nonterminal.type;
		level = nonterminal.level;
		
		children = new ArrayList<MetricalLpcfgNode>();
		for (MetricalLpcfgNode child : nonterminal.children) {
			children.add(child.deepCopy());
		}
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
	 * Save head for faster computation.
	 */
	private MetricalLpcfgHead head = null;
	
	/**
	 * Get the head of this non-terminal. This is calculated recursively as the max of the heads
	 * of all of its children with onsets shifted, or a length 0 head if it has no children.
	 * 
	 *  @return The head of this non-terminal.
	 */
	@Override
	public MetricalLpcfgHead getHead() {
		if (head != null) {
			return head;
		}
		
		if (children.size() == 1 && children.get(0) instanceof MetricalLpcfgTerminal) {
			return head = children.get(0).getHead();
		}
		
		// Head is either a head of a child, or some head overlapping a boundary
		return head = MetricalLpcfgTerminal.generateHead(getQuantum(), level == MetricalLpcfgLevel.BEAT ? 1 : children.size(), 1);
	}
	
	@Override
	public List<MetricalLpcfgQuantum> getQuantum() {
		List<MetricalLpcfgQuantum> quantum = new ArrayList<MetricalLpcfgQuantum>();
		
		// Calculate LCM
		int lcm = 1;
		for (MetricalLpcfgNode child : children) {
			lcm = child.getQuantum().size() * lcm / MathUtils.getGCF(lcm, child.getQuantum().size());
		}
		
		// Concatenate children quantums, scaled up to LCM length
		for (MetricalLpcfgNode child : children) {
			List<MetricalLpcfgQuantum> childQuantum = child.getQuantum();
			
			int toAdd = lcm / childQuantum.size();
			
			for (MetricalLpcfgQuantum q : childQuantum) {
				quantum.add(q);
				for (int i = 1; i < toAdd; i++) {
					quantum.add(q == MetricalLpcfgQuantum.REST ? MetricalLpcfgQuantum.REST : MetricalLpcfgQuantum.TIE);
				}
			}
		}
		
		return quantum;
	}
	
	/**
	 * Fix the {@link #type}s of all of the {@link #children} of this non-terminal. If a child is
	 * a terminal node, do nothing to it.
	 */
	public void fixChildrenTypes() {
		if (children.size() == 1) {
			return;
		}
		
		MetricalLpcfgHead min = MetricalLpcfgHead.MAX_HEAD;
		MetricalLpcfgHead max = MetricalLpcfgHead.MIN_HEAD;
		
		for (MetricalLpcfgNode child : children) {
			MetricalLpcfgHead childHead = child.getHead();
			
			if (min.compareTo(childHead) < 0) {
				min = childHead;
			}
			
			if (max.compareTo(childHead) > 0) {
				max = childHead;
			}
		}
		
		// EVEN is default. No need to change for those.
		if (!max.equals(min)) {
			// Strong (max) and weak (other)
			for (MetricalLpcfgNode child : children) {
				((MetricalLpcfgNonterminal) child).setType(child.getHead().equals(max) ?
							MetricalLpcfgType.STRONG : MetricalLpcfgType.WEAK);
			}
		}
	}
	
	@Override
	public MetricalLpcfgNonterminal deepCopy() {
		return new MetricalLpcfgNonterminal(this);
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
