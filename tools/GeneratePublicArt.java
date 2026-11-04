/* SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Cinema TV contributors.
 * Original procedural artwork. No photographs, AI output, external images, fonts or logos are sampled.
 */
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Random;
import javax.imageio.ImageIO;

public final class GeneratePublicArt {
    private static final int W=1920,H=1080;
    record Scene(String path,int sky,int horizon,int water,int mountain,int accent,boolean night,boolean aurora,int season){}
    static final Scene[] SCENES={
        new Scene("raw/cinema_lake.png",0x182D4A,0xF9A36A,0x122D45,0x20314A,0xF5A760,false,false,2),
        new Scene("raw/cinema_autumn.png",0x543546,0xF4B36A,0x2F3343,0x58404C,0xDA7940,false,false,2),
        new Scene("raw/cinema_oled.png",0x030812,0x23233D,0x050D17,0x101727,0x6D68AD,true,false,3),
        new Scene("drawable-nodpi/cinema_sunrise.png",0x274E75,0xFFE1AB,0x1B485B,0x344E6B,0xF8CD8D,false,false,0),
        new Scene("drawable-nodpi/theme_oled_sunrise.png",0x020812,0x182942,0x030B17,0x0F1B2D,0x526C9B,true,false,3),
        new Scene("drawable-nodpi/theme_oled_sunset.png",0x050510,0x332139,0x070817,0x181329,0x8C5072,true,false,3),
        new Scene("drawable-nodpi/theme_autumn_sunrise.png",0x4F6377,0xF8D5A1,0x3D5C65,0x5A6873,0xDEA642,false,false,2),
        new Scene("drawable-nodpi/theme_autumn_sunset.png",0x4A3346,0xF8AC67,0x383D50,0x4D3C4D,0xD97838,false,false,2),
        new Scene("drawable-nodpi/theme_aurora_sunrise.png",0x102D43,0x7AACA8,0x102C3E,0x203A4A,0x67C6B0,true,true,3),
        new Scene("drawable-nodpi/theme_aurora_sunset.png",0x10142D,0x5B426C,0x101D32,0x24223F,0x77D5B9,true,true,3),
        new Scene("drawable-nodpi/season_spring.png",0x476D89,0xF7D5C7,0x315569,0x5F7285,0xE7AAC0,false,false,0),
        new Scene("drawable-nodpi/season_summer.png",0x155275,0xB9E2DC,0x154E65,0x2C5967,0x73B786,false,false,1),
        new Scene("drawable-nodpi/season_autumn.png",0x51445B,0xF6C28A,0x364B61,0x595367,0xE79B54,false,false,2),
        new Scene("drawable-nodpi/season_winter.png",0x344467,0xB7BED8,0x253A58,0x4F5C7A,0xCEDAE5,false,false,3)
    };
    static Color c(int rgb){return new Color(rgb);}
    static Color a(int rgb,int alpha){return new Color((rgb>>16)&255,(rgb>>8)&255,rgb&255,alpha);}
    static void render(Scene s,Path out)throws Exception{
        BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0,0,c(s.sky),0,650,c(s.horizon)));g.fillRect(0,0,W,680);
        if(s.night){Random stars=new Random(91);for(int i=0;i<180;i++){int x=stars.nextInt(W),y=stars.nextInt(440);g.setColor(new Color(223,238,255,30+stars.nextInt(70)));g.fillOval(x,y,2,2);}}
        int sx=1400,sy=555;for(int r=270;r>20;r-=8){g.setColor(a(s.accent,s.night?1:2));g.fillOval(sx-r,sy-r,2*r,2*r);}
        if(!s.night){g.setColor(new Color(255,236,190,225));g.fillOval(sx-33,sy-33,66,66);}
        if(s.aurora){for(int n=0;n<40;n++){Path2D band=new Path2D.Float();band.moveTo(-50,160+n*2);band.curveTo(420,30+n*3,650,430+n,1070,190+n*2);band.curveTo(1400,0+n*3,1650,80+n*2,1980,170+n);g.setStroke(new BasicStroke(8));g.setColor(a(n%3==0?0x9C87D2:s.accent,9));g.draw(band);}}
        g.setPaint(new GradientPaint(0,635,c(s.water),0,H,c(0x091522)));g.fillRect(0,635,W,H-635);
        for(int layer=0;layer<3;layer++){
            Random random=new Random(800+layer);Path2D peaks=new Path2D.Float();peaks.moveTo(-60,680);
            int y=650-layer*10;for(int x=-60;x<=W+120;x+=200){int peak=y-80-random.nextInt(240)+(layer*40);peaks.lineTo(x,peak);peaks.lineTo(x+80,y-15-random.nextInt(60));}peaks.lineTo(W+160,700);peaks.closePath();
            Color mountain=c(s.mountain);g.setColor(new Color(Math.max(0,mountain.getRed()-layer*8),Math.max(0,mountain.getGreen()-layer*8),Math.max(0,mountain.getBlue()-layer*7)));g.fill(peaks);
        }
        // Two clean snow ridges provide recognizable mountain/lake silhouettes.
        for(int x:new int[]{360,870}){Path2D snow=new Path2D.Float();snow.moveTo(x,315);snow.lineTo(x-120,465);snow.lineTo(x-40,430);snow.lineTo(x+3,455);snow.lineTo(x+42,400);snow.lineTo(x+120,475);snow.closePath();g.setColor(a(0xD9E9EF,s.night?75:155));g.fill(snow);}
        Random ripples=new Random(17);for(int i=0;i<160;i++){int y=690+ripples.nextInt(350);float depth=(y-650)/430f;int width=20+(int)(depth*350);int x=sx-width/2+ripples.nextInt(180)-90;g.setColor(a(s.accent,s.night?8:12));g.fillRoundRect(x,y,width,2+Math.round(depth*3),3,3);}
        for(int i=0;i<22;i++){g.setColor(new Color(190,215,235,2));g.fillOval(-100,620-i*4,W+200,70+i*5);}
        // Original stylized trees frame the lake; each season has a distinct palette.
        for(int side=0;side<2;side++){Random trees=new Random(112+side);for(int i=0;i<16;i++){int x=side==0?trees.nextInt(300):W-300+trees.nextInt(300),base=830+trees.nextInt(230),height=120+trees.nextInt(260);g.setColor(a(0x07131D,215));g.fillRect(x-3,base-height/2,7,height/2);for(int j=0;j<4;j++){int half=25+j*12,y=base-height+j*height/6;Polygon leaves=new Polygon(new int[]{x,x-half,x+half},new int[]{y,y+height/3,y+height/3},3);g.setColor(a(s.season==2?0xA96138:s.season==0?0xA08099:s.season==3?0x506980:0x356454,190));g.fill(leaves);}}}
        g.setPaint(new GradientPaint(0,800,new Color(3,10,19,0),0,H,new Color(3,10,19,180)));g.fillRect(0,800,W,280);
        g.dispose();Path file=out.resolve(s.path);Files.createDirectories(file.getParent());ImageIO.write(image,"png",file.toFile());
    }
    public static void main(String[] args)throws Exception{if(args.length!=1)throw new IllegalArgumentException("Output resource directory required");Path out=Path.of(args[0]);for(Scene scene:SCENES)render(scene,out);}
}
