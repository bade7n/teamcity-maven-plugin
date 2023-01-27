package org.jetbrains.teamcity.agent;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
public class PathSet {
    private final Path dir;

    private final List<PathEntry> pathEntryList = new ArrayList<>();

    public PathSet with(PathEntry entry) {
        pathEntryList.add(entry);
        return this;
    }

    public PathSet cloneWithRoot(Path base) {
        PathSet ps = new PathSet(AssemblyContext.baseOn(dir, base));
        for (PathEntry pe: pathEntryList) {
           ps.getPathEntryList().add(pe.cloneWithRoot(base));
        }
        return ps;
    }
}
