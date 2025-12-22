package semantic.symbols;

import lexer.token.Token;
import semantic.types.SType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User-defined function symbol.
 * (Built-ins like shout/radio can be handled specially in SemanticAnalyzer.)
 */
public final class FuncSymbol extends Symbol {
    public final SType returnType;
    public final List<SType> paramTypes;

    public FuncSymbol(String name, Token declaredAt, SType returnType, List<SType> paramTypes) {
        super(name, declaredAt);
        if (returnType == null) throw new IllegalArgumentException("returnType cannot be null");
        if (paramTypes == null) throw new IllegalArgumentException("paramTypes cannot be null");
        this.returnType = returnType;
        this.paramTypes = List.copyOf(paramTypes);
    }

    public int arity() {
        return paramTypes.size();
    }
}
