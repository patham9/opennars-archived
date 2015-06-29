package nars.vision;

import boofcv.alg.misc.ImageMiscOps;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.io.webcamcapture.UtilWebcamCapture;
import boofcv.struct.image.ImageUInt8;
import boofcv.struct.image.MultiSpectral;
import com.github.sarxos.webcam.Webcam;
import com.gs.collections.api.map.primitive.IntFloatMap;
import com.gs.collections.api.map.primitive.IntObjectMap;
import com.gs.collections.impl.map.mutable.primitive.IntFloatHashMap;
import com.gs.collections.impl.map.mutable.primitive.IntObjectHashMap;
import georegression.struct.point.Point2D_I32;
import nars.NAR;
import nars.gui.NARSwing;
import nars.model.impl.Default;
import org.jboss.marshalling.util.IntMap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Class for NARS Vision using a webcam with raster hierarchy representation.
 * Includes visualization.  All relevant parameters can be adjusted in real time
 * and will update the visualization.
 *
 * @author James McLaughlin
 */
public class RasterHierarchy extends JPanel
{
    // The number of rasters to calculate.
    int numberRasters;


    // The dimensions of the input frame.
    int frameWidth, frameHeight;

    // The number of blocks to divide the coarsest raster into.
    int divisions;

    // The scaling factor for each raster in the hierarchy.
    float scalingFactor;

    // The center of the region of focus
    Point2D_I32 focusPoint = new Point2D_I32();

    // Image for visualization
    BufferedImage workImage;

    // Window for visualization
    JFrame window;

    //holds multispectralization of input image
    transient private MultiSpectral<ImageUInt8> multiInputImg;
    private boolean running = true;

