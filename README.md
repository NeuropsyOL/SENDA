[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.1419527.svg)](https://doi.org/10.5281/zenodo.1419527)
---
# SENDA Android Application to stream out Sensor Data directly from the Phone

SENDA(Sensor Data Streamer) is an android application to stream real time sensor reading using LSL (Lab Streaming Layer). Along with the sensors it streams real time audio as well. This is a fork from the original author's Git [Ali Ayub Khan's SENDA](https://github.com/AliAyub007/SENDA), which contains additional features and support for Samsung phones.

## Features 
The following sensors are included: 
- Accelerometer
- Light
- Proximity
- Gravity
- Linear Acceleration
- Rotation Vector
- Step Count
- Location
- Movella DOT (via Bluetooth)

## Data formats

### Location
The location stream includes four channels which contain, in this order:
- Latitude
- Longitude
- Elevation
- Delta (in meters)

### Movella sensors
All Movella DOT sensors are set to the EULER_COMPLETE measurement mode at the start of the measurement and stream free acceleration and Euler angles at a nominal sampling rate of 60 Hz. The stream contains 6 channels:
- Free acceleration (3 channels)
- Euler angles (3 channels)

For details about the data format and the orientation of the axes, see the Movella user manual.

## Getting Started
#### Development:

In order to start with development you need to follow these steps: 

- Clone this repository
- Open project with Android Studio


#### Usage: 

Install this application and start streaming data by clicking on Start LSL button. You can record this data on PC using Lab Recoder from https://github.com/sccn/labstreaminglayer/tree/master/Apps/LabRecorder. 

In newer Android versions, it might be useful to prevent Android from saving battery power by limiting processing time for the app. Go to Settings -> Battery optimization and disable this feature for the app.

## Built With

* [Android Studio](https://developer.android.com/studio/) - Android development framework
* [LSL ](https://github.com/sccn/labstreaminglayer) - Lab Streaming Layer library
* [Google MediaPipe ](https://developers.google.com/mediapipe) Google MediaPipe library

## Contributing

Please feel free to contribute to this project by creating an issue first and then sending a pull request respectively. 

## Authors

* **Sarah Blum** - [s4rify](https://github.com/s4rify) 
* **Ali Ayub Khan** - [AliAyub007](https://github.com/AliAyub007)
* **Paul Maanen** - [pmaanen](https://github.com/pmaanen)


## License

This project is licensed under GNU General Public License License - see the [LICENSE.md](https://github.com/AliAyub007/SENDA/blob/master/LICENSE) file for details


## Known Issues
- Sometimes the scan button complains about mission bluetooth permissions but does the scan for Movella sensors anyway.
- On a new installation multiple restarts of the app might be necessary until it asks for and notices newly granted permissions

## Acknowledgments
[liblsl Library](https://github.com/sccn/labstreaminglayer/tree/master/LSL): I used this library to develop this application.
[Google Mediapipe](https://developers.google.com/mediapipe): The audio classifier uses portions of the Google MediaPipe example code released under the Apache v2.0 license.
## Cite As:

