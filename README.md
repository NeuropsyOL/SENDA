[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.1419527.svg)](https://doi.org/10.5281/zenodo.1419527)
---
# SENDA Android Application to stream out sensor data directly from the Phone

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
All Movella DOT sensors are set to the EULER_COMPLETE measurement mode at the start of the measurement and stream free acceleration and Euler angles at a nominal sampling rate of 60 Hz. The stream contains 7 channels:
- Free acceleration (3 channels)
- Euler angles (3 channels)
- Sensor sample time fine.

For details about the data format and the orientation of the axes, see the Movella user manual.

## Getting Started

#### Installation 
Download the [latest release](https://github.com/NeuropsyOL/SENDA/releases/latest) and install the apk on your smartphone or tablet running Android 11 or higher.

#### Usage: 
Upon launching the app, the user is presented with a main screen displaying a list of available device sensors. Each sensor can be individually selected using check buttons, allowing the user to choose which data to stream. The list of sensors can be refreshed with a swipe-down gesture.

Some sensors—such as audio classification and recording, GPS, and Movella sensors—require special permissions to function properly. When necessary, a permission request dialog is shown when a sensor is selected. GPS requires access to precise location at all times in order to operate correctly. If a permission is repeatedly denied, Android may block further requests. In such cases, the app opens the system settings screen, allowing the user to grant the required permission manually.

To start streaming, the user must press the **START LSL** button. To stop streaming, the **STOP LSL** button must be pressed. While streaming is active, it is not possible to modify the selection of sensors.

The LSL streams transmitted by SENDA can be recorded using any LSL-compatible application, such as the [LabRecorder](https://github.com/labstreaminglayer/App-LabRecorder) on PC or [RECORDA](https://github.com/NeuropsyOL/RECORDA) on Android.

On newer versions of Android, it may be necessary to prevent the system from limiting the app's processing time to conserve battery. This can be done by navigating to **Settings → Battery optimization ** and disabling battery optimization for SENDA.

## Development

#### Prerequisites:
- Android Studio Giraffe | 2022.3.1 Patch 2
- Android API 34 SDK platform
- Android NDK 25.2.9519653
- CMake 3.22.1

Other version may work, the above listed versions are the ones used for development and testing.

#### Development:

In order to start with development you need to follow these steps: 

- Clone this repository
- Open project with Android Studio
- Import the project

## Contributing
Please feel free to contribute to this project by creating an issue first and then sending a pull request respectively. 

## Authors
* **Sarah Blum** - [s4rify](https://github.com/s4rify) 
* **Ali Ayub Khan** - [AliAyub007](https://github.com/AliAyub007)
* **Paul Maanen** - [pmaanen](https://github.com/pmaanen)

## License
This project is licensed under GNU General Public License License - see the [LICENSE.md](LICENSE.md) file for details

## Known Issues
- Sometimes the app complains about mission bluetooth permissions but does the scan for Movella sensors anyway.
- On a new installation multiple restarts of the app might be necessary until it asks for and notices newly granted permissions
- Some phones don't respect the foreground service and wakelock. A possible workaround is to set the app to unlimited background power usage in the app settings.


## Acknowledgments
[liblsl](https://github.com/sccn/liblsl), used under MIT license
[liblsl-Java](https://github.com/labstreaminglayer/liblsl-Java), used under MIT license
[Google Mediapipe](https://developers.google.com/mediapipe), Apache v2.0 license.

## Cite As:

