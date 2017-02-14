package com.coxautodev.halp;

import org.junit.Test;

import java.util.List;

import static com.coxautodev.halp.Assertions.*;
import static com.coxautodev.halp.Core.*;
import static com.coxautodev.halp.Modules.*;

public class AssertionsTest {

    private static final List<ClassInfo> aNeedsB = analyzeClasspath("com.coxautodev.halp.aneedsb.**");

    @Test(expected = AssertionError.class) public void classCycle() throws Exception{
        List<ClassInfo> cp = analyzeClasspath("com.coxautodev.halp.classCycle.*");
        assertNoClassCycles(cp);
    }

    @Test public void noClassCycle() {
        assertNoClassCycles(aNeedsB);
    }

    @Test(expected = AssertionError.class) public void packageCycle() {
        List<ClassInfo> cp = analyzeClasspath("com.coxautodev.halp.packageCycle.**");
        assertNoPackageCycles(cp);
    }

    @Test public void noPackageCycle() {
        assertNoPackageCycles(aNeedsB);
    }

    @Test(expected = AssertionError.class) public void boundaryViolations() {
        assertModuleBoundaries(
            aNeedsB,
            modules(module("a").include("**.aneedsb.a.*")));
    }

    @Test public void noBoundaryViolations() {
        assertModuleBoundaries(
            aNeedsB,
            modules(
                module("a")
                    .include("**.aneedsb.a.*")
                    .use("**.aneedsb.b.*")));
    }

    @Test(expected = AssertionError.class) public void unmodularizedBehavior() {
        assertNoUnmodularizedBehavior(
            aNeedsB,
            modules(
                module("a")
                    .include("**.aneedsb.a.*")));
    }

    @Test public void noUnmodularizedBehavior() {
        assertNoUnmodularizedBehavior(
            aNeedsB,
            modules(
                module("a")
                    .include("**.aneedsb.a.*"),
                module("a")
                    .include("**.aneedsb.b.*")));
    }
}
