package intermidiate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JasminProgram {
    private final List<String> lines;

    public JasminProgram(List<String> lines) {
        this.lines = lines;
    }

    public List<String> lines() {
        return lines;
    }

    public String asText() {
        return String.join("\n", lines) + "\n";
    }

    public void writeTo(Path path) throws IOException {
        Files.writeString(path, asText());
    }
}
