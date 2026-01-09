package semantic.errors;

import lexer.token.Token;

public final class SemanticError {

    public enum Kind {
        NO_MAIN,
        MULTIPLE_MAIN,

        UNDECLARED_NAME,
        DUPLICATE_DECLARATION,

        INVALID_CAST,
        ARITH_TYPE_MISMATCH,
        RELATIONAL_TYPE_ERROR,
        LOGICAL_TYPE_ERROR,
        INVALID_CONDITION,

        ARRAY_ELEMENT_TYPE_MISMATCH,
        RETURN_TYPE_MISMATCH,

        PARAM_COUNT_MISMATCH,
        PARAM_TYPE_MISMATCH,
        RETURN_USED_AS_WRONG_TYPE,

        INDEXING_NON_INDEXABLE,
        CALLING_NON_FUNCTION,

        INVALID_ASSIGN_TARGET
    }

    public final Kind kind;
    public final String message;
    public final Token token; // may be null

    public SemanticError(Kind kind, String message, Token token) {
        if (kind == null) throw new IllegalArgumentException("kind cannot be null");
        if (message == null) throw new IllegalArgumentException("message cannot be null");
        this.kind = kind;
        this.message = message;
        this.token = token;
    }

    public String format() {
        if (token == null) {
            return "SEMANTIC > " + kind + ": " + message;
        }
        return "SEMANTIC > " + kind + ": " + message +
                " (line: " + token.line + ", col: " + token.colStart + ")";
    }

    @Override
    public String toString() {
        return format();
    }
}
