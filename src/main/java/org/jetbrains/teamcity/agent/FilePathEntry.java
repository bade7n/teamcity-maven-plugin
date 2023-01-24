package org.jetbrains.teamcity.agent;

import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class FilePathEntry implements PathEntry {
    private final Path file;

    @Override
    public List<Path> resolve() {
        return List.of(file);
    }

    @Override
    public PathEntry cloneWithRoot(Path base) {
        return new FilePathEntry(AssemblyContext.baseOn(file, base));
    }
}
