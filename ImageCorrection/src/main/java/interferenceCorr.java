
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.log.Logger;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.special.computer.Computers;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.stats.Normalize;
import net.imglib2.converter.Converters;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;


@Plugin(type = Command.class, headless = true, menuPath = "Plugins>Corrections>Interference BeamProfile")
public class interferenceCorr<T extends RealType<T> & NativeType<T>> implements Command {
	
	/* If there is a interference in the image that is not constant overtime than the normal beamProfile correction won't work well.
	 * Mainly because of the z-proj. Try blurring every frame separately and basically correct every frame in the stack.
	 * This should be merged into BeamProfileUtil eventually.
	 * 
	 */

	@Parameter		//do they need to be parameters? or just fields?
	OpService ops;
	@Parameter
	Logger logger;
	
	
	@Parameter(label = "ImagePlus")
	private ImagePlus input; // seems to be fastest
	@Parameter(type = ItemIO.OUTPUT, label = "result")
	private RandomAccessibleInterval<FloatType> result;
	@Parameter(type = ItemIO.OUTPUT, label = "resultImg")
	private Img<FloatType> resultImg;
	@Parameter(label = "darkframe")
	float darkframe;
	@Parameter(label = "sigma")
	double sigma;
	
	//constructor to find get context and services. Necessary? 
	//public interferenceCorr(final Context context) {
	//	   context.inject(this);
	//	   ops = context.getService(OpService.class);
	//	   logger = context.getService(LogService.class);		  		 
	//	}
	public <T extends RealType<T> & NativeType<T>> Img<T> ImgBuilder(RandomAccessibleInterval<T> input) {
		final Img<T> ImgOut = Util.getSuitableImgFactory(input, Util.getTypeFromInterval(input)).create(input);
		LoopBuilder.setImages(ImgOut, input).multiThreaded().forEachPixel(T::set);
		return ImgOut;
	}

	@Override
	public void run() {
		long startRun = System.currentTimeMillis();
		Context con = ops.getContext();
		// ImgPlus dimensions are alwyas array with 5 elements: XYCZT
		// Img dimensions are <=5 in same order, if time exists it is last!
		int[] dims = input.getDimensions();
		int nFrames = dims[4];
		int nChannels = dims[2];
		logger.info("input:"+"frames:"+nFrames+"channels:"+nChannels);
		BeamProfileUtil util = new BeamProfileUtil(con);
		final Img<T> img = ImagePlusAdapter.wrap(input);
		// first I do the darkframeCorr, so I only do it on the whole stack once, the
		// blur etc is then corrected already.
		RandomAccessibleInterval<FloatType> corr_img = util.subtract(img, darkframe);
		// now don't do the z projection
		
		// now blur the projection the subtraction, frame by frame.
		RandomAccessibleInterval<FloatType> blurImg = util.blur(corr_img, sigma);
		
		// normalize the blurred image:
		util.normMultiChannel2(blurImg, nChannels);
		//ImageJFunctions.show(blurImg);
		// create new image for result
		ArrayImgFactory<FloatType> fac = new ArrayImgFactory<FloatType>(corr_img.randomAccess().get());
		RandomAccessibleInterval<FloatType> div_output = fac.create(img.dimensionsAsLongArray());
		RandomAccessibleInterval<FloatType> beamProfileCorr = util.divideStackbyStack(corr_img, blurImg, div_output,nFrames);
		logger.info("correction took:" + (System.currentTimeMillis() - startRun) + "ms");
		result = beamProfileCorr;

		
	}
    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

                }
    }























