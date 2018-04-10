# ODG: Omics Database Generator

## Introduction

Please see the [BMC Bioinformatics](https://bmcbioinformatics.biomedcentral.com/articles/10.1186/s12859-017-1777-7) publication for more general information. You may also be interested in viewing [screenshots](https://josephguhlin.com/projects/odg-omics-database-generator/).

## What this does

This program collates genome files and output from genomic analysis programs to create a graph database for querying genomic data across domains. This program is stable and database structure is well defined.

Feel free to ask questions, make suggestions, or reach out for additional help.

## Manual

A [manual and FAQ are available](https://github.com/jguhlin/ODG/tree/master/doc). [Screenshots are also available](https://josephguhlin.com/projects/odg-omics-database-generator/).

An [Installation Video](https://youtu.be/X7RL-pZ4ZTA) is also available. It also contains some initial steps to getting the database pipeline prepared. The set-up is similar across all platforms. A quick-start PDF / PPT guide is currently being generated.

## FAQ

Please see the FAQ for additional information if you are having trouble.

## Usage

Please see the documentation to see what types of files are necessary.

### File Formats
File formats and attributes within are the most important factor in taking flat 2-dimensional files and generating a connected graph database. The manual details this, although you may take a look at the example files if you have additional questions. With ODG we have striven to make it flexible and work with the output of several programs. Many output files (especailly GFF and FASTA) can be converted to work with ODG relatively easily, as it is usually renaming a field. Many programs can do this, including *nano* on Linux and Mac OS X, and [Notepad2](http://www.flos-freeware.ch/notepad2.html) on Windows, although any text editor with Find/Replace functionality can work. Window's own "notepad" can work although it often struggles to open larger text files, such as GFF or FASTA files. 

### All Platforms
Download and extract one of the available releases.

Place files for each of your genomes in the data/ directory, one directory for each genome and genome version, or accession (strain). For Example:

    data/
         Arabidopsis_thaliana_10/
             assembly.fasta
             genes.gff3
             proteins.fasta
             ipscan_results.tsv
         Oryza_sativa_2/
             assembly.fasta
             genes.gff3
             proteins.fasta
             ipscan_results.tsv
         E_coli_1/
             assembly.fasta
             genes.gff3
             proteins.fasta
             ipscan_results.tsv

#### Example Input Files
Example input files are found in the example-input-files subdirectory on this github. They include a configuration file. You may place the configuration file in your ODG installed directory and the data files in the data directory, and proceed with database generation (create-db.bat or create-db.sh).

#### Example Output Files
Example output files (and input files, when necessary to generate output files) are provided here to give you an idea of what output you can expect from most command-line activities. The web interface is available in the Test Drive version of ODG, simple download it and run query-server.bat or query-server.sh and connect to http://localhost:6789/ on your computer once it has started.

Command-line queries are also available to run from the test-drive version of ODG using odg.sh or odg.bat as the initial command. Please see the manual for more information.

### For Windows
To begin the configuration program double click "odg-config.bat" and set your browser to http://localhost:33333 after the server has started, usually a few minutes after starting.
To compile the database, once all intervening software has been run you can run "odg-generate.bat" and wait, usually a half hour to a few hours depending on the size of your dataset.
To run the query server, run "odg-query.bat" and point your web browser to http://localhost:6789

### For *nix Systems
To begin the configuration program run "./odg-config.sh" and set your browser to http://localhost:33333 after the server has started, usually a few minutes after starting.
To compile the database, once all intervening software has been run you can run "./odg-generate.sh" and wait, usually a half hour to a few hours depending on the size of your dataset.
To run the query server, run "./odg-query.sh" and point your web browser to http://localhost:6789

## For More Information
Please see the user guide, available [here](https://github.com/jguhlin/ODG/blob/master/doc/ODG%20User%20Guide.pdf)

## Releases

ODG is offered as 2 releases, and should work on Windows, Mac, and *nix Systems. *nix systems are preferred, but ODG is developed and tested primarily on Windows. 

### Requirements

Java 1.8 is required. You likely have Java installed on your system. If not you may download it at http://www.oracle.com/technetwork/java/javase/downloads/ 
ODG is expected to work with, but not tested on, OpenJDK versions equivalent to Java 1.8. ODG is memory-intensive, at least 8GB is recommended.

## Releases

You can [view our releases here.](https://github.com/jguhlin/ODG/releases/tag/1.1.0)

### Test-drive ODG
This version contains a completed database built with publicly available data and compiled information for Arabidopsis thaliana, Glycine max, Medicago truncatula, and Phaseolus vulgaris. It is provided here to provide a way for you to test out the features of ODG and see if it is right for you.   

### Empty ODG
This version contains no test data and is ready to be populated with your genomes and data of choice. ODG is now bundled as an installer. To download the installer, click on this link:

### Installer ODG
With version 1.1.0 ODG now comes as an installable file. If you have issues please check your Java version first (see above).
[odg_installer_1.1.0.jar](https://github.com/jguhlin/ODG/releases/download/1.1.0/odg_installer_1.1.0.jar)

## Accessing ODG's Generated Database Programmatically

To access ODG's database directly, you must use a Neo4j library/package for your chosen language. This will allow you to access Neo4j via it's REST API. To do this you must mount the database using Neo4j's database software directly. I've written up [tutorial on doing this](josephguhlin.com/odg-neo4j-web-console/amp). 

Tutorials for accessing ODG's database from R is coming soon.

## Future

I am currently updating ODG's web interfaces to be more responsive. This is happening in a separate branch at this time.

## License
Copyright � 2017-2018 Joseph Guhlin, Licensed under GPL v3 (see LICENSE file)

