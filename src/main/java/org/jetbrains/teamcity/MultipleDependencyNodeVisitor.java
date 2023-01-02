package org.jetbrains.teamcity;

import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

import java.util.List;

public class MultipleDependencyNodeVisitor implements DependencyNodeVisitor {
    private final List<DependencyNodeVisitor> visitors;

    public MultipleDependencyNodeVisitor(List<DependencyNodeVisitor> visitors) {
        this.visitors = visitors;
    }

    @Override
    public boolean visit(DependencyNode node) {
        visitors.forEach(it -> it.visit(node));
        return true;
    }

    @Override
    public boolean endVisit(DependencyNode node) {
        visitors.forEach(it -> it.endVisit(node));
        return true;
    }
}
