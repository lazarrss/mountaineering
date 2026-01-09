package semantic.types;

import parser.ast.Ast;

public final class TypeUtil {
    private TypeUtil() {}

    public static SType fromAstType(Ast.Type t) {
        if (t == null) return null;

        return switch (t.kind) {
            case INT    -> new SType(SType.Kind.INT, t.rank);
            case DOUBLE -> new SType(SType.Kind.DOUBLE, t.rank);
            case CHAR   -> new SType(SType.Kind.CHAR, t.rank);
            case STRING -> new SType(SType.Kind.STRING, t.rank);
            case BOOL   -> new SType(SType.Kind.BOOL, t.rank);
            case VOID   -> new SType(SType.Kind.VOID, t.rank);
        };
    }

    public static boolean canAssign(SType dst, SType src) {
        if (dst == null || src == null) return false;
        if (dst.equals(src)) return true;

        if (src.isNull()) {
            return dst.isStringScalar() || dst.isArrayLike();
        }

        return false;
    }

    public static boolean canCast(SType dst, SType src) {
        if (dst == null || src == null) return false;
        if (dst.equals(src)) return true;

        if (dst.isVoid() || src.isVoid()) return false;

        // scalar-only
        if (!dst.isScalar() || !src.isScalar()) return false;

        if (src.isNull()) {
            return dst.isStringScalar();
        }

        return dst.isNumericScalar() && src.isNumericScalar();
    }

    public static boolean isIndexable(SType t) {
        return t != null && t.isArrayLike();
    }
}
