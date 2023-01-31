package org.jetbrains.teamcity.agent;

import lombok.Data;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.filter.AndDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.ArtifactDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.internal.ConflictData;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.FilteringDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.SerializingDependencyNodeVisitor;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.jetbrains.teamcity.ArtifactBuilder;
import org.jetbrains.teamcity.MultipleDependencyNodeVisitor;
import org.jetbrains.teamcity.SkipFilteringDependencyNodeVisitor;
import org.jetbrains.teamcity.data.ResolvedArtifact;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.maven.artifact.ArtifactUtils.key;
import static org.jetbrains.teamcity.agent.AgentPluginWorkflow.TEAMCITY_AGENT_PLUGIN_CLASSIFIER;
import static org.jetbrains.teamcity.ServerPluginWorkflow.TEAMCITY_PLUGIN_CLASSIFIER;

@Data
public class WorkflowUtil {
    public static final String TEAMCITY_PLUGIN_XML = "teamcity-plugin.xml";

    private final Log log;
    private final List<Artifact> reactorProjectList;
    private final MavenProject project;
    private final Path workDirectory;

    private final ResolveUtil resolve;
    private final String tokens;
    private final ArtifactFactory artifactFactory;

    public WorkflowUtil(Log log, List<MavenProject> reactorProjects, MavenProject project, Path workDirectory, ResolveUtil resolve, String tokens, ArtifactFactory artifactFactory) {
        this.log = log;
        this.reactorProjectList = reactorProjects.stream().flatMap(this::getArtifactList).collect(Collectors.toList());
        this.project = project;
        this.workDirectory = workDirectory;
        this.resolve = resolve;
        this.tokens = tokens;
        this.artifactFactory = artifactFactory;
    }

    private Stream<Artifact> getArtifactList(MavenProject it) {
        return new ArrayList<Artifact>() {{
            add(it.getArtifact());
            addAll(it.getArtifacts());
        }}.stream();
    }

    public boolean isNeedToBuild(String a) {
        return a != null && !a.isBlank();
    }


    public Path createDir(Path path) {
        try {
            return Files.createDirectories(Files.createDirectories(path));
        } catch (IOException e) {
            getLog().warn("Error while creation " + path, e);
            return path;
        }
    }

    protected List<DependencyNode> copyTransitiveDependenciesInto(boolean failOnMissingDependencies, String ignoreExtraFilesIn, AssemblyContext assemblyContext, DependencyNode rootNode, String spec, Path toPath, List<String> excludes) throws MojoExecutionException {
        List<DependencyNode> nodes = getDependencyNodeList(rootNode, spec, excludes);
        List<ResolvedArtifact> artifacts = copyTransitiveDependenciesInto(failOnMissingDependencies, ignoreExtraFilesIn, assemblyContext, nodes, toPath);
        return nodes;
    }

    public List<ResolvedArtifact> copyTransitiveDependenciesInto(boolean failOnMissingDependencies, String ignoreExtraFilesIn, AssemblyContext assemblyContext, List<DependencyNode> nodes, Path toPath) throws MojoExecutionException {
        List<Path> destinations = new ArrayList<>();
        List<ResolvedArtifact> result = new ArrayList<>();
        for (DependencyNode node : nodes) {
            Artifact alternativeArtifact = findAlternativeArtifacts(node.getArtifact());
            if (alternativeArtifact == null)
                continue;
            org.eclipse.aether.artifact.Artifact source = resolve.resolve(alternativeArtifact);
            ResolvedArtifact ra = new ResolvedArtifact(source, isReactorProject(node.getArtifact()));
            result.add(ra);
            String name = ra.getFileName();
            Path destination = toPath.resolve(name);
            destinations.add(destination);
            assemblyContext.addToLastPathSet(new DependencyPathEntry(node.getArtifact(), ra.isReactorProject(), destination.getFileName().toString(), source.getFile().toPath()));
            try {
                internalCopy(failOnMissingDependencies, source.getFile(), destination, ra.isReactorProject());
            } catch (IOException e) {
                getLog().warn("Error while copying " + source + " to " + destination, e);
            }
        }
        removeOtherFiles(ignoreExtraFilesIn, toPath, destinations);
        return result;
    }

