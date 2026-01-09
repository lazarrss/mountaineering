package semantic.symbols;

import lexer.token.Token;
import semantic.types.SType;

public final class VarSymbol extends Symbol {
    public final SType type;
    public final boolean isParam;

    public VarSymbol(String name, Token declaredAt, SType type, boolean isParam) {
        super(name, declaredAt);
        if (type == null) throw new IllegalArgumentException("type cannot be null");
        this.type = type;
        this.isParam = isParam;
    }

    public VarSymbol(String name, Token declaredAt, SType type) {
        this(name, declaredAt, type, false);
    }
}
