from singleMoleculeBiophysics.ImageCorrections import BeamProfileDriftCorrection
from ij import IJ
import net.imagej.ImageJ as c
import net.imglib2.img.display.imagej.ImageJFunctions as ijf;
from ij.io import DirectoryChooser,FileSaver
from loci.formats import ImageReader
from loci.plugins import BF
from loci.plugins.in import ImporterOptions
import os

def loadFile(path,series):			
	options = ImporterOptions()
	options.setSeriesOn(series,True)
	options.setVirtual(True)	#something goes wrong when it is set to true....
	options.setId(path)
	im = BF.openImagePlus(options)
	return im
def initializeFile(path):
	IR = ImageReader()
	IR.setId(path)
	SeriesCount = IR.getSeriesCount()
	return SeriesCount
def saveData(image,path,closeIm=True):
	FS = FileSaver(image)	
	FS.saveAsTiff(path+'.tif')
	if closeIm == True:
		image.close()
#seriesCount = initializeFile(inputFolder+f)
inputFolder = IJ.getDirectory("choose Folder with raw Data")#"C:/Users/stefa/OneDrive - University of Wollongong/"
outputFolder = IJ.getDirectory("choose folder to save results")
#inputFolder = "C:/Users/stefa/OneDrive/Desktop/beamProfileTest/testdata/2color_stack/"
files = os.listdir(inputFolder)

corr = BeamProfileDriftCorrection(c().getContext())		#initialize PlugIn
corr.setSigma(60.0)										#set sigma value for Gaussian blur
corr.setDarkframe(2348.0)								#set value for subtraction of dark "baseline"
for f in files:
	seriesCount = initializeFile(inputFolder+f)
	for s in range(seriesCount):
		im = loadFile(inputFolder+f,s)					#load data
		print(im)
		corr.setInput(im[0])								#set Input, im is array of ImagePlus and MetaData?
		corr.run()											
		result = corr.getResult()
		print(result)
		outName = "corr_"+f+"_series{:02d}".format(s)
		ij_result = ijf.wrapUnsignedShort(result,"title")			#result is Imglib2 Img<FloatType>. Wrap in ij1 ImagePlus and convert to 16-bit
		saveData(ij_result,outputFolder + outName)


