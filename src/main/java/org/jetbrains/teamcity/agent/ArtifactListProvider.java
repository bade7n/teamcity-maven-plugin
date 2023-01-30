package org.jetbrains.teamcity.agent;

import java.nio.file.Path;
import java.util.List;

public interface ArtifactListProvider {
    List<Path> getIdeaArtifactList();
}
