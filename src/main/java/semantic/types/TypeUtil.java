package semantic.types;

import parser.ast.Ast;

/**
 * Utilities for converting parser AST types into semantic types and basic predicates.
 */
public final class TypeUtil {
    private TypeUtil() {}

    /**
     * Convert parser Ast.Type -> semantic SType.
     * Returns null if input is null.
     */
    public static SType fromAstType(Ast.Type t) {
        if (t == null) return null;

        // Your Ast.Type has: kind (enum), rank (int), baseType (Token) :contentReference[oaicite:1]{index=1}
        // We only need kind + rank.
        return switch (t.kind) {
            case INT    -> new SType(SType.Kind.INT, t.rank);
            case DOUBLE -> new SType(SType.Kind.DOUBLE, t.rank);
            case CHAR   -> new SType(SType.Kind.CHAR, t.rank);
            case STRING -> new SType(SType.Kind.STRING, t.rank);
            case BOOL   -> new SType(SType.Kind.BOOL, t.rank);
            case VOID   -> new SType(SType.Kind.VOID, t.rank);
        };
    }

    public static boolean same(SType a, SType b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public static boolean isNumericScalar(SType t) {
        return t != null && t.isNumericScalar();
    }

    public static boolean isBoolScalar(SType t) {
        return t != null && t.isBoolScalar();
    }

    /**
     * Assignment compatibility WITHOUT implicit casting.
     * - Same exact type is allowed.
     * - NULL handling: by default, only allow assigning NULL to STRING (and later possibly arrays)
     *   You can expand this later if you decide null is allowed for more types.
     */
    public static boolean canAssign(SType dst, SType src) {
        if (dst == null || src == null) return false;
        if (dst.equals(src)) return true;

        // whiteout/null rules:
        // - allow NULL to STRING scalar
        // - allow NULL to any array/matrix (rank > 0)
        if (src.isNull()) {
            return dst.isStringScalar() || dst.isArrayLike();
        }

        // no implicit casting otherwise
        return false;
    }

    /**
     * Explicit cast rules.
     * - No arrays/matrices casting (rank must be 0 for both sides).
     * - No casting involving VOID.
     * - Allowed:
     *   altitude <-> slope
     *   altitude <-> mark
     *   (route) whiteout  (optional convenience)
     */
    /**
     * Explicit cast rules (SPEC-aligned):
     * - Cast is allowed ONLY between numeric scalar types:
     *     altitude (int) <-> slope (double)
     * - No implicit casts (this is explicit-cast helper only).
     * - No casting involving VOID.
     * - No array/matrix casting (rank must be 0 for both sides).
     * - Optional: allow (route) whiteout (null)  [keep if you want]
     *
     * NOTE: The SPEC special rule for slope -> altitude (only if fractional part is 0)
     * cannot be fully decided here for non-literals. Enforce that rule in SemanticAnalyzer.visitCast
     * when the source is a slope literal.
     */
    public static boolean canCast(SType dst, SType src) {
        if (dst == null || src == null) return false;
        if (dst.equals(src)) return true;

        if (dst.isVoid() || src.isVoid()) return false;

        // scalar-only
        if (!dst.isScalar() || !src.isScalar()) return false;

        // optional convenience: allow casting null only to string
        if (src.isNull()) {
            return dst.isStringScalar();
        }

        // SPEC: numeric casts only (altitude <-> slope)
        // this allows int<->double and int->int/double->double etc.
        // the stricter slope->altitude fractional rule is checked in SemanticAnalyzer.visitCast
        return dst.isNumericScalar() && src.isNumericScalar();
    }




    /**
     * Whether indexing is supported: rank must be > 0.
     */
    public static boolean isIndexable(SType t) {
        return t != null && t.isArrayLike();
    }
}
