package org.jetbrains.teamcity;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.List;

@Mojo(name = "build-agent", defaultPhase = LifecyclePhase.PACKAGE, aggregator = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class AgentPluginMojo extends BaseTeamCityMojo {
    @Parameter
    private String spec;
    @Parameter(defaultValue = "${project.artifactId}")
    private String pluginName;
    @Parameter(defaultValue = "org.jetbrains.teamcity,::zip")
    private List<String> exclusions;
    @Parameter
    private boolean tool; // if this is tool deployment


    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File projectBuildOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/teamcity")
    private File workDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().warn("TeamCity Agent Assemble start");

    }
}
