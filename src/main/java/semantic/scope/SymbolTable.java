package semantic.scope;

import semantic.symbols.Symbol;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Stack of scopes + lookup rules (static/lexical scoping).
 */
public final class SymbolTable {
    private final Deque<Scope> stack = new ArrayDeque<>();

    public SymbolTable() {
        // global scope by default
        pushScope();
    }

    public void pushScope() {
        stack.push(new Scope());
    }

    public void popScope() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Cannot pop scope: stack is empty");
        }
        stack.pop();
        if (stack.isEmpty()) {
            // keep at least one scope so analyzer doesn't have to special-case
            pushScope();
        }
    }

    public Scope current() {
        if (stack.isEmpty()) throw new IllegalStateException("No current scope");
        return stack.peek();
    }

    /**
     * Declares a symbol in the current scope.
     * @return true if inserted, false if name already exists in current scope.
     */
    public boolean declare(Symbol sym) {
        Scope cur = current();
        if (cur.containsHere(sym.name)) return false;
        cur.define(sym);
        return true;
    }

    /**
     * Lookup by searching from the current scope outward.
     * @return the first matching symbol, or null if not found.
     */
    public Symbol lookup(String name) {
        for (Scope s : stack) {
            Symbol found = s.getHere(name);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Lookup only in current scope (useful for redeclaration checks).
     */
    public Symbol lookupHere(String name) {
        return current().getHere(name);
    }

    public int depth() {
        return stack.size();
    }

    /** Useful for debugging if you want to print scopes from inner to outer. */
    public Iterator<Scope> scopesFromInnerToOuter() {
        return stack.iterator();
    }
}
