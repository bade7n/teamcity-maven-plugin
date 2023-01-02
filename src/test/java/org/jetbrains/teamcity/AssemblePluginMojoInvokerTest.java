package org.jetbrains.teamcity;

import org.apache.maven.cli.CliRequest;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.shared.invoker.*;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.junit.Test;

import java.util.Arrays;

import static org.jetbrains.teamcity.AssemblePluginMojoTestCase.getTestDir;

public class AssemblePluginMojoInvokerTest {
    @Test
    public void testAnotherMultiModule() throws MavenInvocationException {
        ClassWorld classWorld = new ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader());
        CliRequest cliRequest = new CliRequest(new String[] { "package", "teamcity:build"}, classWorld);
        int main = new MavenCli().doMain(cliRequest);

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
