package semantic.errors;

public final class SemanticException extends RuntimeException {
    public final SemanticError error;

    public SemanticException(SemanticError error) {
        super(error == null ? null : error.format());
        if (error == null) throw new IllegalArgumentException("error cannot be null");
        this.error = error;
    }
}
