package semantic.scope;

import semantic.symbols.Symbol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Scope {
    private final Map<String, Symbol> locals = new HashMap<>();

    public boolean containsHere(String name) {
        return locals.containsKey(name);
    }

    public Symbol getHere(String name) {
        return locals.get(name);
    }

    public void define(Symbol sym) {
        locals.put(sym.name, sym);
    }

}
