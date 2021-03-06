# Metrical Alignment
This is the code and data from my 2018 ISMIR paper ([branch](https://github.com/apmcleod/met-align/tree/ismir2018),[tag](https://github.com/apmcleod/met-align/releases/tag/ismir2018)) and my 2019 ICASSP paper ([branch](https://github.com/apmcleod/met-align/tree/icassp2019),[tag](https://github.com/apmcleod/met-align/releases/tag/icassp2019)). The master branch currently contains updated/better documented icassp code. If you use either, please cite it:

```
@inproceedings{McLeod:18b,
  title={Meter detection and alignment of {MIDI} performance},
  author={McLeod, Andrew and Steedman, Mark},
  booktitle={International Society for Music Information Retrieval Conference (ISMIR)},
  year={2018},
  pages={113--119}
}
```

```
@inproceedings{McLeod:19,
  title={Improved metrical alignment of {MIDI} performance based on a repetition-aware online-adapted grammar},
  author={McLeod, Andrew and Nakamura, Eita and Yoshii, Kazuyoshi},
  booktitle={IEEE International Conference on Acoustics, Speech, and Signal Processing (ICASSP)},
  year={2019},
  pages={186--190}
}
```

## Project Overview
This is a model for meter detection and alignment from live performance MIDI data. Example corpora are found in the `corpora` directory, [anacrusis files](#anacrusis-files) are found in the `anacrusis` directory, and a pre-trained grammar can be found in the `grammars` directory.

*NOTE*: In order to work well, the notes in the input MIDI files should be split into monophonic voices per MIDI channel. If they are not, the recommended way to do so is to use my [voice-splitting](https://github.com/apmcleod/voice-splitting) package, and run like this:

`$ java -cp bin voicesplitting.voice.hmm.HmmVoiceSplittingModelTester -w voice FILES`
(also add `-l` if the files are live performance).

This will create new MIDI files in the "voice" directory, with voice separation performed. Use these new MIDI files as input for meter alignment.

This model will not output time signature changes, and will by default skip MIDI files with time signature changes in them. This can be disabled in the arguments (see below), but it is better to split files on time signature changes if the resulting files are long enough (at least 5 bars).

If your MIDI files are quantized to a grid, and you want the model to use the 32nd-note pulse, use the option `-BFromFile` as described below. This does not force the correct phase or the correct time signature.

## Documentation
This document contains some basic examples and a general overview of how to use
the classes in this project. All specific documentation for the code found in this
project can be found in the [Javadocs](https://apmcleod.github.io/met-align/doc).

## Installing
The java files can all be compiled into class files in a bin directory using the Makefile
included with the project with the following command: `$ make`.

## Running
Once the class files are installed in the bin directory, the project can be run.
Run the project from the command line as follows (with default settings):

`$ java -cp bin metalign.Main -g grammar FILES`

For example: `$ java -cp bin metalign.Main -g grammars/all.lpcfg corpora/WTCInv/invent1.mid`

`grammar` should be a pre-trained grammar (see [Training a Grammar](#training-a-grammar)). You may include `-g` multiple times with different grammar files to create a joint grammar. (grammars/all.lpcfg contains all songs within the corpora directory, but is untested.)
`FILES` should be a list of 1 or more music files (MIDI/krn) or directories containing only music files. Any directory entered will be searched recursively for files.

Arguments to change settings:
 * `-f` = DO NOT extend each note within each voice to the next note's onset. Default extends, as in the paper.
 * `-m INT` = For beat tracking and hierarchy detection, throw out notes whose length is shorter than INT microseconds, once extended. Defaults to 100000, as in the paper.
 * `-s INT` = Use INT as the sub beat length. Defaults to 4, as in the paper. The value here should be the same as the one given when training the grammar and the beat tracking HMM, and when running evaluation.
 * `-b INT` = Use INT as the beam size. Defaults to 200, as in the paper.
 * `-L DOUBLE` = Set the local grammar weight alpha. Defaults to 2/3, as in the paper.
 * `-c` = Do not use the Rule of Congruence (described in the SMC paper). If you use this option, also make the beam much larger, at least `-b 500`.
 * `-X` = Force the model to output results for pieces with time signature changes and irregular time signatures. By default, such files are skipped because the model cannot output either.

Arguments important for special case of leave-one-out cross validation:
 * `-x` = Extract the trees of the song for testing from the loaded grammar when testing.
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.

Arguments to help debugging:
 * `-p` = Use verbose printing.
 * `-P` = Use super verbose printing.
 * `-l` = Print logging (time, hypothesis count, and notes at each step).

Arguments to use ground truth beats, or to perform beat tracking only:
 * `-BClass` = Use the given class for beat tracking. (FromFile or Hmm (default)). See [Training](#training-the-beat-tracking-hmm) for information on how to train the HMM parameters. `-BFromFile` will cause the output to be locked to the input file's 32nd-note grid (though not necessarily in phase or with the correct time signature).
 * `-HClass` = Use the given class for hierarchy detection. (FromFile or lpcfg (default)).

Other arguments:
 * `-T` = Use tracks as correct voice (instead of channels). Only used for MIDI files.
 * `-E FILE` = Print out the evaluation for each hypothesis as well with the given FILE as ground truth.
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.

### Output Format
The output of the Main program (without the verbose `-p` or `-P` flags) is as follows:

```
Voices: [[note_1,note_2,...],[note_1,note_2,...],...]
Beats: [tatum_1,tatum_2,...]
Hierarchy: M_4,2 length=4 anacrusis=0
Tatum times: ...
Sub-beat times: ...
Beat times: ...
Downbeat times: ...
```
The Beats and Hierarchy lines also contain that component's log-likelihood (a large, negative number) at the end.

The `Voices` line simply prints the voices given in the input file, since this model does not perform voice separation itself.

The `Beats` line contains a list of tatum objects (NOT just downbeats, beats, or sub-beats). Each looks like `(0.32,1000)`, where the first two numbers just index the tatum, and the last number represents the time of the tatum in microseconds.

The `Hierarchy` line describes the metrical structure of the printed tatums of the previous line.
* First, in `M_4,2`, the first number is the number of beats per bar, and the second number is the number of sub-beats per beat. E.g., `M_4,2` describes 4/4 time, and `M_2,3` describes 6/8 time. (Note that we consider there to be only 2 beats per 6/8 bar, each with 3 sub-beats. Other compound meters are defined similarly.)
* The `length` represents the number of tatums per sub-beat, and should always be 4, unless that was changed with `-s`.
* The `anacrusis` represents how many sub-beats fall before the first downbeat.

Finally, the 4 `times` lines list the tatum, sub-beat, beat, and downbeat times of the metrical structure, in microseconds.

### Training a grammar
Grammars for the LPCFG can be trained as follows:

`$ java -cp bin metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner -g grammar.lpcfg Files`

For example: `$ java -cp bin metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner -g grammar.lpcfg corpora/misc/quant`

The trained grammar will be saved in the file `grammar.lpcfg`.

`Files` should be a list of 1 or more music files (MIDI/krn/[noteB](#temperley-file-modifications)) or directories containing only music files. Any directory entered will be searched recursively for files.

Important arguments:
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.

Arguments to change settings:
 * `-l` = Do NOT use lexicalisation.
 * `-f` = Do NOT extend each note within each voice to the next note's onset. (default extends, as in the paper)
 * `-m INT` = Throw out notes whose length is shorter than INT microseconds, once extended. (defaults to 100000, as in the paper)
 * `-s INT` = Use INT as the sub beat length. (defaults to 4, as in the paper).

Additional arguments:
 * `-p INT` = Run in parallel with INT processes.
 * `-x` = Do NOT save trees in the grammar file. Saves memory, but makes extracting the trees at test time (for cross-validation) impossible.
 * `-v` = Use verbose printing.
 * `-T` = Use tracks as correct voice (instead of channels). Only used for MIDI files.

 #### Pre-trained grammars
 Some pre-trained grammars are included in the grammars directory:


  * `all.lpcfg`: Trained on all of the files within the corpora directory.
  * `misc.lpcfg`: Trained on corpora/misc/perf
  * `WTCInv.lpcfg`: Trained on corpora/WTCInv with -a anacrusis. (When testing on WTCInv, use `-a anacrusis -x`.

### Training the Beat Tracking HMM
The parameters for the beat tracking HMM must be set manually after an automatic training run. It can be trained as follows:

`$ java -cp bin metalign.beat.hmm.HmmBeatTrackingModelTrainer Files`

Files should be a list of 1 or more music files (MIDI/krn/[noteB](#temperley-file-modifications)) or directories containing only music
files. Any directory entered will be searched recursively for files.

Arguments:
 * `-T` = Use tracks as correct voice (instead of channels). Only used for MIDI files.
 * `-s` INT = Use INT as the sub beat length. Defaults to 4, as in the paper.
 * `-X` = Input files are xml directories from CrestMusePEDB.
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.

NoteB Training example: `$ java -cp bin metalign.beat.hmm.HmmBeatTrackingModelTrainer corpora/misc/perf`

Once the program is run, the parameter values should be written into the file `src/metalign/beat/hmm/HmmBeatTrackingModelParameters` on line 71, and then the files should be re-compiled by running `$ make`.

### Evaluating Performance
Performance can be evaluated as follows:

`$ java -cp bin metalign.utils.Evaluation -E groundtruth.midi <out.txt`

ARGS:
 * `-E FILE` = Evaluate the Main output (from std in) given the ground truth FILE.
 * `-w INT` = Use the given INT as the window length for accepted grouping matches, in microseconds. (Default = 70000).
 * `-T` = Use tracks as correct voice (instead of channels). Only used for MIDI files.
 * `-s INT` = Use INT as the sub beat length. Defaults to 4.
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.
 * `-X` = Force the model to output results for pieces with time signature changes and irregular time signatures. By default, such files are skipped because the model cannot output either.

To calculate means of multiple evaluations:
`$ java -cp bin metalign.utils.Evaluation -F <eval*.txt`

 * `-F` = Calculate means and standard deviations of the -E FILE results (read from std in).

Usage examples:

Evaluating output: `$ java -cp bin metalign.utils.Evaluation -E corpora/WTCInv/bach-0846-fugue.mid -a anacrusis <bach-0846-fugue.mid.out`

Calculating overall metric: `$ java -cp bin metalign.utils.Evaluation -F <bach-*-fugue.mid.out.eval`

### Anacrusis Files
Anacrusis files are used for MIDI files, since they often do not align tick 0 with a downbeat. They are found in the directory `anacrusis`.

They must be named the same as the MIDI file, plus `.anacrusis`.

They contain a single line with a single number, the number of MIDI ticks in the associated MIDI file before the first downbeat.


### Temperley File Modifications
The misc corpus contains noteB files from [David Temperley](http://www.link.cs.cmu.edu/melisma/melisma2003/nbfiles/misc/), to which I have added a single line at the beginning of the form `Bar bpb sbpb a`, where `bpb` is the number of beats per bar, `sbpb` is the number of sub beats per beat, and `a` is the anacrusis length, in sub beats.
