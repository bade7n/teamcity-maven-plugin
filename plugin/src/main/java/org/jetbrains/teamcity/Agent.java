package org.jetbrains.teamcity;

import lombok.*;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;
import java.util.Objects;

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
    @Parameter
    private String ignoreExtraFilesIn;

    private Descriptor descriptor = new Descriptor();

    private String artifactId = null;

    public boolean isNeedToBuild() {
        return spec != null && !spec.isBlank();
    }

    public void setDefaultValues(String spec, MavenProject project, File projectBuildOutputDirectory) {
        if (Objects.isNull(this.spec))
            this.spec = spec;
        if (pluginName == null) {
            pluginName = project.getArtifactId();
            artifactId = project.getArtifactId();
        }
        if (exclusions == null)
            exclusions = List.of("org.jetbrains.teamcity", "::zip");
        descriptor.adjustDefaults(projectBuildOutputDirectory, "teamcity-agent-plugin.xml");
    }

    public boolean isCustomPluginName() {
        return !Objects.equals(artifactId, pluginName);
    }
}
