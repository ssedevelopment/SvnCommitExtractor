# SvnCommitExtractor
This [ComAnI](https://github.com/CommitAnalysisInfrastructure/ComAnI) plug-in realizes an extractor for extracting commits from SVN repositories and providing them as input to a ComAnI analyzer. It supports the following extraction variants:
- **Full repository extraction**, which performs the extraction of all commits of a software repository. This requires the definition of the location of the target repository as part of the configuration file using the parameter `extraction.input`.
- **Partial repository extraction**, which performs the extraction of a predefined set of commits. Besides the location of the target repository, this requires the specification of an additional file, which contains a list of unique commit numbers, e.g., "r23". Each line of this commit list file must contain exactly one commit number. Further, the author of the commit list file must ensure that the commit numbers specify commits of the target repository. The usage of a commit list file requires its definition in the configuration file as follows: `extraction.commit_list = <path>/<to>/commitlist-file`
- **Single commit extraction**, in which the content of a single commit can be passed on the command line as an input. Therefore, the infrastructure has to be executed using the `-i` option followed by the commit information, which is terminated by a last line containing the string “!q!”.

Depending on the extraction variant, this extractor executes the following SVN commands:
- `svn log -q`: Prints all revisions of the current branch
- `svn log -q -<REVISION>`: Prints the log of a particular revision; `<REVISION>` will be replaced by a particular revision number, like "r78"
- `svn diff -x -U100000 -c -<REVISION>`: Prints the revision information (number and date), the content of the changed files (100.000 lines of context including renamed files), and the changes to these files; `<REVISION>` will be replaced by a particular revision number, like "r78"

*Main class name:* `net.ssehub.comani.extraction.svn.SvnCommitExtractor`

*Support:*
- Operating system: all
- Version control system: “svn”

For more information on how to use ComAnI and its plug-ins, we recommend reading the [ComAnI Guide](https://github.com/CommitAnalysisInfrastructure/ComAnI/blob/master/guide/ComAnI_Guide.pdf).

## Installation
Download the [SvnCommitExtractor.jar](/release/SvnCommitExtractor.jar) file from the release directory and save it to the ComAnI plug-ins directory on your machine. This directory is the one specified as `core.plugins_dir` in the configuration file of a particular ComAnI instance.

*Requirements:*
- The [ComAnI infrastructure](https://github.com/CommitAnalysisInfrastructure/ComAnI) has to be installed to execute this plug-in as the extractor of a particular ComAnI instance
- [SVN](https://subversion.apache.org/) has to be installed and globally executable

## Execution
This plug-in is not a standalone tool, but only executable as the extractor of a particular ComAnI instance. Therefore, it has to be defined in the configuration file via its fully qualified main class name as follows:

`extraction.extractor = net.ssehub.comani.extraction.svn.SvnCommitExtractor`

*Plug-in-specific configuration parameter(s):*

A positive integer value defining the maximum number of allowed attempts to execute the same SVN command.
Multiple attempts may be necessary as SVN commands trigger calls to the base repository, which may only be
available via network and, hence, may result in (temporal) unavailability. The definition of this property
is optional as the default maximum number of allowed attempts is 1 guaranteeing the execution of each SVN
command at least once.

```Properties
Type: optional
Default value: 1
Related parameters: none
extraction.svn_extractor.max_attempts = <Number>
```

*Single commit extraction:*

The ComAnI infrastructure offers the single commit extraction as one of three different extraction variants (read the [ComAnI Guide](https://github.com/CommitAnalysisInfrastructure/ComAnI/blob/master/guide/ComAnI_Guide.pdf) for more information). When using the SvnCommitExtractor, the required format of the string representing the single commit must be as illustrated by the following example:

```
r3
Index: readme.txt
===================================================================
--- readme.txt	(nonexistent)
+++ readme.txt	(revision 2)
@@ -0,0 +1,5 @@
+This repository hosts some files to check whether the GitCommitExtractor works as expected.
+These files neither represent a particular software nor do they have any relations.
+These files are only used for testing correct extraction of commits.
+
+This line was introduced on a new branch.
!q!
```

The first line in the example above is optional and defines the commit (revision) number of the commit. It must be defined exactly as illustrated: `r<NUMBER>`. If this line is not available, the commit extractor will use the default id `<no_id>` as commit id.

The following lines represent the content of the actual commit as provided by the SVN command `svn diff –x –U100000 –c –r<NUMBER>`, where `<NUMBER>` identifies the respective revision. Note that there is no further information, like the commit message. This content starts directly with the first changed file `Index: ...`. The commit extractor expects this either as the first line (if the optional revision above is not available) or as the second line.

The last line terminates the commit string as required by the infrastructure. This line must only contain `!q!`. All lines after this termination-string will be ignored.

## License
This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).

## Acknowledgments
This work is partially supported by the ITEA3 project [REVaMP²](http://www.revamp2-project.eu/), funded by the [BMBF (German Ministry of Research and Education)](https://www.bmbf.de/) under grant 01IS16042H.

A special thanks goes to the developers of [KernelHaven](https://github.com/KernelHaven/): Adam Krafczyk, Sascha El-Sharkawy, Moritz Fl\"oter, Alice Schwarz, Kevin Stahr, Johannes Ude, Manuel Nedde, Malek Boukhari, and Marvin Forstreuter. Their architecture and core concepts significantly inspired the development of this project. In particular, the mechanisms for file-based configuration of the infrastructure and the plug-ins as well as loading and executing individual plug-ins are adopted in this work.
