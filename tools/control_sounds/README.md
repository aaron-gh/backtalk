# Control sounds

The sounds in `talkback/src/main/res/raw/control_*.wav` are from the
[Unspoken](https://github.com/ahicks92/Unspoken) add-on for NVDA, by Bryan Smart and Austin Hicks,
which is under the GNU General Public License, version 2. They were converted to mono, 16 bit,
44.1 kHz WAV, the format that plays in 3D, with:

    afconvert -f WAVE -d LEI16@44100 -c 1 button.wav control_button.wav

`make_hrtf.py` packs the head-related transfer functions that play the sounds in 3D into
`utils/src/main/res/raw/hrtf_kemar.bin`. Its comments say how to download the measurements.

The HRTFs are the compact set of the MIT Media Lab KEMAR measurements, which Unspoken also used:

> Bill Gardner and Keith Martin, "HRTF Measurements of a KEMAR Dummy-Head Microphone", MIT Media
> Lab Perceptual Computing Technical Report #280, 1994.
> https://sound.media.mit.edu/resources/KEMAR.html

The report says: "This HRTF data is Copyright 1994 by the MIT Media Lab. It is provided without
any usage restrictions. We request that you cite the authors when using this data for research or
commercial applications."
