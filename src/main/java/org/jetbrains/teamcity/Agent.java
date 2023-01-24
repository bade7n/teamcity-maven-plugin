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
    private String spec;
    @Parameter(defaultValue = "${project.artifactId}")
    private String pluginName;

    private Descriptor descriptor = new Descriptor();
    @Parameter(defaultValue = "org.jetbrains.teamcity,::zip")
    private List<String> exclusions;

    private boolean tool; // if this is tool deployment

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
