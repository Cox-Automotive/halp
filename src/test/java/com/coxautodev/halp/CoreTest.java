package com.coxautodev.halp;

import com.coxautodev.halp.nestedArrays.NestedArrays;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static com.coxautodev.halp.Core.classInfo;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class CoreTest {

    private static void assertDepsEqual(ClassInfo a, ClassInfo b) {
        assertEquals(a.name(), b.name());
        assertEquals(a.dependencies(), b.dependencies());
    }

    private static void assertDepsEqual(List<ClassInfo> a, List<ClassInfo> b) {
        assertEquals(a.size(), b.size());
        for (int i=0; i<a.size(); i++) {
            assertDepsEqual(a.get(i), b.get(i));
        }
    }

    @Ignore
    @Test public void nestedArrays() {
        List<ClassInfo> actual = Core.analyzeClasspath("com.coxautodev.halp.nestedArrays.**");
        List<ClassInfo> expected = asList(classInfo(NestedArrays.class.getName(), "java.lang.Object"));
        assertDepsEqual(actual, expected);
    }
}
