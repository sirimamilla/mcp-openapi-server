package com.siri.api.mcp.mcp_openapi_server.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

public class ClassScanner {
    public static void main(String[] args) throws IOException {
        System.out.println("Scanning for Spring AI classes...");
        ClassLoader classLoader = ClassScanner.class.getClassLoader();

        Enumeration<URL> resources = classLoader.getResources("org/springframework/ai");
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            System.out.println("Found resource: " + resource.getPath());

            File directory = new File(resource.getPath());
            if (directory.exists()) {
                scanDirectory(directory, "org.springframework.ai");
            }
        }
    }

    private static void scanDirectory(File directory, String packageName) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    scanDirectory(file, packageName + "." + file.getName());
                } else if (file.getName().endsWith(".class")) {
                    String className = file.getName().substring(0, file.getName().length() - 6);
                    System.out.println("Class: " + packageName + "." + className);
                }
            }
        }
    }
}
