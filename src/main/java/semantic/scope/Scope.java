package semantic.scope;

import semantic.symbols.Symbol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One lexical scope (one environment frame).
 * It stores only names declared IN THIS scope.
 */
public final class Scope {
    private final Map<String, Symbol> locals = new HashMap<>();

    public boolean containsHere(String name) {
        return locals.containsKey(name);
    }

    public Symbol getHere(String name) {
        return locals.get(name);
    }

    /**
     * Defines a symbol in THIS scope (no lookup in parents).
     * Caller should check containsHere() before define().
     */
    public void define(Symbol sym) {
        locals.put(sym.name, sym);
    }

    /** Read-only view for debugging/testing. */
    public Map<String, Symbol> snapshot() {
        return Collections.unmodifiableMap(locals);
    }
}
