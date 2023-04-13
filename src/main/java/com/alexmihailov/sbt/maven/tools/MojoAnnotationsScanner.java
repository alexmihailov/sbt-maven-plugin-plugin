package com.alexmihailov.sbt.maven.tools;

import org.apache.maven.tools.plugin.extractor.ExtractionException;
import org.apache.maven.tools.plugin.extractor.annotations.scanner.DefaultMojoAnnotationsScanner;
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotatedClass;
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotationsScannerRequest;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MojoAnnotationsScanner extends DefaultMojoAnnotationsScanner {

    @Override
    public Map<String, MojoAnnotatedClass> scan(MojoAnnotationsScannerRequest request) throws ExtractionException {
        // Overridden the method to leave only scanning annotations in directories
        // from org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotationsScannerRequest.classesDirectories.
        Map<String, MojoAnnotatedClass> mojoAnnotatedClasses = new HashMap<>();
        // Added logger to avoid NPE.
        enableLogging(new ConsoleLogger(Logger.LEVEL_INFO, "standalone-scanner-logger"));
        try {
            for (File classDirectory : request.getClassesDirectories()) {
                scan(
                        mojoAnnotatedClasses,
                        classDirectory,
                        request.getIncludePatterns(),
                        null,
                        false);
            }
        } catch (IOException e) {
            throw new ExtractionException(e.getMessage(), e);
        }
        return mojoAnnotatedClasses;
    }
}
