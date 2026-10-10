package com.lootluck.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

/** Full scroll extent with Swing children only for visible rows plus a small buffer. */
final class VirtualItemList extends JPanel
{
	private final int[] offsets;
	private int squareColumns;
	private int expandedSquareRow=-1,expandedSquareHeight,squarePitch;
	private final IntFunction<JComponent> factory;
	private final IntConsumer released;
	private final Map<Integer,JComponent> mounted=new HashMap<>();
	private JViewport viewport;
	// JViewport already repaints the exposed viewport region when it scrolls.
	private final ChangeListener scrolled=event->doLayout();
	VirtualItemList(List<Integer> heights,IntFunction<JComponent> factory,IntConsumer released)
	{
		this.factory=factory;this.released=released;setLayout(null);setOpaque(false);setAlignmentX(LEFT_ALIGNMENT);
		offsets=new int[heights.size()+1];for(int i=0;i<heights.size();i++) { offsets[i+1]=Math.addExact(offsets[i],heights.get(i)); }
	}
	VirtualItemList(int rows,int columns,IntFunction<JComponent> factory,IntConsumer released)
	{
		this(rows,columns,-1,0,factory,released);
	}
	VirtualItemList(int rows,int columns,int expandedRow,int expandedHeight,IntFunction<JComponent> factory,IntConsumer released)
	{
		this(java.util.Collections.nCopies(rows,42),factory,released);squareColumns=columns;expandedSquareRow=expandedRow;expandedSquareHeight=expandedHeight;resizeSquares(210);
	}
	private boolean resizeSquares(int width)
	{
		if(squareColumns==0) { return false; }
		int pitch=Math.max(1,(width-2*(squareColumns-1))/squareColumns)+2;
		if(squarePitch==pitch) { return false; }squarePitch=pitch;
		for(int i=0;i<offsets.length-1;i++) { offsets[i+1]=Math.addExact(offsets[i],pitch+(i==expandedSquareRow?expandedSquareHeight:0)); }return true;
	}
	@Override public void setBounds(int x,int y,int width,int height)
	{
		super.setBounds(x,y,width,height);if(resizeSquares(width))
		{
			// BoxLayout can cache the old height while its containers are already invalid.
			// Clear each enclosing layout cache so the next pass reserves the new row extent.
			for(java.awt.Container parent=getParent();parent!=null;parent=parent.getParent()) { parent.invalidate();if(parent instanceof JViewport) { break; } }
			revalidate();
		}
	}
	@Override public Dimension getPreferredSize() { return new Dimension(210,offsets[offsets.length-1]); }
	@Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); }
	@Override public void addNotify()
	{
		super.addNotify();viewport=(JViewport)SwingUtilities.getAncestorOfClass(JViewport.class,this);if(viewport!=null) { viewport.addChangeListener(scrolled); }
	}
	@Override public void removeNotify()
	{
		if(viewport!=null) { viewport.removeChangeListener(scrolled);viewport=null; }super.removeNotify();
	}
	private int rowAt(int y)
	{
		int low=0,high=offsets.length-1;
		while(low<high) { int middle=(low+high+1)/2;if(offsets[middle]<=y) { low=middle; }else { high=middle-1; } }
		return low;
	}
	@Override public void doLayout()
	{
		Rectangle visible=getVisibleRect();
		// Detached previews/tests have no scroll viewport; keep their initial work bounded too.
		if(viewport==null) { visible.height=Math.min(visible.height,600); }
		int first=rowAt(Math.max(0,visible.y-100)),last=rowAt(Math.min(getHeight(),visible.y+visible.height+100));
		if(visible.height<=0) { first=0;last=-1; }
		final int start=first,end=last;
		mounted.entrySet().removeIf(entry->{if(entry.getKey()<start||entry.getKey()>end) { remove(entry.getValue());released.accept(entry.getKey());return true; }return false;});
		for(int i=first;i<=last&&i<offsets.length-1;i++)
		{
			JComponent row=mounted.get(i);boolean created=row==null;if(created) { row=factory.apply(i);mounted.put(i,row);add(row); }
			int height=offsets[i+1]-offsets[i];
			boolean changed=row.getX()!=0||row.getY()!=offsets[i]||row.getWidth()!=getWidth()||row.getHeight()!=height;
			if(created||changed||!row.isValid()) { row.setBounds(0,offsets[i],getWidth(),height);layoutChildren(row); }
			if(created) { row.repaint(); }
		}
	}
	private static void layoutChildren(java.awt.Container container)
	{
		container.doLayout();for(java.awt.Component child:container.getComponents()) { if(child instanceof java.awt.Container) { layoutChildren((java.awt.Container)child); } }
	}
}
