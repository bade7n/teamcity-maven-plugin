package org.jetbrains.teamcity;

import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

import java.io.File;

public class ModuleBStub extends MavenProjectStub {
    public ModuleBStub() {
        readModel(new File(this.getClass().getClassLoader().getResource("unit/multi-module-to-test/moduleB/pom.xml").getFile()));
    }
}
