package singleMoleculeBiophysics.ImageCorrections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.log.Logger;
import org.scijava.plugin.Parameter;

import net.imagej.ops.OpEnvironment;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.Ops.Filter;
import net.imagej.ops.Ops.Filter.Gauss;
import net.imagej.ops.special.computer.Computers;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imagej.util.Images;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fft2.FFTConvolution;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.stats.Normalize;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.sparse.NtreeImg;
import net.imglib2.img.sparse.NtreeImgFactory;
import net.imglib2.loops.ListUtils;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexDoubleType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.Dimensions;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;

public class BeamProfileUtil<T extends RealType<T> & NativeType<T>> {
	@Parameter		//do they need to be parameters? or just fields?
	OpService ops;
	@Parameter
	Logger logger;
	
	
	//constructor to find get context and services. Necessary? 
	public BeamProfileUtil(final Context context) {
		   context.inject(this);
		   ops = context.getService(OpService.class);
		   logger = context.getService(LogService.class);		  		 
		}
	
	public <T extends RealType<T>& NativeType<T>> RandomAccessibleInterval<FloatType> subtract(
	    	RandomAccessibleInterval<T> input,float value){
			RandomAccessibleInterval<FloatType> cor = Converters.convert(
					input, 
					(i, o) -> o.set(i.getRealFloat()-value), 
					new FloatType());	
		return cor;
	    }
	
    public Img<T> zprojOpFunction(final RandomAccessibleInterval<T> input){
    	
    	//the a result img is allocated by making a facotry for it
    	//the assumed order is XYCZT, => time is always last, the caller needs to make sure it exists!
    	int Tindex = input.numDimensions()-1;

    	
    	NtreeImgFactory<T> fac = new NtreeImgFactory<T>(input.randomAccess().get());

    	//the dimension of the z-projection depends on the input. It is the same as one isolated time frame of the input    	    
    	IntervalView<T> slice0 = Views.hyperSlice(input,Tindex, 0);//.dimensions(dim0); collapse in T dimension
    	long[] dim0 = new long[slice0.numDimensions()];
    	slice0.dimensions(dim0);  	    	
    	//CellImg<T> target = fac.create(dim0);	//now create result with correct dimensions.  
    	NtreeImg<T, ?> target = fac.create(dim0);
		UnaryComputerOp<Iterable, RealType> mean_op = Computers.unary(ops, Ops.Stats.Mean.class, RealType.class, Iterable.class);
    	ops.run("project",target,input,mean_op,Tindex);		  
    	return target;    		
    }
    public <T extends RealType<T>& NativeType<T>> RandomAccessibleInterval<T> blur(RandomAccessibleInterval<T> input,double sigma){            
        CellImgFactory<T> fac = new CellImgFactory<T>(input.randomAccess().get());    	    	
        long[] dim = new long[input.numDimensions()];
    	input.dimensions(dim);
        Img<T> target = fac.create(dim);        	        	
        double[] Sigma= new double[] {sigma,sigma};  
        //final ArrayList<RandomAccessibleInterval<T>> resultList = new ArrayList<RandomAccessibleInterval<T>>();                	
    	Gauss3.gauss(Sigma, Views.extendMirrorSingle(input), target);
    	return target;       	
}
    public <T extends RealType<T>& NativeType<T>> RandomAccessibleInterval<T> blur(RandomAccessibleInterval<T> input,double sigma, boolean noOut){            
        //CellImgFactory<T> fac = new CellImgFactory<T>(input.randomAccess().get());    	    	
        long[] dim = new long[input.numDimensions()];
        System.out.print(dim.length);
    	//input.dimensions(dim);
        //Img<T> target = fac.create(dim);        	        	
        double[] Sigma= new double[] {sigma,sigma};  
        //final ArrayList<RandomAccessibleInterval<T>> resultList = new ArrayList<RandomAccessibleInterval<T>>();                	
    	Gauss3.gauss(Sigma, Views.extendMirrorSingle(input), input); 
    	return input;
}
    
