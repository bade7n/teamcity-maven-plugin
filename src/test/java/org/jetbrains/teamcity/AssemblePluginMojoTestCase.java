package org.jetbrains.teamcity;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.codehaus.plexus.PlexusTestCase.getTestFile;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AssemblePluginMojoTestCase {
    @Rule
    public MojoRule rule = new MojoRule();

    @Test
    public void testSimple() {
        System.out.println(1);
    }

    @Test
    public void testMakeSimpleArtifact() throws Exception {
        // setup with pom set BRANCHNAME  set in pom
        MavenProject project = rule.readMavenProject(getTestDir("unit/project-to-test"));

        // Generate session
        MavenSession session = rule.newMavenSession(project);

        // add localRepo - framework doesn't do this on its own
        ArtifactRepository localRepo = createLocalArtifactRepository(new File(getTestDir("unit/project-to-test"), "repo").getCanonicalPath());
        session.getRequest().setLocalRepository(localRepo);
        LocalRepository localRepository = createLocalRepository(new File(getTestDir("unit/project-to-test"), "repo"));
        LocalRepositoryManager lrm = rule.getContainer().lookup(SimpleLocalRepositoryManagerFactory.class).newInstance(session.getRepositorySession(), localRepository);
        ((DefaultRepositorySystemSession) session.getRepositorySession()).setLocalRepositoryManager(lrm);

        // Generate Execution and Mojo for testing
        MojoExecution execution = rule.newMojoExecution("build");
        AssemblePluginMojo mojo = (AssemblePluginMojo) rule.lookupConfiguredMojo(session, execution);
        mojo.execute();

    }

    private LocalRepository createLocalRepository(File repo) {
        return new LocalRepository(repo);
    }

    private File getTestDir(String pathToBase) {
        return new File(getClass().getClassLoader().getResource(pathToBase).getFile());
    }

    private ArtifactRepository createLocalArtifactRepository(String localRepoDir) {
        return new MavenArtifactRepository("local",
                localRepoDir,
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE ),
                new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE )

        );
    }
}
