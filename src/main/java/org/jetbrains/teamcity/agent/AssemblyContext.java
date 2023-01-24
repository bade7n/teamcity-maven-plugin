package org.jetbrains.teamcity.agent;

import lombok.Data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Data
public class AssemblyContext {
    private Path root;
    private Path targetName;
    private Path descriptorPath;
    private List<PathSet> paths = new ArrayList<>();

    public void addToLastPathSet(PathEntry entry) {
        paths.get(paths.size()-1).with(entry);

    }

    public AssemblyContext cloneWithRoot(Path base) {
        AssemblyContext ac = new AssemblyContext();
        ac.setRoot(baseOn(root, base));
        ac.setTargetName(baseOn(targetName, base));
        for (PathSet ps: paths) {
            ac.getPaths().add(ps.cloneWithRoot(base));
        }
        return ac;
    }

    public static Path baseOn(Path dir, Path base) {
        if (dir != null && dir.isAbsolute())
            return base.relativize(dir);
        return dir;
    }
}
