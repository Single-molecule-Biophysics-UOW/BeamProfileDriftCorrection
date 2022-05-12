package singleMoleculeBiophysics.ImageCorrections;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.log.Logger;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

@Plugin(type = Command.class, headless = true, menuPath = "Plugins>Corrections>Beamprofile and drift Correction")
public class BeamProfileDriftCorrection<T extends RealType<T> & NativeType<T>> implements Command {
	@Parameter
	private OpService ops;
	@Parameter(label = "ImagePlus")
	private ImagePlus input; // seems to be fastest
	@Parameter(type = ItemIO.OUTPUT, label = "result")
	private RandomAccessibleInterval<FloatType> result;
	@Parameter(type = ItemIO.OUTPUT, label = "resultImg")
	private Img<FloatType> resultImg;
	@Parameter(label = "darkframe")
	float darkframe = 2348;
	@Parameter(label = "sigma")
	double sigma = 25;
	@Parameter(label = "stationary Beamprofile?")
	boolean stationaryProfile = true;
	@Parameter
	Logger logger;
	double maxShift = 20.0;

	public BeamProfileDriftCorrection() {
		
	}
	public BeamProfileDriftCorrection(final Context context) {
		context.inject(this);
		ops = context.getService(OpService.class);
		logger = context.getService(LogService.class);
	}
	public BeamProfileDriftCorrection(final Context context, ImagePlus in) {
		this();
		input = in;	
	}
	public BeamProfileDriftCorrection(final Context context, ImagePlus in, double s) {
		this(context,in);
		sigma = s;
	}
	public BeamProfileDriftCorrection(final Context context, ImagePlus in, double s, float df) {
		this(context, in, s);
		darkframe = df;
	}
	public BeamProfileDriftCorrection(final Context context, ImagePlus in, double s, float df, boolean sp ) {
			this(context, in, s, df);
			stationaryProfile = sp;
	}	
	public void setInput(ImagePlus in) {
		input = in;
	}
	public ImagePlus getInput() {
		return input;
	}
	public void setSigma(double s) {
		sigma = s;
	}
	public double getSigma() {
		return sigma;
	}
	public void setDarkframe(float df) {
		darkframe = df;
	}
	public float getDarkframe() {
		return darkframe; 
	}
	public void setStationaryProfile(boolean sp) {
		stationaryProfile = sp;
	}
	public boolean getStationaryProfile() {
		return stationaryProfile;
	}
	public Img<FloatType> getResult() {
		return resultImg;
	}
	public double getMaxShift() {
		return maxShift;
	}
	public void setMaxShift(double ms) {
		maxShift = ms;
	}

	public <T extends RealType<T> & NativeType<T>> Img<T> ImgBuilder(RandomAccessibleInterval<T> input) {
		final Img<T> ImgOut = Util.getSuitableImgFactory(input, Util.getTypeFromInterval(input)).create(input);
		LoopBuilder.setImages(ImgOut, input).multiThreaded().forEachPixel(T::set);
		return ImgOut;
	}
	
	public void run() {
		long startRun = System.currentTimeMillis();
		logger.info("start beamprofile and drift correction");
		Context con = ops.getContext();
		// ImgPlus dimensions are always array with 5 elements: XYCZT
		// Img dimensions are <=5 in same order, if time exists it is last!
		int[] dims = input.getDimensions();
		int nFrames = dims[4];
		int nSlices = dims[3];
		int nChannels = dims[2];
		logger.info(Arrays.toString(dims));
		logger.info("frames:"+nFrames);
		logger.info("slices:"+nSlices);
		
		//TODO Add some magic code here that cleans up the dimensions if they are wrong
		
		//if (nFrames == 1 & nSlices > 1){
			//z and t are probably switched, through warning and switch back
			//logger.warn("it appears like z and t-dimension is swapped. I t will be swapped for correction");
			//nFrames = nSlices;
		//	nSlices = 1;
	//	}
		//if (nFrames == 1 & nChannels > 3){
			//z and t are probably switched, through warning and switch back
		//	logger.warn("it appears like color and t-dimension is swapped. I t will be swapped for correction");
		//	nFrames = nChannels;
		//	nChannels = 1;
		//}
		
		
		
		BeamProfileUtil<FloatType> util = new BeamProfileUtil<FloatType>(con);
		final Img<T> img = ImagePlusAdapter.wrap(input);
		// first I do the darkframeCorr, so I only do it on the whole stack once, the
		// blur etc is then corrected already.
		RandomAccessibleInterval<FloatType> corr_img = util.subtract(img, darkframe);
		logger.info("subtracted darkframe");
		// now do z projection, if no frames, no projection necessary
		//if the beamProfile is not stationary over time there should be a projection as well.
		Img<FloatType> proj_corr_Img;
		if (nFrames > 1 & stationaryProfile == true) {
			proj_corr_Img = util.zprojOpFunction(corr_img);		
		} else {
			proj_corr_Img = ImgBuilder(corr_img);
		}
		// now blur the projection
		RandomAccessibleInterval<FloatType> blurImg = util.blur(proj_corr_Img, sigma);
		// normalize the blurred image:
		util.normMultiChannel2(blurImg, nChannels);
		// create new image for result
		CellImgFactory<FloatType> fac = new CellImgFactory<FloatType>(corr_img.randomAccess().get());
		RandomAccessibleInterval<FloatType> div_output = fac.create(img.dimensionsAsLongArray());
		RandomAccessibleInterval<FloatType> beamProfileCorr = util.divideStackbyStack(corr_img, blurImg, div_output,nFrames);
		logger.info("finished beamProfileCorrection, do the drift now");
		// now do drift correction, but only if there are actually frames:
		if (nFrames > 1) {
			//logger.info("start drift");
			DriftUtil<FloatType> dutil = new DriftUtil<FloatType>(con);
			ArrayList<float[]> shifts = dutil.driftCorrStack(beamProfileCorr, nChannels, nFrames, maxShift);
			RandomAccessibleInterval<FloatType> driftCorr = dutil.shiftStack3D(beamProfileCorr, shifts, nChannels,nFrames);
			resultImg = ImgBuilder(driftCorr);
		} else {
			//logger.info("no drift needed");
			result = beamProfileCorr;
		} 
		logger.info("beamprofile and drift correction finished after" + (System.currentTimeMillis()-startRun));		
	}
}
	
	

