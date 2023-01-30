package org.jetbrains.teamcity;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenWorkspaceReader;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.*;
import org.jetbrains.teamcity.agent.AgentPluginWorkflow;
import org.jetbrains.teamcity.agent.ArtifactListProvider;
import org.jetbrains.teamcity.agent.ResultArtifact;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.*;

public class AssemblePluginMojoTestCase {
    @Rule
    public MojoRule rule = new MojoRule();
    private SoftAssertions sa = new SoftAssertions();

    @After
    public void after() {
        sa.assertAll();
    }

    @Test
    public void testMakeAgentArtifact() throws Exception {
        MavenSession session = initMavenSession("agent/simple");
        MojoExecution execution = rule.newMojoExecution("build-agent");
        AgentPluginMojo mojo = (AgentPluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.execute();
        StringJoiner sb = new StringJoiner("\n");
        SoftAssertions sa = new SoftAssertions();
        appendTestResult(sb, mojo.getAgentPluginWorkflow());
        Assert.assertEquals("AGENT:\n" +
                "lib\n" +
                "lib/commons-beanutils-core-1.8.3.jar\n" +
                "lib/commons-logging-1.1.1.jar\n" +
                "teamcity-plugin.xml", sb.toString());
        filesAreEqual(sa, mojo.getAgentPluginWorkflow().getPluginDescriptorPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <teamcity-agent-plugin xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                       xsi:noNamespaceSchemaLocation="urn:schemas-jetbrains-com:teamcity-agent-plugin-v1-xml">
                    <!-- @@AGENT_PLUGIN_NAME=simple2@@ -->
                        
                        <plugin-deployment use-separate-classloader="false"/>
                        <dependencies>
                                <plugin name="java-dowser"/>
                                <tool name="ant"/>
                        </dependencies>
                </teamcity-agent-plugin>
                """);
        assertThat( mojo.getAgentPluginWorkflow().getIdeaArtifactList()).hasSize(2);
        filesAreEqual(sa, mojo.getAgentPluginWorkflow().getIdeaArtifactList().get(0), """
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::simple2">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent</output-path>
                        <root id="root">
                            <element id="archive" name="simple2.zip">
                                <element artifact-name="TC::AGENT::simple2::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """);
        filesAreEqual(sa, mojo.getAgentPluginWorkflow().getIdeaArtifactList().get(1), """
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::simple2::EXPLODED">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent-unpacked</output-path>
                        <root id="root">
                            <element id="directory" name="simple2">
                                <element id="directory" name="lib">
                                    <element id="library" level="project" name="Maven: commons-beanutils:commons-beanutils-core:1.8.3"/>
                                    <element id="library" level="project" name="Maven: commons-logging:commons-logging:1.1.1"/>
                                </element>
                                <element id="file-copy" output-file-name="teamcity-plugin.xml" path="$PROJECT_DIR$/target/teamcity/teamcity-agent-plugin-generated.xml"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """);
        sa.assertAll();
    }

    private void filesAreEqual(SoftAssertions sa, Path path, String expected) throws IOException {
        String actual = Files.readString(path);
        assertThat(actual).isEqualToIgnoringNewLines(expected);
    }


    @Test
    public void testMakeSimpleArtifact() throws Exception {
        MavenSession session = initMavenSession("unit/project-to-test");
        MojoExecution execution = rule.newMojoExecution("build");
        AssemblePluginMojo mojo = (AssemblePluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.setFailOnMissingDependencies(false);
        mojo.execute();
        String sb = getTestResult(mojo);
        SoftAssertions sa = new SoftAssertions();
        sa.assertThat(sb).asString().isEqualToIgnoringNewLines("AGENT:\n" +
                "lib\n" +
                "lib/commons-beanutils-core-1.8.3.jar\n" +
                "lib/commons-logging-1.1.1.jar\n" +
                "teamcity-plugin.xml\n" +
                "SERVER:\n" +
                "agent/\n" +
                "agent/project-to-test.zip\n" +
                "bundles/\n" +
                "bundles/1\n" +
                "server/\n" +
                "server/commons-beanutils-core-1.8.3.jar\n" +
                "server/commons-codec-1.15.jar\n" +
                "server/commons-logging-1.1.1.jar\n" +
                "server/project-to-test-1.1-SNAPSHOT.jar\n" +
                "teamcity-plugin.xml");
        filesAreEqual(sa, mojo.getAgentPluginWorkflow().getPluginDescriptorPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <teamcity-agent-plugin xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                       xsi:noNamespaceSchemaLocation="urn:schemas-jetbrains-com:teamcity-agent-plugin-v1-xml">
                        
                        <plugin-deployment use-separate-classloader="false"/>
                        <dependencies>
                                <plugin name="java-dowser"/>
                                <tool name="ant"/>
                        </dependencies>
                </teamcity-agent-plugin>
                """);
        filesAreEqual(sa, mojo.getServerPluginWorkflow().getPluginDescriptorPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <teamcity-plugin xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:noNamespaceSchemaLocation="urn:schemas-jetbrains-com:teamcity-plugin-v1-xml">
                    <info>
                        <name>project-to-test</name>
                        <display-name>Test</display-name>
                        <version>1.1-SNAPSHOT</version>
                    </info>
                    <deployment use-separate-classloader="false" allow-runtime-reload="false" node-responsibilities-aware="false"/>
                </teamcity-plugin>
                """);
        assertIdeaArtifacts(sa, mojo.getAgentPluginWorkflow(), """
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::project-to-test">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent</output-path>
                        <root id="root">
                            <element id="archive" name="project-to-test.zip">
                                <element artifact-name="TC::AGENT::project-to-test::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """, """
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::project-to-test::EXPLODED">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent-unpacked</output-path>
                        <root id="root">
                            <element id="directory" name="project-to-test">
                                <element id="directory" name="lib">
                                    <element id="library" level="project" name="Maven: commons-beanutils:commons-beanutils-core:1.8.3"/>
                                    <element id="library" level="project" name="Maven: commons-logging:commons-logging:1.1.1"/>
                                </element>
                                <element id="file-copy" output-file-name="teamcity-plugin.xml" path="$PROJECT_DIR$/target/teamcity/teamcity-agent-plugin-generated.xml"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """);
        assertIdeaArtifacts(sa, mojo.getServerPluginWorkflow(), """
                <component name="ArtifactManager">
                    <artifact name="TC::SERVER::project-to-test::EXPLODED">
                        <output-path>$PROJECT_DIR$/target/teamcity/plugin/project-to-test</output-path>
                        <root id="root">
                            <element id="file-copy" output-file-name="teamcity-plugin.xml" path="$PROJECT_DIR$/target/teamcity/teamcity-plugin-generated.xml"/>
                            <element id="directory" name="server">
                                <element id="archive" name="project-to-test-1.1-SNAPSHOT.jar">
                                    <element id="module-output" name="project-to-test"/>
                                </element>
                                <element id="library" level="project" name="Maven: commons-beanutils:commons-beanutils-core:1.8.3"/>
                                <element id="library" level="project" name="Maven: commons-logging:commons-logging:1.1.1"/>
                                <element id="library" level="project" name="Maven: commons-codec:commons-codec:1.15"/>
                            </element>
                            <element id="directory" name="agent">
                                <element id="archive" name="project-to-test.zip">
                                    <element artifact-name="TC::AGENT::project-to-test" id="artifact"/>
                                </element>
                            </element>
                        </root>
                    </artifact>
                </component>
                """, """
                <component name="ArtifactManager">
                    <artifact name="TC::SERVER::project-to-test::4IDEA">
                        <output-path>$PROJECT_DIR$/target/teamcity/plugin</output-path>
                        <root id="root">
                            <element id="directory" name="project-to-test">
                                <element artifact-name="TC::SERVER::project-to-test::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """, """
                <component name="ArtifactManager">
                    <artifact name="TC::SERVER::project-to-test">
                        <output-path>$PROJECT_DIR$/target/teamcity/dist</output-path>
                        <root id="root">
                            <element id="archive" name="project-to-test.zip">
                                <element artifact-name="TC::SERVER::project-to-test::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """);
    }

    private void assertIdeaArtifacts(SoftAssertions sa, ArtifactListProvider apl, String... s) throws IOException {
        assertThat(apl.getIdeaArtifactList()).hasSize(s.length);
        for (int i = 0; i < s.length;i++) {
            filesAreEqual(sa, apl.getIdeaArtifactList().get(i), s[i]);
        }
    }

    @Test
    public void testDependencyToPlugin() throws Exception {
        MavenSession session = initMavenSession("unit/dependency-to-plugin", "moduleA");
        MojoExecution execution = rule.newMojoExecution("build");
        AssemblePluginMojo mojo = (AssemblePluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.setFailOnMissingDependencies(false);
        mojo.execute();
        String sb = getTestResult(mojo);
        Assert.assertEquals("SERVER:\n" +
                "agent/\n" +
                "agent/moduleA.zip\n" +
                "server/\n" +
                "server/commons-beanutils-core-1.8.3.jar\n" +
                "server/commons-codec-1.15.jar\n" +
                "server/commons-logging-1.1.1.jar\n" +
                "server/dependency-to-plugin-1.1-SNAPSHOT.jar\n" +
                "teamcity-plugin.xml", sb);

    }

    @Test
    public void testMakeMultiModuleArtifact() throws Exception {
        MavenSession session = initMavenSession("unit/multi-module-to-test");
        MojoExecution execution = rule.newMojoExecution("build");
        AssemblePluginMojo mojo = (AssemblePluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.setFailOnMissingDependencies(false);
        mojo.execute();
        String sb = getTestResult(mojo);
        sa.assertThat(sb).isEqualToIgnoringCase("""
                AGENT:
                lib
                lib/commons-beanutils-core-1.8.3.jar
                lib/commons-logging-1.1.1.jar
                lib/moduleA-1.1-SNAPSHOT.jar
                lib/moduleB-1.1-SNAPSHOT.jar
                teamcity-plugin.xml
                SERVER:
                agent/
                agent/multi-module-to-test.zip
                server/
                server/moduleB-1.1-SNAPSHOT.jar
                teamcity-plugin.xml""");
        assertIdeaArtifacts(sa, mojo.getAgentPluginWorkflow(), """
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::multi-module-to-test">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent</output-path>
                        <root id="root">
                            <element id="archive" name="multi-module-to-test.zip">
                                <element artifact-name="TC::AGENT::multi-module-to-test::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>""", """
                <component name="ArtifactManager">
                    <artifact name="TC::AGENT::multi-module-to-test::EXPLODED">
                        <output-path>$PROJECT_DIR$/target/teamcity/agent-unpacked</output-path>
                        <root id="root">
                            <element id="directory" name="multi-module-to-test">
                                <element id="directory" name="lib">
                                    <element id="archive" name="moduleA-1.1-SNAPSHOT.jar">
                                        <element id="module-output" name="moduleA"/>
                                    </element>
                                    <element id="archive" name="moduleB-1.1-SNAPSHOT.jar">
                                        <element id="module-output" name="moduleB"/>
                                    </element>
                                    <element id="library" level="project" name="Maven: commons-beanutils:commons-beanutils-core:1.8.3"/>
                                    <element id="library" level="project" name="Maven: commons-logging:commons-logging:1.1.1"/>
                                </element>
                                <element id="file-copy" output-file-name="teamcity-plugin.xml" path="$PROJECT_DIR$/target/teamcity/teamcity-agent-plugin-generated.xml"/>
                            </element>
                        </root>
                    </artifact>
                </component>""");
        assertIdeaArtifacts(sa, mojo.getServerPluginWorkflow(), """
                <component name="ArtifactManager">
                    <artifact name="TC::SERVER::multi-module-to-test::EXPLODED">
                        <output-path>$PROJECT_DIR$/target/teamcity/plugin/multi-module-to-test</output-path>
                        <root id="root">
                            <element id="file-copy" output-file-name="teamcity-plugin.xml" path="$PROJECT_DIR$/target/teamcity/teamcity-plugin-generated.xml"/>
                            <element id="directory" name="server">
                                <element id="archive" name="moduleB-1.1-SNAPSHOT.jar">
                                    <element id="module-output" name="moduleB"/>
                                </element>
                            </element>
                            <element id="directory" name="agent">
                                <element id="archive" name="multi-module-to-test.zip">
                                    <element artifact-name="TC::AGENT::multi-module-to-test" id="artifact"/>
                                </element>
                            </element>
                        </root>
                    </artifact>
                </component>""", """
                <component name="ArtifactManager">
                    <artifact name="TC::SERVER::multi-module-to-test::4IDEA">
                        <output-path>$PROJECT_DIR$/target/teamcity/plugin</output-path>
                        <root id="root">
                            <element id="directory" name="multi-module-to-test">
                                <element artifact-name="TC::SERVER::multi-module-to-test::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>
                """, """
                <component name="ArtifactManager">
                    <artifact name="TC::SERVER::multi-module-to-test">
                        <output-path>$PROJECT_DIR$/target/teamcity/dist</output-path>
                        <root id="root">
                            <element id="archive" name="multi-module-to-test.zip">
                                <element artifact-name="TC::SERVER::multi-module-to-test::EXPLODED" id="artifact"/>
                            </element>
                        </root>
                    </artifact>
                </component>""");
    }

    private void appendTestResult(StringJoiner sb, AgentPluginWorkflow apw) throws IOException {
        if (apw.getAgentPath() != null) {
            sb.add("AGENT:");
            Files.walk(apw.getAgentPath()).skip(1).sorted().forEachOrdered(it -> sb.add(apw.getAgentPath().relativize(it).toString()));
        }
    }

    private void appendTestResult(StringJoiner sb, ServerPluginWorkflow spw) throws IOException {
        sb.add("SERVER:");
        Optional<ResultArtifact> a = spw.getAttachedArtifacts().stream().filter(it -> it.getClassifier().equalsIgnoreCase("teamcity-plugin")).findFirst();
        if (a.isPresent()) {
            try (ZipFile zipFile = new ZipFile(a.get().getFile().toFile())) {
                zipFile.stream()
                        .map(ZipEntry::getName)
                        .sorted()
                        .forEach(sb::add);
            }
        }
    }

    private String getTestResult(AssemblePluginMojo mojo) throws IOException {
        StringJoiner sb = new StringJoiner("\n");
        appendTestResult(sb, mojo.getAgentPluginWorkflow());
        appendTestResult(sb, mojo.getServerPluginWorkflow());
        return sb.toString();
    }

    private MavenSession initMavenSession(String projectBase, String ...additionalModules) throws Exception {
        MavenProject project = rule.readMavenProject(getTestDir(projectBase));
        List<MavenProject> projects = getMavenProjectList(projectBase, project.getModules());
        List<MavenProject> projects1 = getMavenProjectList(projectBase, List.of(additionalModules));
        projects.addAll(projects1);
        projects.add(0, project);
        MavenSession session = rule.newMavenSession(project);
        session.setProjects(projects);
        File repoFile = new File(getTestDir(projectBase), "repo");
        ArtifactRepository localRepo = createLocalArtifactRepository(repoFile);
        LocalRepository localRepository = createLocalRepository(repoFile);
        session.getRequest().setLocalRepository(localRepo);
        LocalRepositoryManager lrm = rule.getContainer().lookup(SimpleLocalRepositoryManagerFactory.class)
                .newInstance(session.getRepositorySession(), localRepository);
        DefaultRepositorySystemSession repositorySession = (DefaultRepositorySystemSession) session.getRepositorySession();
        repositorySession.setWorkspaceReader(new MavenWorkspaceReader() {
            @Override
            public Model findModel(Artifact artifact) {
                Optional<MavenProject> projectOptional = session.getProjects().stream().filter(it -> equalArtifacts(it, artifact)).findFirst();
                return projectOptional.map(MavenProject::getModel).orElse(null);
            }

            @Override
            public WorkspaceRepository getRepository() {
                return new WorkspaceRepository();
            }

            @Override
            public File findArtifact(Artifact artifact) {
                Optional<MavenProject> projectOptional = session.getProjects().stream().filter(it -> equalArtifacts(it, artifact)).findFirst();
                if (projectOptional.isPresent()) {
                    MavenProject p = projectOptional.get();
                    if ("teamcity-agent-plugin".equalsIgnoreCase(artifact.getClassifier())) {
                        return Path.of(p.getBuild().getDirectory(), p.getArtifactId()+"-"+p.getVersion()+"-" + artifact.getClassifier()+"." + artifact.getExtension()).toFile();
                    } else
                        return Path.of(p.getBuild().getDirectory(), p.getArtifactId()+"-"+p.getVersion()+"."+artifact.getExtension()).toFile();
                } else
                    return null;
            }

            @Override
            public List<String> findVersions(Artifact artifact) {
                List<String> versions = session.getProjects().stream().filter(it -> equalArtifacts(it, artifact))
                        .map(MavenProject::getArtifact)
                        .map(org.apache.maven.artifact.Artifact::getVersion)
                        .collect(Collectors.toList());

                return versions;
            }
        });
        repositorySession.setLocalRepositoryManager(lrm);
        return session;
    }

    private List<MavenProject> getMavenProjectList(String projectBase, List<String> modules) {
        List<MavenProject> projects = modules.stream().map(it -> {
            try {
                return rule.readMavenProject(new File(getTestDir(projectBase), it));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        return projects;
    }

    private static <T> boolean eq(T s1, T s2) {
        return s1 != null ? s1.equals(s2) : s2 == null;
    }

    private boolean equalArtifacts(MavenProject it, Artifact artifact) {
        return eq(it.getArtifact().getGroupId(), artifact.getGroupId()) && eq(it.getArtifact().getArtifactId(), artifact.getArtifactId());
    }

    private LocalRepository createLocalRepository(File repo) {
        return new LocalRepository(repo);
    }

    public static File getTestDir(String pathToBase) {
        return new File(AssemblePluginMojo.class.getClassLoader().getResource(pathToBase).getFile());
    }

    private ArtifactRepository createLocalArtifactRepository(File localRepo) throws IOException {
        return new MavenArtifactRepository("local",
                localRepo.getCanonicalPath(),
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE ),
                new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE )

        );
    }
}
