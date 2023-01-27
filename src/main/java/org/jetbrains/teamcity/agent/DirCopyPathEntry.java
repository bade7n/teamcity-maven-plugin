package org.jetbrains.teamcity.agent;

import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class DirCopyPathEntry implements PathEntry {
    private final String name;
    private final Path resolved;

    @Override
    public List<Path> resolve() {
        return List.of(resolved);
    }

    @Override
    public PathEntry cloneWithRoot(Path base) {
        return new DirCopyPathEntry(name, resolved);
    }
}
