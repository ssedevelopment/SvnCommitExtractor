package net.ssehub.comani.extraction.svn;

import java.io.File;


/**
 * Class is storing commit meta data for test cases. 
 * Designed for easier parameter tests setup. 
 * Used for interactive mode tests where a commit file is stored on the disk.
 * 
 * @author Marcel
 */
public class CommitTestData {
    
    /**
     * Read only path to a text file which stores a commit.
     */
    private File commitFile;
    
    /**
     * Read only commit id which can be used in test cases.
     */
    private String commitId;
    
    /**
     * Sets the attributes of this class.
     * 
     * @param commitFile {@link File} denoting the location of the text file which contains a valid commit which should
     * be extracted through an interactive mode test. 
     * @param commitId Expected commit id
     */
    public CommitTestData(File commitFile, String commitId) {
        this.commitFile = commitFile;
        this.commitId = commitId;
    }
    
    /**
     * Returns the commit file.
     * @return the commitFile
     */
    public File getCommitFile() {
        return commitFile;
    }

    /**
     * Returns the commit id.
     * @return the commitId
     */
    public String getCommitId() {
        return commitId;
    }
    
}