    private void removeOtherFiles(String ignoreExtraFilesIn, Path toPath, List<Path> destinations) {
        try {
            List<Path> existingFiles = Files.walk(toPath)
                    .filter(it -> !it.equals(toPath))
                    .filter(it -> !destinations.contains(it))
                    .filter(it -> shouldRemove(ignoreExtraFilesIn, toPath, it))
                    .collect(Collectors.toList());
            if (!existingFiles.isEmpty()) {
                getLog().warn("Found extra files in " + toPath + " removing (" + existingFiles + ")");
                existingFiles.forEach(it -> it.toFile().delete());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean shouldRemove(String ignoreExtraFilesIn, Path toPath, Path it) {
        if (ignoreExtraFilesIn != null) {
            String[] extraPaths = ignoreExtraFilesIn.split(",");
            Path relativePath = toPath.relativize(it);
            for (String extra : extraPaths) {
                Path p = Paths.get(extra);
                if ((p.isAbsolute() && it.equals(p)) || (!p.isAbsolute() && isSubpathOf(relativePath, p))) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isSubpathOf(Path path, Path basedir) {
        return !basedir.relativize(path).startsWith(Path.of(".."));
    }

    private Artifact findAlternativeArtifacts(Artifact a) {
        if ("war".equalsIgnoreCase(a.getType())) {
            if (project.getArtifact().equals(a)) {
                List<Artifact> jarArtifacts = project.getAttachedArtifacts().stream().filter(it -> it.getType().equalsIgnoreCase("jar")).collect(Collectors.toList());
                if (jarArtifacts.size() == 1)
                    return jarArtifacts.get(0);
                else {
                    getLog().warn("Not possible to resolve WAR " + key(a) + " to a classes artifact. The result is [" + jarArtifacts.stream().map(ArtifactUtils::key).collect(Collectors.joining(",")) + "]");
                    return null;// no need to attach war file inside the plugin.
                }
            }
        }
        return a;
    }


    public List<DependencyNode> getDependencyNodeList(DependencyNode rootNode, String spec, List<String> exclusions) {
        List<DependencyNode> nodes;
        // looking for the nodes specified by user
        if (Arrays.asList("*", ".").contains(spec)) {
            nodes = Collections.singletonList(rootNode);
        } else {
            List<String> patterns = Arrays.asList(spec.split(","));
            nodes = collectNodes(rootNode, new StrictPatternIncludesArtifactFilter(patterns));
        }
        // getting transitive dependencies excluding ones specified in exclusions filter. Not to include teamcity-core by mistake for example.
        StringWriter writer = new StringWriter();
        CollectingDependencyNodeVisitor transitiveCollectingVisitor = new CollectingDependencyNodeVisitor();
        MultipleDependencyNodeVisitor mdnv = new MultipleDependencyNodeVisitor(Arrays.asList(transitiveCollectingVisitor, getSerializingDependencyNodeVisitor(writer)));
        DependencyNodeFilter exclusionFilter = new ArtifactDependencyNodeFilter(new StrictPatternExcludesArtifactFilter(exclusions));
        AndDependencyNodeFilter andDependencyNodeFilter = new AndDependencyNodeFilter(exclusionFilter, it -> {
            return isParentClassifierIn(it, TEAMCITY_PLUGIN_CLASSIFIER, TEAMCITY_AGENT_PLUGIN_CLASSIFIER);
        });
        SkipFilteringDependencyNodeVisitor visitor = new SkipFilteringDependencyNodeVisitor(mdnv, andDependencyNodeFilter);
        nodes.forEach(it -> it.accept(visitor));
        getLog().info("Dependencies according to spec " + spec + ":\n" + writer);
        List<DependencyNode> nodes1 = transitiveCollectingVisitor.getNodes();
        // now conflicted dependencies might be in list, need to find them and resolve to the right version
        List<DependencyNode> result = new ArrayList<>();
        for (DependencyNode node : nodes1) {
            ConflictData cd = getPrivateField(node);
            if (cd != null && cd.getWinnerVersion() != null) {
                List<DependencyNode> substitutions = findSubstitutions(rootNode, node, cd.getWinnerVersion());
                CollectingDependencyNodeVisitor collector = new CollectingDependencyNodeVisitor();
                SkipFilteringDependencyNodeVisitor visitor1 = new SkipFilteringDependencyNodeVisitor(collector, exclusionFilter);
                substitutions.forEach(it -> it.accept(visitor1));
                result.addAll(collector.getNodes());
            } else {
                result.add(node);
            }
        }
        return result;
    }

    private boolean isParentClassifierIn(DependencyNode it, String s, String s1) {
        if (it.getParent() != null && (Objects.equals("teamcity-plugin", it.getParent().getArtifact().getClassifier()) ||
                Objects.equals("teamcity-agent-plugin", it.getParent().getArtifact().getClassifier())))
            return false;
        return true;
    }

    private List<DependencyNode> findSubstitutions(DependencyNode rootNode, DependencyNode node, String winnerVersion) {
        Artifact a = node.getArtifact();
        StringJoiner sj = new StringJoiner(":");
        sj.add(a.getGroupId());
        sj.add(a.getArtifactId());
        sj.add(a.getType());
        sj.add(winnerVersion);

        StrictPatternIncludesArtifactFilter filter = new StrictPatternIncludesArtifactFilter(Collections.singletonList(sj.toString()));
        AndDependencyNodeFilter nodeFilter = new AndDependencyNodeFilter(new ArtifactDependencyNodeFilter(filter), node1 -> node1 != node);
        CollectingDependencyNodeVisitor collector = new CollectingDependencyNodeVisitor();
        rootNode.accept(new FilteringDependencyNodeVisitor(collector, nodeFilter));
        return collector.getNodes();
    }


    public SerializingDependencyNodeVisitor getSerializingDependencyNodeVisitor(Writer writer) {
        return new SerializingDependencyNodeVisitor(writer, toGraphTokens(tokens));
    }


    private SerializingDependencyNodeVisitor.GraphTokens toGraphTokens(String theTokens) {
        SerializingDependencyNodeVisitor.GraphTokens graphTokens;

        if ("whitespace".equals(theTokens)) {
            getLog().debug("+ Using whitespace tree tokens");

            graphTokens = SerializingDependencyNodeVisitor.WHITESPACE_TOKENS;
        } else if ("extended".equals(theTokens)) {
            getLog().debug("+ Using extended tree tokens");

            graphTokens = SerializingDependencyNodeVisitor.EXTENDED_TOKENS;
        } else {
            graphTokens = SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }

        return graphTokens;
    }

    private List<DependencyNode> collectNodes(DependencyNode rootNode, ArtifactFilter artifactFilter) {
        CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
        DependencyNodeVisitor firstPassVisitor = new FilteringDependencyNodeVisitor(collectingVisitor,
                new ArtifactDependencyNodeFilter(artifactFilter));
        rootNode.accept(firstPassVisitor);
        return collectingVisitor.getNodes();
    }

    private void internalCopy(boolean failOnMissingDependencies, File source, Path destination, boolean isReactorProject) throws IOException {
        try {
            if (!destination.toFile().exists() || isReactorProject || destination.toFile().length() != source.length()) {
                Files.copy(source.toPath(), destination);
            }
        } catch (NoSuchFileException e) {
            if (failOnMissingDependencies)
                getLog().error("Can't find dependency to add to plugin " + source);
            else
                destination.toFile().createNewFile();
            getLog().warn("NoSuchFileException: " + e.getMessage());
        } catch (FileAlreadyExistsException e) {
            Files.copy(source.toPath(), destination, REPLACE_EXISTING);
        } catch (IOException e) {
            getLog().warn(e);
        }
    }


    private ConflictData getPrivateField(DependencyNode node) {
        try {
            Field f = node.getClass().getDeclaredField("data");
            f.setAccessible(true);
            Object o = f.get(node);
            return (ConflictData) o;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    private boolean isReactorProject(Artifact a) {
        return reactorProjectList.contains(a);
    }

    public Path getJarFile(Path basedir, String resultFinalName, String classifier) {
        if (basedir == null) {
            throw new IllegalArgumentException("basedir is not allowed to be null");
        }
        if (resultFinalName == null) {
            throw new IllegalArgumentException("finalName is not allowed to be null");
        }

        String fileName;
        if (hasClassifier(classifier)) {
            fileName = resultFinalName + "-" + classifier + ".jar";
        } else {
            fileName = resultFinalName + ".jar";
        }

        return basedir.resolve(fileName);
    }

    protected boolean hasClassifier(String classifier) {
        return classifier != null && classifier.trim().length() > 0;
    }

    public Path createDescriptor(String templateName, Path destination, Object parameters) throws IOException {
        File descriptor = destination.toFile();
        try (FileWriter fw = new FileWriter(descriptor)) {
            VelocityContext context = new VelocityContext();
            VelocityEngine ve = new VelocityEngine();
            ve.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
            ve.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            context.put("project", project);
            context.put("param", parameters);
            Template template = ve.getTemplate(templateName);
            template.merge(context, fw);
        }
        return descriptor.toPath();
    }

    public Path zipFile(Path source, Path baseDir, String zipName) throws MojoFailureException {
        try {
            Path zipPath = Files.createDirectories(baseDir).resolve(zipName);
            if (zipPath.toFile().exists()) {
                boolean deleted = zipPath.toFile().delete();
                if (!deleted) {
                    getLog().warn("Failed to delete " + zipPath);
                }
            }
            URI uri = URI.create("jar:file:" + zipPath);
            try (FileSystem zipfs = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
                List<Path> filesInAgentZip = Files.walk(source).collect(Collectors.toList());
                for (Path entry : filesInAgentZip) {
                    Path relativePath = zipfs.getPath(source.relativize(entry).toString());
                    try {
                        if (!relativePath.toString().isBlank())
                            Files.copy(entry, relativePath);
                    } catch (IOException e) {
                        getLog().warn("Can't zip file " + entry + " to " + relativePath, e);
                    }
                }
            }
            return zipPath;
        } catch (IOException e) {
            getLog().warn(e);
            throw new MojoFailureException("Error while building " + zipName, e);
        }
    }

    public List<ResolvedArtifact> copyDependenciesInto(AssemblyContext assemblyContext, boolean failOnMissingDependencies, List<Dependency> nodes, Path toPath) throws MojoExecutionException {
        assemblyContext.getPaths().add(new PathSet(toPath));
        List<Path> destinations = new ArrayList<>();
        List<ResolvedArtifact> result = new ArrayList<>();
        for (Dependency node : nodes) {
            Artifact a = artifactFactory.createArtifactWithClassifier(node.getGroupId(), node.getArtifactId(), node.getVersion(), node.getType(), node.getClassifier());
            org.eclipse.aether.artifact.Artifact source = resolve.resolve(a);
            ResolvedArtifact ra = new ResolvedArtifact(source, isReactorProject(a));
            String name = ra.getFileName();
            Path destination = toPath.resolve(name);
            destinations.add(destination);
            try {
                internalCopy(failOnMissingDependencies, source.getFile(), destination, ra.isReactorProject());
                assemblyContext.addToLastPathSet(new ArtifactPathEntry(name, getAssemblyName(a.getArtifactId(), "AGENT", "EXPLODED")));
            } catch (IOException e) {
                getLog().warn("Error while copying " + source + " to " + destination, e);
            }
        }
        return result;
    }

    public AssemblyContext createAssemblyContext(String prefix, String suffix, Path root) {
        AssemblyContext assemblyContext = new AssemblyContext();
        assemblyContext.setName(getAssemblyName(getProject().getArtifactId(), prefix, suffix));
        assemblyContext.setRoot(root);
        return assemblyContext;
    }

    public String getAssemblyName(String artifactId, String prefix, String suffix) {
        if (suffix != null && !suffix.isBlank())
            suffix = "::" + suffix;
        else
            suffix = "";
        return "TC::" + prefix + "::" + artifactId + suffix;
    }

    public AssemblyContext createAssemblyContext(String prefix, Path root) {
        return createAssemblyContext(prefix, null, root);
    }
}