package org.jetbrains.teamcity;

import org.jetbrains.teamcity.data.ResolvedArtifact;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class AssemblyMojoTest {
    @Test
    public void testLookupPluginName() {
        testString("<!-- @@AGENT=123@@ -->", "AGENT", "123");
        testString("<!-- @@AGENT_1-2=123_1-2@@ -->", "AGENT_1-2", "123_1-2");
    }

    private void testString(String line, String key, String value) {
        assertThat(ResolvedArtifact.lookupPluginName(line).collect(Collectors.toList()).get(0))
                .extracting("key","value")
                .contains(key, value);
    }
}
