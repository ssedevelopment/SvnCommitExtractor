/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package net.ssehub.comani.extraction.svn;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.ssehub.comani.core.Logger.MessageType;
import net.ssehub.comani.data.ChangedArtifact;
import net.ssehub.comani.data.Commit;
import net.ssehub.comani.data.IExtractionQueue;
import net.ssehub.comani.extraction.AbstractCommitExtractor;
import net.ssehub.comani.extraction.ExtractionSetupException;
import net.ssehub.comani.utility.ProcessUtilities;
import net.ssehub.comani.utility.ProcessUtilities.ExecutionResult;

/**
 * The main class of this extractor. It extracts commits from SVN repositories on any platform.
 * 
 * @author Christian Kröher
 *
 */
public class SvnCommitExtractor extends AbstractCommitExtractor {

    /**
     * The identifier of this class, e.g., for printing messages.
     */
    private static final String ID = "SvnCommitExtractor";
    
    /**
     * The simple SVN log command, which can be used for printing all or single revisions with an arbitrary amount of
     * information. This command is usually combined with additional options to print the desired amount of information
     * for a particular (set of) revision(s).
     * 
     * @see SvnCommitExtractor#SVN_ALL_REVISIONS_COMMAND
     * @see SvnCommitExtractor#SVN_REVISION_LOG_COMMAND
     */
    private static final String SVN_LOG_COMMAND = "svn log";
    
    /**
     * The command for printing all revisions of the current branch. Each revision is printed in a single line, which
     * contains the revision number, the author, and the date of the commit. The log lines of the revisions are
     * separated by a single line of dashes.
     * 
     * <b>Note:</b> The alternative command "svn log -q -r 0:HEAD ^/" will print all revisions over all branches.
     * However, revisions from branches other than the current one cannot be used for extraction as they point to other
     * trunks, etc., which we would need to checkout first.
     */
    private static final String SVN_ALL_REVISIONS_COMMAND = SVN_LOG_COMMAND + " -q";
    
    /**
     * The command for printing the log of a particular revision. The number of the revision must be appended, like
     * "r78". The revision log is printed in a single line, which contains the revision number, the author, and the date
     * of the commit. A leading and trailing line of dashes surrounds the log line.
     */
    private static final String SVN_REVISION_LOG_COMMAND = SVN_ALL_REVISIONS_COMMAND + " -";
    
    /**
     * The command for printing the revision information (number and date), the content of the changed files
     * (100.000 lines of context including renamed files), and the changes to these files. The number of the commit, for
     * which this information shall be printed, must be appended, like "r78". 
     */
    private static final String SVN_COMMIT_CHANGES_COMMAND = "svn diff -x -U100000 -c -";
    
    /**
     * The string identifying the start of a diff header in a commit. The first line of the diff header starts with this
     * string. Each diff header marks the beginning of an individual file being changed by the respective commit.
     */
    private static final String DIFF_HEADER_START_PATTERN = "Index:";
    
    /**
     * The string identifying the end of a diff header in a commit. The last line of the diff header starts with this
     * string. After that line, the content of the changed artifact described by the diff header as well as the actual
     * changes to the artifact are listed. 
     * 
     * @see #DIFF_HEADER_START_PATTERN
     */
    private static final String DIFF_HEADER_END_PATTERN = "@@";
    
    /**
     * The string representation of the properties' key identifying the maximum number of allowed attempts to execute
     * the same SVN command. Multiple attempts may be necessary as SVN commands trigger calls to the base repository,
     * which may only be available via network and, hence, may result in (temporal) unavailability.
     * The definition of this property is optional as the default maximum number of allowed attempts is <i>1</i>
     * guaranteeing the execution of each SVN command at least once.
     */
    private static final String PROPERTY_MAX_ATTEMPTS = "extraction.svn_extractor.max_attempts";
    