    public <T extends RealType<T>& NativeType<T>> RandomAccessibleInterval<T> blur(Img<T> input,double sigma){            
        CellImgFactory<T> fac = new CellImgFactory<T>(input.randomAccess().get());    	    	
        long[] dim = new long[input.numDimensions()];
    	input.dimensions(dim);
        Img<T> target = fac.create(dim);     	        	
        double[] Sigma= new double[] {sigma,sigma};  
        //final ArrayList<RandomAccessibleInterval<T>> resultList = new ArrayList<RandomAccessibleInterval<T>>();                	
    	Gauss3.gauss(Sigma, Views.extendMirrorSingle(input), target);
    	return target;       	
}
    /**
	 * Copy the contents of an source {@link RandomAccessible} in an
	 * interval defined by and target {@link RandomAccessibleInterval}
	 * into that target {@link RandomAccessibleInterval}.
	 *
	 * @param <T>
	 * @param source
	 * @param target
	 */
    public static <T extends Type<T>> void copy(
			final RandomAccessible<? extends T> source,
			final RandomAccessibleInterval<T> target) {

		Views.flatIterable(Views.interval(Views.pair(source, target), target)).forEach(
				pair -> pair.getB().set(pair.getA()));
	}
	public void blurStack(
			RandomAccessibleInterval<T>inputStack, int nChannels, int nFrames, double sigma) {
			
			//make a stack of same dimensions as input:
			//ArrayImgFactory<T> shiftFac = new ArrayImgFactory<>(inputStack.getAt(0));
			//RandomAccessibleInterval<T> shiftStack = shiftFac.create(inputStack.dimensionsAsLongArray());
			int Tindex = inputStack.numDimensions()-1;
			long[] dim = new long[inputStack.numDimensions()];

			
			
			System.out.print(Tindex);
			System.out.print(Arrays.toString(dim));
			//if(nChannels == 1){		       	    				
				
				
				//logger.info("stack has channels");
				
				//loop through Stack:
				//make array to hold the slices
			ArrayImgFactory<T> sliceFac =new ArrayImgFactory<T>(inputStack.randomAccess().get());
			Img<T> blurred_slice = sliceFac.create(Views.hyperSlice(inputStack, Tindex, 0));
				for (int i=0;i<nFrames;i++) {
//					System.out.print(i);
					
					
					//logger.info("looping through stack");
					//blur the slice:
//					ImageJFunctions.show(Views.hyperSlice(inputStack, Tindex, i)).setTitle("current slice to lbur");
					Gauss3.gauss(sigma, Views.extendMirrorSingle(Views.hyperSlice(inputStack, Tindex, i)), blurred_slice);
//					System.out.print("blurred frame "+i);
					
					copy(blurred_slice, Views.hyperSlice(inputStack,Tindex,i));
//					ImageJFunctions.show(Views.hyperSlice(inputStack,Tindex,i));
				}
				//put back in a stack:				

			//}
			
			
			//else{		       	    				
				
				//TODO
			//}
			
			
		}
    public < T extends Comparable< T > & Type< T > > void computeMinMax(
    		final Iterable< T > input, final T min, final T max )
    	{
    		// create a cursor for the image (the order does not matter)
    		final Iterator< T > iterator = input.iterator();
     
    		// initialize min and max with the first image value
    		T type = iterator.next();
     
    		min.set( type );
    		max.set( type );
     
    		// loop over the rest of the data and determine min and max value
    		while ( iterator.hasNext() )
    		{
    			// we need this type more than once
    			type = iterator.next();
     
    			if ( type.compareTo( min ) < 0 )
    				min.set( type );
     
    			if ( type.compareTo( max ) > 0 )
    				max.set( type );
    		}
    	}
    public <T extends RealType<T>& NativeType<T>> void normMultiChannel2(RandomAccessibleInterval<FloatType> input, int nChannels){
    	FloatType maxIn = new FloatType();
    	FloatType minIn = new FloatType();
    	computeMinMax(Views.iterable(input),minIn, maxIn);
    	
    	FloatType max = new FloatType();
    	FloatType min = new FloatType();
    	max.setOne();
    	min.set(minIn.get()/maxIn.get());
    	//min should not be zero but min/max of input, otherwise there will be infinities/ huge values at the borders
    	if (nChannels ==1) {Normalize.normalize(Views.iterable(input), min, max);}
    	else {
    		int Cindex = 2; //if it is not 1 it's always index 2!
    		for (int i=0; i<nChannels;i++) {
    			Normalize.normalize(Views.iterable(Views.hyperSlice(input, Cindex, i)),min, max);    	
    		}    	
    	}
    }
	private boolean checkDimensions(RandomAccessibleInterval< ? > image1, RandomAccessibleInterval< ? > image2)
	{			
		final long[] dims1 = Intervals.dimensionsAsLongArray( image1 );
		final long[] dims2 = Intervals.dimensionsAsLongArray( image2 );
		
		final boolean equal = Arrays.equals( dims1, dims2 );
		if ( !equal ){			
			//throw new IllegalArgumentException( "LoopBuilder, image dimensions do not match: " + Arrays.toString(dims1) + Arrays.toString(dims2) );
			return false;
		}
		else {
			return true;
		}
	}
    public RandomAccessibleInterval<FloatType> divideStackbyStack(final RandomAccessibleInterval<FloatType> input1,final RandomAccessibleInterval<FloatType> input2, RandomAccessibleInterval<FloatType> output, int nFrames) {
    	//first see if there are any frames at all, if not the dims should already match.
    	if (nFrames == 1) {
    		divideLoopBuilder(input1,input2,output);    		
    	}
    	else {
    		//two cases: 1. the blur was done on the z-projection or 2 . on every slice.
    		//1. create stack of blurs to match dimensions
    		//2. dimensions should already match do nothing here:
    		//check which is true:
    		if (checkDimensions(input1,input2)==true) {	//case 1.
    			divideLoopBuilder(input1,input2,output);
    		}
    		else {
    			//this time I create a View of the blurred that has the same dimension as the image to correct!
    			//the colors should already be the same, so just stack it nFrames times!   	
    			ArrayList<RandomAccessibleInterval<FloatType>>FrameList = new ArrayList<RandomAccessibleInterval<FloatType>>();	
    			for (int i=0; i<nFrames; i++) {
    				FrameList.add(input2);
    			}
    			//create a big View:
    			RandomAccessibleInterval<FloatType> StackView = Views.stack(FrameList);
    			//and do the division using LoopBuilder:
    			divideLoopBuilder(input1,StackView,output);
    		}
    	}
    	return output;
    }
    public void divideLoopBuilder(final RandomAccessibleInterval<FloatType> slice1, final RandomAccessibleInterval<FloatType> slice2, RandomAccessibleInterval<FloatType> output){    
    	//TODO: call LoopBuilder with multithreading?
    	
    	LoopBuilder.setImages(output, slice1, slice2).multiThreaded().forEachPixel(
    	    (o, a, b) -> {
    	        o.set(a.getRealFloat() / b.getRealFloat());
    	    }
    	);
    }
	public <T extends RealType<T>, C extends ComplexType<C>> RandomAccessibleInterval<FloatType> convertTo32bit(
			final RandomAccessibleInterval<T> img) {
		return Converters.convert(img, (i, o) -> o.setReal(i.getRealFloat()), new FloatType());
	}    
}
