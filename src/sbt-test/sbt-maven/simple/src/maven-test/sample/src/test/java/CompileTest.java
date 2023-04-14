import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class CompileTest {
    @Test
    public void testHello() throws IOException {
        File hello = new File("target/generated-sources/hello/hello.txt");
        assertThat(hello).exists();
        String content = Files.readString(hello.toPath(), UTF_8);
        assertThat(content).contains("Hello from SBT!");
    }
}
