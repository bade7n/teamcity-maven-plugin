package org.jetbrains.teamcity;

import org.apache.maven.cli.MavenCli;
import org.junit.Test;

import static org.jetbrains.teamcity.AssemblePluginMojoTestCase.getTestDir;

public class AssemblePluginMojoInvokerTest {
    @Test
    public void testAnotherMultiModule() {
        MavenCli cli = new MavenCli();
        System.setProperty("maven.multiModuleProjectDirectory", getTestDir("unit/multi-module-to-test").getAbsolutePath());
        int result = cli.doMain(new String[]{"package", "teamcity:build"},
                getTestDir("unit/multi-module-to-test").getAbsolutePath(),
                null, null);
        System.out.println("testAnotherMultiModule result=" + result);
    }
}
