package io.eele.collector.entities;

import com.sun.source.tree.*;

import java.util.ArrayList;
import java.util.List;

public class Node {
    public String packageName;
    public ClassTree classTree;
    public List<? extends Tree> tAgreementList = new ArrayList<>();
    public List<Tree> importTreeList = new ArrayList<>();
    public List<VariableTree> variableTreeList = new ArrayList<>();
    public List<MethodTree> methodTreeList = new ArrayList<>();
    public List<MethodInvocationTree> methodInvocationTreeList = new ArrayList<>();

    @Override
    public String toString() {
        return "Node{" +
                "classTree=" + classTree.getSimpleName().toString() +
                '}';
    }
}
