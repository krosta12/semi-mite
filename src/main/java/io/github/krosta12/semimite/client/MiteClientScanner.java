package io.github.krosta12.semimite.client;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/**
 * Scans the classpath (directories and/or jar files) for interfaces annotated
 * with {@link MiteClient} and collects them into a {@link MiteClientRegistry}.
 */
public class MiteClientScanner {

    /**
     * Scans the given base package for {@code @MiteClient}-annotated interfaces.
     *
     * @param basePackage the package to scan, e.g. {@code "com.example.app"}
     * @return a registry containing all discovered client interfaces
     */
    public MiteClientRegistry scan(String basePackage) {
        MiteClientRegistry registry = new MiteClientRegistry();
        for (Class<?> candidate : findClasses(basePackage)) {
            if (candidate.isInterface() && candidate.isAnnotationPresent(MiteClient.class)) {
                registry.register(candidate);
            }
        }
        return registry;
    }

    /**
     * Locates all classes belonging to the given package, whether they live on
     * the filesystem or inside a jar file.
     *
     * @param basePackage the package to search
     * @return the list of loaded classes found in the package
     */
    private List<Class<?>> findClasses(String basePackage) {
        List<Class<?>> result = new ArrayList<>();
        String path = basePackage.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            boolean found = false;
            while (resources.hasMoreElements()) {
                found = true;
                URL resource = resources.nextElement();
                if ("jar".equals(resource.getProtocol())) {
                    scanJar(resource, path, result);
                } else {
                    scanDirectory(new File(resource.toURI()), basePackage, result);
                }
            }

            if (!found) {
                Enumeration<URL> roots = classLoader.getResources("");
                while (roots.hasMoreElements()) {
                    URL root = roots.nextElement();
                    if ("jar".equals(root.getProtocol())) {
                        scanJar(root, path, result);
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to scan package: " + basePackage, e);
        }
        return result;
    }


    /**
     * Recursively walks a directory tree of {@code .class} files, loading each
     * class into {@code result}.
     *
     * @param directory   the directory to walk
     * @param basePackage the package name corresponding to {@code directory}
     * @param result      the list to append discovered classes to
     */
    private void scanDirectory(File directory, String basePackage, List<Class<?>> result) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, basePackage + "." + file.getName(), result);
            } else if (file.getName().endsWith(".class")) {
                String className = basePackage + "." + file.getName().substring(0, file.getName().length() - 6);
                loadClass(className, result);
            }
        }
    }


    /**
     * Scans a jar file for {@code .class} entries under the given path and
     * loads matching classes into {@code result}.
     *
     * @param resource the {@code jar:} URL pointing at the package location
     * @param path     the package path (slash-separated) to filter entries by
     * @param result   the list to append discovered classes to
     * @throws IOException if the jar file cannot be read
     */
    private void scanJar(URL resource, String path, List<Class<?>> result) throws IOException {
        JarURLConnection connection = (JarURLConnection) resource.openConnection();
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(path) && name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    loadClass(className, result);
                }
            }
        }
    }

    /**
     * Loads a class by its binary name and adds it to {@code result}.
     * Classes that cannot be loaded (missing dependencies, etc.) are silently skipped.
     *
     * @param className the fully qualified class name to load
     * @param result    the list to append the loaded class to
     */
    private void loadClass(String className, List<Class<?>> result) {
        try {
            result.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader()));
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // skip unloadable class
        }
    }

}