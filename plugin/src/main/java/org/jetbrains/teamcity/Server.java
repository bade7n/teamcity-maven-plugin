package org.jetbrains.teamcity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
public class Server {
    @Parameter
    private String spec;
    @Parameter(defaultValue = "${project.artifactId}")
    private String pluginName;
    @Parameter
    private Descriptor descriptor = new Descriptor();
    @Parameter(defaultValue = "org.jetbrains.teamcity")
    private List<String> exclusions;
    @Parameter(property = "buildServerResources")
    private List<String> buildServerResources;
    @Parameter
    private String commonSpec;
    @Parameter(defaultValue = "org.jetbrains.teamcity")
    private List<String> commonExclusions;
    @Parameter(defaultValue = "${project.build.outputDirectory}/kotlin-dsl")
    private File kotlinDslDescriptorsPath;
    @Parameter
    private List<String> ignoreExtraFilesIn;
    @Parameter
    private List<String> toolDependencies;
    @Parameter(defaultValue = "true", property = "failOnMissingDependencies")
    private boolean failOnMissingDependencies = true;
    @Parameter(defaultValue = "true", property = "excludeAgent")
    private boolean excludeAgent = true;
    @Parameter(property = "extras")
    private List<SourceDest> extras;


    public void setDefaultValues(String spec, MavenProject project, File projectBuildOutputDirectory) {
        if (Objects.isNull(this.spec))
            this.spec = spec;
        if (pluginName == null)
            pluginName = project.getArtifactId();
        if (exclusions == null)
            exclusions = Jdk8Compat.of("org.jetbrains.teamcity");
        if (commonExclusions == null)
            commonExclusions = Jdk8Compat.of("org.jetbrains.teamcity");
        if (buildServerResources == null)
            buildServerResources = new ArrayList<>();
        if (kotlinDslDescriptorsPath == null)
            kotlinDslDescriptorsPath = projectBuildOutputDirectory.toPath().resolve("kotlin-dsl").toFile();
        if (descriptor.getPath() == null) {
            descriptor.setPath(projectBuildOutputDirectory.toPath().resolve("META-INF").resolve("teamcity-plugin.xml").toFile());
        }
    }

    public boolean isNeedToBuild() {
        return ntb(spec);
    }

    private boolean ntb(String spec) {
        return spec != null && !Jdk8Compat.isBlank(spec);
    }

    public boolean isNeedToBuildCommon() {
        return ntb(commonSpec);
    }

    public boolean hasExtras() {
        return extras != null && !extras.isEmpty();
    }
}
