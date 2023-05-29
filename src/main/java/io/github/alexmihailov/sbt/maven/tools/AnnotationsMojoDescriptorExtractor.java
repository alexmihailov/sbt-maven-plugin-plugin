package io.github.alexmihailov.sbt.maven.tools;

import org.apache.maven.plugin.descriptor.InvalidPluginDescriptorException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.tools.plugin.PluginToolsRequest;
import org.apache.maven.tools.plugin.extractor.ExtractionException;
import org.apache.maven.tools.plugin.extractor.annotations.JavaAnnotationsMojoDescriptorExtractor;
import org.apache.maven.tools.plugin.extractor.annotations.scanner.DefaultMojoAnnotationsScanner;
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotatedClass;
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotationsScannerRequest;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AnnotationsMojoDescriptorExtractor extends JavaAnnotationsMojoDescriptorExtractor {

    private final File classesDirectory;
    private final DefaultMojoAnnotationsScanner mojoAnnotationsScanner;

    public AnnotationsMojoDescriptorExtractor(File classesDirectory) {
        this.classesDirectory = classesDirectory;
        this.mojoAnnotationsScanner = new DefaultMojoAnnotationsScanner();
        // Added logger to avoid NPE.
        mojoAnnotationsScanner.enableLogging(new ConsoleLogger(Logger.LEVEL_INFO, "standalone-scanner-logger"));
    }

    @Override
    public List<MojoDescriptor> execute(PluginToolsRequest request) throws ExtractionException, InvalidPluginDescriptorException {
        // Overridden the method to skip scanning javadocs as it requires a maven project.
        // Left only scanning annotations.
        MojoAnnotationsScannerRequest mojoRequest = new MojoAnnotationsScannerRequest();
        // Set directories for classes scan.
        mojoRequest.setClassesDirectories(Collections.singletonList(classesDirectory));
        // Added empty set to avoid dependencies scan.
        mojoRequest.setDependencies(Collections.emptySet());
        // Added project to avoid NPE.
        mojoRequest.setProject(new MavenProject());
        Map<String, MojoAnnotatedClass> mojoAnnotatedClasses = mojoAnnotationsScanner.scan(mojoRequest);
        try {
            Method toMojoDescriptorsRef = getClass().getSuperclass()
                    .getDeclaredMethod("toMojoDescriptors", Map.class, PluginDescriptor.class);
            toMojoDescriptorsRef.setAccessible(true);
            return (List<MojoDescriptor>) toMojoDescriptorsRef.invoke(this, mojoAnnotatedClasses, request.getPluginDescriptor());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
