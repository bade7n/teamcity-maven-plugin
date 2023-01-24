package org.jetbrains.teamcity.agent;

import lombok.Data;

import java.nio.file.Path;
import java.util.List;

/**
 * dependency or artifact or file
 */
public interface PathEntry {
    List<Path> resolve();

    default boolean isReactorProject() {
        return false;
    }

    PathEntry cloneWithRoot(Path base);
}
