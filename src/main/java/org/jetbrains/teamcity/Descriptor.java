package org.jetbrains.teamcity;

import lombok.*;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Descriptor {
    @Parameter(defaultValue = "false")
    private boolean doNotGenerate;
    @Parameter(defaultValue = "false")
    private boolean failOnMissing;
    @Parameter(defaultValue = "false")
    private boolean allowRuntimeReload;
    @Parameter(defaultValue = "false")
    private boolean nodeResponsibilitiesAware;
    @Parameter(defaultValue = "false")
    private boolean useSeparateClassloader;
    @Parameter
    private List<String> pluginDependencies = new ArrayList<>();
    @Parameter
    private List<String> toolDependencies = new ArrayList<>();
    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/teamcity-agent-plugin.xml")
    private File path;

    public void adjustDefaults(File projectBuildOutputDirectory, String fileName) {
        if (path == null)
            path = projectBuildOutputDirectory.toPath().resolve("META-INF").resolve(fileName).toFile();
    }
}
