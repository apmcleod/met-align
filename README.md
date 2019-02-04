# Metrical Alignment

This is the code and data from my 2018 ISMIR paper. If you use it, please cite it:

```
@inproceedings{McLeod:18b,
  title={Meter detection and alignment of {MIDI} performance},
  author={McLeod, Andrew and Steedman, Mark},
  booktitle={{ISMIR}},
  year={2018},
  pages={113--119}
}
```

If you would like to use the code from the 2019 ICASSP paper, see the branch [icassp2019](https://github.com/apmcleod/met-align/tree/icassp2019).

## Project Overview
This is a model for meter detection and alignment from live performance MIDI data. Example corpora are found in the `corpora` directory, [anacrusis files](#anacrusis-files) are found in the `anacrusis` directory, and pre-trained grammars are found in the `grammars` directory.

## Documentation
This document contains some basic examples and a general overview of how to use
the classes in this project. All specific documentation for the code found in this
project can be found in the [Javadocs](https://apmcleod.github.io/met-align/doc). 

## Installing
The java files can all be compiled into class files in a bin directory using the Makefile
included with the project with the following command: `$ make`.

## Running
Once the class files are installed in the bin directory, the project can be run.
Run the project from the command line as follows:

`$ java -cp bin metalign.Main ARGS Files` 

 Standard usage: `$ java -cp bin metalign.Main -BHmm -HLpcfg -g grammars/WTCInvMiscq4e.m100000.lpcfg -e -m 100000 -x -a anacrusis -b 200 corpora/WTCInv/bach-0846-fugue.mid`

Files should be a list of 1 or more music files (MIDI/krn) or directories containing only music
files. Any directory entered will be searched recursively for files.

ARGS:
 * `-T` = Use tracks as correct voice (instead of channels). Only used for MIDI files.
 * `-p` = Use verbose printing.
 * `-P` = Use super verbose printing.
 * `-l` = Print logging (time, hypothesis count, and notes at each step).
 * `-J` = Run with incremental joint processing.
 * `-VClass` = Use the given class for voice separation. (FromFile (default) or Hmm)
 * `-BClass` = Use the given class for beat tracking. (FromFile (default) or Hmm). See [Training](#training-the-beat-tracking-hmm) for information on how to train the HMM parameters.
 * `-HClass` = Use the given class for hierarchy detection. (FromFile (default) or lpcfg).
 * `-g FILE` = Load a grammar in from the given file. Used only with -Hlpcfg. See [Generating](#generating-an-lpcfg-grammar-file), or use a pretrained one from the grammars directory.
 * `-x` = Extract the trees of the song for testing from the loaded grammar when testing. Used only with -Hlpcfg.
 * `-e` = Extend each note within each voice to the next note's onset.
 * `-m INT` = For beat tracking and hierarchy detection, throw out notes whose length is shorter than INT microseconds, once extended.
 * `-s INT` = Use INT as the sub beat length.
 * `-b INT` = Use INT as the beam size.
 * `-v INT` = Use INT as the voice beam size.
 * `-E FILE` = Print out the evaluation for each hypothesis as well with the given FILE as ground truth.
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.
 
 Usage examples:
 
 Standard usage: `$ java -cp bin metalign.Main -BHmm -HLpcfg -g grammars/WTCInvMiscq4e.m100000.lpcfg -e -m 100000 -x -a anacrusis -b 200 corpora/WTCInv/bach-0846-fugue.mid -l`
 
 Incremental joint: `$ java -cp bin metalign.Main -BHmm -HLpcfg -g grammars/WTCInvMiscq4e.m100000.lpcfg -e -m 100000 -x -a anacrusis -b 200 -v 5 corpora/WTCInv/bach-0846-fugue.mid -l`
 
 
### Generating an LPCFG Grammar File
Grammars for the LPCFG can be generated as follows:

`$ java -cp bin metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner ARGS Files`

Files should be a list of 1 or more music files (MIDI/krn/[noteB](#temperley-file-modifications)) or directories containing only music
files. Any directory entered will be searched recursively for files.

ARGS:
 * `-g FILE` = Write the grammar out to the given FILE.
 * `-v` = Use verbose printing.
 * `-T` = Use tracks as correct voice (instead of channels). Only used for MIDI files.
 * `-l` = Do NOT use lexicalisation.
 * `-e` = Extend each note within each voice to the next note's onset.
 * `-m INT` = Throw out notes whose length is shorter than INT microseconds, once extended.
 * `-s INT` = Use INT as the sub beat length.
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.
 
 Usage example:
 
 `$ java -cp bin metalign.hierarchy.lpcfg.MetricalLpcfgGeneratorRunner -g grammars/WTCInvMiscq4e.m100000.lpcfg -a anacrusis -e -m 100000 -s 4 -v corpora/misc/quant corpora/WTCInv`


### Training the Beat Tracking HMM
The parameters for the beat tracking HMM must be set manually after an automatic training run. It can be trained as follows:  

`$ java -cp bin metalign.beat.hmm.HmmBeatTrackingModelTrainer [ARGS] Files`

Files should be a list of 1 or more music files (MIDI/krn/[noteB](#temperley-file-modifications)) or directories containing only music
files. Any directory entered will be searched recursively for files.  

ARGS:
 * `-T` = Use tracks as correct voice (instead of channels). Only used for MIDI files.
 * `-s` INT = Use INT as the sub beat length.
 * `-X` = Input files are xml directories from CrestMusePEDB.
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.
 
Usage examples:  

NoteB Training: `$ java -cp bin metalign.beat.hmm.HmmBeatTrackingModelTrainer -s 4 corpora/misc/perf`  

XML Training: `$ java -cp bin metalign.beat.hmm.HmmBeatTrackingModelTrainer -s 4 -X corpora/CrestMusePEDB-xml`  

Once the program is run, the parameter values should be written into the file `src/metalign/beat/hmm/HmmBeatTrackingModelParameters` on line 71, and then the files should be re-compiled by running `$ make`.

### Evaluating Performance
Performance can be evaluated as follows:  

`$ java -cp bin metalign.utils.Evaluation ARGS`

ARGS:
 * `-E FILE` = Evaluate the Main output (from std in) given the ground truth FILE.
 * `-F` = Calculate means and standard deviations of the -E FILE results (read from std in).
 * `-w INT` = Use the given INT as the window length for accepted grouping matches, in microseconds. (Default = 70000).
 * `-T` = Use tracks as correct voice (instead of channels). Only used for MIDI files.
 * `-s INT` = Use INT as the sub beat length.
 * `-a FILE` = Search recursively under the given FILE for anacrusis files. See [Anacrusis Files](#anacrusis-files) for information about the anacrusis file format.
 
Usage examples:  

Evaluating output: `$ java -cp bin metalign.utils.Evaluation -E corpora/WTCInv/bach-0846-fugue.mid -a anacrusis <bach-0846-fugue.mid.out`  

Calculating overall metric: `$ java -cp bin metalign.utils.Evaluation -F <bach-*-fugue.mid.out.eval`

### Anacrusis Files
Anacrusis files are used for MIDI files, since they often do not align tick 0 with a downbeat. They are found in the directory `anacrusis`.  

They must be named the same as the MIDI file, plus `.anacrusis`.  

They contain a single line with a single number, the number of MIDI ticks in the associated MIDI file before the first downbeat.


### Temperley File Modifications
The misc corpus contains noteB files from [David Temperley](http://www.link.cs.cmu.edu/melisma/melisma2003/nbfiles/misc/), to which I have added a single line at the beginning of the form `Bar bpb sbpb a`, where `bpb` is the number of beats per bar, `sbpb` is the number of sub beats per beat, and `a` is the anacrusis length, in sub beats.
