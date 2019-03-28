package net.ssehub.comani.extraction.svn;


import java.io.File;
import java.util.List;
import java.util.Properties;

import net.ssehub.comani.data.Commit;
import net.ssehub.comani.data.CommitQueue;
import net.ssehub.comani.data.CommitQueue.QueueState;
import net.ssehub.comani.extraction.AllTests;
import net.ssehub.comani.extraction.ExtractionSetupException;
import net.ssehub.comani.utility.FileUtilities;

/**
 * This class provides the common attributes and preparations for interactive mode based extractor tests.
 * 
 * @author spark
 */
public abstract class AbstractInteractiveModeTests {
    
    /**
     * Commit object which was created by {@link GitCommitExtractor} during the extraction process. 
     * 
     * @see #AbstractInteractiveModeTests(File)
     */
    protected Commit extractedCommit; 
    
    /**
     * The return value of the extraction process indicating whether the process terminated successfully
     * (<code>true</code>) or not (<code>false</code>).
     * Will set in constructor. 
     * @see #AbstractInteractiveModeTests
     */
    protected boolean extractionTerminatedSuccessful;
    
    /**
     * Instantiates a {@link GitCommitExtractor}, performs the extraction of a specific commit through 
     * {@link GitCommitExtractor#extract(String)} and sets the value of 
     * {@link #extractionTerminatedSuccessful} as well as {@link #extractedCommits} for their use in the tests 
     * of the extending classes. 

     * @param commitFile path to the file which stores a commit
     * @throws ExtractionSetupException if instantiating the commit extractor failed
     */
    public AbstractInteractiveModeTests(File commitFile) throws ExtractionSetupException {
        List<String> fileContent = null;
        fileContent = loadContent(commitFile);
        
        Properties extractionProperties = new Properties();
        extractionProperties.setProperty("core.version_control_system", "svn");
        
        CommitQueue commitQueue = new CommitQueue(10);
        commitQueue.setState(QueueState.OPEN);
        
        SvnCommitExtractor commitExtractor = new SvnCommitExtractor(extractionProperties, commitQueue);
        extractionTerminatedSuccessful = commitExtractor.extract(buildString(fileContent));
        
        commitQueue.setState(QueueState.CLOSED); // Actual closing after getting all commits below
        while (commitQueue.isOpen()) {
            Commit commit = commitQueue.getCommit();
            if (commit != null) {
                extractedCommit = (commit);
            }
        }
    }
    
    /**
     * Reads the content of a given file.
     * 
     * @param fileToRead the file which should be read
     * @return the content of the file as string list
     * @see FileUtilities
     */
    protected List<String> loadContent(File fileToRead) {
        FileUtilities fileUtils = FileUtilities.getInstance(); 
        return fileUtils.readFile(fileToRead);
    }
    
    /**
     * Takes a list full of strings and build a single big string. 
     * 
     * @param content which will converted to a big string
     * @return a big string containing everything from given list, separated by {@link AllTests#LINE_BREAK}. 
     */
    protected String buildString(List<String> content) {
        StringBuilder sb = new StringBuilder();
        content.forEach((singleLine) -> {
            sb.append(singleLine).append(AllTests.LINE_BREAK);
        });
        int last = sb.lastIndexOf(AllTests.LINE_BREAK);
        sb.delete(last, sb.length()); // remove last line
        return sb.toString();
    }
}
