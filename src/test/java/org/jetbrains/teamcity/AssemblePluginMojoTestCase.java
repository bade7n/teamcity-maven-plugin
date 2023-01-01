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
    public void testMakeSimpleArtifact() throws Exception {
        rule.executeMojo(getTestDir("unit/project-to-test"), "build");
    }

    private File getTestDir(String pathToBase) {
        return new File(getClass().getClassLoader().getResource(pathToBase).getFile());
    }
}
