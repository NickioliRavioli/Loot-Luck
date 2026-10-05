package com.lootluck.ui;

import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.geom.*;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JButton;
import javax.swing.Timer;

/** One shared repaint clock; effects only paint inside visible item buttons. */
final class EffectButton extends JButton
{
	private static final Set<EffectButton> BUTTONS=Collections.newSetFromMap(new WeakHashMap<>());
	private static final Timer CLOCK=new Timer(50,event->{
		for(EffectButton button:BUTTONS) { if(button.isShowing()&&!button.getVisibleRect().isEmpty()) { button.repaint(); } }
	});
	private final ItemEffect effect;
	private final Color rarity;
	EffectButton(ItemEffect effect,Color rarity)
	{
		this.effect=effect==null?ItemEffect.NONE:effect;this.rarity=rarity==null?new Color(160,160,160):rarity;
		addHierarchyListener(event->{if((event.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0) { updateClock(); }});
	}
	@Override public void addNotify() { super.addNotify();if(effect!=ItemEffect.NONE) { BUTTONS.add(this); }updateClock(); }
	@Override public void removeNotify() { BUTTONS.remove(this);updateClock();super.removeNotify(); }
	private static void updateClock()
	{
		if(BUTTONS.stream().anyMatch(Component::isShowing)) { if(!CLOCK.isRunning()) { CLOCK.start(); } }
		else { CLOCK.stop(); }
	}
	@Override protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D g=(Graphics2D)graphics.create();
		try { paintEffect(g,getWidth(),getHeight(),effect,rarity,System.nanoTime()/1_000_000_000.0); }
		finally { g.dispose(); }
	}
	static void paintEffect(Graphics2D g,int width,int height,ItemEffect effect,Color rarity,double seconds)
	{
		if(width<=0||height<=0||effect==ItemEffect.NONE) { return; }
		g.clipRect(2,2,Math.max(0,width-4),Math.max(0,height-4));
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		Color gold=new Color(255,203,85);double phase=seconds%6/6;
		switch(effect)
		{
			case SHIMMER:
				wash(g,width,height,gold,35);float sweep=(float)(phase*(width+height*2)-height);
				g.setPaint(new LinearGradientPaint(sweep-height,0,sweep+height,height,new float[]{0,.5f,1},new Color[]{alpha(gold,0),alpha(gold,100),alpha(gold,0)}));g.fillRect(0,0,width,height);break;
			case SPARKLES:
				wash(g,width,height,gold,24);particles(g,width,height,gold,seconds,false);break;
			case PRISMATIC:
				Color first=Color.getHSBColor((float)(phase),.65f,1),second=Color.getHSBColor((float)((phase+.35)%1),.7f,1);
				g.setPaint(new GradientPaint(0,0,alpha(first,80),width,height,alpha(second,65)));g.fillRect(0,0,width,height);edge(g,width,height,first,90);break;
			case STARBURST:
				wash(g,width,height,gold,28);g.setColor(alpha(gold,40));double rotation=seconds*.08;
				for(int i=0;i<10;i++) { double a=rotation+i*Math.PI/5;Path2D ray=new Path2D.Double();ray.moveTo(width*.35,height*.5);ray.lineTo(width*.35+Math.cos(a)*width*2,height*.5+Math.sin(a)*width*2);ray.lineTo(width*.35+Math.cos(a+.08)*width*2,height*.5+Math.sin(a+.08)*width*2);ray.closePath();g.fill(ray); }particles(g,width,height,gold,seconds*.4,false);break;
			case CELEBRATION:
				wash(g,width,height,gold,25);particles(g,width,height,gold,seconds,true);edge(g,width,height,gold,(int)(35+55*(1-phase)));break;
			case RARITY:
				wash(g,width,height,rarity,45);float radius=Math.max(width,height)*.75f;
				g.setPaint(new RadialGradientPaint(new Point2D.Float((float)(width*(.5+.25*Math.sin(seconds*.7))),height*.5f),radius,new float[]{0,1},new Color[]{alpha(rarity,85),alpha(rarity,0)}));g.fillRect(0,0,width,height);edge(g,width,height,rarity,(int)(55+30*Math.sin(seconds)));break;
			default:break;
		}
	}
	private static Color alpha(Color color,int opacity) { return new Color(color.getRed(),color.getGreen(),color.getBlue(),opacity); }
	private static void wash(Graphics2D g,int w,int h,Color c,int opacity) { g.setPaint(new GradientPaint(0,0,alpha(c,opacity),0,h,alpha(c,8)));g.fillRect(0,0,w,h); }
	private static void edge(Graphics2D g,int w,int h,Color c,int opacity) { g.setColor(alpha(c,opacity));g.drawRoundRect(3,3,w-7,h-7,7,7); }
	private static void particles(Graphics2D g,int w,int h,Color color,double seconds,boolean burst)
	{
		for(int i=0;i<9;i++)
		{
			double age=burst?seconds%6/2:(seconds*.22+i*.137)%1;if(burst&&age>1) { continue; }
			double angle=i*2.399;double x=burst?w*.5+Math.cos(angle)*age*w*.65:((i*.618)%1)*w;
			double y=burst?h*.5+Math.sin(angle)*age*h*.8+age*age*h*.2:h*(1-age);
			g.setColor(alpha(color,(int)(180*(1-age))));int size=i%3==0?3:2;
			g.fill(new Ellipse2D.Double(x,y,size,size));if(i%3==0) { g.draw(new Line2D.Double(x-2,y+1,x+4,y+1));g.draw(new Line2D.Double(x+1,y-2,x+1,y+4)); }
		}
	}
}
