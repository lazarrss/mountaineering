package semantic.symbols;

import lexer.token.Token;

/**
 * Base class for named symbols in semantic analysis (variables, functions, ...).
 */
public abstract class Symbol {
    public final String name;
    public final Token declaredAt; // can be null, but very useful for error reporting

    protected Symbol(String name, Token declaredAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("symbol name cannot be null/blank");
        }
        this.name = name;
        this.declaredAt = declaredAt;
    }
}
