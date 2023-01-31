package org.jetbrains.teamcity.agent;

import lombok.Data;
import lombok.Getter;
import org.apache.maven.artifact.Artifact;

import java.nio.file.Path;
import java.util.List;

@Data
public class DependencyPathEntry implements PathEntry {
    private final Artifact artifact;
    private final boolean isReactorProject;

    private final String name;
    private final Path resolved;

    @Override
    public List<Path> resolve() {
        return List.of(resolved);
    }

    @Override
    public PathEntry cloneWithRoot(Path base) {
        return new DependencyPathEntry(artifact, isReactorProject, name, resolved);
    }
}
