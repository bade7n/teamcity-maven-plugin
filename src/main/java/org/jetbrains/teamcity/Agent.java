package org.jetbrains.teamcity;

import lombok.*;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    @Parameter
    private String spec;
    @Parameter(defaultValue = "${project.artifactId}")
    private String pluginName;
    @Parameter(defaultValue = "org.jetbrains.teamcity,::zip")
    private List<String> exclusions;
    @Parameter
    private boolean tool; // if this is tool deployment
    @Parameter(defaultValue = "true")
    private boolean failOnMissingDependencies = true;
    private String ignoreExtraFilesIn;

    private Descriptor descriptor = new Descriptor();

    public boolean isNeedToBuild() {
        return spec != null && !spec.isBlank();
    }

    public void setDefaultValues(MavenProject project, File projectBuildOutputDirectory) {
        if (pluginName == null)
            pluginName = project.getArtifactId();
        if (exclusions == null)
            exclusions = List.of("org.jetbrains.teamcity", "::zip");
        if (descriptor.getPath() == null) {
            descriptor.setPath(projectBuildOutputDirectory.toPath().resolve("META-INF").resolve("teamcity-agent-plugin.xml").toFile());
        }
    }
}
