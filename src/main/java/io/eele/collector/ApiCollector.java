package io.eele.collector;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import io.eele.collector.components.FolderFileScanner;
import io.eele.collector.components.XmlReader;
import io.eele.collector.entities.CallerMethod;
import io.eele.collector.entities.Node;
import org.apache.commons.lang3.StringUtils;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApiCollector {

    public final static String BASE_DIR_PATH = "";
    public final static String OUTPUT_FILE_PATH = "test.csv";

    public static void main(String[] args) throws IOException {
        // 获取JavaCompiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // DiagnosticListener用于获取Diagnostic信息，Diagnostic信息包括：错误，警告和说明性信息
        MyDiagnosticListener diagnosticListener = new MyDiagnosticListener();
        // JavaFileManager:用于管理与工具有关的所有文件，这里的文件可以是内存数据，也可以是SOcket数据，而不仅仅是磁盘文件。
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnosticListener, Locale.ENGLISH, StandardCharsets.UTF_8);
        // JavaFileObjects: 是java源码文件(.java)和class文件(.class)的抽象
        Iterable<? extends JavaFileObject> javaFileObjects = fileManager.getJavaFileObjects(FolderFileScanner
                .scanFilesWithNoRecursion(BASE_DIR_PATH)
                .stream().filter(o -> o.endsWith(".java")).toArray(String[]::new));
        // 编译任务
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticListener, null, null, javaFileObjects);
        JavacTask javacTask = (JavacTask) task;
        List<Node> nodeList = new ArrayList<>();
        final boolean[] start = {true};
        javacTask.parse().forEach(o -> {
            o.accept(new TreeScanner<Void, List<Node>>() {
                @Override
                public Void visitVariable(VariableTree variableTree, List<Node> list) {
                    list.get(list.size() - 1).variableTreeList.add(variableTree);
                    return super.visitVariable(variableTree, list);
                }


                @Override
                public Void visitClass(ClassTree classTree, List<Node> list) {
                    start[0] = true;
                    list.get(list.size() - 1).classTree = classTree;
                    return super.visitClass(classTree, list);
                }

                @Override
                public Void visitParameterizedType(ParameterizedTypeTree parameterizedTypeTree, List<Node> list) {
                    if (parameterizedTypeTree.getTypeArguments().stream().noneMatch(o -> o.toString().contains("Dao") || o.toString().contains("Mapper"))) return super.visitParameterizedType(parameterizedTypeTree, list);
                    list.get(list.size() - 1).tAgreementList = parameterizedTypeTree.getTypeArguments();
                    return super.visitParameterizedType(parameterizedTypeTree, list);
                }


                @Override
                public Void visitMethod(MethodTree methodTree, List<Node> list) {
                    list.get(list.size() - 1).methodTreeList.add(methodTree);
                    return super.visitMethod(methodTree, list);
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree methodInvocationTree, List<Node> list) {
                    list.get(list.size() - 1).methodInvocationTreeList.add(methodInvocationTree);
                    return super.visitMethodInvocation(methodInvocationTree, list);
                }

                @Override
                public Void visitImport(ImportTree importTree, List<Node> list) {
                    list.get(list.size() - 1).importTreeList.add(importTree.getQualifiedIdentifier());
                    return super.visitImport(importTree, list);
                }

                @Override
                public Void visitCompilationUnit(CompilationUnitTree compilationUnitTree, List<Node> list) {
                    if (start[0]) {
                        list.add(new Node());
                        start[0] = false;
                    }
                    list.get(list.size() - 1).packageName = compilationUnitTree.getPackageName().toString();
                    return super.visitCompilationUnit(compilationUnitTree, list);
                }
            }, nodeList);
        });

        callerMethods = XmlReader.getMappers(Arrays.asList(
                "Table1",
                "Table2"
        ));
        callerMethods.forEach(o -> getCallerMethodsFromMethodInvocation(o, nodeList));
        controllerMethods = controllerMethods.stream().sorted(Comparator.comparing(CallerMethod::getClassName).thenComparing(CallerMethod::getMethodName)).collect(Collectors.toList());
        Map<String, List<CallerMethod>> callerMethodMap = controllerMethods.stream().collect(Collectors.groupingBy(CallerMethod::getUri));
//        System.out.println(JSON.toJSONString(controllerMethods, SerializerFeature.PrettyFormat));

        PrintWriter printWriter = new PrintWriter(new File(OUTPUT_FILE_PATH));
        final int[] i = {1};
