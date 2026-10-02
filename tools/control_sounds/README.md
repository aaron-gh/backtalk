# Control sounds

The control sounds are from the [Unspoken](https://github.com/ahicks92/Unspoken) add-on for NVDA,
by Bryan Smart and Austin Hicks. Unlike the rest of Backtalk, which is under the Apache License 2.0,
they are under the GNU General Public License, version 2. Its text, as Unspoken ships it, is in
[COPYING](COPYING) in this directory. They are data files that Backtalk only reads and plays, so
the license of Backtalk's code is unchanged.

These files are under the GPL:

    talkback/src/main/res/raw/control_button.wav        (Unspoken's button.wav)
    talkback/src/main/res/raw/control_checkbox.wav      (checkbox.wav)
    talkback/src/main/res/raw/control_clock.wav         (clock.wav)
    talkback/src/main/res/raw/control_combo_box.wav     (combobox.wav)
    talkback/src/main/res/raw/control_edit_text.wav     (editabletext.wav)
    talkback/src/main/res/raw/control_image.wav         (icon.wav)
    talkback/src/main/res/raw/control_link.wav          (link.wav)
    talkback/src/main/res/raw/control_list_item.wav     (listitem.wav)
    talkback/src/main/res/raw/control_menu_item.wav     (menuitem.wav)
    talkback/src/main/res/raw/control_radio_button.wav  (radiobutton.wav)
    talkback/src/main/res/raw/control_slider.wav        (slider.wav)
    talkback/src/main/res/raw/control_tab.wav           (tab.wav)
    talkback/src/main/res/raw/control_tree_item.wav     (treeviewitem.wav)

They were changed only by converting them to mono, 16 bit, 44.1 kHz WAV, with:

    afconvert -f WAVE -d LEI16@44100 -c 1 button.wav control_button.wav

The originals are in the Unspoken repository, in addon/globalPlugins/Unspoken/sounds.

`make_hrtf.py` packs the head-related transfer functions that play the sounds in 3D into
`utils/src/main/res/raw/hrtf_kemar.bin`. Its comments say how to download the measurements.

The HRTFs are the compact set of the MIT Media Lab KEMAR measurements, which Unspoken also used:

> Bill Gardner and Keith Martin, "HRTF Measurements of a KEMAR Dummy-Head Microphone", MIT Media
> Lab Perceptual Computing Technical Report #280, 1994.
> https://sound.media.mit.edu/resources/KEMAR.html

The report says: "This HRTF data is Copyright 1994 by the MIT Media Lab. It is provided without
any usage restrictions. We request that you cite the authors when using this data for research or
commercial applications."
