package org.example.client;

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

public class MiteClientScanner {

    public MiteClientRegistry scan(String basePackage) {
        MiteClientRegistry registry = new MiteClientRegistry();
        for (Class<?> candidate : findClasses(basePackage)) {
            if (candidate.isInterface() && candidate.isAnnotationPresent(MiteClient.class)) {
                registry.register(candidate);
            }
        }
        return registry;
    }

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

    private void loadClass(String className, List<Class<?>> result) {
        try {
            result.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader()));
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // skip unloadable class
        }
    }

}