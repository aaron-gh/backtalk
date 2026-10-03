#!/usr/bin/env python3
"""Makes a Backtalk sound pack of the control sounds from the Unspoken add-on for NVDA.

Backtalk has no control sounds of its own. Unspoken's sounds are under the GNU General Public
License, version 2, so they are not part of Backtalk, but anyone can make a pack of them:

    git clone https://github.com/ahicks92/Unspoken
    python3 make_unspoken_pack.py Unspoken unspoken-sounds.zip

Then load the pack in Backtalk settings, under Sound and vibration > Custom sounds > Load a sound
pack. The pack holds Unspoken's WAV files unchanged, renamed after the control sounds they play
for, with Unspoken's license text and a note of where they came from.
"""

import os
import sys
import zipfile

# Backtalk's control sounds, and the Unspoken sounds that play for them.
SOUNDS = {
    "control_button": "button.wav",
    "control_checkbox": "checkbox.wav",
    "control_radio_button": "radiobutton.wav",
    "control_edit_text": "editabletext.wav",
    "control_combo_box": "combobox.wav",
    "control_slider": "slider.wav",
    "control_link": "link.wav",
    "control_image": "icon.wav",
    "control_clock": "clock.wav",
    "control_tab": "tab.wav",
    "control_menu_item": "menuitem.wav",
    "control_list_item": "listitem.wav",
    "control_tree_item": "treeviewitem.wav",
}

NOTE = """These sounds are from the Unspoken add-on for NVDA, by Bryan Smart and Austin Hicks:
https://github.com/ahicks92/Unspoken

They are under the GNU General Public License, version 2, in COPYING.txt. They are Unspoken's
files, unchanged, renamed after the Backtalk control sounds they play for:

{names}
"""


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: make_unspoken_pack.py UNSPOKEN_CHECKOUT OUTPUT.zip")
    checkout, output = sys.argv[1:]
    sounds = os.path.join(checkout, "addon", "globalPlugins", "Unspoken", "sounds")
    license_text = os.path.join(checkout, "COPYING.txt")
    for path in [*(os.path.join(sounds, f) for f in SOUNDS.values()), license_text]:
        if not os.path.isfile(path):
            sys.exit(f"missing {path}; is {checkout} a checkout of Unspoken?")

    names = "\n".join(f"    {key}.wav  ({source})" for key, source in SOUNDS.items())
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as pack:
        for key, source in SOUNDS.items():
            pack.write(os.path.join(sounds, source), f"{key}.wav")
        pack.write(license_text, "COPYING.txt")
        pack.writestr("README.txt", NOTE.format(names=names))
    print(f"wrote {len(SOUNDS)} sounds to {output}")


if __name__ == "__main__":
    main()
