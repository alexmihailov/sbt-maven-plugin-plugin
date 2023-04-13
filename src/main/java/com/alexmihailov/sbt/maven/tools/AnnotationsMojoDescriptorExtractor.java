package com.alexmihailov.sbt.maven.tools;

import org.apache.maven.plugin.descriptor.InvalidParameterException;
import org.apache.maven.plugin.descriptor.InvalidPluginDescriptorException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.Requirement;
import org.apache.maven.tools.plugin.ExtendedMojoDescriptor;
import org.apache.maven.tools.plugin.PluginToolsRequest;
import org.apache.maven.tools.plugin.extractor.ExtractionException;
import org.apache.maven.tools.plugin.extractor.annotations.JavaAnnotationsMojoDescriptorExtractor;
import org.apache.maven.tools.plugin.extractor.annotations.datamodel.ComponentAnnotationContent;
import org.apache.maven.tools.plugin.extractor.annotations.datamodel.ExecuteAnnotationContent;
import org.apache.maven.tools.plugin.extractor.annotations.datamodel.MojoAnnotationContent;
import org.apache.maven.tools.plugin.extractor.annotations.datamodel.ParameterAnnotationContent;
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotatedClass;
import org.apache.maven.tools.plugin.extractor.annotations.scanner.MojoAnnotationsScannerRequest;
import org.apache.maven.tools.plugin.util.PluginUtils;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class AnnotationsMojoDescriptorExtractor extends JavaAnnotationsMojoDescriptorExtractor {

    private final File classesDirectory;
    private final MojoAnnotationsScanner mojoAnnotationsScanner;

    public AnnotationsMojoDescriptorExtractor(File classesDirectory) {
        this.classesDirectory = classesDirectory;
        this.mojoAnnotationsScanner = new MojoAnnotationsScanner();
    }

    @Override
    public List<MojoDescriptor> execute(PluginToolsRequest request) throws ExtractionException, InvalidPluginDescriptorException {
        // Overridden the method to skip scanning javadocs as it requires a maven project.
        // Left only scanning annotations.
        MojoAnnotationsScannerRequest mojoRequest = new MojoAnnotationsScannerRequest();
        mojoRequest.setClassesDirectories(Collections.singletonList(classesDirectory));
        Map<String, MojoAnnotatedClass> mojoAnnotatedClasses = mojoAnnotationsScanner.scan(mojoRequest);
        return toMojoDescriptors(mojoAnnotatedClasses, request.getPluginDescriptor());
    }

    // A complete copy of org.apache.maven.tools.plugin.extractor.annotations.JavaAnnotationsMojoDescriptorExtractor.toMojoDescriptors,
    // since the original method is private
    private List<MojoDescriptor> toMojoDescriptors(
            Map<String, MojoAnnotatedClass> mojoAnnotatedClasses, PluginDescriptor pluginDescriptor)
            throws InvalidPluginDescriptorException {
        List<MojoDescriptor> mojoDescriptors = new ArrayList<>(mojoAnnotatedClasses.size());
        for (MojoAnnotatedClass mojoAnnotatedClass : mojoAnnotatedClasses.values()) {
            // no mojo so skip it
            if (mojoAnnotatedClass.getMojo() == null) {
                continue;
            }

            ExtendedMojoDescriptor mojoDescriptor = new ExtendedMojoDescriptor(true);

            // mojoDescriptor.setRole( mojoAnnotatedClass.getClassName() );
            // mojoDescriptor.setRoleHint( "default" );
            mojoDescriptor.setImplementation(mojoAnnotatedClass.getClassName());
            mojoDescriptor.setLanguage("java");

            mojoDescriptor.setV4Api(mojoAnnotatedClass.isV4Api());

            MojoAnnotationContent mojo = mojoAnnotatedClass.getMojo();

            mojoDescriptor.setDescription(mojo.getDescription());
            mojoDescriptor.setSince(mojo.getSince());
            mojo.setDeprecated(mojo.getDeprecated());

            mojoDescriptor.setProjectRequired(mojo.requiresProject());

            mojoDescriptor.setRequiresReports(mojo.requiresReports());

            mojoDescriptor.setComponentConfigurator(mojo.configurator());

            mojoDescriptor.setInheritedByDefault(mojo.inheritByDefault());

            mojoDescriptor.setInstantiationStrategy(mojo.instantiationStrategy().id());

            mojoDescriptor.setAggregator(mojo.aggregator());
            mojoDescriptor.setDependencyResolutionRequired(
                    mojo.requiresDependencyResolution().id());
            mojoDescriptor.setDependencyCollectionRequired(
                    mojo.requiresDependencyCollection().id());

            mojoDescriptor.setDirectInvocationOnly(mojo.requiresDirectInvocation());
            mojoDescriptor.setDeprecated(mojo.getDeprecated());
            mojoDescriptor.setThreadSafe(mojo.threadSafe());

            MojoAnnotatedClass mojoAnnotatedClassWithExecute =
                    findClassWithExecuteAnnotationInParentHierarchy(mojoAnnotatedClass, mojoAnnotatedClasses);
            if (mojoAnnotatedClassWithExecute != null && mojoAnnotatedClassWithExecute.getExecute() != null) {
                ExecuteAnnotationContent execute = mojoAnnotatedClassWithExecute.getExecute();
                mojoDescriptor.setExecuteGoal(execute.goal());
                mojoDescriptor.setExecuteLifecycle(execute.lifecycle());
                if (execute.phase() != null) {
                    mojoDescriptor.setExecutePhase(execute.phase().id());
                    if (StringUtils.isNotEmpty(execute.customPhase())) {
                        throw new InvalidPluginDescriptorException(
                                "@Execute annotation must only use either 'phase' "
                                        + "or 'customPhase' but not both. Both are used though on "
                                        + mojoAnnotatedClassWithExecute.getClassName(),
                                null);
                    }
                } else if (StringUtils.isNotEmpty(execute.customPhase())) {
                    mojoDescriptor.setExecutePhase(execute.customPhase());
                }
            }

            mojoDescriptor.setExecutionStrategy(mojo.executionStrategy());
            // ???
            // mojoDescriptor.alwaysExecute(mojo.a)

            mojoDescriptor.setGoal(mojo.name());
            mojoDescriptor.setOnlineRequired(mojo.requiresOnline());

            mojoDescriptor.setPhase(mojo.defaultPhase().id());

            // Parameter annotations
            Map<String, ParameterAnnotationContent> parameters =
                    getParametersParentHierarchy(mojoAnnotatedClass, mojoAnnotatedClasses);

            for (ParameterAnnotationContent parameterAnnotationContent : new TreeSet<>(parameters.values())) {
                org.apache.maven.plugin.descriptor.Parameter parameter =
                        new org.apache.maven.plugin.descriptor.Parameter();
                String name = StringUtils.isEmpty(parameterAnnotationContent.name())
                        ? parameterAnnotationContent.getFieldName()
                        : parameterAnnotationContent.name();
                parameter.setName(name);
                parameter.setAlias(parameterAnnotationContent.alias());
                parameter.setDefaultValue(parameterAnnotationContent.defaultValue());
                parameter.setDeprecated(parameterAnnotationContent.getDeprecated());
                parameter.setDescription(parameterAnnotationContent.getDescription());
                parameter.setEditable(!parameterAnnotationContent.readonly());
                String property = parameterAnnotationContent.property();
                if (StringUtils.contains(property, '$')
                        || StringUtils.contains(property, '{')
                        || StringUtils.contains(property, '}')) {
                    throw new InvalidParameterException(
                            "Invalid property for parameter '" + parameter.getName() + "', "
                                    + "forbidden characters ${}: " + property,
                            null);
                }
                parameter.setExpression(StringUtils.isEmpty(property) ? "" : "${" + property + "}");
                StringBuilder type = new StringBuilder(parameterAnnotationContent.getClassName());
                if (!parameterAnnotationContent.getTypeParameters().isEmpty()) {
                    type.append(parameterAnnotationContent.getTypeParameters().stream()
                            .collect(Collectors.joining(", ", "<", ">")));
                }
                parameter.setType(type.toString());
                parameter.setSince(parameterAnnotationContent.getSince());
                parameter.setRequired(parameterAnnotationContent.required());

                mojoDescriptor.addParameter(parameter);
            }

            // Component annotations
            Map<String, ComponentAnnotationContent> components =
                    getComponentsParentHierarchy(mojoAnnotatedClass, mojoAnnotatedClasses);

            for (ComponentAnnotationContent componentAnnotationContent : new TreeSet<>(components.values())) {
                org.apache.maven.plugin.descriptor.Parameter parameter =
                        new org.apache.maven.plugin.descriptor.Parameter();
                parameter.setName(componentAnnotationContent.getFieldName());

                // recognize Maven-injected objects as components annotations instead of parameters
                String expression = PluginUtils.MAVEN_COMPONENTS.get(componentAnnotationContent.getRoleClassName());
                if (expression == null) {
                    // normal component
                    parameter.setRequirement(new Requirement(
                            componentAnnotationContent.getRoleClassName(), componentAnnotationContent.hint()));
                } else {
                    // not a component but a Maven object to be transformed into an expression/property: deprecated
                    getLogger()
                            .warn("Deprecated @Component annotation for '" + parameter.getName() + "' field in "
                                    + mojoAnnotatedClass.getClassName()
                                    + ": replace with @Parameter( defaultValue = \"" + expression
                                    + "\", readonly = true )");
                    parameter.setDefaultValue(expression);
                    parameter.setType(componentAnnotationContent.getRoleClassName());
                    parameter.setRequired(true);
                }
                parameter.setDeprecated(componentAnnotationContent.getDeprecated());
                parameter.setSince(componentAnnotationContent.getSince());

                // same behaviour as JavaMojoDescriptorExtractor
                // parameter.setRequired( ... );
                parameter.setEditable(false);

                mojoDescriptor.addParameter(parameter);
            }

            mojoDescriptor.setPluginDescriptor(pluginDescriptor);

            mojoDescriptors.add(mojoDescriptor);
        }
        return mojoDescriptors;
    }
}
