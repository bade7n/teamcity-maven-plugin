package org.jetbrains.teamcity.agent;

import lombok.Data;

import java.io.File;

@Data
public class ResultArtifact {
    private final String type;
    private final String classifier;
    private final File file;
}
