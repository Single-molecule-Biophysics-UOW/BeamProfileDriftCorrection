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
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.haesleinhuepf.clij.*;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;

import net.haesleinhuepf.clij.utilities.CLIJOps;

import ij.IJ;

import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;


@Plugin(type = Command.class, headless = true, menuPath = "Plugins>Corrections>GPU_zproj")
public class BeamProfileGPU<T extends RealType<T> & NativeType<T>> implements Command {
	@Parameter(label = "run on GPU?")
	boolean GPU = true;
	@Parameter
	private OpService ops;
	@Parameter
	Logger logger;
	@Parameter(label = "ImagePlus")
	private ImagePlus input; // seems to be fastest
	@Parameter(type = ItemIO.OUTPUT,label = "inputShow")
	private ImagePlus showInput; // seems to be fastest
	@Parameter(type = ItemIO.OUTPUT, label = "result")
	private RandomAccessibleInterval<FloatType> result;
	@Parameter(type = ItemIO.OUTPUT, label = "resultImg")
	private Img<FloatType> resultImg;
	
	public <T extends RealType<T> & NativeType<T>> Img<T> ImgBuilder(RandomAccessibleInterval<T> input) {
		final Img<T> ImgOut = Util.getSuitableImgFactory(input, Util.getTypeFromInterval(input)).create(input);
		LoopBuilder.setImages(ImgOut, input).multiThreaded().forEachPixel(T::set);
		return ImgOut;
	}
	public void run() {
		//long startRun = System.currentTimeMillis();
		//logger.info("start beamprofile and drift correction");
		Context con = ops.getContext();
		BeamProfileUtil<FloatType> util = new BeamProfileUtil<FloatType>(con);
		final Img<T> img = ImagePlusAdapter.wrap(input);
		//this is easiest for now to get a floatImage
		RandomAccessibleInterval<FloatType> corr_img = util.subtract(img, (float)0.0);
		
		
		Img<FloatType> proj_corr_Img;
		if (GPU != true) {
			long startRun = System.currentTimeMillis();
			proj_corr_Img = util.zprojOpFunction(corr_img);
			long calctime_CPU = System.currentTimeMillis()-startRun;
			result = corr_img;
			logger.info("z projection without GPU finished after " + calctime_CPU +" ms");
			
		}
		else {
			GPUZproj(corr_img);
		}
}
	
	
public void CPUZproj() {
		//run a z projection without cliJ

	

	}
	
public void GPUZproj(RandomAccessibleInterval<FloatType> in) {
	long startRun = System.currentTimeMillis();
	 // load example image
    ImagePlus inputTEST = IJ.openImage("https://imagej.nih.gov/ij/images/t1-head.zip");
    showInput= inputTEST;
	// initialize GPU
    CLIJ clij = CLIJ.getInstance();

    // push image to GPU
    ClearCLBuffer inputOnGPU = clij.push(inputTEST);
    // create memory for target
    ClearCLBuffer resultOnGPU = clij.create(inputOnGPU);

    // apply transform
    clij.op().meanZProjection(inputOnGPU, resultOnGPU);

    // retrieve result or show it
    ImagePlus imp = clij.pull(resultOnGPU);
    result = ImagePlusAdapter.wrap(imp);
    
    
    // free memory
    inputOnGPU.close();
    resultOnGPU.close();
    long calctime_CPU = System.currentTimeMillis()-startRun;
    //clij.show(resultOnGPU, "result");
    logger.info("z projection with GPU acceleration finished after " + calctime_CPU +" ms");
	

}

}
