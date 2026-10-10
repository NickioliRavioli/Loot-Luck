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
class EffectButton extends JButton
{
	private static final Set<EffectButton> BUTTONS=Collections.newSetFromMap(new WeakHashMap<>());
	private static final EffectFrames FRAMES=new EffectFrames(2*1024*1024,128);
	private static long frameTick=System.nanoTime()/50_000_000L;
	private static final Timer CLOCK=new Timer(50,event->{
		frameTick=System.nanoTime()/50_000_000L;
		for(EffectButton button:BUTTONS) { if(button.isShowing()&&!button.getVisibleRect().isEmpty()) { button.repaint(); } }
	});
	private final ItemEffect effect;
	private final Color rarity;
	EffectButton(ItemEffect effect,Color rarity)
	{
		this.effect=effect==null?ItemEffect.NONE:effect;this.rarity=rarity==null?new Color(160,160,160):rarity;
		addHierarchyListener(event->{if((event.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0) { updateClock(); }});
	}
	@Override public void addNotify() { super.addNotify();if(effect!=ItemEffect.NONE&&effect!=ItemEffect.RARITY_FADE) { BUTTONS.add(this); }updateClock(); }
	@Override public void removeNotify() { BUTTONS.remove(this);updateClock();super.removeNotify(); }
	private static void updateClock()
	{
		if(BUTTONS.stream().anyMatch(Component::isShowing)) { if(!CLOCK.isRunning()) { frameTick=System.nanoTime()/50_000_000L;CLOCK.start(); } }
		else { CLOCK.stop();FRAMES.clear(); }
	}
	@Override protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		if(effect==ItemEffect.NONE||getWidth()<=4||getHeight()<=4) { return; }
		Graphics2D g=(Graphics2D)graphics.create();
		try
		{
			AffineTransform transform=g.getTransform();
			int pixelWidth=Math.max(1,(int)Math.ceil(getWidth()*Math.hypot(transform.getScaleX(),transform.getShearY())));
			int pixelHeight=Math.max(1,(int)Math.ceil(getHeight()*Math.hypot(transform.getScaleY(),transform.getShearX())));
			g.drawImage(FRAMES.get(effect,rarity,getWidth(),getHeight(),pixelWidth,pixelHeight,frameTick),0,0,getWidth(),getHeight(),null);
		}
		finally { g.dispose(); }
	}
	static void paintEffect(Graphics2D g,int width,int height,ItemEffect effect,Color rarity,double seconds)
	{
		if(width<=0||height<=0||effect==ItemEffect.NONE) { return; }
		g.clipRect(2,2,Math.max(0,width-4),Math.max(0,height-4));
		// Rectangle gradients do not need antialiasing. On Windows, combining it
		// with complex paints can force software tiles followed by D3D transfers.
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
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
				wash(g,width,height,gold,28);smooth(g);g.setColor(alpha(gold,40));double rotation=seconds*.08;
				for(int i=0;i<10;i++) { double a=rotation+i*Math.PI/5;Path2D ray=new Path2D.Double();ray.moveTo(width*.35,height*.5);ray.lineTo(width*.35+Math.cos(a)*width*2,height*.5+Math.sin(a)*width*2);ray.lineTo(width*.35+Math.cos(a+.08)*width*2,height*.5+Math.sin(a+.08)*width*2);ray.closePath();g.fill(ray); }particles(g,width,height,gold,seconds*.4,false);break;
			case CELEBRATION:
				wash(g,width,height,gold,25);particles(g,width,height,gold,seconds,true);edge(g,width,height,gold,(int)(35+55*(1-phase)));break;
			case RARITY:
				wash(g,width,height,rarity,45);float radius=Math.max(width,height)*.75f;
				g.setPaint(new RadialGradientPaint(new Point2D.Float((float)(width*(.5+.25*Math.sin(seconds*.7))),height*.5f),radius,new float[]{0,1},new Color[]{alpha(rarity,85),alpha(rarity,0)}));g.fillRect(0,0,width,height);edge(g,width,height,rarity,(int)(55+30*Math.sin(seconds)));break;
			case FIRE:
				wash(g,width,height,new Color(255,86,20),32);smooth(g);
				for(int i=0;i<12;i++)
				{
					double age=(seconds*.6+i*.173)%1,x=((i*.618)%1)*width+Math.sin(seconds*3+i)*3;
					double y=height*(1-age*.85),size=(1-age)*(5+height*.16);
					g.setPaint(new GradientPaint((float)x,(float)y,alpha(new Color(255,204,60),(int)(135*(1-age))),(float)x,(float)(y+size*2),alpha(new Color(239,61,14),0)));
					Path2D flame=new Path2D.Double();flame.moveTo(x,y-size);flame.curveTo(x+size,y,x+size,y+size,x,y+size);flame.curveTo(x-size,y+size,x-size,y,x,y-size);g.fill(flame);
				}break;
			case RAIN:
				wash(g,width,height,new Color(87,143,208),25);smooth(g);g.setStroke(new BasicStroke(1f));
				for(int i=0;i<15;i++) { double age=(seconds*.7+i*.137)%1,x=((i*.618)%1)*width-age*8,y=age*(height+12)-6;g.setColor(alpha(new Color(143,201,242),90+i%3*20));g.draw(new Line2D.Double(x,y,x-3,y+7)); }break;
			case RARITY_PRISM:
				wash(g,width,height,rarity,60);Color prism=neighbor(rarity,.06f);float center=(float)(width*(.5+.4*Math.sin(seconds*.7)));
				g.setPaint(new LinearGradientPaint(center-width,0,center+width,height,new float[]{0,.4f,.55f,.7f,1},new Color[]{alpha(rarity,25),alpha(rarity,80),alpha(prism,100),alpha(rarity,75),alpha(rarity,20)}));g.fillRect(0,0,width,height);edge(g,width,height,rarity,100);break;
			case RARITY_AURORA:
				wash(g,width,height,rarity,60);
				for(int i=0;i<3;i++) { float x=(float)(width*(.5+.45*Math.sin(seconds*.35+i*1.7))),y=(float)(height*(.5+.4*Math.cos(seconds*.45+i)));g.setPaint(new RadialGradientPaint(new Point2D.Float(x,y),Math.max(width,height)*.6f,new float[]{0,1},new Color[]{alpha(i==1?neighbor(rarity,-.05f):rarity,65),alpha(rarity,0)}));g.fillRect(0,0,width,height); }edge(g,width,height,rarity,85);break;
			case RARITY_OPAL:
				wash(g,width,height,rarity,65);smooth(g);double motion=seconds*.3;
				for(int i=0;i<6;i++) { double x=width*((i*.213+motion*.08)%1),y=height*(.5+.5*Math.sin(motion+i));g.setColor(alpha(i%3==0?neighbor(rarity,.04f):rarity,30+i%3*15));Path2D facet=new Path2D.Double();facet.moveTo(x-width*.3,y);facet.lineTo(x,y-height);facet.lineTo(x+width*.3,y);facet.lineTo(x,y+height);facet.closePath();g.fill(facet); }edge(g,width,height,rarity,95);break;
			case RARITY_FADE:
				iconFade(g,width,height,rarity,90);break;
			case RARITY_PULSE:
				iconFade(g,width,height,rarity,(int)(75+30*Math.sin(seconds*1.6)));break;
			case RARITY_DUST:
				iconFade(g,width,height,rarity,85);smooth(g);
				for(int i=0;i<8;i++)
				{
					double age=(seconds*.16+i*.137)%1,x=iconCenter(width)+age*width*.65,y=height*(.2+(i*.213)% .65)+Math.sin(seconds+i)*2;
					g.setColor(alpha(rarity,(int)(140*(1-age)*(1-age))));g.fill(new Ellipse2D.Double(x,y,i%3==0?3:2,i%3==0?3:2));
				}break;
			case RARITY_SHEEN:
				iconFade(g,width,height,rarity,85);float light=(float)(phase*width*1.4-width*.2);
				int intensity=(int)(55*Math.pow(Math.max(0,1-Math.max(0,light)/width),2));
				g.setPaint(new LinearGradientPaint(light-width*.15f,0,light+width*.15f,height,new float[]{0,.5f,1},new Color[]{alpha(rarity,0),alpha(neighbor(rarity,0),intensity),alpha(rarity,0)}));g.fillRect(0,0,width,height);break;
			default:break;
		}
	}
	private static float iconCenter(int width) { return width<80?width*.5f:22f; }
	private static void iconFade(Graphics2D g,int width,int height,Color rarity,int opacity)
	{
		float center=iconCenter(width);
		g.setPaint(new LinearGradientPaint(0,0,Math.max(1,width),0,new float[]{0,center/width,1},new Color[]{alpha(rarity,(int)(opacity*.7)),alpha(rarity,opacity),alpha(rarity,0)}));
		g.fillRect(0,0,width,height);
	}
	private static Color neighbor(Color color,float offset)
	{
		float[] hsb=Color.RGBtoHSB(color.getRed(),color.getGreen(),color.getBlue(),null);
		return Color.getHSBColor((hsb[0]+offset+1)%1,hsb[1]*.75f,Math.min(1,hsb[2]*1.2f));
	}
	private static Color alpha(Color color,int opacity) { return new Color(color.getRed(),color.getGreen(),color.getBlue(),opacity); }
	private static void wash(Graphics2D g,int w,int h,Color c,int opacity) { g.setPaint(new GradientPaint(0,0,alpha(c,opacity),0,h,alpha(c,8)));g.fillRect(0,0,w,h); }
	private static void smooth(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); }
	private static void edge(Graphics2D g,int w,int h,Color c,int opacity) { smooth(g);g.setColor(alpha(c,opacity));g.drawRoundRect(3,3,w-7,h-7,7,7); }
	private static void particles(Graphics2D g,int w,int h,Color color,double seconds,boolean burst)
	{
		smooth(g);
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
