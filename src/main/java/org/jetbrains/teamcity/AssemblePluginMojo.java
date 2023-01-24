package org.jetbrains.teamcity;

import lombok.Getter;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifeCyclePluginAnalyzer;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.*;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
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
import org.apache.maven.shared.dependency.graph.traversal.*;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.teamcity.agent.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.apache.maven.artifact.Artifact.SCOPE_RUNTIME;

@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, aggregator = true, requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST, requiresDependencyCollection = ResolutionScope.TEST)
public class AssemblePluginMojo extends AbstractMojo {

    public static final String TEAMCITY_AGENT_PLUGIN_CLASSIFIER = "teamcity-agent-plugin";
    public static final String TEAMCITY_PLUGIN_CLASSIFIER = "teamcity-plugin";
    public static final String AGENT_SUBDIR = "agent";
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    private List<MavenProject> reactorProjects;
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
    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File projectBuildOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/teamcity")
    private File workDirectory;

    @Parameter(property = "ignoreExtraFilesIn")
    private String ignoreExtraFilesIn;

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
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystem}")
    private RepositorySystem repositorySystemParam;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;


    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repositories;

    @Parameter(defaultValue = "true", property = "failOnMissingDependencies")
    private boolean failOnMissingDependencies;



    @Component
    private LifecycleExecutor lifecycleExecutor;
    @Component
    private MojoExecution execution;
    @Component
    private PluginDescriptor pluginDescriptor;
    @Component
    PluginManager pluginManager;

    @Component
    ArchiverManager archiverManager;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private LifeCyclePluginAnalyzer lifeCyclePluginAnalyzer;

    @Parameter( defaultValue = "${project.build.outputTimestamp}" )
    private String outputTimestamp;

    @Component
    private Map<String, Archiver> archivers;
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    /**
     * TeamCity Agent configuration parameters.
     */
    @Parameter(property = "agent")
    @Getter
    private Agent agent = new Agent();

    /**
     * TeamCity Server configuration parameters.
     */
    @Parameter(property = "server")
    @Getter
    private Server server;

    @Getter
    private AgentPluginWorkflow agentPluginWorkflow;
    @Getter
    private ServerPluginWorkflow serverPluginWorkflow;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().warn("TeamCityAssemble start");
        setDefaultconfigurationValues();
        try {
            Path serverPluginRoot = Files.createDirectories(workDirectory.toPath().resolve("plugin").resolve(server.getPluginName()));
            ResolveUtil resolve = new ResolveUtil(getLog(), repoSystem, repositories, repoSession);
            WorkflowUtil util = new WorkflowUtil(getLog(), reactorProjects, project, serverPluginRoot, resolve, ignoreExtraFilesIn, tokens, artifactFactory, failOnMissingDependencies);
            DependencyNode rootNode = findRootNode(util);

            agentPluginWorkflow = new AgentPluginWorkflow(rootNode, agent, util, workDirectory.toPath());
            agentPluginWorkflow.execute();
            attachArtifacts(agentPluginWorkflow.getAttachedArtifacts());
            buildArtifact(agentPluginWorkflow.getAssemblyContext());

            serverPluginWorkflow = new ServerPluginWorkflow(rootNode, server, util, findPluginConfiguration(), project);
            serverPluginWorkflow.execute();
            createArchives(serverPluginWorkflow.getAttachedArchives());

            Path plugin = util.zipFile(serverPluginRoot, workDirectory.toPath(), server.getPluginName() + ".zip");
            projectHelper.attachArtifact(project, "zip", "teamcity-plugin", plugin.toFile());
        } catch (IOException e) {
            getLog().warn(e);
            throw new MojoFailureException("Error while assembly execution", e);
        }
    }

    private void setDefaultconfigurationValues() {
        agent.setDefaultValues(project, projectBuildOutputDirectory);
        server.setDefaultValues(project, projectBuildOutputDirectory);
    }

    private Optional<Plugin> findPluginConfiguration() {
        if (pluginDescriptor.getPlugin() != null)
            return Optional.of(pluginDescriptor.getPlugin());
        Optional<Plugin> p = project.getBuild().getPlugins().stream().filter(it -> match(it, this.pluginDescriptor)).findFirst();
        return p;
    }

    private boolean match(Plugin it, PluginDescriptor pluginDescriptor) {
        Artifact pluginArtifact = pluginDescriptor.getPluginArtifact();
        return Objects.equals(pluginArtifact.getGroupId(), it.getGroupId()) && Objects.equals(pluginArtifact.getArtifactId(), it.getArtifactId());
    }


    private void createArchives(List<ResultArchive> attachedArchives) throws MojoExecutionException {
        for (ResultArchive a: attachedArchives) {
            createArchive(a);
        }
    }

    private void createArchive(ResultArchive a) throws MojoExecutionException {
        MavenArchiver archiver = new MavenArchiver();
//        archiverManager.getArchiver("jar");
        archiver.setArchiver((JarArchiver) archivers.get(a.getType()));
        archiver.setOutputFile(a.getFile());
        archiver.configureReproducibleBuild(outputTimestamp);
        try {
            for (FileSet fs : a.getFileSets()) {
                archiver.getArchiver().addFileSet(fs);
            }
            archiver.createArchive(session, project, archive);
        } catch (Exception e) {
            throw new MojoExecutionException("Error assembling JAR " + a.getFile(), e);
        }

    }

    private void attachArtifacts(List<ResultArtifact> artifacts) {
        artifacts.forEach(it -> projectHelper.attachArtifact(project, it.getType(), it.getClassifier(), it.getFile()));
    }

    private void buildArtifact(AssemblyContext agentAssemblyContext) {

    }


    public List<Artifact> getAttachedArtifact() {
        return project.getAttachedArtifacts();
    }

    private DependencyNode findRootNode(WorkflowUtil util) throws MojoExecutionException {
        DependencyNode rootNode;
        try {
            ArtifactFilter artifactFilter = createResolvingArtifactFilter(SCOPE_RUNTIME);
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(project);

            rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, artifactFilter);
            String dependencyTreeString = serializeDependencyTree(rootNode, util);
            getLog().warn("Dependency Tree:\n" + dependencyTreeString);
        } catch (DependencyCollectorBuilderException  exception) {
            throw new MojoExecutionException("Cannot build project dependency graph", exception);
        }

        return rootNode;
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

    private String serializeDependencyTree(DependencyNode theRootNode, WorkflowUtil util) {
        StringWriter writer = new StringWriter();

        DependencyNodeVisitor visitor = util.getSerializingDependencyNodeVisitor(writer);

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


    public String getServerPluginName() {
        return server.getPluginName();
    }


    public String getCustomAgentPluginName() {
        return project.getArtifactId().equalsIgnoreCase(getAgentPluginName()) ? null : getAgentPluginName();
    }
    public void setFailOnMissingDependencies(boolean b) {
        this.failOnMissingDependencies = b;
    }

    public String getAgentPluginName() {
        if (this.agent.getPluginName() == null) {
            return server.getPluginName();
        }
        return agent.getPluginName();
    }
}
