package com.coxautodev.halp;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.coxautodev.halp.Patterns.pattern;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class Core {

    public static List<ClassInfo> analyzeClasspath(Iterable<String> includes) {
        List<ClassInfo> output = new ArrayList();
        Patterns.PatternMatcher matcher = Patterns.pattern(includes);
        Scanner.scan(matcher::matches, in -> output.add(Analyzer.create(in)));
        return output;
    }

    public static List<ClassInfo> analyzeClasspath(String...includes) {
        return analyzeClasspath(asList(includes));
    }

    public static List<ClassInfo> analyzeClasspath(Collection<Module> modules) {
        return analyzeClasspath(Modules.includes(modules));
    }

    public static String printClassInfo(List<ClassInfo> deps) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        for (ClassInfo d : deps) {

            pw.println(d.name() + " [");
            List<String> sorted = new ArrayList(d.dependencies());
            Collections.sort(sorted);
            for (String dep : sorted) {
                if (dep != null) {
                    pw.println("    " + dep);
                }
            }
            System.out.println("]");
        }
        return sw.getBuffer().toString();
    }

    public static ClassInfo classInfo(final String name, final Set<String> dependencies) {
        return new ClassInfo() {
            public String name() { return name; }
            public Set<String> dependencies() { return dependencies; }
        };
    }

    public static ClassInfo classInfo(final String name, final String...dependencies) {
        return classInfo(name, new HashSet(asList(dependencies)));
    }

    @FunctionalInterface private interface Transformer { String transform(String name); }

    private static List<ClassInfo> transformIdentifiers(List<ClassInfo> classes, Transformer t) {

        Map<String, Set<String>> transformed = new HashMap();

        for (ClassInfo c : classes) {

            final String identifier = t.transform(c.name());

            Set<String> deps = transformed.get(identifier);
            if (deps == null) {
                deps = new HashSet();
                transformed.put(identifier, deps);
            }

            for (String s : c.dependencies()) {
                deps.add(t.transform(s));
            }

            deps.remove(identifier);
        }

        return transformed.entrySet().stream()
            .map(e -> classInfo(e.getKey(), e.getValue()))
            .collect(toList());
    }

    private static List<String> findFirstCycle(List<ClassInfo> deps) {

        Map<String, List<String>> input = new HashMap();

        for (ClassInfo d : deps) {
            input.put(d.name(), new ArrayList(d.dependencies()));
        }

        try {
            TopoSort.topoSort(input);
            return null;
        }
        catch (TopoSort.CycleDiscovery d) {
            return d.cyclePath;
        }
    }

    private static final Pattern topLevelClassPattern = Pattern.compile("([^\\$]+).*$");

    private static String toTopLevelClass(String name) {
        if (!name.contains("$")) {
            return name;
        }
        else {
            Matcher m = topLevelClassPattern.matcher(name);
            if (m.matches()) {
                return m.group(1);
            }
            else {
                throw new RuntimeException("Not a class: " + name);
            }
        }
    }

    /*
     * Takes class metadata and asserts that no two classes are mutually dependent.
     * If this condition occurrs, a runtime exception is thrown.
     */
    public static List<String> firstClassCycle(List<ClassInfo> deps) {
        List<ClassInfo> topLevelDeps = transformIdentifiers(deps, Core::toTopLevelClass);
        return findFirstCycle(topLevelDeps);
    }

    private static final Pattern packagePattern = Pattern.compile("(.*)\\..*$");

    private static String toPackage(String name) {
        if (!name.contains(".")) {
            return "";
        }
        else {
            Matcher m = packagePattern.matcher(name);
            if (m.matches()) {
                return m.group(1);
            }
            else {
                throw new RuntimeException("Not a class: " + name);
            }
        }
    }

    /*
     * Takes class metadata and asserts that no two packages are mutually dependent.
     * If this condition occurrs, a runtime exception is thrown.
     */
    public static List<String> firstPackageCycle(List<ClassInfo> deps) {
        List<ClassInfo> packageDeps = transformIdentifiers(deps, Core::toPackage);
        return findFirstCycle(packageDeps);
    }

    public interface ModuleInspection {
        String moduleName();
        Set<String> undeclared();
        Set<String> unused();
    }

    private static boolean isBuiltin(String id) {
        return (id.startsWith("java.") || id.startsWith("javax."));
    }

    public static ModuleInspection inspectModule(Collection<ClassInfo> deps, Module m) {

        Patterns.PatternMatcher include = Patterns.pattern(m.includes());
        Patterns.PatternMatcher uses = Patterns.pattern(m.uses());

        Set<String> undeclared = deps.stream()
            .filter(i -> include.matches(i.name()))
            .flatMap(i -> i.dependencies().stream())
            .filter(id -> !isBuiltin(id))
            .filter(id -> !include.matches(id))
            .filter(id -> !uses.matches(id))
            .collect(toSet());

        Set<String> unused = uses.usage().entrySet().stream()
            .filter(e -> e.getValue() == 0)
            .map(Map.Entry::getKey)
            .collect(toSet());

        return new ModuleInspection() {
            public String moduleName() { return m.name(); }
            public Set<String> undeclared() { return undeclared; }
            public Set<String> unused() { return unused; }
        };
    }

    /*
     * Takes class metadata, a module definition and metaIncludes. metaIncludes are
     * intended match the full set of application behavior. This function asserts that
     * the full set of application behavior is contained by all the modules. If this
     * is not true, a runtime exception is thrown.
     */
    public static Set<String> findUnmodularizedBehavior(Collection<ClassInfo> classpath, Collection<Module> modules) {

        Set<Patterns.PatternMatcher> matchers = modules.stream()
            .map(m -> Patterns.pattern(m.includes()))
            .collect(toSet());

        Predicate<String> notReferenced = i -> matchers.stream()
            .noneMatch(p -> p.matches(i));

        return classpath.stream()
            .map(ClassInfo::name)
            .filter(notReferenced)
            .collect(toSet());
    }
}
