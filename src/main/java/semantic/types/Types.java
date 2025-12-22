package semantic.types;

/**
 * Common semantic types + small factory helpers.
 */
public final class Types {
    private Types() {}

    public static final SType INT    = new SType(SType.Kind.INT, 0);
    public static final SType DOUBLE = new SType(SType.Kind.DOUBLE, 0);
    public static final SType CHAR   = new SType(SType.Kind.CHAR, 0);
    public static final SType STRING = new SType(SType.Kind.STRING, 0);
    public static final SType BOOL   = new SType(SType.Kind.BOOL, 0);
    public static final SType VOID   = new SType(SType.Kind.VOID, 0);
    public static final SType NULL   = new SType(SType.Kind.NULL, 0);

    public static SType of(SType.Kind kind, int rank) {
        return new SType(kind, rank);
    }

    public static SType arrayOf(SType baseScalarType, int extraRank) {
        if (baseScalarType == null) throw new IllegalArgumentException("baseScalarType cannot be null");
        if (!baseScalarType.isScalar()) {
            throw new IllegalArgumentException("baseScalarType must be scalar, got: " + baseScalarType);
        }
        if (extraRank < 0) throw new IllegalArgumentException("extraRank cannot be negative");
        return new SType(baseScalarType.kind(), baseScalarType.rank() + extraRank);
    }
}
