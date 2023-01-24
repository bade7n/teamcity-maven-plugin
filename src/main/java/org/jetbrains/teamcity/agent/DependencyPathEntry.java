package org.jetbrains.teamcity.agent;

import lombok.Data;
import org.apache.maven.artifact.Artifact;

import java.nio.file.Path;
import java.util.List;

@Data
public class DependencyPathEntry implements PathEntry {
    private final Artifact artifact;
    private final boolean isReactorProject;
    private final Path path;
    private final Path resolved;

    @Override
    public List<Path> resolve() {
        if (isReactorProject)
            return List.of(path.getFileName());
        else
            return List.of(path);
    }

    @Override
    public PathEntry cloneWithRoot(Path base) {
        return new DependencyPathEntry(artifact, isReactorProject, AssemblyContext.baseOn(path, base), resolved);
    }
}
