package org.jetbrains.teamcity;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AssemblyMojoTest {
    @Test
    public void testLookupPluginName() {
        testString("<!-- @@AGENT=123@@ -->", "AGENT", "123");
        testString("<!-- @@AGENT_1-2=123_1-2@@ -->", "AGENT_1-2", "123_1-2");
    }

    private void testString(String line, String key, String value) {
        assertThat(AssemblePluginMojo.lookupPluginName(line))
                .extracting("key","value")
                .contains(key, value);
    }
}
