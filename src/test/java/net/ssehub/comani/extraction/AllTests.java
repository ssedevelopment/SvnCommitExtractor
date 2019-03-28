package net.ssehub.comani.extraction;

import java.io.File;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import net.ssehub.comani.extraction.svn.InteractiveModeArtifactContentTests;


/**
 * Runs all unit tests for net.ssehub.comani.extraction.
 * 
 * @author Marcel
 */
@RunWith(Suite.class)
@SuiteClasses({ 
        InteractiveModeArtifactContentTests.class
    })
public class AllTests {

    /**
     * Defines the resource directory for all test cases.
     */
    public static final File TESTDATA = new File("src/test/resources");
    
    /**
     * Defines the line break that should be used in test cases. Here LF is used. 
     */
    public static final String LINE_BREAK = "\n";
}
