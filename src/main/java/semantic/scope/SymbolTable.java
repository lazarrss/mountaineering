package semantic.scope;

import semantic.symbols.Symbol;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

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
            pushScope();
        }
    }

    public Scope current() {
        if (stack.isEmpty()) throw new IllegalStateException("No current scope");
        return stack.peek();
    }

    public boolean declare(Symbol sym) {
        Scope cur = current();
        if (cur.containsHere(sym.name)) return false;
        cur.define(sym);
        return true;
    }

    public Symbol lookup(String name) {
        for (Scope s : stack) {
            Symbol found = s.getHere(name);
            if (found != null) return found;
        }
        return null;
    }
}
