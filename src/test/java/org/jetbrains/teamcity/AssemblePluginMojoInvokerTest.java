package org.jetbrains.teamcity;

import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.MavenCli;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.junit.Test;

import java.util.Arrays;

import static org.jetbrains.teamcity.AssemblePluginMojoTestCase.getTestDir;

public class AssemblePluginMojoInvokerTest {
    @Test
    public void testAnotherMultiModule() {
//        ClassWorld classWorld = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
//        CliRequest cliRequest = new CliRequest(new String[] { "package", "teamcity:build"}, classWorld);
        MavenCli cli = new MavenCli();
        System.setProperty("maven.multiModuleProjectDirectory", getTestDir("unit/multi-module-to-test").getAbsolutePath());
        getClass().getResource("AssemblePluginMojo.class");
        int result = cli.doMain(new String[]{"package", "teamcity:build"},
                getTestDir("unit/multi-module-to-test").getAbsolutePath(),
                null, null);
        System.out.println("some result");
//        int main = new MavenCli().doMain(cliRequest);

//        InvocationRequest request = new DefaultInvocationRequest();
//        request.setPomFile(getTestDir("unit/multi-module-to-test/pom.xml"));
//        request.setGoals(Arrays.asList("package", "teamcity:build"));
//
//        Invoker invoker = new DefaultInvoker();
//        InvocationResult result = invoker.execute(request);
//        if ( result.getExitCode() != 0 )
//        {
//            throw new IllegalStateException( "Build failed." );
//        }
    }
}
