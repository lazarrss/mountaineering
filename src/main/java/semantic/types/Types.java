package semantic.types;

public final class Types {
    private Types() {}

    public static final SType INT    = new SType(SType.Kind.INT, 0);
    public static final SType DOUBLE = new SType(SType.Kind.DOUBLE, 0);
    public static final SType CHAR   = new SType(SType.Kind.CHAR, 0);
    public static final SType STRING = new SType(SType.Kind.STRING, 0);
    public static final SType BOOL   = new SType(SType.Kind.BOOL, 0);
    public static final SType VOID   = new SType(SType.Kind.VOID, 0);
    public static final SType NULL   = new SType(SType.Kind.NULL, 0);

}
