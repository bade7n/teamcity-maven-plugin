package org.jetbrains.teamcity.agent;

import lombok.Data;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.jetbrains.teamcity.Agent;
import org.jetbrains.teamcity.ArtifactBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.jetbrains.teamcity.agent.WorkflowUtil.TEAMCITY_PLUGIN_XML;

@Data
public class AgentPluginWorkflow implements ArtifactListProvider {
    public static final String TEAMCITY_AGENT_PLUGIN_CLASSIFIER = "teamcity-agent-plugin";
    private final DependencyNode rootNode;
    private final Agent parameters;
    private final WorkflowUtil util;

    private final List<AssemblyContext> assemblyContexts = new ArrayList<>();

    private final List<ResultArtifact> attachedArtifacts = new ArrayList<>();

    private final Path workDirectory;

    private Path agentPath;
    private Path pluginDescriptorPath;

    private final List<Path> ideaArtifactList = new ArrayList<>();

    public void execute() throws MojoExecutionException {
        if (parameters.isNeedToBuild()) {
            AssemblyContext assemblyContext = buildAgentPlugin(rootNode);
            assemblyContexts.add(assemblyContext);
        }
        ideaArtifactList.addAll(new ArtifactBuilder(util.getLog(), util).build(getAssemblyContexts()));
    }


    public AssemblyContext buildAgentPlugin(DependencyNode rootNode) throws MojoExecutionException {
        AssemblyContext assemblyContext = new AssemblyContext();
        String ideaArtifactBaseName = getIdeaArtifactBaseName(parameters.getPluginName());
        assemblyContext.setName(getExplodedName(ideaArtifactBaseName));
        Path agentUnpacked = workDirectory.resolve("agent-unpacked");
        agentPath  = util.createDir(agentUnpacked.resolve(parameters.getPluginName()));
        assemblyContext.setRoot(agentUnpacked);

        /**
         * pluginRoot/
         * |-agent/
         * | |-plugin_name.zip
         * |   |-plugin_name/
         * |     |-dependencies.jar
         * |-server
         * |-teamcity-plugin.xml
         */
        Path agentLibPath = agentPath.resolve("lib");
        assemblyContext.getPaths().add(new PathSet(agentLibPath));
        List<DependencyNode> nodesCopied = util.copyTransitiveDependenciesInto(parameters.isFailOnMissingDependencies(), parameters.getIgnoreExtraFilesIn(), assemblyContext, rootNode, parameters.getSpec(), util.createDir(agentLibPath), parameters.getExclusions());
        if (!nodesCopied.isEmpty()) {

            File targetDescriptorPath = parameters.getDescriptor().getPath();
            if (!targetDescriptorPath.exists() && !parameters.getDescriptor().isDoNotGenerate()) {
                try {
                    Path generatedPath = workDirectory.resolve("teamcity-agent-plugin-generated.xml");
                    util.createDescriptor("teamcity-agent-plugin.vm", generatedPath, parameters);
                    targetDescriptorPath = generatedPath.toFile();
                } catch (IOException e) {
                    util.getLog().warn("Error while generating agent descriptor: " + agentPath, e);
                }
            }

            if (targetDescriptorPath.exists()) {
                try {
                    pluginDescriptorPath = agentPath.resolve(TEAMCITY_PLUGIN_XML);
                    Files.copy(targetDescriptorPath.toPath(), pluginDescriptorPath, REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new MojoExecutionException(String.format("Can't copy %s.", targetDescriptorPath), e);
                }
            } else if (parameters.getDescriptor().isFailOnMissing()) {
                throw new MojoExecutionException(String.format("`agent.pluginDescriptorPath` must point to teamcity plugin descriptor (%s).", targetDescriptorPath));
            }
            assemblyContext.getPaths().add(new PathSet(agentPath).with(new FilePathEntry(TEAMCITY_PLUGIN_XML, targetDescriptorPath.toPath()))); // add it anyway if it exists or not

            Path agentPluginPath = workDirectory.resolve("agent");
            try {
                String zipName = parameters.getPluginName() + ".zip";
                AssemblyContext zipAssemblyContext =  new AssemblyContext();
                zipAssemblyContext.setName(ideaArtifactBaseName);
                zipAssemblyContext.setRoot(agentPluginPath);
                zipAssemblyContext.getPaths().add(new PathSet(agentPluginPath).with(new ArtifactPathEntry(zipName, assemblyContext.getName())));
                assemblyContexts.add(zipAssemblyContext.cloneWithRoot(agentPluginPath));

                Path agentPart = util.zipFile(agentUnpacked, Files.createDirectories(agentPluginPath), zipName);
                attachedArtifacts.add(new ResultArtifact("zip", "teamcity-agent-plugin", agentPart, zipAssemblyContext));
            } catch (IOException | MojoFailureException e) {
                util.getLog().warn("Error while packing agent part to: " + agentPluginPath, e);
            }
        }
        return assemblyContext.cloneWithRoot(agentUnpacked);
    }

    public static String getIdeaArtifactExplodedName(String pluginName) {
        return getExplodedName(getIdeaArtifactBaseName(pluginName));
    }

    public static String getExplodedName(String ideaArtifactBaseName) {
        return ideaArtifactBaseName + "::EXPLODED";
    }

    public static String getIdeaArtifactBaseName(String pluginName) {
        return "TC::AGENT::" + pluginName;
    }
}
