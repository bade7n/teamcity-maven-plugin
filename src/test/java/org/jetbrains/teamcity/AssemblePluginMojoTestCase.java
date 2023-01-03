package org.jetbrains.teamcity;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenWorkspaceReader;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AssemblePluginMojoTestCase {
    @Rule
    public MojoRule rule = new MojoRule();

    @Test
    public void testSimple() {
        System.out.println(1);
    }

    @Test
    public void testMakeSimpleArtifact() throws Exception {
        MavenSession session = initMavenSession("unit/project-to-test");
        MojoExecution execution = rule.newMojoExecution("build");
        AssemblePluginMojo mojo = (AssemblePluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.execute();
    }

    @Test
    public void testMakeMultiModuleArtifact() throws Exception {
        MavenSession session = initMavenSession("unit/multi-module-to-test");
        MojoExecution execution = rule.newMojoExecution("build");
        AssemblePluginMojo mojo = (AssemblePluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.execute();
    }

    private MavenSession initMavenSession(String projectBase) throws Exception {
        MavenProject project = rule.readMavenProject(getTestDir(projectBase));
        List<MavenProject> projects = project.getModules().stream().map(it -> {
            try {
                return rule.readMavenProject(new File(getTestDir(projectBase), it));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
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
                    return Path.of(p.getBuild().getDirectory(), p.getArtifactId()+"-"+p.getVersion()+".jar").toFile();
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
