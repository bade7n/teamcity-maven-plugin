package org.jetbrains.teamcity.agent;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
@AllArgsConstructor
public class ArtifactPathEntry implements PathEntry {
    private String name;
    private String artifactName;

    @Override
    public List<Path> resolve() {
        return null;
    }

    @Override
    public PathEntry cloneWithRoot(Path base) {
        return this;
    }
}
