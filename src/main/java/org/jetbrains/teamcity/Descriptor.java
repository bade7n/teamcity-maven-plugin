package org.jetbrains.teamcity;

import lombok.Data;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.List;

@Data
public class Descriptor {
    @Parameter(defaultValue = "true")
    private boolean generate;
    private boolean failOnMissing;
    private boolean allowRuntimeReload;
    private boolean nodeResponsibilitiesAware;
    private boolean useSeparateClassloader;
    private List<String> pluginDependencies;
    private List<String> toolDependencies;
    private File path;
}