    /**
     * The {@link ProcessUtilities} for retrieving SVN information, like the available commits and their data, via the
     * execution of external processes.
     */
    private ProcessUtilities processUtilities;
    
    /**
     * The maximum number of allowed attempts to execute the same SVN command. The default value is <i>1</i>, which is
     * used if the user does not specify any other maximum number via the {@link #PROPERTY_MAX_ATTEMPTS} property.
     */
    private int maxSvnCommandAttempts;
    
    /**
     * Constructs a new instance of this extractor, which extracts commits from SVN repositories on any platform.
     * 
     * @param extractionProperties the properties of the properties file defining the extraction process and the
     *        configuration of the extractor in use; all properties, which start with the prefix "<tt>extraction.</tt>"
     * @param commitQueue the {@link IExtractionQueue} for transferring commits from an extractor to an analyzer
     * @throws ExtractionSetupException if setting-up this extractor failed
     */
    public SvnCommitExtractor(Properties extractionProperties, IExtractionQueue commitQueue)
            throws ExtractionSetupException {
        super(extractionProperties, commitQueue);
        prepare();
        logger.log(ID, this.getClass().getName() + " created", null, MessageType.DEBUG);
    }
    
    /**
     * Prepares this extractor for execution, e.g., reading and setting the properties as well as creating required
     * utilities.
     * 
     * @throws ExtractionSetupException if setting-up the necessary elements of this extractor failed
     */
    private void prepare() throws ExtractionSetupException {
        processUtilities = ProcessUtilities.getInstance();
        // Check if SVN is installed and available
        ExecutionResult executionResult = processUtilities.executeCommand("svn --version", null);
        if (!executionResult.executionSuccessful()) {
            throw new ExtractionSetupException("Testing SVN availability failed.\n" 
                    + executionResult.getErrorOutputData());
        }
        // Check if the maximum number of allowed process executions attempts is defined
        String maxSvnCommandAttemptsString = extractionProperties.getProperty(PROPERTY_MAX_ATTEMPTS);
        if (maxSvnCommandAttemptsString == null) {
            maxSvnCommandAttempts = 1;
        } else {
            try {                
                maxSvnCommandAttempts = Integer.parseInt(maxSvnCommandAttemptsString);
            } catch (NumberFormatException e) {
                throw new ExtractionSetupException("The maximum of attempts to execute an SVN command is not a number");
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean extract(File repository) {
        logger.log(ID, "Full extraction of all available revisions in the repository", null, MessageType.DEBUG);
        boolean extractionSuccessful = false;
        String revisionsLog = executeSvnCommand(SVN_ALL_REVISIONS_COMMAND, repository);
        if (revisionsLog != null) {
            // We assume that the standard output stream of the process executed above contains the revision numbers
            Map<String, String> revisionNumbers = getRevisionNumbers(revisionsLog);
            if (!revisionNumbers.isEmpty()) {                
                extractionSuccessful = extract(repository, revisionNumbers);
            } else {
                logger.log(ID, "Full extraction of all available revisions from repository \"" 
                        + repository.getAbsolutePath() + "\" failed", "Retrieving revisions log using \"" 
                        + SVN_ALL_REVISIONS_COMMAND + "\" returned no revision numbers", MessageType.ERROR);
            }
        } else {
            logger.log(ID, "Full extraction of all available revisions from repository \"" 
                    + repository.getAbsolutePath() + "\" failed", "Retrieving revisions log using \"" 
                    + SVN_ALL_REVISIONS_COMMAND + "\" returned no revision numbers", MessageType.ERROR);
        }
        return extractionSuccessful;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean extract(File repository, List<String> commitList) {
        boolean extractionSuccessful = false;
        logger.log(ID, "Selective extraction of " + commitList.size() + " revision(s) based on commit list file", null,
                MessageType.DEBUG);
        Map<String, String> revisionNumbers = new HashMap<String, String>();
        String revisionLog = null;
        for (String revisionNumberString : commitList) {
            if (revisionNumberString.matches("^r\\d+")) {
                revisionLog = executeSvnCommand(SVN_REVISION_LOG_COMMAND + revisionNumberString, repository);
                if (revisionLog != null) {
                    // We assume that the standard output stream of the process executed above contains the revision log
                    Map<String, String> revisionNumber = getRevisionNumbers(revisionLog);
                    if (!revisionNumber.isEmpty()) {                        
                        revisionNumbers.putAll(revisionNumber);
                    } else {
                        logger.log(ID, "Selective extraction of revision \"" + revisionNumberString 
                                + "\" (based on commit list file) failed", "Retrieving revision log using \"" 
                                + SVN_REVISION_LOG_COMMAND + revisionNumberString + "\" returned no revision number" 
                                + "; this revision is skipped", MessageType.WARNING);
                    }
                } else {
                    logger.log(ID, "Retrieving revision log failed",
                            "\"" + revisionNumberString + "\" seems to be an unknown revision", MessageType.WARNING);
                }
            } else {
                logger.log(ID, "Excluding commit from extraction",
                        "\"" + revisionNumberString + "\" is not a valid SVN revision, like \"r70\"",
                        MessageType.INFO);
            }
        }
        if (!revisionNumbers.isEmpty()) {
            extractionSuccessful = extract(repository, revisionNumbers);
        } else {
            logger.log(ID, "Selective extraction from repository \"" + repository.getAbsolutePath() 
                    + "\" based on commit list file failed", "Retrieving revision logs using \"" 
                    + SVN_REVISION_LOG_COMMAND + "r<NUMBER>\" for the specified revisions returned no revision numbers",
                    MessageType.ERROR);
        }
        return extractionSuccessful;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean extract(String commit) {
        logger.log(ID, "Extraction (parsing) of single commit", null, MessageType.DEBUG);
        boolean extractionSuccessful = false;
        Commit commitObject = createCommit("<no_id>", "<no_date>", new String[0], commit);
        if (commitObject != null) {
            while (!commitQueue.addCommit(commitObject)) {
                logger.log(ID, "Waiting to add commit to queue", null, MessageType.DEBUG);
            }
            extractionSuccessful = true;
        }
        return extractionSuccessful;
    }
    
    /**
     * Extracts all revisions from the defined repository included in the given set of revision numbers.
     * 
     * @param repository the {@link File} representing the repository directory; never <code>null</code> and always a
     *                   directory
     * @param revisionNumbers the map of revision numbers as created by {@link #getRevisionNumbers(String)}, which
     *        define the revisions to be extracted; should never be <code>null</code>
     * @return <code>true</code> if extracting <b>all</b> revisions was successful; <code>false</code> if the
     *         extraction of at least one revision failed, which terminates the execution of this method
     */
    private boolean extract(File repository, Map<String, String> revisionNumbers) {
        boolean extractionSuccessful = true;
        Set<String> revisionNumbersSet = revisionNumbers.keySet();
        logger.log(ID, "Extracting " + revisionNumbersSet.size() + " revisions from \"" + repository.getAbsolutePath(),
                null, MessageType.DEBUG);
        String standardOutputString = null;
        String[] commitHeader = new String[0];
        Commit commit;
        for (String revisionNumber : revisionNumbersSet) {
            logger.log(ID, "Extracting revision \"" + revisionNumber + "\"", null, MessageType.DEBUG);
            // Retrieve the commit header information, like the author, date, and commit message
            standardOutputString = executeSvnCommand(SVN_LOG_COMMAND + " -" + revisionNumber, repository);
            if (standardOutputString != null) {
                commitHeader = createCommitHeader(standardOutputString);
            } else {
                logger.log(ID, "Retrieving commit header information for revision \"" + revisionNumber + "\" failed", 
                        "Creating commit without commit header information", MessageType.WARNING);
            }
            // Retrieve the commit information, like the changed files
            standardOutputString = executeSvnCommand(SVN_COMMIT_CHANGES_COMMAND + revisionNumber, repository);
            if (standardOutputString != null) {
                commit = createCommit(revisionNumber, revisionNumbers.get(revisionNumber), commitHeader,
                        standardOutputString);
                if (commit != null) {                    
                    while (!commitQueue.addCommit(commit)) {
                        logger.log(ID, "Waiting to add commit to queue", null, MessageType.DEBUG);
                    }
                }
            } else {
                logger.log(ID, "Retrieving commit changes information for revision \"" + revisionNumber + "\" failed", 
                        "Extraction terminated", MessageType.ERROR);
                extractionSuccessful = false;
                break;
            }
        }
        return extractionSuccessful;
    }
    
    /**
     * Creates the commit header information lines as required by a {@link Commit} object.
     * 
     * @param commitHeaderString the single string containing the commit header line(s) as provided by the 
     *        {@link #SVN_LOG_COMMAND} command for a particular revision; should never be <code>null</code>
     * @return a set of strings representing the commit header information lines; never <code>null</code>, but may
     *         <i>empty</i>
     */
    private String[] createCommitHeader(String commitHeaderString) {
        String[] commitHeaderLines = new String[0];
        if (!commitHeaderString.isEmpty()) {
            commitHeaderLines = commitHeaderString.split("\n");
        }
        return commitHeaderLines;
    }
    
    /**
     * Creates a new {@link Commit} based on the given information.
     * 
     * @param commitNumber the commit number (revision) of the commit as provided by the keys of the map created by
     *        {@link #getRevisionNumbers(String)}
     * @param commitDate the date of the commit as provided by the values of the map created by
     *        {@link #getRevisionNumbers(String)}
     * @param commitHeader the commit header information lines as provided by {@link #createCommitHeader(String)}
     * @param commitContent the content of the commit as provided by executing the {@link #SVN_COMMIT_CHANGES_COMMAND}
     *        command, which does not include any commit information, like the author or message
     * @return a new commit based on the given information; note that due to the {@link #SVN_COMMIT_CHANGES_COMMAND}
     *         command the commit contains an empty commit header
     * 
     */
    private Commit createCommit(String commitNumber, String commitDate, String[] commitHeader, String commitContent) {
        List<ChangedArtifact> changedArtifacts = null;
        if (commitNumber != null && !commitNumber.isEmpty()) {
            if (commitDate != null && !commitDate.isEmpty()) {
                // Commit content may be empty, which results in an empty list of changed artifacts
                if (commitContent != null) {
                    changedArtifacts = createChangedArtifacts(commitContent);
                } else {
                    logger.log(ID, "Commit content for commit \"" + commitNumber + "\" not available",
                            "No commit created", MessageType.WARNING);
                }
            } else {
                logger.log(ID, "Commit date for commit \"" + commitNumber + "\" not available", "No commit created",
                        MessageType.WARNING);
            }
        } else {
            logger.log(ID, "Commit number not available", "No commit created", MessageType.WARNING);
        }
        return new Commit(commitNumber, commitDate, commitHeader, changedArtifacts);
    }
    
    /**
     * Creates a list of {@link ChangedArtifact}s based on the given information.
     * 
     * @param commitContent the content of the commit as provided by executing the {@link #SVN_COMMIT_CHANGES_COMMAND}
     *        command, which does not include any commit information, like the author or message; should never be
     *        <code>null</code>
     * @return a list of changed artifacts; may be <code>null</code>, if parsing the commit content failed or no
     *         artifacts were changed by the commit
     */
    private List<ChangedArtifact> createChangedArtifacts(String commitContent) {
        List<ChangedArtifact> changedArtifacts = null;
        String[] commitContentLines = commitContent.split("\n");
        if (commitContentLines.length > 1) {
            String commitContentLine;
            changedArtifacts = new ArrayList<ChangedArtifact>();
            ChangedArtifact changedArtifact = null;
            boolean artifactContentReached = false;
            for (int i = 0; i < commitContentLines.length; i++) {
                // Due to the SVN_COMMIT_CHANGES_COMMAND, the content directly starts with changed artifacts
                commitContentLine = commitContentLines[i];
                if (commitContentLine.startsWith(DIFF_HEADER_START_PATTERN)) {
                    // Start of changed artifact; save the previous one to the list before creating a new one
                    artifactContentReached = false;
                    if (changedArtifact != null) {
                        changedArtifacts.add(changedArtifact);
                    }
                    changedArtifact = new ChangedArtifact();
                    String artifactPath = commitContentLine.substring(commitContentLine.indexOf(" ") + 1);
                    changedArtifact.addArtifactPath(artifactPath);
                    if (artifactPath.contains("/")) {
                        changedArtifact.addArtifactName(artifactPath.substring(artifactPath.lastIndexOf("/") + 1));
                    } else {
                        changedArtifact.addArtifactName(artifactPath);
                    }
                    changedArtifact.addDiffHeaderLine(commitContentLine);
                } else {
                    if (artifactContentReached) {
                        /*
                         * In some cases SVN adds an additional line after the content of a changed artifact stating
                         * "\ No newline at end of file". To avoid adding this line to the content of a changed
                         * artifact, skip such lines.
                         */
                        if (!commitContentLine.startsWith("\\")) {
                            changedArtifact.addContentLine(commitContentLine);
                        }
                    } else {
                        artifactContentReached = commitContentLine.startsWith(DIFF_HEADER_END_PATTERN);
                        changedArtifact.addDiffHeaderLine(commitContentLine);
                    }
                }
                if ((i + 1) == commitContentLines.length) {
                    // End of changes, add last changed artifact to list
                    changedArtifacts.add(changedArtifact);
                }
            }
        }
        return changedArtifacts;
    }
    
    /**
     * Extracts the revisions and their commit date from the given revision log as returned by the
     * {@link #SVN_ALL_REVISIONS_COMMAND} or {@link #SVN_REVISION_LOG_COMMAND} command.
     * 
     * @param revisionLog the string containing the revision log; should never be <code>null</code>
     * @return the map of revision numbers (keys) and their commit date (value) as provided by the revision log; never
     *         <code>null</code>, but may be <i>empty</i>
     */
    private Map<String, String> getRevisionNumbers(String revisionLog) {        
        logger.log(ID, "Extracting revision numbers and commit dates from log", null, MessageType.DEBUG);
        /*
         * As the SVN command for listing all available revisions already includes the commit date, this extractor uses
         * a map of commits instead of a simple list. The entries of this map consist of the revision number, e.g.,
         * "r30", as key and the commit date as value, like "2007-03-14 13:03:51 +0100 (Mi, 14 Mrz 2007)". Note that the
         * last part of the date in brackets is system-language-dependent. In the example, German language is
         * configured.
         */
        Map<String, String> revisionMap = new HashMap<String, String>();
        if (!revisionLog.isEmpty()) {
            String[] revisionLogLines = revisionLog.split("\n");
            for (int i = 0; i < revisionLogLines.length; i++) {
                String revisionLogLine = revisionLogLines[i];
                if (!revisionLogLine.isEmpty() && revisionLogLine.startsWith("r")) {
                    revisionMap.put(revisionLogLine.substring(0, getIndexOfFirstWhitespace(revisionLogLine)),
                            getCommitDate(revisionLogLine));
                }
            }
        }
        return revisionMap;
    }
    
    /**
     * Returns the index of the first whitespace in the given text.
     * 
     * @param text the text in which the first whitespace shall be found
     * @return the index of the first whitespace in the given text or <code>-1</code> if no whitespace can be found
     */
    private int getIndexOfFirstWhitespace(String text) {
        int index = -1;
        int currentIndex = 0;
        while (index == -1 && currentIndex < text.length()) {
            if (Character.isWhitespace(text.charAt(currentIndex))) {
                index = currentIndex;
            }
            currentIndex++;
        }
        return index;
    }
    
    /**
     * Returns the commit date of a revision log line, like:<br><br>
     * 
     * <tt>r78 | cameronrich | 2007-03-14 13:03:51 +0100 (Mi, 14 Mrz 2007)</tt><br><br>
     * 
     * Hence, the return value is "2007-03-14 13:03:51 +0100 (Mi, 14 Mrz 2007)" in this example.
     * 
     * @param revisionLogLine the revision log line from which the commit date shall be extracted; note that the caller
     *        needs to take care of a correct log line, like the one above, as this method does not perform any further
     *        checks
     * @return the commit date as defined in the given revision log line
     */
    private String getCommitDate(String revisionLogLine) {
        return revisionLogLine.substring(getIndexOfLastPipe(revisionLogLine) + 1, revisionLogLine.length());
    }
    
    /**
     * Returns the index of the last pipe ("|") in the given text.
     * 
     * @param text the text in which the last pipe shall be found
     * @return the index of the last pipe in the given text or <code>-1</code> if no pipe can be found
     */
    private int getIndexOfLastPipe(String text) {
        int index = -1;
        int currentIndex = text.length() - 1;
        while (index == -1 && currentIndex < text.length()) {
            if (text.charAt(currentIndex) == '|') {
                index = currentIndex;
            }
            currentIndex--;
        }
        return index;
    }

    /**
     * Executes the given (SVN) command at the location of the given repository using
     * {@link ProcessUtilities#executeCommand(String, File)}. If the execution fails, the given number of allowed
     * attempts defines how often this method will retry executing the command.
     * 
     * @param command the command to execute at the location of the given repository, which is typically one of 
     *        {@link #SVN_LOG_COMMAND}, {@link #SVN_ALL_REVISIONS_COMMAND}, {@link #SVN_REVISION_LOG_COMMAND},
     *        or {@link #SVN_COMMIT_CHANGES_COMMAND}; should never be <code>null</code>
     * @param repository the (SVN) repository at which the given command will be executed; can be <code>null</code>
     *        if the command should be executed in the same directory as the entire infrastructure
     * @return a string containing the standard output data of the process, which executed the given command; may be
     *         <code>null</code> if executing the command failed or no standard output data was available
     */
    private String executeSvnCommand(String command, File repository) {
        String standardOutputString = null;
        ExecutionResult executionResult = null;
        int attemptCounter = 1;
        boolean tryAgain = true;
        do {
            executionResult = processUtilities.executeCommand(command, repository);
            if (!executionResult.executionSuccessful()) {
                logger.log(ID, "Attempt " + attemptCounter + " of " + maxSvnCommandAttempts 
                        + " to execute SVN command \"" + command + "\" failed", 
                        executionResult.getErrorOutputData(), MessageType.WARNING);
                if (attemptCounter == maxSvnCommandAttempts) {
                    logger.log(ID, "Aborting execution of SVN command \"" + command + "\"", null,
                            MessageType.WARNING);
                    tryAgain = false;
                    executionResult = null;
                }
            } else {
                // Execution successful; no further need to retry
                tryAgain = false;
                standardOutputString = executionResult.getStandardOutputData();
            }
            attemptCounter++;
        } while (tryAgain);
        return standardOutputString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean operatingSystemSupported(String operatingSystem) {
        // This extractor is OS-independent
        logger.log(ID, "Supported operating systems: all", null, MessageType.DEBUG);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean versionControlSystemSupported(String versionControlSystem) {
        String supportedVCS = "svn";
        logger.log(ID, "Supported version control system: " + supportedVCS, null, MessageType.DEBUG);
        return versionControlSystem.equalsIgnoreCase(supportedVCS);
    }
}
