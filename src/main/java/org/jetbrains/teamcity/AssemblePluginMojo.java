package org.jetbrains.teamcity;

import com.google.common.collect.Lists;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;
import org.apache.maven.shared.dependency.graph.*;
import org.apache.maven.shared.dependency.graph.filter.AncestorOrSelfDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.AndDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.ArtifactDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.traversal.*;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.exists;
import static org.apache.maven.artifact.Artifact.SCOPE_COMPILE_PLUS_RUNTIME;

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
    @Parameter(defaultValue = "${project.build.finalName}", property = "outputName")
    private String zipName;

    @Parameter(defaultValue = "org.jetbrains.teamcity", property = "excludeGroupId")
    private String excludeGroupId;

    @Parameter(defaultValue = "*", property = "agent")
    private String agent;

    @Parameter(defaultValue = "*", property = "server")
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

    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    @Parameter(property = "unitTest", defaultValue = "${unitTest}")
    private boolean unitTest;

    @Parameter( defaultValue = "${localRepository}", readonly = true, required = true )
    private ArtifactRepository local;
    @Component
    private org.eclipse.aether.RepositorySystem repoSystem;

    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true )
    private List<RemoteRepository> repositories;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        verifyAndPrepareStructure();
        ArtifactFilter artifactFilter = createResolvingArtifactFilter(SCOPE_COMPILE_PLUS_RUNTIME);
        ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        try {
            DependencyNode rootNode = findRootNode(artifactFilter, buildingRequest);
            buildAgentPlugin(rootNode, agent);
            buildServerPlugin(rootNode, server);
        } catch (DependencyGraphBuilderException | DependencyCollectorBuilderException exception) {
            throw new MojoExecutionException("Cannot build project dependency graph", exception);
        }
        project.getAttachedArtifacts();
    }

    private DependencyNode findRootNode(ArtifactFilter artifactFilter, ProjectBuildingRequest buildingRequest) throws DependencyCollectorBuilderException, DependencyGraphBuilderException {
        DependencyNode rootNode;
        String dependencyTreeString;
        if (verbose) {
            rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, artifactFilter);
            dependencyTreeString = serializeDependencyTree(rootNode);
        } else {
            // non-verbose mode use dependency graph component, which gives consistent results with Maven version
            // running
            rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, artifactFilter);
            dependencyTreeString = serializeDependencyTree(rootNode);
        }
        getLog().warn(dependencyTreeString);

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

            ArtifactRequest req = new ArtifactRequest().setRepositories( this.repositories ).setArtifact( aetherArtifact );
            ArtifactResult resolutionResult;
            try {
                resolutionResult = this.repoSystem.resolveArtifact( this.repoSession, req );

            } catch( ArtifactResolutionException e ) {
                throw new MojoExecutionException( "Artifact " + artifactId + " could not be resolved.", e );
            }

            // The file should exists, but we never know.
            File file = resolutionResult.getArtifact().getFile();
            if( file == null || ! file.exists()) {
                getLog().warn( "Artifact " + artifactId + " has no attached file. Its content will not be copied in the target model directory." );
            }
            return file;
    }

    private void buildServerPlugin(DependencyNode rootNode, String server) {
    }

    public void buildAgentPlugin(DependencyNode rootNode, String agentSpec) throws MojoExecutionException {
        List<DependencyNode> nodes = getDependencyNodeList(rootNode, agentSpec);
        List<Artifact> reactorProjectList = reactorProjects.stream().flatMap(this::getArtifactList).collect(Collectors.toList());
        for (DependencyNode node: nodes) {
            File file = resolve(node.getArtifact());
            if (reactorProjectList.contains(node)) {
                System.out.println("Node is reactor dependency " + node.toNodeString());
            } else {
                System.out.println("Node is not reactor dependency " + node.toNodeString());
            }
        }
        System.out.println(nodes);
    }

    private Stream<Artifact> getArtifactList(MavenProject it) {
        return new ArrayList<Artifact>() {{
            add(it.getArtifact());
            addAll(it.getArtifacts());
        }}.stream();
    }

    private List<DependencyNode> getDependencyNodeList(DependencyNode rootNode, String agentSpec) {
        List<DependencyNode> nodes;
        if (Objects.equals("*", agentSpec) || agentSpec == null || agentSpec.isBlank()) {
            nodes = Collections.singletonList(rootNode);
        } else {
            List<String> patterns = Arrays.asList(agentSpec.split(","));
            CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
            DependencyNodeVisitor firstPassVisitor = new FilteringDependencyNodeVisitor(collectingVisitor, new ArtifactDependencyNodeFilter(new StrictPatternIncludesArtifactFilter(patterns)));
            rootNode.accept(firstPassVisitor);
            nodes = collectingVisitor.getNodes();
        }
        StringWriter writer = new StringWriter();
        CollectingDependencyNodeVisitor transitiveCollectingVisitor = new CollectingDependencyNodeVisitor();
        MultipleDependencyNodeVisitor mdnv = new MultipleDependencyNodeVisitor(Lists.newArrayList(transitiveCollectingVisitor, getSerializingDependencyNodeVisitor(writer)));
        FilteringDependencyNodeVisitor visitor = new FilteringDependencyNodeVisitor(mdnv, new DescendantOrSelfDependencyNodeFilter(nodes));
        rootNode.accept(visitor);
        getLog().warn(writer.toString());
        return transitiveCollectingVisitor.getNodes();
    }

    private void verifyAndPrepareStructure() throws MojoExecutionException {
        new File(String.valueOf(outputDirectory)).mkdirs();
        if (!exists(Path.of(pluginDescriptorPath)) && !unitTest)
            throw new MojoExecutionException(String.format("`pluginDescriptorPath` must point to teamcity plugin descriptor (%s).", pluginDescriptorPath));

    }

    private ArtifactFilter createResolvingArtifactFilter(String scope) {
        ArtifactFilter filter;

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
}
