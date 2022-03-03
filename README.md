# BeamProfileDriftCorrection
ImageJ plugIn for BeamProfile and Drift correction

## Install:

Download the .jar file  in BeamProfileDriftCorrection/ImageCorrection/target/
Copy it in your fiji.app/plugins folder
Done!

## Scripting the plugin for batch-processing
BeamProfileDrift_batch.py contains a script for batch processing of all files in a folder.


##Parameters

sigma: determines the blurryness of the blur needed for the beamprofile correction

darkframe: The noise-pixel brightness produced by the camera in the absence of light. This is a property of the camera and can be measured easily.