//        controllerMethods.forEach(c -> {
//            printWriter.println(i[0]++ + "," + "" + ",\"" + String.join("\n", c.getTableNames()) + "\"," + c.getUri() + "," + c.getClassName() + "#" + c.getMethodName() + "," + "" + "," + "" + ",\"" + c.getSql().replaceAll("\"", "\"\"") + "\"," + "" + ",");
//        });
        callerMethodMap.keySet().stream().sorted().forEach(uri -> {
            printWriter.println(
                    i[0]++ + "," +
                    "" + ",\"" +
                    callerMethodMap.getOrDefault(uri, new ArrayList<>()).stream().map(c -> String.join("\n", c.getTableNames())).distinct().collect(Collectors.joining("\n")) + "\"," +
                    uri + "," +
                    callerMethodMap.getOrDefault(uri, new ArrayList<>()).stream().findFirst().orElse(new CallerMethod()).getClassName() + "#" + callerMethodMap.getOrDefault(uri, new ArrayList<>()).stream().findFirst().orElse(new CallerMethod()).getMethodName() + "," +
                    "" + "," +
                    "" + ",\"" +
                    callerMethodMap.getOrDefault(uri, new ArrayList<>()).stream().map(CallerMethod::getSql).distinct().collect(Collectors.joining("\n\n")).replaceAll("\"", "\"\"") + "\"," +
                    "" + "," +
                    ""
            );
        });
        printWriter.close();
    }

    static List<CallerMethod> callerMethods;
    static List<CallerMethod> controllerMethods = new ArrayList<>();

    static void getCallerMethodsFromMethodInvocation(CallerMethod methodInvocation, List<Node> nodeList) {
        List<CallerMethod> list = new ArrayList<>();
        Node curNode = nodeList.stream().filter(n -> methodInvocation.getClassName().endsWith(n.packageName + "." + n.classTree.getSimpleName().toString())).findFirst().orElse(null);
        if (curNode == null) return;

        // 继承/实现关系
        nodeList.stream().filter(n ->
                (curNode.classTree.getExtendsClause() != null && curNode.classTree.getExtendsClause().toString().replaceAll("( )*<.*>", "").equals(n.classTree.getSimpleName().toString())) ||
                (curNode.classTree.getImplementsClause() != null &&
                 curNode.classTree.getImplementsClause().size() > 0 &&
                 curNode.classTree.getImplementsClause().stream().anyMatch(i -> i.toString().replaceAll("( )*<.*>", "").equals(n.classTree.getSimpleName().toString())))).forEach(n -> {
            CallerMethod callerMethod = new CallerMethod();
            callerMethod.setClassName(n.packageName + "." + n.classTree.getSimpleName().toString());
            callerMethod.setSql(methodInvocation.getSql());
            callerMethod.setTableNames(methodInvocation.getTableNames());
            callerMethod.setMethodName(methodInvocation.getMethodName());
            if (StringUtils.isNotBlank(n.classTree.getSimpleName().toString()) && Stream.of("BaseController", "BaseService", "BaseDao").noneMatch(c -> callerMethod.getClassName().contains(c))) {
                list.add(callerMethod);
            }
        });

        // 不同类 方法调用
        nodeList.forEach(n -> n.variableTreeList.stream().filter(v -> v.getType() != null).forEach(v -> {
            String varTypeName = n.importTreeList.stream().filter(i -> i.toString().endsWith(v.getType().toString())).map(Object::toString).findFirst().orElse("");
            if (varTypeName.equals(methodInvocation.getClassName())) {
                n.methodTreeList.stream().filter(m -> m.getBody() != null && (
                        m.getBody().getStatements().toString().contains(v.getName() + "." + methodInvocation.getMethodName()))).forEach(m -> {
                    CallerMethod callerMethod = new CallerMethod();
                    callerMethod.setClassName(n.packageName + "." + n.classTree.getSimpleName().toString());
                    callerMethod.setSql(methodInvocation.getSql());
                    callerMethod.setTableNames(methodInvocation.getTableNames());
                    callerMethod.setMethodName(m.getName().toString());
                    if (StringUtils.isNotBlank(n.classTree.getSimpleName().toString()) && Stream.of("BaseController", "BaseService", "BaseDao").noneMatch(c -> callerMethod.getClassName().contains(c))) {
                        list.add(callerMethod);
                    }
                });
            } else if (methodInvocation.getClassName().endsWith("Dao")) {
                n.methodTreeList.stream().filter(m -> m.getBody() != null && (
                        m.getBody().getStatements().toString().contains("mapper." + methodInvocation.getMethodName()))).forEach(m -> {
                    if (n.tAgreementList.stream().anyMatch(t -> methodInvocation.getClassName().endsWith(t.toString()))) {
                        CallerMethod callerMethod = new CallerMethod();
                        callerMethod.setClassName(n.packageName + "." + n.classTree.getSimpleName().toString());
                        callerMethod.setSql(methodInvocation.getSql());
                        callerMethod.setTableNames(methodInvocation.getTableNames());
                        callerMethod.setMethodName(m.getName().toString());
                        if (StringUtils.isNotBlank(n.classTree.getSimpleName().toString()) && Stream.of("BaseController", "BaseService", "BaseDao").noneMatch(c -> callerMethod.getClassName().contains(c))) {
                            list.add(callerMethod);
                        }
                    }
                });
            }
        }));

        // 同类 方法调用
        nodeList.stream().filter(n -> (n.packageName + "." + n.classTree.getSimpleName().toString()).equals(methodInvocation.getClassName())).forEach(n -> n.methodTreeList.forEach(m -> {
            if (m.getBody() != null && m.getBody().getStatements().toString().matches(".*((this\\.)|(?<!\\.))" + methodInvocation.getMethodName() + "\\(.*")) {
                CallerMethod callerMethod = new CallerMethod();
                callerMethod.setClassName(n.packageName + "." + n.classTree.getSimpleName().toString());
                callerMethod.setSql(methodInvocation.getSql());
                callerMethod.setTableNames(methodInvocation.getTableNames());
                callerMethod.setMethodName(m.getName().toString());
                if (StringUtils.isNotBlank(n.classTree.getSimpleName().toString()) && Stream.of("BaseController", "BaseService", "BaseDao").noneMatch(c -> callerMethod.getClassName().contains(c))) {
                    list.add(callerMethod);
                }
            }
        }));

        // 泛型参数匹配
        nodeList.stream().filter(n -> n.tAgreementList.stream().anyMatch(t -> methodInvocation.getClassName().endsWith(t.toString()))).forEach(n -> {
            // mapper type == methodInvocation.getClassName()
            n.methodInvocationTreeList.stream().filter(m -> m.getMethodSelect().toString().equals("mapper." + methodInvocation.getMethodName()))
                .forEach(mInv -> {
                    n.methodTreeList.stream().filter(m -> m.getBody().getStatements().toString().contains(mInv.getMethodSelect().toString())).forEach(m -> {
                        CallerMethod callerMethod = new CallerMethod();
                        callerMethod.setClassName(n.packageName + "." + n.classTree.getSimpleName().toString());
                        callerMethod.setSql(methodInvocation.getSql());
                        callerMethod.setTableNames(methodInvocation.getTableNames());
                        callerMethod.setMethodName(m.getName().toString());
                        if (StringUtils.isNotBlank(n.classTree.getSimpleName().toString()) && Stream.of("BaseController", "BaseService", "BaseDao").noneMatch(c -> callerMethod.getClassName().contains(c))) {
                            list.add(callerMethod);
                        }
                    });
                });
        });

        methodInvocation.setCallerMethodList(list.stream().distinct().collect(Collectors.toList()));

        if (methodInvocation.getClassName().endsWith("Controller")) {
            String baseUri = "/" + Arrays.stream(curNode.classTree.toString().split("\r\n")).filter(c -> c.startsWith("@Request"))
                    .findFirst().orElse("").replaceAll(".*\"(/|)(supplier.*?)\".*", "$2") + "/";
            curNode.methodTreeList.stream().filter(m -> m.getName().contentEquals(methodInvocation.getMethodName())).findFirst()
                    .ifPresent(w -> methodInvocation.setUri((baseUri + Arrays.stream(w.toString().split("\r\n"))
                            .filter(a -> a.startsWith("@Req") || a.startsWith("@Get") || a.startsWith("@Put") || a.startsWith("@Post") || a.startsWith("@Del"))
                            .findFirst().orElse("").replaceAll(".*\"(.*?)\".*", "$1")).replaceAll("//+", "/")));
            controllerMethods.add(methodInvocation);
            System.out.println(JSON.toJSONString(methodInvocation, SerializerFeature.PrettyFormat));
            return;
        }

        list.stream().distinct().collect(Collectors.toList()).forEach(c -> getCallerMethodsFromMethodInvocation(c, nodeList));
    }

    /**
     * 诊断信息监听器
     */
    static class MyDiagnosticListener implements DiagnosticListener<JavaFileObject> {
        @Override
        public void report(Diagnostic diagnostic) {
            System.out.println("Code->" + diagnostic.getCode());
            System.out.println("Column Number->" + diagnostic.getColumnNumber());
            System.out.println("End Position->" + diagnostic.getEndPosition());
            System.out.println("Kind->" + diagnostic.getKind());
            System.out.println("Line Number->" + diagnostic.getLineNumber());
            System.out.println("Message->" + diagnostic.getMessage(Locale.ENGLISH));
            System.out.println("Position->" + diagnostic.getPosition());
            System.out.println("Source" + diagnostic.getSource());
            System.out.println("Start Position->" + diagnostic.getStartPosition());
            System.out.println("\n");
        }
    }

}
