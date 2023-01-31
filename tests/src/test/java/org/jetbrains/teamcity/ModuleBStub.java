package org.jetbrains.teamcity;

import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

import java.io.File;

public class ModuleBStub extends BaseMavenModuleStub {
    public ModuleBStub() {
        super("jb.int", "moduleB", "1.0-SNAPSHOT");
        addDependency("commons-io:commons-io:2.2:::provided");
    }
}
