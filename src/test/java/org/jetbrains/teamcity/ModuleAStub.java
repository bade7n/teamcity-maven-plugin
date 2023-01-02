package org.jetbrains.teamcity;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.testing.ArtifactStubFactory;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleAStub extends BaseMavenModuleStub {
    public ModuleAStub() {
        super("jb.int", "moduleA", "1.0-SNAPSHOT");
        addDependency("jb.int:moduleB:1.0-SNAPSHOT");
    }



}
