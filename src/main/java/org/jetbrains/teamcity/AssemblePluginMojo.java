package org.jetbrains.teamcity;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
//import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;

import java.io.File;
import java.nio.file.Path;

import static java.nio.file.Files.exists;

@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.TEST, requiresDependencyCollection = ResolutionScope.TEST)
public class AssemblePluginMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;
    /**
     * Input directory which contains the files that will zipped. Defaults to the
     * build output directory.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "inputDir")
    private File inputDirectory;

    /**
     * Directory which will contain the generated zip file. Defaults to the Maven
     * build directory.
     */
    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir")
    private File outputDirectory;

    /**
     * Final name of the zip file. Defaults to the build final name attribute.
     */
    @Parameter(defaultValue = "${project.build.finalName}", property = "outputName")
    private String zipName;

    @Parameter(defaultValue = "org.jetbrains.teamcity", property = "excludeGroupId")
    private String excludeGroupId;

    @Parameter(defaultValue = "*1", property = "agent")
    private String agent;

    @Parameter(defaultValue = "*2", property = "server")
    private String server;

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/teamcity-plugin.xml", property = "pluginDescriptorPath")
    private String pluginDescriptorPath;
//    @Component(
//            hint = "default"
//    )
//    private DependencyCollectorBuilder dependencyCollectorBuilder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        verifyAndPrepareStructure();
        project.getAttachedArtifacts();
        System.out.println("1");
    }

    private void verifyAndPrepareStructure() throws MojoExecutionException {
        new File(String.valueOf(outputDirectory)).mkdirs();
        if (!exists(Path.of(pluginDescriptorPath)))
            throw new MojoExecutionException(String.format("`pluginDescriptorPath` must point to teamcity plugin descriptor (%s).", pluginDescriptorPath));

    }
}
