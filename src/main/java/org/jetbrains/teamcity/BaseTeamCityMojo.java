package org.jetbrains.teamcity;

import com.google.common.base.Strings;
import lombok.Getter;
import lombok.Setter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.filter.ScopeArtifactFilter;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.BuildingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.teamcity.agent.*;
import org.jetbrains.teamcity.data.ArtifactNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.apache.maven.artifact.Artifact.SCOPE_RUNTIME;
import static org.jetbrains.teamcity.data.ArtifactNode.ArtifactNodeType.*;

@Getter
@Setter
public abstract class BaseTeamCityMojo extends AbstractMojo {
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;
    @Component
    private RepositorySystem repoSystem;
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private ArtifactFactory artifactFactory;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File projectBuildOutputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/teamcity")
    private File workDirectory;

    @Parameter(property = "tokens", defaultValue = "standard")
    private String tokens;

    @Component(hint = "default")
    private DependencyCollectorBuilder dependencyCollectorBuilder;

    @Component
    private MavenProjectHelper mavenProjectHelper;

    @Parameter( defaultValue = "${project.build.outputTimestamp}" )
    private String outputTimestamp;


    public WorkflowUtil getWorkflowUtil() throws IOException {
        ResolveUtil resolve = new ResolveUtil(getLog(), repoSystem, repositories, repoSession);
        return new WorkflowUtil(getLog(), reactorProjects, project, workDirectory.toPath(), resolve, tokens, artifactFactory);
    }

    protected List<Path> buildArtifact(List<AssemblyContext> assemblyContexts) {
        getLog().debug("Building " + assemblyContexts);
        List<Path> generatedArtifacts = new ArrayList<>();
        for (AssemblyContext ac: assemblyContexts) {
            try {
                generatedArtifacts.add(generateAssembly(ac));
            } catch (Exception e) {
                getLog().warn("Error while assembling " + assemblyContexts, e);
            }
        }
        return generatedArtifacts;
    }

    public Path generateAssembly(AssemblyContext ac) throws ParserConfigurationException, IOException, TransformerException {
        Path intellijProject = project.getBasedir().toPath();
        if (project.getParent() != null) {
            intellijProject = project.getParent().getBasedir().toPath();
        }
        IJArtifactParameters params = new IJArtifactParameters();
        params.setArtifactPath(intellijProject.relativize(ac.getRoot()));
        params.setProjectRoot(intellijProject);

        ArtifactNode root = new ArtifactNode("", DIR, null);
        for (PathSet ps: ac.getPaths()) {
            ArtifactNode location = getArtifactNode(root, ps);
            for (PathEntry pe: ps.getPathEntryList()) {
                ArtifactNode.ArtifactNodeType type = FILE;
                if (pe instanceof DependencyPathEntry)
                    type = DEPENDENCY;
                else if (pe instanceof FilePathEntry)
                    type = FILE;
                else if (pe instanceof DirCopyPathEntry)
                    type = DIR_COPY;
                else if (pe instanceof ArtifactPathEntry)
                    type = ARTIFACT;
                location.getChilds().add(new ArtifactNode(pe.getName(), type, pe));
            }
        }
        Path destinationFile = intellijProject.resolve(".idea").resolve("artifacts").resolve(ac.getName().replaceAll("[^\\w\\d]", "_") + ".xml");
        getWorkflowUtil().createDir(destinationFile.getParent());
        serializeIntoXml(root, ac, params, destinationFile);
        return destinationFile;
    }

    private void serializeIntoXml(ArtifactNode root, AssemblyContext ac, IJArtifactParameters params, Path destinationFile) throws ParserConfigurationException, TransformerException, IOException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // root elements
        Document doc = docBuilder.newDocument();

