package org.jetbrains.teamcity;

import org.eclipse.aether.artifact.ArtifactProperties;

public class ModuleAStub extends BaseMavenModuleStub {
    public ModuleAStub() {
        super("jb.int", "moduleA", "1.0-SNAPSHOT");
        addDependency("jb.int:moduleB:1.0-SNAPSHOT");
    }



}
