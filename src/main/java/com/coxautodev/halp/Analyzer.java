package com.coxautodev.halp;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptySet;

/**
 * Extracts class dependency information from class files.
 */
public class Analyzer {

    private static final Pattern singlePattern = Pattern.compile("\\[*L([\\w/$]+);");

    private static final Pattern multiPattern = Pattern.compile("(?<=L)([\\w/$]+)(?=[;<])");

    private static String classNode(String slashSeparatedName) {
        if (slashSeparatedName.contains(";")) {
            throw new RuntimeException();
        }
        return slashSeparatedName.replaceAll("/", ".");
    }

    private static String classNodeFromSingleType(String single) {
        Matcher m = singlePattern.matcher(single);
        if (m.matches()) {
            return classNode(m.group(1));
        }
        else {
            return classNode(single);
        }
    }

    private static Set<String> classNodeFromDescriptor(String desc) {
        if (desc == null || desc.equals("")) {
            return emptySet();
        } else {
            Matcher m = multiPattern.matcher(desc);
            Set<String> nodes = new HashSet();
            while (m.find()) {
                nodes.add(classNode(m.group()));
            }
            return nodes;
        }
    }

    private interface Collector {
        void collect(String name);
        void collect(Set<String> names);
    }

    private static class AnalysisAnnotationVisitor extends AnnotationVisitor {

        private final Collector collector;

        public AnalysisAnnotationVisitor(Collector collector) {
            super(Opcodes.ASM5);
            this.collector = collector;
        }

        public void visit(String name, Object value) {
            if (value instanceof Type) {
                collector.collect(classNodeFromSingleType(((Type)value).getClassName()));
            }
        }

        public void visitEnum(String name, String desc, String value) {
            collector.collect(classNodeFromDescriptor(desc));
        }

        public AnnotationVisitor visitAnnotation(String name, String desc) {
            collector.collect(classNodeFromDescriptor(desc));
            return this;
        }

        public AnnotationVisitor visitArray(String name) {
            return this;
        }
    }

    private static class AnalysisMethodVisitor extends MethodVisitor {

        private final Collector collector;

        AnalysisMethodVisitor(Collector collector) {
            super(Opcodes.ASM5);
            this.collector = collector;
        }

        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            collector.collect(classNodeFromDescriptor(desc));
            return new AnalysisAnnotationVisitor(collector);
        }

        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            collector.collect(classNodeFromDescriptor(desc));
            return new AnalysisAnnotationVisitor(collector);
        }

        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            collector.collect(classNodeFromDescriptor(desc));
            return new AnalysisAnnotationVisitor(collector);
        }

        public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            collector.collect(classNodeFromDescriptor(desc));
            return new AnalysisAnnotationVisitor(collector);
        }

        public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            collector.collect(classNodeFromDescriptor(desc));
            return new AnalysisAnnotationVisitor(collector);
        }

        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
            collector.collect(classNodeFromDescriptor(desc));
            return new AnalysisAnnotationVisitor(collector);
        }

        public void visitTypeInsn(int opcode, String type) {
            collector.collect(classNodeFromSingleType(type));
        }

        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            collector.collect(classNodeFromSingleType(owner));
            collector.collect(classNodeFromDescriptor(desc));
        }

        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            collector.collect(classNodeFromSingleType(owner));
            collector.collect(classNodeFromDescriptor(desc));
        }

        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            visitMethodInsn(opcode, owner, name, desc);
        }

        public void visitLdcInsn(Object cst) {
            if (cst instanceof Type) {
                collector.collect(classNodeFromDescriptor(((Type)cst).getClassName()));
            }
        }

        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            if (type != null) {
                collector.collect(classNode(type));
            }
        }

        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            collector.collect(classNodeFromDescriptor(desc));
            collector.collect(classNodeFromDescriptor(signature));
        }
    }

    private static class AnalysisVisitor extends ClassVisitor {

        private String className;
        private final Set<String> dependencies = new HashSet();

        private Collector notifier = new Collector() {
            public void collect(String name) {
                dependencies.add(name);
            }
            public void collect(Set<String> names) {
                dependencies.addAll(names);
            }
        };

        AnalysisVisitor() { super(Opcodes.ASM5); }

        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            className = classNode(name);

            if (superName != null) {
                notifier.collect(classNode(superName));
            }

            for (String iface : interfaces) {
                notifier.collect((classNode(iface)));
            }
        }

        public void visitOuterClass(String owner, String name, String desc) {
            notifier.collect(classNode(owner));
            notifier.collect(classNodeFromDescriptor(desc));
        }

        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return null;
        }

        public void visitAttribute(Attribute attr) {}

        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            notifier.collect(classNodeFromDescriptor(desc));
            return new AnalysisAnnotationVisitor(notifier);
        }

        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (outerName == null) {
                notifier.collect(classNode(name));
            }
        }

        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            notifier.collect(classNodeFromDescriptor(desc));
            notifier.collect(classNodeFromDescriptor(signature));
            return null;
        }

        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            notifier.collect(classNodeFromDescriptor(desc));
            notifier.collect(classNodeFromDescriptor(signature));

            if (exceptions != null) {
                for (String e : exceptions) {
                    notifier.collect(classNode(e));
                }
            }

            return new AnalysisMethodVisitor(notifier);
        }

        public void visitEnd() {
            // that a class depends on itself is not interesting
            dependencies.remove(className);
        }

        public ClassInfo output() {
            final String name = className;
            final Set<String> deps = Collections.unmodifiableSet(new HashSet(dependencies));
            return new ClassInfo() {
                @Override public String name() { return name; }
                @Override public Set<String> dependencies() { return deps; }

                @Override
                public String toString() {
                    return className + ":" + dependencies;
                }
            };
        }
    }

    public static ClassInfo create(InputStream in) throws IOException {
        ClassReader reader = new ClassReader(in);
        AnalysisVisitor v = new AnalysisVisitor();
        reader.accept(v, 0);
        return v.output();
    }
}
