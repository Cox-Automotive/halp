package com.coxautodev.halp;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.fail;

public class Assertions {

    public static void assertNoClassCycles(List<ClassInfo> deps) {
        List<String> cycle = Core.firstClassCycle(deps);
        if (cycle != null) {
            fail("found at least one cycle representing mutual dependency between top-level classes: " + cycle);
        }
    }

    public static void assertNoPackageCycles(List<ClassInfo> deps) {
        List<String> cycle = Core.firstPackageCycle(deps);
        if (cycle != null) {
            fail("found at least one cycle representing mutual dependency between top-level classes: " + cycle);
        }
    }

    public static void assertModuleBoundaries(Collection<ClassInfo> classpath, Collection<Module> modules) {

        String msg = modules.stream()
            .map(m -> Core.inspectModule(classpath, m))
            .filter(r -> r.undeclared().size() > 0)
            .map(r -> r.moduleName() + ":" + r.undeclared())
            .reduce((h, v) -> h + ", " + v + "\n")
            .orElseGet(() -> "");

        if (!msg.isEmpty()) {
            fail("the following modules use dependencies but do not not declare them: " + msg);
        }
    }

    public static void assertNoUnmodularizedBehavior(Collection<ClassInfo> classpath, Collection<Module> modules) {

        Set<String> found = Core.findUnmodularizedBehavior(classpath, modules);

        if (!found.isEmpty()) {
            fail("meta-module contains classes that are not covered by a module boundary: " + found);
        }
    }
}