        Element rootElement = doc.createElement("component");
        rootElement.setAttribute("name", "ArtifactManager");
        doc.appendChild(rootElement);
        Element artifact = doc.createElement("artifact");
        artifact.setAttribute("name", ac.getName());
        rootElement.appendChild(artifact);
        Element outputPath = doc.createElement("output-path");
        outputPath.setTextContent("$PROJECT_DIR$/"+params.getArtifactPath());
        artifact.appendChild(outputPath);
        Element aRoot = doc.createElement("root");
        aRoot.setAttribute("id", "root");
        artifact.appendChild(aRoot);
        genTree(doc, aRoot, root, params.getProjectRoot());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeXml(doc, baos);
        baos.close();
        String xmlContent = baos.toString(StandardCharsets.UTF_8);
        Files.writeString(destinationFile, xmlContent, StandardCharsets.UTF_8);
    }

    private static void writeXml(Document doc,
                                 OutputStream output)
            throws TransformerException {

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(output);

        transformer.transform(source, result);

    }
    private void genTree(Document doc, Element aRoot, ArtifactNode root, Path ideaProjectRoot) {
        for (ArtifactNode artifactNode: root.getChilds()) {
            Element element = createElement(doc, aRoot, artifactNode, ideaProjectRoot);
            genTree(doc, element, artifactNode, ideaProjectRoot);
        }
    }

    private Element createElement(Document doc, Element parent, ArtifactNode artifactNode, Path ideaProjectRoot) {
        Element element = doc.createElement("element");
        parent.appendChild(element);
        if (artifactNode.getType() == DIR) {
            setIdName(element, "directory", artifactNode.getPath());
        } else if (artifactNode.getType() == DEPENDENCY) {
            if (artifactNode.getInfo() instanceof DependencyPathEntry) {
                DependencyPathEntry dpe = (DependencyPathEntry) artifactNode.getInfo();
                if (dpe.isReactorProject()) {
                    setIdName(element, "archive", artifactNode.getPath());
                    Element moduleOutput = doc.createElement("element");
                    moduleOutput.setAttribute("id", "module-output");
                    moduleOutput.setAttribute("name", dpe.getArtifact().getArtifactId());
                    element.appendChild(moduleOutput);
                } else {
                    setIdName(element, "library", getMavenLibraryName(dpe.getArtifact()));
                    element.setAttribute("level", "project");
                }
            }
        } else if (artifactNode.getType() == ARTIFACT) {
            if (artifactNode.getInfo() instanceof ArtifactPathEntry) {
                ArtifactPathEntry ape = (ArtifactPathEntry) artifactNode.getInfo();
                setIdName(element, "archive", ape.getName());
                Element artifact = doc.createElement("element");
                setIdName(artifact, "artifact", null);
                artifact.setAttribute("artifact-name", ape.getArtifactName());
                element.appendChild(artifact);
            }
        } else if (artifactNode.getType() == FILE) {
            if (artifactNode.getInfo() instanceof FilePathEntry) {
                setIdName(element, "file-copy", null);
                FilePathEntry fpe = (FilePathEntry) artifactNode.getInfo();
                element.setAttribute("path", "$PROJECT_DIR$/"+relativeTo(fpe.getResolved(), ideaProjectRoot));
                if (fpe.getName() != null)
                    element.setAttribute("output-file-name", fpe.getName());

            }
        }
        return element;
    }

    private String relativeTo(Path resolved, Path ideaProjectRoot) {
        return ideaProjectRoot.relativize(resolved).toString();
    }

    private void setIdName(Element element, String id, String name) {
        if (id != null)
            element.setAttribute("id", id);
        if (name != null)
            element.setAttribute("name", name);
    }

    private String getMavenLibraryName(Artifact artifact) {
        return String.format("Maven: %s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    private ArtifactNode getArtifactNode(ArtifactNode root, PathSet ps) {
        ArtifactNode current = root;
        for(int i = 0; i < ps.getDir().getNameCount(); i++) {
           String name = ps.getDir().getName(i).toString();
           current = findOrCreateDir(current, name);
        }
        return current;
    }

    private ArtifactNode findOrCreateDir(ArtifactNode current, String name) {
        for (ArtifactNode an: current.getChilds()) {
            if (Objects.equals(an.getPath(), name)) {
                return an;
            }
        }
        if (!Strings.isNullOrEmpty(name)) { // skip empty directories
            ArtifactNode an = new ArtifactNode(name, DIR, null);
            current.getChilds().add(an);
            return an;
        } else
            return current;
    }


    protected void attachArtifacts(List<ResultArtifact> artifacts) {
        artifacts.forEach(it -> mavenProjectHelper.attachArtifact(getProject(), it.getType(), it.getClassifier(), it.getFile().toFile()));
    }

    protected DependencyNode findRootNode(WorkflowUtil util) throws MojoExecutionException {
        DependencyNode rootNode;
        try {
            ArtifactFilter artifactFilter = createResolvingArtifactFilter(SCOPE_RUNTIME);
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(getProject());

            rootNode = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, artifactFilter);
            String dependencyTreeString = serializeDependencyTree(rootNode, util);
            getLog().warn("Dependency Tree:\n" + dependencyTreeString);
        } catch (DependencyCollectorBuilderException exception) {
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

        theRootNode.accept(visitor);

        return writer.toString();
    }
}
