package org.jetbrains.teamcity.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Getter
public class ArtifactNode {
    private String path;
    private ArtifactNodeType type;
    private Object info;

    private final List<ArtifactNode> childs = new ArrayList<>();

    public enum ArtifactNodeType {
        DIR, DEPENDENCY, FILE, DIR_COPY, ARTIFACT;
    }
}
