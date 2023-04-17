import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_SOURCES;

@Mojo(name = "compile", defaultPhase = GENERATE_SOURCES, threadSafe = true)
public class CompileMojo extends AbstractMojo {

    // test Set params
    @Parameter
    private Set<File> setParam = new LinkedHashSet<>();

    // test File params
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/hello")
    private File target;

    // test Map params
    @Parameter
    private Map<String, String> mapParam = new LinkedHashMap<>();

    // test simple param
    private String simpleParam;

    // test project param
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;
    @Override
    public void execute() {
        File file = new File(target, "hello.txt");
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), "Hello from SBT!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