    /**
     * Configure the Raster Hierarchy
     *
     * @param numberRasters The number of rasters to generate
     * @param frameWidth The desired size of the input stream
     * @param frameHeight The desired height of the input stream
     * @param divisions The number of blocks to divide the coarsest grained raster into
     * @param scalingFactor The scaling factor for each raster in the heirarchy.
     */
    public  RasterHierarchy(int numberRasters, int frameWidth, int frameHeight, int divisions, float scalingFactor)
    {
        this.numberRasters = numberRasters;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;

        this.divisions = divisions;
        this.scalingFactor = scalingFactor;

        // Set the default focus to the center
        this.setFocus(frameWidth/2, frameHeight/2);

        window = new JFrame("Hierarchical Raster Vision Representation");
        window.setContentPane(this);
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);


        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    final MouseAdapter ma = new MouseAdapter() {
        protected void update(MouseEvent e) {
            float px = e.getX() / ((float)getWidth());
            float py = e.getY() / ((float)getHeight());
            setFocus(Math.round(px * frameWidth), Math.round(py * frameHeight));
        }

        @Override
        public void mousePressed(MouseEvent e) {
            update(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            update(e);
        }
    };

    /**
     * Set the focus to the given location.  All rasters (other than the most coarse-grained) are centered on
     * this point.
     *
     * @param x The x-coordinate of the focal point
     * @param y The y-coordinate of the focal point
     */
    public void setFocus(int x, int y)
    {
        this.focusPoint.set(x, y);
    }

    /**
     * Generate the raster hierarchy for a given image.
     *
     * @param input The image to rasterize
     * @return The rasterized image.
     */
    int updaterate=20;
    int cnt=1;
    static int arrsz=1000; //todo refine

    IntFloatHashMap lastvalR = new IntFloatHashMap();
    IntFloatHashMap lastvalG = new IntFloatHashMap();
    IntFloatHashMap lastvalB = new IntFloatHashMap();
    IntObjectHashMap<Value> voter = new IntObjectHashMap();

    public class Value
    {
        public int x;
        public int y;
        public int r;
        public float value;

        public Value() {         }

        public void set(int step, int x, int y, float vote) {
            this.x=x;
            this.y=y;
            this.r=r;
            this.value=value;
        }
    }

    public synchronized BufferedImage rasterizeImage(BufferedImage input)     {
        voter.clear();

        boolean putin=false; //vladimir
        cnt--;
        if(cnt==0) {
            putin = true;
            cnt=updaterate;
        }

        int red, green, blue;
        int redSum, greenSum, blueSum;
        int x, y, startX, startY;
        float newX, newY;

        int width = input.getWidth();
        int height = input.getHeight();

        float fblockXSize = width/divisions;
        float fblockYSize = height/divisions;

        multiInputImg = ConvertBufferedImage.convertFromMulti(input, multiInputImg, true, ImageUInt8.class);
        final ImageUInt8 ib0 = multiInputImg.getBand(0);
        final ImageUInt8 ib1 = multiInputImg.getBand(1);
        final ImageUInt8 ib2 = multiInputImg.getBand(2);

        MultiSpectral<ImageUInt8> output = new MultiSpectral<>(ImageUInt8.class, width, height, 3);

        BufferedImage rasterizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Set the initial raster region
        float regionWidth = width, regionHeight = height;
        newX = 0;
        newY = 0;
        startX = 0;
        startY = 0;

        for (int step = 1; step <= numberRasters; step++) {

            // For each step we need to reduce the dimensions of the area that is pixelated and
            // also reduce the block size.

            if (step > 1) {
                newX = startX + (regionWidth - regionWidth / scalingFactor) / scalingFactor;
                newY = startY + (regionHeight - regionHeight / scalingFactor) / scalingFactor;
                if (newX < 0) {newX = 0;}
                if (newY < 0) {newY = 0;}

                regionWidth  = regionWidth/ scalingFactor;
                regionHeight = regionHeight/ scalingFactor;

                fblockXSize = fblockXSize/ scalingFactor;
                fblockYSize = fblockYSize/ scalingFactor;
                if (fblockXSize < 1) { fblockXSize = 1;}
                if (fblockYSize < 1) { fblockYSize = 1;}
            }

            // Set the starting point for the next step
            startX = Math.round(this.focusPoint.getX() - ((regionWidth) / 2));
            startY = Math.round(this.focusPoint.getY() - ((regionHeight)/2));

            int blockXSize = Math.round(fblockXSize);
            int blockYSize = Math.round(fblockYSize);

            int pixelCount = blockXSize * blockYSize; // Number of pixels per block

            int h=0,j=0;

            // StringBuilder to hold the Narsese translation
            for (x = Math.round(newX); x < ((step == 1 ? 0 : startX) + regionWidth); x += blockXSize) {
                h++;
                for (y = Math.round(newY); y < ((step == 1 ? 0 : startY) + regionHeight); y += blockYSize) {
                    j++;

                    redSum = 0;
                    greenSum = 0;
                    blueSum = 0;



                    for (int pixelX = 0; (pixelX < blockXSize) && (x + pixelX < width); pixelX++) {
                        for (int pixelY = 0; (pixelY < blockYSize) && (y + pixelY < height); pixelY++) {
                            redSum += ib0.get(x + pixelX, y + pixelY);
                            greenSum += ib1.get(x + pixelX, y + pixelY);
                            blueSum += ib2.get(x + pixelX, y + pixelY);
                        }
                    }

                    red = redSum / pixelCount;
                    green = greenSum / pixelCount;
                    blue = blueSum / pixelCount;

                    float fred = red / 256.0f;
                    float fgreen = green / 256.0f; //was: red / 255f
                    float fblue = blue / 256.0f; //was: blue/255f

                    //manage move heuristic
                    int brightness = (red+green+blue)/3; //maybe not needed
                    int key=step+10*x+10000*y;

                    if(lastvalR.containsKey(key) && putin) {

                        float area = blockXSize * blockYSize;
                        float diff = Math.abs(fred - (lastvalR.get(key))) + Math.abs(fgreen - (lastvalG.get(key))) + Math.abs(fblue - (lastvalB.get(key)));
                        float vote = diff;// / area;

                       // vote*=step;
                        Value value = voter.get(key);
                        if (value == null) {
                            value = new Value();
                            voter.put(key, value);
                        }

                        value.set(step,
                                x + blockXSize / 2,
                                y + blockYSize / 2,
                                vote);
                    }
                    lastvalR.put(key, fred);
                    lastvalG.put(key, fgreen);
                    lastvalB.put(key, fblue);

                    if(putin && step==numberRasters) {
                        //input Narsese translation, one statement for each band.
                        //ArrayList<String> nalStrings = new ArrayList<String>();

                        //nalStrings.add("<(*,r"+ String.valueOf(step)+","+String.valueOf(h)+","+String.valueOf(j)+") --> RED>. :|: %"+String.valueOf(fred)+System.getProperty("line.separator"));
                        //nalStrings.add("<(*,r" + String.valueOf(step) + "," + String.valueOf(h) + "," + String.valueOf(j) + ") --> GREEN>. :|: %" + String.valueOf(fgreen) + System.getProperty("line.separator"));
                        //nalStrings.add("<(*,r"+ String.valueOf(step)+","+String.valueOf(h)+","+String.valueOf(j)+") --> BLUE>. :|: %"+String.valueOf(fblue)+System.getProperty("line.separator"));

                        /* Here we use the gamma corrected, grayscale version of the image.  Use CCIR 601 weights to convert.
                         * If it is desirable to use only one sentence (vs RGB for example) then use this.
                         *  see: https://en.wikipedia.org/wiki/Luma_%28video%29 or http://cadik.posvete.cz/color_to_gray_evaluation */
                        double dgray = 0.2989*red + 0.5870*green + 0.1140*blue;
                        dgray /= 255.0;

                        //TODO create the Term / Task programmaticaly
                        nar.input("<(*,r" + String.valueOf(step) + "," + String.valueOf(h) + "," + String.valueOf(j) + ") --> GRAY>. :|: %" + String.valueOf(dgray) + System.getProperty("line.separator"));


                    }

                    ImageMiscOps.fillRectangle(output.getBand(0), red, x, y, blockXSize, blockYSize);
                    ImageMiscOps.fillRectangle(output.getBand(1), green, x, y, blockXSize, blockYSize);
                    ImageMiscOps.fillRectangle(output.getBand(2), blue, x, y, blockXSize, blockYSize);
                }
            }
        }

        //search for maximum vote to move heuristic
        if(putin) {
            final Value[] maxvalue = {null};
            float threshold = 0.05f;
            voter.forEachKeyValue((key,value) -> {
                if (maxvalue[0] == null || value.value > maxvalue[0].value) {
                    if (value.value > threshold)
                        maxvalue[0] = value;
                }
            });

            Value maxValue = maxvalue[0];
            if (maxValue != null && maxValue.x!=0 && maxValue.y!=0) {
                this.setFocus(maxValue.x, maxValue.y);
            }
        }

        ConvertBufferedImage.convertTo(output, rasterizedImage, true);
        return rasterizedImage;
    }

    /**
     * Invoke to start the main processing loop.
     */
    public void process() {
        Webcam webcam = UtilWebcamCapture.openDefault(frameWidth, frameHeight);

        // adjust the window size and let the GUI know it has changed
        Dimension actualSize = webcam.getViewSize();
        setPreferredSize(actualSize);
        setMinimumSize(actualSize);
        window.setMinimumSize(actualSize);
        window.setPreferredSize(actualSize);
        window.setVisible(true);

        BufferedImage input, buffered;

        workImage = new BufferedImage(actualSize.width, actualSize.height, BufferedImage.TYPE_INT_RGB);

        //int counter = 0;

        while( running ) {
                /*
                 * Uncomment this section to scan the focal point across the frame
                 * automatically - just for demo purposes.
                 */
                /*
                int xx = this.focusPoint.getX();
                int yy = this.focusPoint.getY();
                xx += 1;

                if(xx > frameWidth)
                {
                    xx = 0;
                    yy += 1;
                    if (yy > frameHeight)
                        yy = 0;
                }

                this.setFocus(xx, yy);
                */
            input = webcam.getImage();

            synchronized( workImage ) {
                // copy the latest image into the work buffer
                Graphics2D g2 = workImage.createGraphics();

                buffered = this.rasterizeImage(input);
                g2.drawImage(buffered,0,0,null);
            }

            repaint();
        }
    }

    @Override
    public void paint (Graphics g) {
        if( workImage != null ) {
            // draw the work image and be careful to make sure it isn't being manipulated at the same time
            synchronized (workImage) {
                ((Graphics2D) g).drawImage(workImage, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }

    static NAR nar;
    public static void main(String[] args) {

        //RasterHierarchy rh = new RasterHierarchy(8, 640, 480, 12, 2);
       // RasterHierarchy rh = new RasterHierarchy(3, 640, 480, 5, 2);
        nar = new NAR(new Default.CommandLineNARBuilder(args));

        NARSwing swing = new NARSwing(nar);

        RasterHierarchy rh = new RasterHierarchy(6, 800, 600, 16, 1.619f);

        rh.process();
    }

    public int getNumberRasters() {
        return numberRasters;
    }

    public void setNumberRasters(int numberRasters) {
        this.numberRasters = numberRasters;
    }

    public int getDivisions() {
        return divisions;
    }

    public void setDivisions(int divisions) {
        this.divisions = divisions;
    }

    public float getScalingFactor() {
        return scalingFactor;
    }

    public void setScalingFactor(int scalingFactor) {
        this.scalingFactor = scalingFactor;
    }
}