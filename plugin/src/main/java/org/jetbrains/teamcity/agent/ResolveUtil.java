package org.jetbrains.teamcity.agent;

import lombok.Data;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.util.List;

@Data
public class ResolveUtil {
    private final Log log;
    @Component
    private final org.eclipse.aether.RepositorySystem repoSystem;
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private final List<RemoteRepository> repositories;
    @Parameter(defaultValue = "${repositorySystemSession}")
    private final RepositorySystemSession repoSession;


    public org.eclipse.aether.artifact.Artifact resolve(Artifact unresolvedArtifact) throws MojoExecutionException {
        // Here, it becomes messy. We ask Maven to resolve the artifact's location.
        // It may imply downloading it from a remote repository,
        // searching the local repository or looking into the reactor's cache.

        // To achieve this, we must use Aether
        // (the dependency mechanism behind Maven).
        String artifactId = unresolvedArtifact.getArtifactId();
        org.eclipse.aether.artifact.Artifact aetherArtifact = new DefaultArtifact(
                unresolvedArtifact.getGroupId(),
                unresolvedArtifact.getArtifactId(),
                unresolvedArtifact.getClassifier(),
                unresolvedArtifact.getType(),
                unresolvedArtifact.getVersion());

        ArtifactRequest req = new ArtifactRequest().setRepositories(this.repositories).setArtifact(aetherArtifact);
        ArtifactResult resolutionResult;
        try {
            resolutionResult = this.repoSystem.resolveArtifact(this.repoSession, req);

        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Artifact " + artifactId + " could not be resolved.", e);
        }

        // The file should exists, but we never know.
        File file = resolutionResult.getArtifact().getFile();
        if (file == null || !file.exists()) {
            getLog().warn("Artifact " + artifactId + " has no attached file (" + file + "). Its content will not be copied in the target model directory.");
        }
        return resolutionResult.getArtifact();
    }

}
