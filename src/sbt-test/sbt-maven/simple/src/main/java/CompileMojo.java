import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import play.japi.twirl.compiler.TwirlCompiler;
import play.twirl.api.HtmlFormat;
import play.twirl.api.JavaScriptFormat;
import play.twirl.api.TxtFormat;
import play.twirl.api.XmlFormat;
import scala.io.Codec;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_SOURCES;
import static org.codehaus.plexus.util.FileUtils.getExtension;

@Mojo(name = "compile", defaultPhase = GENERATE_SOURCES, threadSafe = true)
public class CompileMojo extends AbstractMojo {

    private static final Map<String, String> DEFAULT_FORMATTERS = Map.of(
            "html", HtmlFormat.class.getCanonicalName(),
            "txt", TxtFormat.class.getCanonicalName(),
            "xml", XmlFormat.class.getCanonicalName(),
            "js", JavaScriptFormat.class.getCanonicalName()
    );

    /**
     * The directories from which the templates will be compiled.
     * <p>
     * Default: /src/main/twirl
     * <p>
     * Example:
     * <pre>
     * &lt;sourceDirectories&gt;
     *    &lt;sourceDirectory&gt;${project.basedir}/src/main/templates&lt;/sourceDirectory&gt;
     * &lt;/sourceDirectories&gt;
     * </pre>
     */
    @Parameter
    private Set<File> sourceDirectories = new LinkedHashSet<>();

    /**
     * The directory where the compiled scala template files are placed.
     * <p>
     * Default: target/generated-sources/twirl
     * <p>
     * Example:
     * <pre>
     * &lt;target&gt;${project.build.directory}/generated/twirl&lt;/target&gt;
     * </pre>
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/twirl")
    private File target;

    /**
     * Defined twirl template formats.
     * <p>
     * Default: {@link CompileMojo#DEFAULT_FORMATTERS}
     * <p>
     * Example:
     * <pre>
     * &lt;templateFormats&gt;
     *   &lt;html&gt;play.twirl.api.HtmlFormat&lt;/html&gt;
     * &lt;/templateFormats&gt;
     * </pre>
     */
    @Parameter
    private Map<String, String> templateFormats = new LinkedHashMap<>();

    /**
     * Extra imports for twirl templates.
     * <p>
     * Default: {@link play.twirl.compiler.TwirlCompiler#DefaultImports()}
     * <p>
     * Example:
     * <pre>
     * &lt;templateImports&gt;
     *   &lt;templateImport&gt;org.abc.backend._&lt;/templateImport&gt;
     * &lt;/templateImports&gt;
     * </pre>
     */
    @Parameter
    private Set<String> templateImports = new LinkedHashSet<>();

    /**
     * Annotations added to constructors in injectable templates.
     * <p>
     * Example:
     * <pre>
     * &lt;constructorAnnotations&gt;
     *   &lt;constructorAnnotation&gt;@javax.inject.Inject()&lt;/constructorAnnotation&gt;
     * &lt;/constructorAnnotations&gt;
     * </pre>
     */
    @Parameter
    private Set<String> constructorAnnotations = new LinkedHashSet<>();

    /**
     * A set of inclusion filters for the compiler.
     * <p>
     * By default, includes for all supported extensions {@link CompileMojo#DEFAULT_FORMATTERS}.
     * <p>
     * Example:
     * <pre>
     * &lt;includeFilters&gt;
     *   &lt;includeFilter&gt;{@literal *}{@literal *}/HelloWorld.scala.html&lt;/includeFilter&gt;
     * &lt;/includeFilters&gt;
     * </pre>
     */
    @Parameter
    private Set<String> includeFilters = new LinkedHashSet<>();

    /**
     * A set of exclusion filters for the compiler.
     * <p>
     * Example:
     * <pre>
     * &lt;excludeFilters&gt;
     *   &lt;excludeFilter&gt;{@literal *}{@literal *}/Example.scala.html&lt;/excludeFilter&gt;
     * &lt;/excludeFilters&gt;
     * </pre>
     */
    @Parameter
    private Set<String> excludeFilters = new LinkedHashSet<>();

