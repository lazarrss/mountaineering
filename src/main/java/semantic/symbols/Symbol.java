package semantic.symbols;

import lexer.token.Token;

public abstract class Symbol {
    public final String name;
    public final Token declaredAt;

    protected Symbol(String name, Token declaredAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("symbol name cannot be null/blank");
        }
        this.name = name;
        this.declaredAt = declaredAt;
    }
}
