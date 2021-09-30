package singleMoleculeBiophysics.ImageCorrections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.log.Logger;
import org.scijava.plugin.Parameter;

import net.imagej.ops.OpService;
import net.imagej.util.Images;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.fft2.FFTConvolution;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexDoubleType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;


public class DriftUtil<T extends RealType<T> & NativeType<T>> {
	@Parameter		//do they need to be parameters? or just fields?
	OpService ops;
	@Parameter
	Logger logger;
	
	public DriftUtil(final Context context) {
		   context.inject(this);
		   ops = context.getService(OpService.class);
		   logger = context.getService(LogService.class);		  		 
		}
	
	public ArrayList<float[]> driftCorrStack(
			RandomAccessibleInterval<T>inputStack,int nChannels, int nFrames, double filterRadius) {
		
		//I don't understand that bit...
		final ExecutorService convService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		//create array to store the result. Does it have to be double? float? long?
		ArrayList<float[]> shiftList = new ArrayList<float[]>();
		shiftList.add(new float[] {0,0});	//shift of the first frames is 0 by definition		
		int Tindex = inputStack.numDimensions()-1;
		//copy the first slice, so the orignial data is not overridden, make sure all this happens only with one color channel if there are colors
		//TODO: make user choose the color channel used for correction?
		int channelChoice = 0;			
		IntervalView<T> slice0 = null;
		//if there are color channels we have to choose which one to use:
		if (nChannels>1) {				
			int Cindex = 2;
			slice0 = Views.hyperSlice(Views.hyperSlice(inputStack, Tindex, 0),Cindex,channelChoice);				
			}
		else {
			slice0 = Views.hyperSlice(inputStack, Tindex, 0);
			}		
		long[] dim = new long[slice0.numDimensions()];	
		slice0.dimensions(dim);
		logger.info("dimensions of slice0:"+Arrays.toString(dim));	
		ArrayImgFactory<T> referenceFac = new ArrayImgFactory<>(slice0.firstElement());
		RandomAccessibleInterval<T> referenceImg = referenceFac.create(dim);
		Images.copy(slice0, referenceImg);
		//and make it iterable:
		IntervalView<T> reference = Views.interval(referenceImg,referenceImg);			
			
		//Convolution Class. initialize with first Frame as image and kernel.
		//the kernel needs to be normalized to not add "energy" to the image		
		
		RandomAccessibleInterval<DoubleType> normslice0 = normalize(reference);
		net.imglib2.algorithm.fft2.FFTConvolution FFTConv = new FFTConvolution(reference,normslice0,new ArrayImgFactory<>(new ComplexDoubleType()),convService);			
		long[] refDim = reference.dimensionsAsLongArray();
		long[] kernelDim = normslice0.dimensionsAsLongArray();
			
        //now to do correlation complex-conjugate the FFT before multipliying in fourier domain easily done by:
     	FFTConv.setComputeComplexConjugate(true);
     	//keep the FFT of the image, only the kernel is replaced with the slices
     	FFTConv.setKeepImgFFT(true);	     	
        //calculate the correlation:
        FFTConv.convolve();
		//now find the maximum of this first correlation (its the auto correlation)
        //around the middle:
        long radius = (long)filterRadius;
        IterableInterval<T> cropRef = Views.interval( reference, 
				new long[] { dim[0]/2-radius, dim[1]/2-radius}, 
				new long[]{  dim[0]/2+radius, dim[1]/2+radius } );
		Point refMax = findMaxLocation(cropRef);				
		//loop through stack:
		for (int i=1;i<nFrames;i++) {
			//get the slice via view:
			IntervalView<T> slice;
			if (nChannels >1) {				
				int Cindex = 2;
				slice = Views.hyperSlice(Views.hyperSlice(inputStack, Tindex, i),Cindex,channelChoice);				
				}
			else {
				slice = Views.hyperSlice(inputStack, Tindex, i);
				}
			//IntervalView<T> slice = Views.hyperSlice(inputStack, Tindex, i);
			//nomalize it:
			RandomAccessibleInterval<DoubleType> normslice = normalize(slice);				
			//now update the kernel
			FFTConv.setKernel(normslice);								
			//and compute the new correlation
			FFTConv.convolve();			
	
	
			//search for the maximum in the middle 2radius*2radius pixels
			IterableInterval<T> cropRefNew = Views.interval( reference, 
					new long[] { dim[0]/2-radius, dim[1]/2-radius}, 
					new long[]{  dim[0]/2+radius, dim[1]/2+radius } );
			//ImageJFunctions.show((RandomAccessibleInterval<T>)cropRef).setTitle("cropped");
			//and find the maximum position
			Point newMax = findMaxLocation(cropRefNew);												
			//now calculate the shift vector:			
			float[] shiftVector = findShift(newMax,refMax);
			
			shiftList.add(shiftVector);
			}

			return shiftList;
		}							
	private float[] findShift(Point origin, Point loc) {
		int dim = loc.numDimensions();
		float[] locPos = new float[dim];
		float[] originPos = new float[dim];
		float[] shift = new float[dim];
		loc.localize(locPos);
		origin.localize(originPos);
		for (int i=0; i<dim;i++) {
			//negative is necessary for the shift!
			shift[i] = -(locPos[i]-originPos[i]);
		}
		return shift;		
	}
	
