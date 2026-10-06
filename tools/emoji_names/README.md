# Emoji names

When Verbosity > Emoji is set to "Read by Backtalk", Backtalk replaces each emoji with its name
before the speech engine gets the text. The names are the text-to-speech names from the
[Unicode CLDR](https://cldr.unicode.org/) emoji annotations, in every CLDR language that names
at least half the emoji (138 languages and regional variants). They cover every emoji sequence: skin tones, families and other joined
sequences, flags and keycaps.

`make_emoji_names.py` writes them into `utils/src/main/assets/emoji_names`. Its comments say how
to download the data and describe the files. The names are from Emoji 18.0 and CLDR 49.0.0-BETA1.
To update to a new CLDR release, download that release and the emoji version it names, and run
the script again. Emoji newer than the data are left to the speech engine.

The data is copyright Unicode, Inc. and is used under the Unicode License v3, which is in
`utils/src/main/assets/emoji_names/LICENSE`.
