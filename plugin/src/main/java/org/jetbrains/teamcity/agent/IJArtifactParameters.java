package org.jetbrains.teamcity.agent;

import lombok.Data;

import java.nio.file.Path;

@Data
public class IJArtifactParameters {
    private Path artifactPath;
    private Path projectRoot;
}
