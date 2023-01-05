package org.jetbrains.teamcity;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.*;
import org.apache.maven.shared.dependency.graph.*;
import org.apache.maven.shared.dependency.graph.filter.AncestorOrSelfDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.AndDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.ArtifactDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.internal.ConflictData;
import org.apache.maven.shared.dependency.graph.traversal.*;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.exists;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.maven.artifact.Artifact.SCOPE_RUNTIME;

@Mojo(name = "build", aggregator = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST, requiresDependencyCollection = ResolutionScope.TEST)
public class AssemblePluginMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystem}")
    private RepositorySystem repositorySystemParam;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    private List<RemoteRepository> projectRepos;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
    private MojoExecution mojoExecution;
    /**
     * Input directory which contains the files that will zipped. Defaults to the
     * build output directory.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "inputDir")
    private File inputDirectory;

    /**
     * Directory which will contain the generated zip file. Defaults to the Maven
     * build directory.
     */
    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir")
    private File outputDirectory;

    /**
     * Final name of the zip file. Defaults to the build final name attribute.
     */
    @Parameter(defaultValue = "${project.artifactId}", property = "pluginName")
    private String pluginName;

    @Parameter(defaultValue = "", property = "agent")
    private String agent;

    @Parameter(defaultValue = "", property = "server")
    private String server;

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/teamcity-plugin.xml", property = "pluginDescriptorPath")
    private String pluginDescriptorPath;

    @Component(hint = "default")
    private DependencyCollectorBuilder dependencyCollectorBuilder;

    @Component(hint = "default")
    private DependencyGraphBuilder dependencyGraphBuilder;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(property = "tokens", defaultValue = "standard")
    private String tokens;

    @Parameter(property = "includes")
    private String includes;

    @Parameter(property = "excludes")
    private String excludes;

    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    private ArtifactRepository local;
    @Component
    private org.eclipse.aether.RepositorySystem repoSystem;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repositories;

    @Parameter(defaultValue = "false", property = "failOnMissingServerDescriptor")
    private boolean failOnMissingServerDescriptor;

    @Parameter(defaultValue = "false", property = "failOnMissingAgentDescriptor")
    private boolean failOnMissingAgentDescriptor;

    @Parameter(defaultValue = "true", property = "failOnMissingDependencies")
    private boolean failOnMissingDependencies;

    @Parameter(defaultValue = "false", property = "useSeparateClassloader")
    private boolean useSeparateClassloader;

    @Parameter(defaultValue = "false", property = "nodeResponsibilitiesAware")
    private boolean nodeResponsibilitiesAware;

    /**
     *
     */
    @Parameter(property = "pluginDependencies")
    private List<String> pluginDependencies;

    @Parameter(defaultValue = "org.jetbrains.teamcity", property = "agentExclusions")
    private List<String> agentExclusions;

    @Parameter(defaultValue = "org.jetbrains.teamcity", property = "serverExclusions")
    private List<String> serverExclusions;

    private Path pluginRoot;
    private Path agentPath;
    private List<Artifact> reactorProjectList;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().warn("TeamCityAssemble start");
        try {
            verifyAndPrepareStructure();
            if (agent != null && !agent.isBlank())
                buildAgentPlugin(agent);
            if (server != null && !server.isBlank())
                buildServerPlugin(server);
        } catch (IOException e) {
            throw new MojoFailureException(e);
        }
        Path plugin = zipIt();
        projectHelper.attachArtifact(project, "zip", "teamcity-plugin", plugin.toFile());
    }

    public List<Artifact> getAttachedArtifact() {
        return project.getAttachedArtifacts();
    }

    private Path zipIt() throws MojoFailureException {
        zipFile(agentPath, pluginRoot.resolve("agent"), pluginName + ".zip");
        Path plugin = zipFile(pluginRoot, outputDirectory.toPath(), pluginName + ".zip");
        return plugin;
    }

    private Path zipFile(Path source, Path baseDir, String zipName) throws MojoFailureException {
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
            throw new MojoFailureException(e);
        }
    }

    private DependencyNode findRootNode() throws MojoExecutionException {
        DependencyNode rootNode;
        try {
            ArtifactFilter artifactFilter = createResolvingArtifactFilter(SCOPE_RUNTIME);
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(project);

            rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, artifactFilter);
            String dependencyTreeString = serializeDependencyTree(rootNode);
            getLog().warn("Dependency Tree:\n" + dependencyTreeString);
        } catch (DependencyCollectorBuilderException  exception) {
            throw new MojoExecutionException("Cannot build project dependency graph", exception);
        }

        return rootNode;
    }

    private File resolve(Artifact unresolvedArtifact) throws MojoExecutionException {
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
        return file;
    }

    private void buildServerPlugin(String serverSpec) throws MojoExecutionException {
        DependencyNode rootNode = findRootNode();
        Path serverPath = createDir(pluginRoot.resolve("server"));
        copyTransitiveDependenciesInto(rootNode, serverSpec, serverPath, serverExclusions);
    }

    private void copyTransitiveDependenciesInto(DependencyNode rootNode, String spec, Path toPath, List<String> excludes) throws MojoExecutionException {
        List<DependencyNode> nodes = getDependencyNodeList(rootNode, spec, excludes);
        List<Path> destinations = new ArrayList<>();
        for (DependencyNode node : nodes) {
            File source = resolve(node.getArtifact());
            Path destination = toPath.resolve(source.getName());
            destinations.add(destination);
            try {
                internalCopy(source, destination, isReactorProject(node));
            } catch (IOException e) {
                getLog().warn("Error while copying " + source + " to " + destination, e);
            }
        }
        try {
            List<Path> existingFiles = Files.walk(toPath).filter(it -> !it.equals(toPath)).filter(it -> !destinations.contains(it)).collect(Collectors.toList());
            if (!existingFiles.isEmpty()) {
                getLog().warn("Found extra files in " + toPath + " removing (" + existingFiles + ")");
                existingFiles.forEach(it -> it.toFile().delete());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path createDir(Path serverUnpacked) {
        try {
            return Files.createDirectories(Files.createDirectories(serverUnpacked));
        } catch (IOException e) {
            getLog().warn("Error while creation " + serverUnpacked, e);
            return serverUnpacked;
        }
    }

    public void buildAgentPlugin(String agentSpec) throws MojoExecutionException {
        DependencyNode rootNode = findRootNode();

        agentPath  = createDir(outputDirectory.toPath().resolve("teamcity-agent"));

        /**
         * pluginRoot/
         * |-agent/
         * | |-plugin_name.zip
         * |   |-plugin_name/
         * |     |-dependencies.jar
         * |-server
         * |-teamcity-plugin.xml
         */
        copyTransitiveDependenciesInto(rootNode, agentSpec, agentPath, agentExclusions);
    }

    private void internalCopy(File source, Path destination, boolean isReactorProject) throws IOException {
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
        } catch (IOException e) {
            getLog().warn(e);
            if (source.exists())
                Files.copy(source.toPath(), destination, REPLACE_EXISTING);
        }
    }

    private boolean isReactorProject(DependencyNode node) {
        return reactorProjectList.contains(node.getArtifact());
    }

    private Stream<Artifact> getArtifactList(MavenProject it) {
        return new ArrayList<Artifact>() {{
            add(it.getArtifact());
            addAll(it.getArtifacts());
        }}.stream();
    }

    private List<DependencyNode> getDependencyNodeList(DependencyNode rootNode, String spec, List<String> exclusions) {
        List<DependencyNode> nodes;
        // looking for the nodes specified by user
        if (Objects.equals("*", spec) || spec == null || spec.isBlank()) {
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
        SkipFilteringDependencyNodeVisitor visitor = new SkipFilteringDependencyNodeVisitor(mdnv, exclusionFilter);
        nodes.forEach(it -> it.accept(visitor));
        getLog().info("Dependencies according to spec " + spec + ":\n"  + writer);
        List<DependencyNode> nodes1 = transitiveCollectingVisitor.getNodes();
        // now conflicted dependencies might be in list, need to find them and resolve to the right version
        List<DependencyNode> result = new ArrayList<>();
        for (DependencyNode node: nodes1) {
            ConflictData cd = getPrivateField(node);
            if (cd != null && cd.getWinnerVersion() != null) {
                List<DependencyNode> substitutions = findSubstitutions(rootNode, node, cd.getWinnerVersion());
                CollectingDependencyNodeVisitor collector = new CollectingDependencyNodeVisitor();
                substitutions.forEach(it -> it.accept(collector));
                result.addAll(collector.getNodes());
            } else {
                result.add(node);
            }
        }
        return result;
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

    private List<DependencyNode> collectNodes(DependencyNode rootNode, ArtifactFilter artifactFilter) {
        CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
        DependencyNodeVisitor firstPassVisitor = new FilteringDependencyNodeVisitor(collectingVisitor,
                new ArtifactDependencyNodeFilter(artifactFilter));
        rootNode.accept(firstPassVisitor);
        return collectingVisitor.getNodes();
    }

    private void verifyAndPrepareStructure() throws MojoExecutionException, IOException {
        pluginRoot = Files.createDirectories(outputDirectory.toPath().resolve(pluginName));
        reactorProjectList = reactorProjects.stream().flatMap(this::getArtifactList).collect(Collectors.toList());

        Path destination = pluginRoot.resolve("teamcity-plugin.xml");
        if (!exists(Path.of(pluginDescriptorPath))) {
            if (failOnMissingServerDescriptor)
                throw new MojoExecutionException(String.format("`pluginDescriptorPath` must point to teamcity plugin descriptor (%s).", pluginDescriptorPath));
            else {
                createServerDescriptor(destination);
            }
        } else {
            Files.copy(Path.of(pluginDescriptorPath), destination);
        }
    }

    private void createServerDescriptor(Path destination) throws IOException {
        File serverDescriptor = destination.toFile();
            try (FileWriter fw = new FileWriter(serverDescriptor)) {
                VelocityContext context = new VelocityContext();
                VelocityEngine ve = new VelocityEngine();
                ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
                ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
                context.put("mojo", this);
                context.put("project", project);
                Template template = ve.getTemplate("teamcity-server-plugin.vm");
                template.merge(context, fw);
            }
    }

    private ArtifactFilter createResolvingArtifactFilter(String scope) {
        ScopeArtifactFilter filter;

        // filter scope
        if (scope != null) {
            getLog().debug("+ Resolving dependency tree for scope '" + scope + "'");
            filter = new ScopeArtifactFilter(scope);
        } else {
            filter = null;
        }

        return filter;
    }

    private String serializeDependencyTree(DependencyNode theRootNode) {
        StringWriter writer = new StringWriter();

        DependencyNodeVisitor visitor = getSerializingDependencyNodeVisitor(writer);

        // TODO: remove the need for this when the serializer can calculate last nodes from visitor calls only
        visitor = new BuildingDependencyNodeVisitor(visitor);

        DependencyNodeFilter filter = createDependencyNodeFilter();

        if (filter != null) {
            CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
            DependencyNodeVisitor firstPassVisitor = new SkipFilteringDependencyNodeVisitor(collectingVisitor, filter);
            theRootNode.accept(firstPassVisitor);

            DependencyNodeFilter secondPassFilter =
                    new AncestorOrSelfDependencyNodeFilter(collectingVisitor.getNodes());
            visitor = new SkipFilteringDependencyNodeVisitor(visitor, secondPassFilter);
        }

        theRootNode.accept(visitor);

        return writer.toString();
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

    private DependencyNodeFilter createDependencyNodeFilter() {
        List<DependencyNodeFilter> filters = new ArrayList<>();

        // filter includes
        if (includes != null) {
            List<String> patterns = Arrays.asList(includes.split(","));

            getLog().debug("+ Filtering dependency tree by artifact include patterns: " + patterns);

            ArtifactFilter artifactFilter = new StrictPatternIncludesArtifactFilter(patterns);
            filters.add(new ArtifactDependencyNodeFilter(artifactFilter));
        }

        // filter excludes
        if (excludes != null) {
            List<String> patterns = Arrays.asList(excludes.split(","));

            getLog().debug("+ Filtering dependency tree by artifact exclude patterns: " + patterns);

            ArtifactFilter artifactFilter = new StrictPatternExcludesArtifactFilter(patterns);
            filters.add(new ArtifactDependencyNodeFilter(artifactFilter));
        }

        return filters.isEmpty() ? null : new AndDependencyNodeFilter(filters);
    }

    public List<String> getPluginDependencies() {
        return pluginDependencies;
    }

    public boolean isNodeResponsibilitiesAware() {
        return nodeResponsibilitiesAware;
    }

    public boolean isUseSeparateClassloader() {
        return useSeparateClassloader;
    }

    public String getPluginName() {
        return pluginName;
    }

    public Path getAgentPath() {
        return agentPath;
    }

    public void setFailOnMissingDependencies(boolean b) {
        this.failOnMissingDependencies = b;
    }
}
