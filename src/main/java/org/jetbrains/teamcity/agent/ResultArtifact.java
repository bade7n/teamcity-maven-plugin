package org.jetbrains.teamcity.agent;

import lombok.Data;

import java.nio.file.Path;

@Data
public class ResultArtifact {
    private final String type;
    private final String classifier;
    private final Path file;
}
