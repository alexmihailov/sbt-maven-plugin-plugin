package com.alexmihailov.sbt.maven.tools;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.tools.plugin.ExtendedPluginDescriptor;
import org.apache.maven.tools.plugin.PluginToolsRequest;
import org.apache.maven.tools.plugin.generator.GeneratorException;
import org.apache.maven.tools.plugin.generator.GeneratorUtils;
import org.apache.maven.tools.plugin.generator.PluginDescriptorFilesGenerator;
import org.apache.maven.tools.plugin.javadoc.JavadocLinkGenerator;
import org.apache.maven.tools.plugin.util.PluginUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.io.CachingOutputStream;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class PluginDescriptorGenerator extends PluginDescriptorFilesGenerator {

    @Override
    public void execute(File destinationDirectory, PluginToolsRequest request) throws GeneratorException {
        // Overridden the method to leave the generation of only plugin.xml,
        // since the generation of other files (plugin-help.xml, plugin-enhanced.xml) requires a maven project.
        try {
            // write standard plugin.xml descriptor
            File f = new File(destinationDirectory, "plugin.xml");
            writeDescriptor(f, request);
        } catch (IOException e) {
            throw new GeneratorException(e.getMessage(), e);
        }
    }

    // A complete copy of org.apache.maven.tools.plugin.extractor.annotations.JavaAnnotationsMojoDescriptorExtractor.toMojoDescriptors
    // and remove DescriptorType because it is closed for use.
    public void writeDescriptor(File destinationFile, PluginToolsRequest request)
            throws IOException {
        PluginDescriptor pluginDescriptor = request.getPluginDescriptor();

        if (!destinationFile.getParentFile().exists()) {
            destinationFile.getParentFile().mkdirs();
        }

        try (Writer writer = new OutputStreamWriter(new CachingOutputStream(destinationFile), UTF_8)) {
            XMLWriter w = new PrettyPrintXMLWriter(writer, UTF_8.name(), null);

            w.startElement("plugin");

            GeneratorUtils.element(w, "name", pluginDescriptor.getName());
            GeneratorUtils.element(w, "description", pluginDescriptor.getDescription());
            GeneratorUtils.element(w, "groupId", pluginDescriptor.getGroupId());
            GeneratorUtils.element(w, "artifactId", pluginDescriptor.getArtifactId());
            GeneratorUtils.element(w, "version", pluginDescriptor.getVersion());
            GeneratorUtils.element(w, "goalPrefix", pluginDescriptor.getGoalPrefix());
            GeneratorUtils.element(w, "isolatedRealm", String.valueOf(pluginDescriptor.isIsolatedRealm()));
            GeneratorUtils.element(w, "inheritedByDefault", String.valueOf(pluginDescriptor.isInheritedByDefault()));

            if (pluginDescriptor instanceof ExtendedPluginDescriptor) {
                ExtendedPluginDescriptor extPluginDescriptor = (ExtendedPluginDescriptor) pluginDescriptor;
                if (StringUtils.isNotBlank(extPluginDescriptor.getRequiredJavaVersion())) {
                    GeneratorUtils.element(w, "requiredJavaVersion", extPluginDescriptor.getRequiredJavaVersion());
                }
            }
            if (StringUtils.isNotBlank(pluginDescriptor.getRequiredMavenVersion())) {
                GeneratorUtils.element(w, "requiredMavenVersion", pluginDescriptor.getRequiredMavenVersion());
            }

            w.startElement("mojos");

            final JavadocLinkGenerator javadocLinkGenerator;
            if (request.getInternalJavadocBaseUrl() != null
                    || (request.getExternalJavadocBaseUrls() != null
                    && !request.getExternalJavadocBaseUrls().isEmpty())) {
                javadocLinkGenerator = new JavadocLinkGenerator(
                        request.getInternalJavadocBaseUrl(),
                        request.getInternalJavadocVersion(),
                        request.getExternalJavadocBaseUrls(),
                        request.getSettings());
            } else {
                javadocLinkGenerator = null;
            }
            if (pluginDescriptor.getMojos() != null) {
                List<MojoDescriptor> descriptors = pluginDescriptor.getMojos();

                PluginUtils.sortMojos(descriptors);

                for (MojoDescriptor descriptor : descriptors) {
                    processMojoDescriptor(descriptor, w, null, javadocLinkGenerator);
                }
            }

            w.endElement();

            GeneratorUtils.writeDependencies(w, pluginDescriptor);

            w.endElement();

            writer.flush();
        }
    }
}
