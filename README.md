# BeamProfileDriftCorrection
ImageJ plugIn for BeamProfile and Drift correction of 2D time series. The algorithm is described in the supplementary information to our paper
[Rapid single-molecule characterisation of nucleic-acid enzymes](https://doi.org/10.1101/2022.03.03.482895). 

## Install:

Download the .jar file in ``BeamProfileDriftCorrection/ImageCorrection/target/``
Copy it in your ``fiji.app/plugins/`` folder
Done!

## Parameters

**sigma**: determines the blurryness of the blur needed for the beamprofile correction. The larger and more prominent your features are the higher sigma generally needs to be. It also depends on the dimensions of the image, the goal is to blur out any features of interest so that changes of intensity are purely due to uneven illumination.

**darkframe**: The noise-pixel brightness produced by the camera in the absence of light. This is a property of the camera and can be measured easily.
## Scripting the plugin for batch-processing
BeamProfileDrift_batch.py contains a script for batch processing of all files in a folder.
To do batch processing from the ImageJ/Fiji gui, copy BeamProfileDrift_batch.py in the ``Fiji.app/plugins/Scripts/`` directory and restart ImageJ. You will then find a menu entry in ImageJ: Plugins -> Scripts -> BeamProfileDrift batch.
Launching this script will prompt two file choosing dialogs, one for the folder containing the raw image data (and only that!) and one for the folder to save the corrected image data. The parameters (see above) need to be manually changed in the script. This will be fixed in a future version.

