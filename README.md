# BeamProfileDriftCorrection
ImageJ plugIn for BeamProfile and Drift correction

## Install:

Download the .jar file  in BeamProfileDriftCorrection/ImageCorrection/target/
Copy it in your fiji.app/plugins folder
Done!

## Scripting the plugin for batch-processing

download BeamProfileDrift_batch.py, open it in fiji and enjoy!

##Parameters

sigma: determines the blurryness of the blur needed for the beamprofile correction
darkframe: The noise-pixel brightness produced by the camera in the absence of light. This is a property of the camera
It was last measured by Lisanne, November 2020 to <b> 2348 </b>
It will be the default value in the next commit

