package org.jetbrains.teamcity;

import org.apache.maven.plugin.testing.MojoRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static org.codehaus.plexus.PlexusTestCase.getTestFile;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AssemblePluginMojoTestCase {
    @Rule
    public MojoRule rule = new MojoRule();

//    protected void setUp() throws Exception {
//        super.setUp();
//    }
//
    @Test
    public void testSimple() {
        System.out.println(1);
    }

    @Test
    public void testMakeSimpleArtifact()
            throws Exception {
        File pom = getTestFile("src/test/resources/unit/project-to-test/pom.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
//        mojo = (MyPlugin) ;
        AssemblePluginMojo myMojo = (AssemblePluginMojo) rule.lookupMojo("build", pom);

        rule.executeMojo(getTestFile("src/test/resources/unit/project-to-test"), "build");
//        AssemblePluginMojo myMojo1 = (AssemblePluginMojo) rule.configureMojo(myMojo, "teamcity-maven-plugin", pom);
        assertNotNull(myMojo);
        myMojo.execute();
    }
}
