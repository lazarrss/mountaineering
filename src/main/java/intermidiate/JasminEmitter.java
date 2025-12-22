package intermidiate;

import java.util.ArrayList;
import java.util.List;

public final class JasminEmitter {
    private final List<String> out = new ArrayList<>();

    public void emitRaw(String s) {
        out.add(s);
    }

    public void emit(String op) {
        out.add("  " + op);
    }

    public void label(String name) {
        out.add(name + ":");
    }

    public void comment(String c) {
        out.add("  ; " + c);
    }

    public List<String> lines() {
        return out;
    }
}
