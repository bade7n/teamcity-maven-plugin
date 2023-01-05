package org.jetbrains.teamcity;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Organization;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.ScopeArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;
import org.apache.maven.shared.dependency.graph.*;
import org.apache.maven.shared.dependency.graph.filter.AncestorOrSelfDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.AndDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.ArtifactDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.traversal.*;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
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

    @Parameter(defaultValue = "org.jetbrains.teamcity", property = "excludeGroupId")
    private String excludeGroupId;

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

    @Parameter(defaultValue = "false", property = "useSeparateClassloader")
    private boolean useSeparateClassloader;

    @Parameter(defaultValue = "false", property = "nodeResponsibilitiesAware")
    private boolean nodeResponsibilitiesAware;

    /**
     *
     */
    @Parameter(property = "pluginDependencies")
    private List<String> pluginDependencies;

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
        zipIt();
    }

    private void zipIt() throws MojoFailureException {
        zipFile(agentPath, pluginRoot.resolve("agent"), pluginName + ".zip");
        zipFile(pluginRoot, outputDirectory.toPath(), pluginName + ".zip");
    }

    private void zipFile(Path source, Path baseDir, String zipName) throws MojoFailureException {
        try {
            Path agentZipPath = Files.createDirectories(baseDir).resolve(zipName);
            if (agentZipPath.toFile().exists()) {
                boolean deleted = agentZipPath.toFile().delete();
                if (!deleted) {
                    getLog().warn("Failed to delete " + agentZipPath);
                }
            }
            URI uri = URI.create("jar:file:" + agentZipPath);
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
            String dependencyTreeString;
//            if (verbose) {
                rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, artifactFilter);
                dependencyTreeString = serializeDependencyTree(rootNode);
//            } else {
//                // non-verbose mode use dependency graph component, which gives consistent results with Maven version
//                // running
//                rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, artifactFilter);
//                dependencyTreeString = serializeDependencyTree(rootNode);
//            }
            getLog().warn("Dependency Tree:\n" + dependencyTreeString);
//        } catch (DependencyGraphBuilderException | DependencyCollectorBuilderException exception) {
        } catch (DependencyCollectorBuilderException exception) {
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
        copyTransitiveDependenciesInto(rootNode, serverSpec, serverPath);
    }

    private void copyTransitiveDependenciesInto(DependencyNode rootNode, String serverSpec, Path toPath) throws MojoExecutionException {
        List<DependencyNode> nodes = getDependencyNodeList(rootNode, serverSpec);
        for (DependencyNode node : nodes) {
            File source = resolve(node.getArtifact());
            Path destination = toPath.resolve(source.getName());
            try {
                internalCopy(source, destination, isReactorProject(node));
            } catch (IOException e) {
                getLog().warn("Error while copying " + source + " to " + destination, e);
            }
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
        copyTransitiveDependenciesInto(rootNode, agentSpec, agentPath);
    }

    private void internalCopy(File source, Path destination, boolean isReactorProject) throws IOException {
        try {
            if (!destination.toFile().exists() || isReactorProject || destination.toFile().length() != source.length()) {
                Files.copy(source.toPath(), destination);
            }
        } catch (NoSuchFileException e) {
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

    private List<DependencyNode> getDependencyNodeList(DependencyNode rootNode, String spec) {
        List<DependencyNode> nodes;
        if (Objects.equals("*", spec) || spec == null || spec.isBlank()) {
            nodes = Collections.singletonList(rootNode);
        } else {
            List<String> patterns = Arrays.asList(spec.split(","));
            CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
            DependencyNodeVisitor firstPassVisitor = new FilteringDependencyNodeVisitor(collectingVisitor, new ArtifactDependencyNodeFilter(new StrictPatternIncludesArtifactFilter(patterns)));
            rootNode.accept(firstPassVisitor);
            nodes = collectingVisitor.getNodes();
        }
        StringWriter writer = new StringWriter();
        CollectingDependencyNodeVisitor transitiveCollectingVisitor = new CollectingDependencyNodeVisitor();
        MultipleDependencyNodeVisitor mdnv = new MultipleDependencyNodeVisitor(Arrays.asList(transitiveCollectingVisitor, getSerializingDependencyNodeVisitor(writer)));
        FilteringDependencyNodeVisitor visitor = new FilteringDependencyNodeVisitor(mdnv, new DescendantOrSelfDependencyNodeFilter(nodes));
        rootNode.accept(visitor);
        getLog().info("Dependencies according to spec " + spec + ":\n"  + writer);
        return transitiveCollectingVisitor.getNodes();
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
            DependencyNodeVisitor firstPassVisitor = new FilteringDependencyNodeVisitor(collectingVisitor, filter);
            theRootNode.accept(firstPassVisitor);

            DependencyNodeFilter secondPassFilter =
                    new AncestorOrSelfDependencyNodeFilter(collectingVisitor.getNodes());
            visitor = new FilteringDependencyNodeVisitor(visitor, secondPassFilter);
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
}
