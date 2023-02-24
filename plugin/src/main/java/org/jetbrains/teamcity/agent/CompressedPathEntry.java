package org.jetbrains.teamcity.agent;

import lombok.Data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
public class CompressedPathEntry implements PathEntry {
    private final String name;
    private final String prefixInArchive;
    private final List<Path> pathsIncluded = new ArrayList<>();

    @Override
    public List<Path> resolve() {
        return pathsIncluded;
    }

    @Override
    public PathEntry cloneWithRoot(Path base) {
        CompressedPathEntry compressedPathEntry = new CompressedPathEntry(name, prefixInArchive);
        compressedPathEntry.getPathsIncluded().addAll(pathsIncluded);
        return compressedPathEntry;
    }
}