	private Point findMaxLocation(IterableInterval<T> input) {
		Point locationMin = new Point( input.numDimensions() );
		Point locationMax = new Point( input.numDimensions() );
		computeMinMaxLocation(input,locationMin,locationMax);
		return locationMax;
	}
	public void computeMinMaxLocation(
	        final IterableInterval< T > input, final Point minLocation, final Point maxLocation )
	    	{
	        // create a cursor for the image (the order does not matter)
	        final Cursor< T > cursor = input.cursor();
	 
	        // initialize min and max with the first image value
	        T type = cursor.next();
	        T min = type.copy();
	        T max = type.copy();
	 
	        // loop over the rest of the data and determine min and max value
	        while ( cursor.hasNext() )
	        {
	            // we need this type more than once
	            type = cursor.next();
	 
	            if ( type.compareTo( min ) < 0 )
	            {
	                min.set( type );
	                minLocation.setPosition( cursor );
	            }
	 
	            if ( type.compareTo( max ) > 0 )
	            {
	                max.set( type );
	                maxLocation.setPosition( cursor );
	            }
	        }
	    }
	private static <T extends RealType<T>, C extends ComplexType<C>> RandomAccessibleInterval<DoubleType> normalize(
			final RandomAccessibleInterval<T> img) {

		RealSum sum = new RealSum();

		for (final T type : Views.iterable(img))
			sum.add(type.getRealDouble());

		final double s = sum.getSum();

		return Converters.convert(img, (i, o) -> o.setReal(i.getRealDouble() / s), new DoubleType());
	}
	public RandomAccessibleInterval<T> shiftStack3D(
			RandomAccessibleInterval<T>inputStack,ArrayList<float[]>shiftVectorList,int nChannels, int nFrames) {
		
		//make a stack of same dimensions as input:
		//ArrayImgFactory<T> shiftFac = new ArrayImgFactory<>(inputStack.getAt(0));
		//RandomAccessibleInterval<T> shiftStack = shiftFac.create(inputStack.dimensionsAsLongArray());
		int Tindex = inputStack.numDimensions()-1;
		
		
		if(nChannels == 1){		       	    				
			
			
			//logger.info("stack has channels");
			
			//loop through Stack:
			//make array to hold the slices
			ArrayList<RandomAccessibleInterval<T>> sliceList = new ArrayList<RandomAccessibleInterval<T>>();
			for (int i=0;i<nFrames;i++) {
				//logger.info("looping through stack");
				//translate the slice:				
				RandomAccessibleInterval<T> slice = translate(Views.hyperSlice(inputStack, Tindex, i),shiftVectorList.get(i));
				sliceList.add(slice);
			}
			//put back in a stack:
			RandomAccessibleInterval<T> shiftResult = Views.stack(sliceList);
		 	return shiftResult;
		}
		else{		       	    				
			
			//loop through Stack:
			//make array to hold the slices
			ArrayList<RandomAccessibleInterval<T>> sliceList = new ArrayList<RandomAccessibleInterval<T>>();
			for (int i=0;i<nFrames;i++) {
				//logger.info("looping through stack");
				//translate the slice:				
				RandomAccessibleInterval<T> slice = translate3D(Views.hyperSlice(inputStack, Tindex, i),shiftVectorList.get(i));
				sliceList.add(slice);
			}
			//put back in a stack:
			RandomAccessibleInterval<T> shiftResult = Views.stack(sliceList);
		 	return shiftResult;
		}
		
		
	}
	public static <T extends RealType<T>& NativeType<T>> RandomAccessibleInterval<T> translate3D(RandomAccessibleInterval<T> img, float[] shiftVector) {		
		double shiftX = shiftVector[0];		
		double shiftY = shiftVector[1];
		double shiftC = 0;
		//first extend the interval with zeros:
		//is the interpolation neccessary?
		final RealRandomAccessible< T > field = Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory<>());
		
		//now define transformation matrix:
		final AffineTransform3D affine = new AffineTransform3D() ;
		affine.set(new double[][] {{1., 0., 0., shiftX}, {0., 1.,0. , shiftY}, {0., 0., 1.,shiftC},{0., 0., 0., 1. }});
		final AffineRandomAccessible< T, AffineGet > shifted = RealViews.affine(field, affine);
		//apply the original interval, the rest is cropped:
		final IntervalView< T > cropShifted = Views.interval(shifted, img);		
		return cropShifted;		
	}
	public static <T extends RealType<T>& NativeType<T>> RandomAccessibleInterval<T> translate(RandomAccessibleInterval<T> img, float[] shiftVector) {
		//TODO is this method ever called? maybe not anymore
		double shiftX = shiftVector[0];
		double shiftY = shiftVector[1];
		//first extend the interval with zeros:
		//is the interpolation neccessary?
		final RealRandomAccessible< T > field = Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory<>());
		
		//now define transformation matrix:
		final AffineTransform2D affine = new AffineTransform2D() ;
		affine.set(new double[][] {{1., 0., shiftX}, {0., 1., shiftY}, {0., 0., 1.} });
		final AffineRandomAccessible< T, AffineGet > shifted = RealViews.affine(field, affine);
		//apply the original interval, the rest is cropped:
		final IntervalView< T > cropShifted = Views.interval(shifted, img);		
		return cropShifted;		
	}
}