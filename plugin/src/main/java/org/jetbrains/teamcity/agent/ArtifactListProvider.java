package org.jetbrains.teamcity.agent;

import java.nio.file.Path;
import java.util.List;

public interface ArtifactListProvider {
    default boolean isApplicable() {
        return true;
    }
    List<Path> getIdeaArtifactList();
}
