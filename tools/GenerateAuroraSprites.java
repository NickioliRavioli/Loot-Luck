import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Offline asset generator: java tools/GenerateAuroraSprites.java. No game/client required. */
public final class GenerateAuroraSprites
{
	private static final int FRAMES=240,COLUMNS=16;
	public static void main(String[] args) throws Exception
	{
		Path directory=Path.of("src/main/resources/com/lootluck/ui");Files.createDirectories(directory);
		write(directory.resolve("aurora-grid.png"),32,32,42,42);
		write(directory.resolve("aurora-list.png"),96,24,210,44);
	}
	private static void write(Path path,int frameWidth,int frameHeight,int width,int height) throws Exception
	{
		BufferedImage sheet=new BufferedImage(frameWidth*COLUMNS,frameHeight*(FRAMES/COLUMNS),BufferedImage.TYPE_INT_ARGB);
		for(int frame=0;frame<FRAMES;frame++)
		{
			Graphics2D g=sheet.createGraphics();
			try
			{
				g.translate(frame%COLUMNS*frameWidth,frame/COLUMNS*frameHeight);g.clipRect(0,0,frameWidth,frameHeight);g.scale((double)frameWidth/width,(double)frameHeight/height);
				g.setPaint(new GradientPaint(0,0,new Color(255,255,255,60),0,height,new Color(255,255,255,8)));g.fillRect(0,0,width,height);
				double phase=frame*Math.PI*2/FRAMES;
				for(int lobe=0;lobe<3;lobe++)
				{
					float x=(float)(width*(.5+.45*Math.sin(phase+lobe*1.7))),y=(float)(height*(.5+.4*Math.cos(phase+lobe)));
					g.setPaint(new RadialGradientPaint(new Point2D.Float(x,y),Math.max(width,height)*.6f,new float[]{0,1},new Color[]{new Color(255,255,255,65),new Color(255,255,255,0)}));g.fillRect(0,0,width,height);
				}
			}
			finally { g.dispose(); }
		}
		ImageIO.write(sheet,"png",path.toFile());
		System.out.println(path+": "+sheet.getWidth()+"x"+sheet.getHeight()+", "+Files.size(path)+" bytes");
	}
}
