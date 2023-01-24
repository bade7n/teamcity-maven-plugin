package org.jetbrains.teamcity;

import lombok.Data;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.jetbrains.teamcity.agent.AssemblyContext;
import org.jetbrains.teamcity.agent.PathSet;
import org.jetbrains.teamcity.agent.ResultArtifact;
import org.jetbrains.teamcity.agent.WorkflowUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.Files.exists;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.jetbrains.teamcity.AssemblePluginMojo.AGENT_SUBDIR;
import static org.jetbrains.teamcity.AssemblePluginMojo.TEAMCITY_AGENT_PLUGIN_CLASSIFIER;
import static org.jetbrains.teamcity.agent.WorkflowUtil.TEAMCITY_PLUGIN_XML;

@Data
public class ServerPluginWorkflow {
    private final DependencyNode rootNode;
    private final Server parameters;
    private final WorkflowUtil util;

    private final Optional<Plugin> plugin;
    private final MavenProject project;

    private final AssemblyContext assemblyContext = new AssemblyContext();

    private final List<ResultArtifact> attachedArtifacts = new ArrayList<>();
    private final List<ResultArchive> attachedArchives = new ArrayList<>();

    public void execute() throws MojoExecutionException, IOException {
        prepareDescriptor();
        if (parameters.isNeedToBuild())
            buildServerPlugin(rootNode);
    }

    private AssemblyContext buildServerPlugin(DependencyNode rootNode) throws MojoExecutionException {
        AssemblyContext assemblyContext = new AssemblyContext();
        Path serverPath = util.createDir(util.getServerPluginRoot().resolve("server"));
        List<DependencyNode> nodes = util.getDependencyNodeList(rootNode, parameters.getSpec(), parameters.getExclusions());
        Map<Boolean, List<DependencyNode>> dependencies = nodes.stream().collect(Collectors.partitioningBy(it -> "teamcity-agent-plugin".equalsIgnoreCase(it.getArtifact().getClassifier())));
        assemblyContext.getPaths().add(new PathSet(serverPath));
        util.copyTransitiveDependenciesInto(assemblyContext, dependencies.get(Boolean.FALSE), serverPath);
        if (!parameters.getBuildServerResources().isEmpty()) {
            String classifier = "teamcity-plugin-resources";
            Path resourcesJar = util.getJarFile(serverPath, parameters.getArtifactId(), classifier);

            File resourcesFile = resourcesJar.toFile();
            List<org.codehaus.plexus.archiver.FileSet> fileSets = new ArrayList<>();
            for (String buildServerResource : parameters.getBuildServerResources()) {
                Path path = Path.of(buildServerResource);
                if (!path.isAbsolute())
                    path = project.getBasedir().toPath().resolve(path);
                org.codehaus.plexus.archiver.FileSet fs = new DefaultFileSet(path.toFile()).prefixed("buildServerResources/");
                fileSets.add(fs);
            }

            attachedArchives.add(new ResultArchive("jar", fileSets, resourcesFile));
            attachedArtifacts.add(new ResultArtifact("jar", classifier, resourcesFile));
        }

        List<DependencyNode> agentPluginDependencies = dependencies.get(Boolean.TRUE);
        assembleExplicitAgentDependencies(assemblyContext, agentPluginDependencies);

        assembleKotlinDsl();

        if (parameters.isNeedToBuildCommon()) {
            Path commonPath = util.createDir(util.getServerPluginRoot().resolve("common"));
            List<DependencyNode> commonNodes = util.getDependencyNodeList(rootNode, parameters.getCommonSpec(), parameters.getCommonExclusions());
            util.copyTransitiveDependenciesInto(assemblyContext, commonNodes, commonPath);
        }
        return assemblyContext;
    }

    private void prepareDescriptor() throws MojoExecutionException, IOException {

        Path destination = util.getServerPluginRoot().resolve(TEAMCITY_PLUGIN_XML);
        if (!exists(parameters.getDescriptor().getPath().toPath())) {
            if (parameters.getDescriptor().isFailOnMissing())
                throw new MojoExecutionException(String.format("`pluginDescriptorPath` must point to teamcity plugin descriptor (%s).", parameters.getDescriptor().getPath()));
            else {
                util.createDescriptor("teamcity-server-plugin.vm", destination);
            }
        } else {
            if (!destination.toFile().exists())
                Files.copy(parameters.getDescriptor().getPath().toPath(), destination);
        }
    }



    private void assembleExplicitAgentDependencies(AssemblyContext assemblyContext, List<DependencyNode> agentPluginDependencies) throws MojoExecutionException {
        if (agentPluginDependencies != null && !agentPluginDependencies.isEmpty()) {
            Path agentPath = util.createDir(util.getServerPluginRoot().resolve(AGENT_SUBDIR));
            util.copyTransitiveDependenciesInto(assemblyContext, agentPluginDependencies, agentPath);
        }

        if (plugin.isPresent()) {
            List<Dependency> agentDependencies = plugin.get().getDependencies().stream().filter(it -> TEAMCITY_AGENT_PLUGIN_CLASSIFIER.equalsIgnoreCase(it.getClassifier())).collect(Collectors.toList());
            if (!agentDependencies.isEmpty()) {
                Path agentPath = util.createDir(util.getServerPluginRoot().resolve(AGENT_SUBDIR));
                util.copyDependenciesInto(agentDependencies, agentPath);
            }
        }
    }

    private void assembleKotlinDsl() {
        if (parameters.getKotlinDslDescriptorsPath().exists()) {
            Path kotlinDslPath = util.createDir(util.getServerPluginRoot().resolve("kotlin-dsl"));
            try {
                Files.walk(parameters.getKotlinDslDescriptorsPath().toPath()).forEach(it -> {
                    try {
                        if (it.toFile().isFile())
                            Files.copy(it, kotlinDslPath.resolve(it.getFileName()), REPLACE_EXISTING);
                    } catch (IOException e) {
                        util.getLog().warn("Can't copy " + it + " to " + kotlinDslPath, e);
                    }
                });
            } catch (IOException e) {
                util.getLog().warn("Can't copy " + parameters.getKotlinDslDescriptorsPath() + " to " + kotlinDslPath);
            }
        }
    }


}
