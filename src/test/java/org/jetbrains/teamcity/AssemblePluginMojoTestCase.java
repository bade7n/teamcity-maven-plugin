package org.jetbrains.teamcity;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.MojoRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static org.codehaus.plexus.PlexusTestCase.getTestFile;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AssemblePluginMojoTestCase extends AbstractMojoTestCase {
//    @Rule
//    public MojoRule rule = new MojoRule();

    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testSimple() {
        System.out.println(1);
    }

    @Test
    public void testMakeSimpleArtifact()
            throws Exception {
        File pom = getTestFile("src/test/resources/unit/basic/pom.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());

        AssemblePluginMojo myMojo = (AssemblePluginMojo) lookupMojo("assemble", pom);
        assertNotNull(myMojo);
        myMojo.execute();
    }
}
