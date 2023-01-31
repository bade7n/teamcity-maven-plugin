package org.jetbrains.teamcity;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.junit.Test;

import java.util.List;
import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleWarTestCase extends BasePluginTestCase {
    @Test
    public void testServerArtifact() throws Exception {
        MavenSession session = initMavenSession("unit/module-war", "module-agent");
        MojoExecution execution = rule.newMojoExecution("build");
        AssemblePluginMojo mojo = (AssemblePluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.setFailOnMissingDependencies(false);
        mojo.execute();
        StringJoiner sb = new StringJoiner("\n");
        appendTestResult(sb, mojo.getServerPluginWorkflow());
        assertThat(
                """
                SERVER:
                agent/
                agent/module-agent.zip
                server/
                teamcity-plugin.xml""").isEqualTo(sb.toString());

    }
        @Test
    public void testAgentArtifact() throws Exception {
        MavenSession session = initMavenSession("unit/module-war/module-agent");
        MojoExecution execution = rule.newMojoExecution("build");
        AssemblePluginMojo mojo = (AssemblePluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.setFailOnMissingDependencies(false);
        mojo.execute();
        StringJoiner sb = new StringJoiner("\n");
        appendTestResult(sb, mojo.getAgentPluginWorkflow());
        assertThat("""
                AGENT:
                lib
                lib/module-agent-1.1.jar
                teamcity-plugin.xml""").isEqualTo(sb.toString());

        filesAreEqual(mojo.getAgentPluginWorkflow().getPluginDescriptorPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <teamcity-agent-plugin xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                       xsi:noNamespaceSchemaLocation="urn:schemas-jetbrains-com:teamcity-agent-plugin-v1-xml">
                                
                        <plugin-deployment use-separate-classloader="false"/>
                </teamcity-agent-plugin>""");
        Descriptor descriptor = mojo.getAgent().getDescriptor();
        descriptor.setUseSeparateClassloader(true);
        descriptor.setToolDependencies(List.of("plugin1", "plugin2"));
        descriptor.setPluginDependencies(List.of("plugin1", "plugin2"));
        mojo.execute();
        filesAreEqual(mojo.getAgentPluginWorkflow().getPluginDescriptorPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <teamcity-agent-plugin xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                       xsi:noNamespaceSchemaLocation="urn:schemas-jetbrains-com:teamcity-agent-plugin-v1-xml">
                
                        <plugin-deployment use-separate-classloader="true"/>
                        <dependencies>
                                <plugin name="plugin1"/>
                                <plugin name="plugin2"/>
                                <tool name="plugin1"/>
                                <tool name="plugin2"/>
                        </dependencies>
                </teamcity-agent-plugin>""");


        this.assertIdeaArtifacts(mojo.getAgentPluginWorkflow(),"""
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::module-agent::4IDEA">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent-unpacked</output-path>
                        <root id="root">
                            <element id="directory" name="module-agent">
                                <element artifact-name="TC::AGENT::module-agent::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """, """
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::module-agent">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent</output-path>
                        <root id="root">
                            <element id="archive" name="module-agent.zip">
                                <element artifact-name="TC::AGENT::module-agent::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """, """
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::module-agent::EXPLODED">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent-unpacked/module-agent</output-path>
                        <root id="root">
                            <element id="directory" name="lib">
                                <element id="archive" name="module-agent-1.1.jar">
                                    <element id="module-output" name="module-agent"/>
                                </element>
                            </element>
                            <element id="file-copy" output-file-name="teamcity-plugin.xml" path="$PROJECT_DIR$/target/teamcity/teamcity-agent-plugin-generated.xml"/>
                        </root>
                    </artifact>
                </component>
                """);
    }

}
