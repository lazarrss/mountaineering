package semantic.types;

import java.util.Objects;

/**
 * Semantic type used by the semantic analyzer.
 * kind + rank (0 scalar, 1 array, 2 matrix, ...)
 */
public final class SType {

    public enum Kind {
        INT, DOUBLE, CHAR, STRING, BOOL, VOID, NULL
    }

    public final Kind kind;
    private final int rank;

    public SType(Kind kind, int rank) {
        if (kind == null) throw new IllegalArgumentException("kind cannot be null");
        if (rank < 0) throw new IllegalArgumentException("rank cannot be negative");
        if (kind == Kind.VOID && rank != 0) {
            throw new IllegalArgumentException("void type cannot have rank > 0");
        }
        if (kind == Kind.NULL && rank != 0) {
            throw new IllegalArgumentException("null type cannot have rank > 0");
        }
        this.kind = kind;
        this.rank = rank;
    }

    public Kind kind() {
        return kind;
    }

    /** 0 = scalar, 1 = [], 2 = [][] ... */
    public int rank() {
        return rank;
    }

    public boolean isScalar() {
        return rank == 0;
    }

    public boolean isArrayLike() {
        return rank > 0;
    }

    public boolean isVoid() {
        return kind == Kind.VOID && rank == 0;
    }

    public boolean isNull() {
        return kind == Kind.NULL && rank == 0;
    }

    public boolean isNumericScalar() {
        return rank == 0 && (kind == Kind.INT || kind == Kind.DOUBLE);
    }

    public boolean isIntegralScalar() {
        return rank == 0 && kind == Kind.INT;
    }

    public boolean isBoolScalar() {
        return rank == 0 && kind == Kind.BOOL;
    }

    public boolean isStringScalar() {
        return rank == 0 && kind == Kind.STRING;
    }

    public boolean isCharScalar() {
        return rank == 0 && kind == Kind.CHAR;
    }

    /**
     * Returns element type by decreasing rank by 1.
     * Example: INT[2] -> INT[1], INT[1] -> INT[0].
     */
    public SType elementType() {
        if (rank == 0) return this;
        return new SType(kind, rank - 1);
    }

    /**
     * Returns a type with the same kind and a new rank.
     */
    public SType withRank(int newRank) {
        return new SType(kind, newRank);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SType other)) return false;
        return kind == other.kind && rank == other.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, rank);
    }

    @Override
    public String toString() {
        if (rank == 0) return kind.name();
        return kind.name() + " rank=" + rank;
    }
}
