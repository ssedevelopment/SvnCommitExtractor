# SvnCommitExtractor
ComAnI plug-in for extracting SVN commits

## Requirements
SVN needs to be installed

## Support
Operating system: all

Version control system: "svn"

## Plug-in-specific Configuration Parameters
```Properties
A positive integer value defining the maximum number of allowed attempts to execute the same SVN command.
Multiple attempts may be necessary as SVN commands trigger calls to the base repository, which may only be
available via network and, hence, may result in (temporal) unavailability. The definition of this property
is optional as the default maximum number of allowed attempts is 1 guaranteeing the execution of each SVN
command at least once.

Type: optional
Default value: 1
Related parameters: none
extraction.svn_extractor.max_attempts = <Number>
```