    /**
     * Source encoding for template files and generated scala files.
     * <p>
     * Example:
     * <pre>
     * &lt;sourceEncoding&gt;UTF-8&lt;/sourceEncoding&gt;
     * </pre>
     */
    @Parameter(defaultValue = "UTF-8")
    private String sourceEncoding;

    /**
     * Adding compiled scala template files to compilation source root.
     * <p>
     * Default: true
     * <p>
     * Example:
     * <pre>
     * &lt;addSourceRoot&gt;true&lt;/addSourceRoot&gt;
     * </pre>
     */
    @Parameter
    @SuppressWarnings("FieldCanBeLocal")
    private boolean addSourceRoot = true;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final var log = getLog();
        if (sourceDirectories.isEmpty()) {
            sourceDirectories.add(new File(project.getBasedir(), "/src/main/twirl"));
        }
        if (templateFormats.isEmpty()) {
            templateFormats.putAll(DEFAULT_FORMATTERS);
        }
        if (templateImports.isEmpty()) {
            templateImports.addAll(TwirlCompiler.DEFAULT_IMPORTS);
        }
        if (includeFilters.isEmpty()) {
            for (String ext : DEFAULT_FORMATTERS.keySet()) {
                includeFilters.add("**/*.scala." + ext);
            }
        }

        if (log.isDebugEnabled()) {
            final var delimiter = "; ";
            log.debug("sourceDirectories: " + sourceDirectories.stream()
                    .map(File::toString).collect(joining(delimiter)));
            log.debug("target: " + target);
            log.debug("templateFormats: " + templateFormats.entrySet().stream()
                    .map(it -> it.getKey() + " to " + it.getValue()).collect(joining(delimiter)));
            log.debug("templateImports: " + String.join(delimiter, templateImports));
            log.debug("constructorAnnotations: " + String.join(delimiter, constructorAnnotations));
            log.debug("includeFilters: " + String.join(delimiter, includeFilters));
            log.debug("excludeFilters: " + String.join(delimiter, excludeFilters));
            log.debug("sourceEncoding: " + sourceEncoding);
        }

        final var templates = findTemplates();
        if (templates.isEmpty()) {
            log.info("No twirl templates found to compile.");
            return;
        }

        log.info("Compile twirl templates.");
        final var annotations = new ArrayList<>(constructorAnnotations);
        for(Map.Entry<File, Set<File>> entry : templates.entrySet()) {
            var sourceDir = entry.getKey();
            for (File template : entry.getValue()) {
                final var extension = getExtension(template.getName());
                final var format = templateFormats.get(extension);
                final var imports = templateImports.stream().map(it -> it.replace("%format%", extension)).collect(toSet());
                log.info("Compile template: " + template);
                TwirlCompiler.compile(
                        template,
                        sourceDir,
                        target,
                        format,
                        imports,
                        annotations,
                        Codec.string2codec(sourceEncoding),
                        false);
            }
        }
        if (addSourceRoot) {
            project.addCompileSourceRoot(target.getAbsolutePath());
            log.info("Adding generated sources (scala): " + target);
        }
    }

    private Map<File, Set<File>> findTemplates() {
        final var scanner = new DirectoryScanner();
        scanner.setIncludes(includeFilters.toArray(new String[0]));
        scanner.setExcludes(excludeFilters.toArray(new String[0]));
        scanner.addDefaultExcludes();

        Map<File, Set<File>> result = new HashMap<>();
        sourceDirectories.stream()
                .filter(File::exists)
                .filter(File::isDirectory)
                .forEach(sourceDir -> {
                    scanner.setBasedir(sourceDir);
                    scanner.scan();
                    var files = stream(scanner.getIncludedFiles())
                            .filter(path -> templateFormats.containsKey(getExtension(path)))
                            .map(path -> new File(sourceDir, path))
                            .collect(toSet());
                    if (!files.isEmpty()) {
                        result.put(sourceDir, files);
                    }
                });
        return result;
    }
}
