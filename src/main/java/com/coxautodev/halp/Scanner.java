package com.coxautodev.halp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public class Scanner {

    @FunctionalInterface
    private interface SimpleVisitor {
        void visit(File f) throws IOException;
    }

    private static FileVisitor simpleFileVisitor(SimpleVisitor v) {
        return new FileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                File f = file.toFile();
                v.visit(f);
                return FileVisitResult.CONTINUE;
            }
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        };
    }

    @FunctionalInterface
    public interface Matcher {
        boolean matches(String s);
    }

    @FunctionalInterface
    public interface Handler {
        void handle(InputStream in) throws IOException;
    }

    private static String pathToClassName(String path) {
        return path
            .replaceAll("\\.class$", "")
            .replaceAll("/", ".");
    }

    private static URL makeUrl(String s) {
        try {
            return new URL(s);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface URLSource { List<URL> urls(); }

    /**
     * assumes we are running in a normal java app, returns the app classloader's urls
     */
    public static URLSource urlClassloaderSource = () -> {
        URLClassLoader cl = (URLClassLoader)Scanner.class.getClassLoader();
        return asList(cl.getURLs());
    };

    /**
     * check and see if we are running inside a surefire test fork
     * if so, pull the classpath from the manifest, otherwise return null
     */
    public static URLSource surefireForkSource = () -> {
        URLClassLoader cl = (URLClassLoader)Scanner.class.getClassLoader();

        try {
            URL url = cl.findResource("META-INF/MANIFEST.MF");
            Manifest manifest = new Manifest(url.openStream());
            Attributes attrs = manifest.getMainAttributes();
            String classPath = attrs.getValue("Class-Path");

            if (classPath != null) {
                return asList(classPath.split(" ")).stream()
                    .map(Scanner::makeUrl)
                    .collect(toList());
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return null;
    };

    public static URLSource defaultSource = () -> {
        List<URL> urls = surefireForkSource.urls();
        if (urls == null) {
            urls = urlClassloaderSource.urls();
        }
        return urls;
    };

    public static void scan(Matcher m, Handler h, URLSource source) {
        try {
            for (URL url : source.urls()) {

                final String rawFile = url.getFile();
                final File file = new File(URLDecoder.decode(rawFile, "UTF-8"));
                final String fileName = file.getName();

                if (fileName.endsWith(".class")) {
                    if (m.matches(pathToClassName(fileName))) {
                        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                            h.handle(in);
                        }
                    }
                } else if (file.isDirectory()) {
                    final String root = file.getPath();
                    Files.walkFileTree(Paths.get(file.toURI()), simpleFileVisitor(f -> {
                        if (f.isFile() && f.getName().endsWith(".class")) {

                            String relativePath = f.getPath().substring(root.length());
                            if (relativePath.startsWith("/")) {
                                relativePath = relativePath.substring(1);
                            }

                            if (m.matches(pathToClassName(relativePath))) {
                                try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                                    h.handle(in);
                                }
                            }
                        }
                    }));
                } else if (fileName.endsWith(".jar")) {
                    ZipFile zipFile = new ZipFile(file);
                    Enumeration<ZipEntry> entries = (Enumeration) zipFile.entries();

                    while (entries.hasMoreElements()) {
                        ZipEntry e = entries.nextElement();
                        if (e.getName().endsWith(".class")) {

                            if (m.matches(pathToClassName(e.getName()))) {
                                try (InputStream in = zipFile.getInputStream(e)) {
                                    h.handle(in);
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void scan(Matcher m, Handler h) {
        scan(m, h, defaultSource);
    }
}

