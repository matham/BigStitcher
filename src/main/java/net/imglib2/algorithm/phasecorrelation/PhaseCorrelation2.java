package net.imglib2.algorithm.phasecorrelation;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ij.ImageJ;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fft2.FFT;
import net.imglib2.algorithm.fft2.FFTMethods;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class PhaseCorrelation2 {
	
	
	
	/**
	 * calculate the phase correlation of fft1 and fft2, save result to res
	 * fft1 and fft2 will be altered by the function
	 * @param fft1
	 * @param fft2
	 * @param pcm
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>, R extends RealType<R>> void calculatePCMInPlace(
			RandomAccessibleInterval<T> fft1, RandomAccessibleInterval<S> fft2, RandomAccessibleInterval<R> pcm, ExecutorService service)
	{
		calculatePCM( fft1, fft1, fft2, fft2, pcm , service);
	}
	
	/**
	 * calculate the phase correlation of fft1 and fft2, save result to res
	 * fft1 and fft2 will NOT be altered by the function
	 * @param fft1
	 * @param ff1Copy - a temporary image same size as fft1 & fft2
	 * @param fft2
	 * @param ff2Copy - a temporary image same size as fft1 & fft2
	 * @param pcm
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>, R extends RealType<R>> void calculatePCM(
			RandomAccessibleInterval<T> fft1, RandomAccessibleInterval<T> fft1Copy, RandomAccessibleInterval<S> fft2, RandomAccessibleInterval<S> fft2Copy, RandomAccessibleInterval<R> pcm,
			ExecutorService service)
	{
		// TODO: multithreaded & check for cursor vs randomaccess
		
		// normalize, save to copies
		PhaseCorrelation2Util.normalizeInterval(fft1, fft1Copy, service);
		PhaseCorrelation2Util.normalizeInterval(fft2, fft2Copy, service);
		// conjugate
		PhaseCorrelation2Util.complexConjInterval(fft2Copy, fft2Copy, service);
		// in-place multiplication
		PhaseCorrelation2Util.multiplyComplexIntervals(fft1Copy, fft2Copy, fft1Copy, service);
		FFT.complexToReal(fft1Copy, pcm, service);
	}
	
	
	/**
	 * calculate phase correlation of fft1 and fft2, return result in a new Img
	 * @param fft1
	 * @param fft2
	 * @return
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>, R extends RealType<R>> RandomAccessibleInterval<R> calculatePCMInPlace(
			RandomAccessibleInterval<T> fft1, RandomAccessibleInterval<S> fft2, ImgFactory<R> factory, R type, ExecutorService service){
		
		long[] paddedDimensions = new long[fft1.numDimensions()];
		long[] realSize = new long[fft1.numDimensions()];
		
		FFTMethods.dimensionsComplexToRealFast(fft1, paddedDimensions, realSize);
		RandomAccessibleInterval<R> res = factory.create(realSize, type);
		
		calculatePCMInPlace(fft1, fft2, res, service);
		
		return res;
	}
	
	/**
	 * calculate phase correlation of fft1 and fft2, doing the calculations in copies of the ffts, return result in a new Img
	 * @param fft1
	 * @param fft2
	 * @return
	 */
	public static <T extends ComplexType<T> & NativeType<T>, S extends ComplexType<S> & NativeType <S>, R extends RealType<R>> RandomAccessibleInterval<R> calculatePCM(
			RandomAccessibleInterval<T> fft1, RandomAccessibleInterval<S> fft2, ImgFactory<R> factory, R type, ExecutorService service){
		
		long[] paddedDimensions = new long[fft1.numDimensions()];
		long[] realSize = new long[fft1.numDimensions()];
		
		FFTMethods.dimensionsComplexToRealFast(fft1, paddedDimensions, realSize);
		RandomAccessibleInterval<R> res = factory.create(realSize, type);
		
		final T typeT = Views.iterable(fft1).firstElement().createVariable();
		final S typeS = Views.iterable(fft2).firstElement().createVariable();
		RandomAccessibleInterval< T > fft1Copy;
		RandomAccessibleInterval< S > fft2Copy;

		try
		{
			fft1Copy = factory.imgFactory( typeT ).create(fft1, typeT );
			fft2Copy = factory.imgFactory( typeS ).create(fft2, typeS );
		}
		catch ( IncompatibleTypeException e )
		{
			throw new RuntimeException( "Cannot instantiate Img for type " + typeS.getClass().getSimpleName() + " or " + typeT.getClass().getSimpleName() );
		}
		
		
		calculatePCM(fft1, fft1Copy, fft2, fft2Copy, res, service);
		
		return res;
	}
	
	/**
	 * calculate and return the phase correlation matrix of two images
	 * @param img1
	 * @param img2
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>, R extends RealType<R>, C extends ComplexType<C>> RandomAccessibleInterval<R> calculatePCM(
			RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, int[] extension,
			ImgFactory<R> factory, R type, ImgFactory<C> fftFactory, C fftType, ExecutorService service){

		
		// TODO: Extension absolute per dimension in pixels, i.e. int[] extension
		// TODO: not bigger than the image dimension because the second mirroring is identical to the image
		
		Dimensions extSize = PhaseCorrelation2Util.getExtendedSize(img1, img2, extension);
		long[] paddedDimensions = new long[extSize.numDimensions()];
		long[] fftSize = new long[extSize.numDimensions()];
		FFTMethods.dimensionsRealToComplexFast(extSize, paddedDimensions, fftSize);
		
		RandomAccessibleInterval<C> fft1 = fftFactory.create(fftSize, fftType);
		RandomAccessibleInterval<C> fft2 = fftFactory.create(fftSize, fftType);
		
		FFT.realToComplex(Views.interval(PhaseCorrelation2Util.extendImageByFactor(img1, extension), 
				FFTMethods.paddingIntervalCentered(img1, new FinalInterval(paddedDimensions))), fft1, service);
		FFT.realToComplex(Views.interval(PhaseCorrelation2Util.extendImageByFactor(img2, extension), 
				FFTMethods.paddingIntervalCentered(img2, new FinalInterval(paddedDimensions))), fft2, service);
		
		RandomAccessibleInterval<R> pcm = calculatePCMInPlace(fft1, fft2, factory, type, service);
		return pcm;
		
	}
	
	/**
	 * calculate PCM with default extension
	 * @param img1
	 * @param img2
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>, R extends RealType<R>, C extends ComplexType<C>> RandomAccessibleInterval<R> calculatePCM(
			RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, ImgFactory<R> factory, R type,
			ImgFactory<C> fftFactory, C fftType, ExecutorService service) {
		
		int [] extension = new int[img1.numDimensions()];
		Arrays.fill(extension, 10);
		return calculatePCM(img1, img2, extension, factory, type, fftFactory, fftType, service);
	}
	
	
	/**
	 * calculate the shift between two images from the phase correlation matrix
	 * @param pcm
	 * @param img1
	 * @param img2
	 * @param nHighestPeaks
	 * @param minOverlap
	 * @param service
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>, R extends RealType<R>> PhaseCorrelationPeak2 getShift(
			RandomAccessibleInterval<T> pcm, RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, int nHighestPeaks,
			Dimensions minOverlap, ExecutorService service)
	{
		List<PhaseCorrelationPeak2> peaks = PhaseCorrelation2Util.getPCMMaxima(pcm, service, nHighestPeaks);
		//peaks = PhaseCorrelation2Util.getHighestPCMMaxima(peaks, nHighestPeaks);
		PhaseCorrelation2Util.expandPeakListToPossibleShifts(peaks, pcm, img1, img2);		
		PhaseCorrelation2Util.calculateCrossCorrParallel(peaks, img1, img2, minOverlap, service);		
		Collections.sort(peaks, Collections.reverseOrder(new PhaseCorrelationPeak2.ComparatorByCrossCorrelation()));
		
		return peaks.get(0);
	}
	
	/**
	 * calculate the sift with default parameters (5 highest pcm peaks are considered, no minimum overlap, temporary thread pool)
	 * @param pcm
	 * @param img1
	 * @param img2
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>, R extends RealType<R>> PhaseCorrelationPeak2 getShift(
			RandomAccessibleInterval<T> pcm, RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2)
	{
		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		PhaseCorrelationPeak2 res = getShift(pcm, img1, img2, 5, null, service);
		service.shutdown();
		return res;		
	}	
	
	
		
	public static void main(String[] args) {
		
		new ImageJ();
		
		Img<FloatType> img1 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img1.tif"));
		Img<FloatType> img2 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img2small.tif"));
		
		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		RandomAccessibleInterval<FloatType> pcm = calculatePCM(img1, img2, new ArrayImgFactory<FloatType>(), new FloatType(),
				new ArrayImgFactory<ComplexFloatType>(), new ComplexFloatType(), service );
		
		PhaseCorrelationPeak2 shiftPeak = getShift(pcm, img1, img2);
		
		RandomAccessibleInterval<FloatType> res = PhaseCorrelation2Util.dummyFuse(img1, img2, shiftPeak,service);
				
		ImageJFunctions.show(res);
	}

}