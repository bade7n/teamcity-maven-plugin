package org.jetbrains.teamcity;

import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

import java.io.File;

public class ModuleAStub extends MavenProjectStub {
    public ModuleAStub() {
        readModel(new File(this.getClass().getClassLoader().getResource("unit/multi-module-to-test/moduleA/pom.xml").getFile()));
        this.getArtifact();
    }
}
