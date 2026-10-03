# Control sounds

Backtalk has no control sounds of its own. Each kind of control plays the sound the sound theme in
use gives it, and a control without one plays the usual focus sound. See [themes.md](../../themes.md)
for sound themes.

`make_unspoken_theme.py` makes a sound theme of the sounds from the
[Unspoken](https://github.com/ahicks92/Unspoken) add-on for NVDA, by Bryan Smart and Austin Hicks,
from a checkout of Unspoken. Their license, the GNU General Public License, version 2, goes in the
theme with them. Backtalk only reads the themes the user installs, so it ships no GPL sounds:

    git clone https://github.com/ahicks92/Unspoken
    python3 make_unspoken_theme.py Unspoken Unspoken.zip

`make_hrtf.py` packs the head-related transfer functions that play the sounds in 3D into
`utils/src/main/res/raw/hrtf_kemar.bin`. Its comments say how to download the measurements.

The HRTFs are the compact set of the MIT Media Lab KEMAR measurements, which Unspoken also used:

> Bill Gardner and Keith Martin, "HRTF Measurements of a KEMAR Dummy-Head Microphone", MIT Media
> Lab Perceptual Computing Technical Report #280, 1994.
> https://sound.media.mit.edu/resources/KEMAR.html

The report says: "This HRTF data is Copyright 1994 by the MIT Media Lab. It is provided without
any usage restrictions. We request that you cite the authors when using this data for research or
commercial applications."
