package semantic;

import lexer.token.Token;
import lexer.token.TokenType;
import parser.ast.Ast;
import parser.ast.Expr;
import parser.ast.Stmt;
import semantic.errors.SemanticError;
import semantic.errors.SemanticException;
import semantic.scope.SymbolTable;
import semantic.symbols.FuncSymbol;
import semantic.symbols.Symbol;
import semantic.symbols.VarSymbol;
import semantic.types.SType;
import semantic.types.TypeUtil;
import semantic.types.Types;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static lexer.token.TokenType.*;

public final class SemanticAnalyzer implements Expr.Visitor<SType>, Stmt.Visitor<Void> {

    private final SymbolTable symbols = new SymbolTable();
    private final Map<Expr, SType> exprTypes = new IdentityHashMap<>();

    private FuncSymbol currentFunction = null;
    private int summitCount = 0;

    private Ast.Program programRef;

    private final java.util.IdentityHashMap<VarSymbol, Double> knownSlopeValues = new java.util.IdentityHashMap<>();

    public void analyze(Ast.Program program) {
        if (program == null) return;

        this.programRef = program;

        // pass 1: declare all function headers first (recursion ok)
        for (Ast.TopItem it : program.items) {
            if (it instanceof Ast.FuncDef fd) {
                declareFunctionHeader(fd);
            }
        }

        // pass 2: analyze top-level var decls + function bodies
        for (Ast.TopItem it : program.items) {
            if (it instanceof Ast.TopVarDecl tv) {
                visitStmt(tv.decl);
            } else if (it instanceof Ast.FuncDef fd) {
                analyzeFunctionBody(fd);
            }
        }

        if (summitCount == 0) {
            error(SemanticError.Kind.NO_MAIN,
                    "Missing main function: routeplan basecamp summit() { ... }",
                    null);
        } else if (summitCount > 1) {
            error(SemanticError.Kind.MULTIPLE_MAIN,
                    "Multiple main functions detected (more than one 'summit').",
                    null);
        }

    }

    public Map<Expr, SType> getExprTypes() {
        return exprTypes;
    }

    // -----------------------------
    // Top-level: functions
    // -----------------------------

    private void declareFunctionHeader(Ast.FuncDef fd) {
        String fname = fd.name.lexeme;

        SType ret = TypeUtil.fromAstType(fd.returnType);
        if (ret == null) {
            error(SemanticError.Kind.RETURN_TYPE_MISMATCH,
                    "Function return type is null",
                    fd.name);
        }

        List<SType> params = new ArrayList<>();
        for (Ast.Param p : fd.params) {
            SType pt = TypeUtil.fromAstType(p.type);
            if (pt == null) {
                error(SemanticError.Kind.PARAM_TYPE_MISMATCH,
                        "Parameter '" + p.name.lexeme + "' has invalid type.",
                        p.name);
            }
            if (pt.isVoid()) {
                error(SemanticError.Kind.PARAM_TYPE_MISMATCH,
                        "Parameter '" + p.name.lexeme + "' cannot be basecamp/void.",
                        p.name);
            }
            params.add(pt);
        }

        FuncSymbol fs = new FuncSymbol(fname, fd.name, ret, params);

        if (!symbols.declare(fs)) {
            error(SemanticError.Kind.DUPLICATE_DECLARATION,
                    "Duplicate function declaration: '" + fname + "'.",
                    fd.name);
        }

        if ("summit".equals(fname)) summitCount++;
    }

    private void analyzeFunctionBody(Ast.FuncDef fd) {
        Symbol sym = symbols.lookup(fd.name.lexeme);
        if (!(sym instanceof FuncSymbol fs)) {
            error(SemanticError.Kind.UNDECLARED_NAME,
                    "Internal error: function symbol not found for '" + fd.name.lexeme + "'.",
                    fd.name);
            return;
        }

        FuncSymbol prev = currentFunction;
        currentFunction = fs;

        symbols.pushScope();
        try {
            // declare params in function scope
            for (int i = 0; i < fd.params.size(); i++) {
                Ast.Param p = fd.params.get(i);
                SType pt = fs.paramTypes.get(i);

                VarSymbol vs = new VarSymbol(p.name.lexeme, p.name, pt, true);
                if (!symbols.declare(vs)) {
                    error(SemanticError.Kind.DUPLICATE_DECLARATION,
                            "Duplicate parameter name: '" + p.name.lexeme + "'.",
                            p.name);
                }
            }

            for (Stmt s : fd.body) {
                visitStmt(s);
            }
        } finally {
            symbols.popScope();
            currentFunction = prev;
        }
    }

    // -----------------------------
    // Stmt dispatch
    // -----------------------------

    private void visitStmt(Stmt s) {
        if (s != null) s.accept(this);
    }

    private void visitBlock(List<Stmt> block) {
        if (block == null) return;
        for (Stmt s : block) visitStmt(s);
    }

    // -----------------------------
    // Stmt visitors
    // -----------------------------

    @Override
    public Void visitVarDecl(Stmt.VarDecl s) {
        if (s == null) return null;

        Ast.Type baseAstType = (Ast.Type) reflectFieldOrNull(s, "type");
        SType baseType = TypeUtil.fromAstType(baseAstType);

        if (baseType == null) {
            error(SemanticError.Kind.ARITH_TYPE_MISMATCH, "Invalid variable base type.", null);
            return null;
        }
        if (baseType.isVoid()) {
            error(SemanticError.Kind.DUPLICATE_DECLARATION,
                    "Variables cannot be declared as basecamp/void.",
                    baseAstType.baseType);
            return null;
        }

        Object varsObj = reflectFieldOrNull(s, "vars");

        // NEW MODE: s.vars = [{name, dims[], init}, ...]
        if (varsObj instanceof List<?> varsList) {
            for (Object vi : varsList) {
                Token nameTok = (Token) reflectFieldOrNull(vi, "name");
                @SuppressWarnings("unchecked")
                List<Expr> dims = (List<Expr>) reflectFieldOrNull(vi, "dims");
                Expr init = (Expr) reflectFieldOrNull(vi, "init");

                handleOneDeclarator(baseType, nameTok, dims, init);
            }
            return null;
        }

        // OLD MODE: s.dims + s.names
        @SuppressWarnings("unchecked")
        List<Expr> sharedDims = (List<Expr>) reflectFieldOrNull(s, "dims");
        @SuppressWarnings("unchecked")
        List<Token> names = (List<Token>) reflectFieldOrNull(s, "names");

        if (names != null) {
            for (Token nameTok : names) {
                handleOneDeclarator(baseType, nameTok, sharedDims, null);
            }
        }

        return null;
    }

    /**
     * Declares exactly one variable declarator:
     *   name [dims...] [<> init]
     * Works for both NEW and OLD AST shapes.
     */
    private void handleOneDeclarator(SType baseType, Token nameTok, List<Expr> dims, Expr init) {
        if (nameTok == null) {
            error(SemanticError.Kind.DUPLICATE_DECLARATION,
                    "Invalid var declarator (missing name token).",
                    null);
            return;
        }

        int extraRank = (dims == null) ? 0 : dims.size();

        // validate dims (must be altitude/int scalar)
        if (dims != null) {
            for (Expr d : dims) {
                SType dt = typeOf(d);
                if (dt == null || !dt.isIntegralScalar()) {
                    error(SemanticError.Kind.ARITH_TYPE_MISMATCH,
                            "Array/matrix dimension must be altitude (int) scalar.",
                            tokenOf(d));
                    return;
                }
            }
        }

        // final declared type
        SType fullType = baseType.withRank(baseType.rank() + extraRank);

        // forbid init of array/matrix
        if (fullType.isArrayLike() && init != null) {
            error(SemanticError.Kind.INVALID_ASSIGN_TARGET,
                    "Cannot initialize an array/matrix in declaration: '" + nameTok.lexeme + "'.",
                    nameTok);
            return;
        }

        VarSymbol vs = new VarSymbol(nameTok.lexeme, nameTok, fullType);
        if (!symbols.declare(vs)) {
            error(SemanticError.Kind.DUPLICATE_DECLARATION,
                    "Duplicate variable declaration: '" + nameTok.lexeme + "'.",
                    nameTok);
            return;
        }

        // initializer type check + remember constant slope value if initializer is slope literal
        if (init != null) {
            SType it = typeOf(init);
            if (!TypeUtil.canAssign(fullType, it)) {
                error(SemanticError.Kind.ARRAY_ELEMENT_TYPE_MISMATCH,
                        "Cannot assign initializer of type " + it + " to variable '" + nameTok.lexeme + "' of type " + fullType + ".",
                        nameTok);
                return;
            }

            // track only: slope s <> 7.0;
            if (fullType.isScalar()
                    && fullType.kind == SType.Kind.DOUBLE
                    && init instanceof Expr.Literal lit
                    && lit.token != null
                    && lit.token.type == TokenType.SLOPE_LIT
                    && lit.value instanceof Double dv) {

                knownSlopeValues.put(vs, dv);
            }
        } else {
            // declared without init -> value unknown
            if (fullType.isScalar() && fullType.kind == SType.Kind.DOUBLE) {
                knownSlopeValues.remove(vs);
            }
        }
    }


    @Override
    public Void visitReturn(Stmt.Return s) {
        if (currentFunction == null) {
            error(SemanticError.Kind.RETURN_TYPE_MISMATCH,
                    "Return statement is not inside a function.",
                    s.keyword);
        }

        SType expected = currentFunction.returnType;

        if (expected.isVoid()) {
            if (s.expr != null) {
                error(SemanticError.Kind.RETURN_TYPE_MISMATCH,
                        "Function '" + currentFunction.name + "' is basecamp/void, return must not have a value.",
                        s.keyword);
            }
            return null;
        }

        if (s.expr == null) {
            error(SemanticError.Kind.RETURN_TYPE_MISMATCH,
                    "Function '" + currentFunction.name + "' must return a value of type " + expected + ".",
                    s.keyword);
        }

        SType actual = typeOf(s.expr);
        if (!TypeUtil.canAssign(expected, actual)) {
            error(SemanticError.Kind.RETURN_TYPE_MISMATCH,
                    "Return type mismatch: expected " + expected + ", got " + actual + ".",
                    tokenOf(s.expr));
        }

        return null;
    }

    @Override
    public Void visitExprStmt(Stmt.ExprStmt s) {
        if (s == null) return null;
        typeOf(s.expr);
        return null;
    }

    @Override
    public Void visitIf(Stmt.If s) {
        SType ct = typeOf(s.condition);
        if (ct == null || !ct.isBoolScalar()) {
            error(SemanticError.Kind.INVALID_CONDITION,
                    "ifridge condition must be secure (bool) scalar.",
                    tokenOf(s.condition));
        }

        symbols.pushScope();
        try {
            visitBlock(s.thenBranch);
        } finally {
            symbols.popScope();
        }

        if (s.elseBranch != null) {
            symbols.pushScope();
            try {
                visitBlock(s.elseBranch);
            } finally {
                symbols.popScope();
            }
        }

        return null;
    }

    @Override
    public Void visitClimb(Stmt.Climb s) {
        symbols.pushScope();
        try {
            if (s.initType != null) {
                SType declaredType = TypeUtil.fromAstType(s.initType);
                if (declaredType == null || declaredType.isVoid()) {
                    error(SemanticError.Kind.ARRAY_ELEMENT_TYPE_MISMATCH,
                            "climb init variable cannot be basecamp/void.",
                            s.initType.baseType);
                }

                VarSymbol loopVar = new VarSymbol(s.varName.lexeme, s.varName, declaredType);
                if (!symbols.declare(loopVar)) {
                    error(SemanticError.Kind.DUPLICATE_DECLARATION,
                            "Duplicate declaration in climb init: '" + s.varName.lexeme + "'.",
                            s.varName);
                }

                SType initT = typeOf(s.initExpr);
                if (!TypeUtil.canAssign(declaredType, initT)) {
                    error(SemanticError.Kind.ARRAY_ELEMENT_TYPE_MISMATCH,
                            "climb init assignment type mismatch: cannot assign " + initT + " to " + declaredType + ".",
                            tokenOf(s.initExpr));
                }
            } else {
                Symbol sym = symbols.lookup(s.varName.lexeme);
                if (!(sym instanceof VarSymbol)) {
                    error(SemanticError.Kind.UNDECLARED_NAME,
                            "climb uses undeclared loop variable: '" + s.varName.lexeme + "'.",
                            s.varName);
                }
                VarSymbol loopVar = (VarSymbol) sym;

                SType initT = typeOf(s.initExpr);
                if (!TypeUtil.canAssign(loopVar.type, initT)) {
                    error(SemanticError.Kind.ARRAY_ELEMENT_TYPE_MISMATCH,
                            "climb init assignment type mismatch: cannot assign " + initT + " to " + loopVar.type + ".",
                            tokenOf(s.initExpr));
                }
            }

            if (s.condition != null) {
                SType condT = typeOf(s.condition);
                if (condT == null || !condT.isBoolScalar()) {
                    error(SemanticError.Kind.INVALID_CONDITION,
                            "climb condition must be secure (bool) scalar.",
                            tokenOf(s.condition));
                }
            }

            typeOf(s.update);

            symbols.pushScope();
            try {
                visitBlock(s.body);
            } finally {
                symbols.popScope();
            }

            return null;
        } finally {
            symbols.popScope();
        }
    }


    @Override
    public Void visitWhile(Stmt.While s) {
        SType ct = typeOf(s.condition);
        if (ct == null || !ct.isBoolScalar()) {
            error(SemanticError.Kind.INVALID_CONDITION,
                    "belay condition must be secure (bool) scalar.",
                    tokenOf(s.condition));
        }

        symbols.pushScope();
        try {
            visitBlock(s.body);
        } finally {
            symbols.popScope();
        }

        return null;
    }

    @Override
    public Void visitDoWhile(Stmt.DoWhile s) {
        symbols.pushScope();
        try {
            visitBlock(s.body);
        } finally {
            symbols.popScope();
        }

        SType ct = typeOf(s.condition);
        if (ct == null || !ct.isBoolScalar()) {
            error(SemanticError.Kind.INVALID_CONDITION,
                    "on (...) condition must be secure (bool) scalar.",
                    tokenOf(s.condition));
        }

        return null;
    }

    // -----------------------------
    // Expr typing
    // -----------------------------

    private SType typeOf(Expr e) {
        if (e == null) return null;
        SType cached = exprTypes.get(e);
        if (cached != null) return cached;
        SType t = e.accept(this);
        exprTypes.put(e, t);
        return t;
    }

    @Override
    public SType visitLiteral(Expr.Literal e) {
        if (e.token == null) return null;

        TokenType tt = e.token.type;
        if (tt == ALTITUDE_LIT) return Types.INT;
        if (tt == SLOPE_LIT) return Types.DOUBLE;
        if (tt == MARK_LIT) return Types.CHAR;
        if (tt == ROUTE_LIT) return Types.STRING;
        if (tt == ANCHORED || tt == LOOSE) return Types.BOOL;
        if (tt == WHITEOUT) return Types.NULL;

        return null;
    }

    @Override
    public SType visitIdent(Expr.Ident e) {
        // parser can create Ident with SHOUT/RADIO/BELAY tokens too
        if (e.name.type != IDENT) {
            error(SemanticError.Kind.RETURN_USED_AS_WRONG_TYPE,
                    "Keyword '" + e.name.lexeme + "' cannot be used as a variable name.",
                    e.name);
        }

        Symbol sym = symbols.lookup(e.name.lexeme);
        if (sym == null) {
            error(SemanticError.Kind.UNDECLARED_NAME,
                    "Undeclared name: '" + e.name.lexeme + "'.",
                    e.name);
        }
        if (sym instanceof VarSymbol vs) return vs.type;

        error(SemanticError.Kind.RETURN_USED_AS_WRONG_TYPE,
                "Name '" + e.name.lexeme + "' refers to a function, not a variable.",
                e.name);
        return null;
    }

    @Override
    public SType visitIndex(Expr.Index e) {
        Symbol sym = symbols.lookup(e.name.lexeme);
        if (sym == null) {
            error(SemanticError.Kind.UNDECLARED_NAME,
                    "Undeclared name: '" + e.name.lexeme + "'.",
                    e.name);
        }
        if (!(sym instanceof VarSymbol)) {
            error(SemanticError.Kind.INDEXING_NON_INDEXABLE,
                    "Cannot index non-variable name: '" + e.name.lexeme + "'.",
                    e.name);
        }
        VarSymbol vs = (VarSymbol) sym;

        SType base = vs.type;
        if (!TypeUtil.isIndexable(base)) {
            error(SemanticError.Kind.INDEXING_NON_INDEXABLE,
                    "Cannot index non-array/matrix variable '" + e.name.lexeme + "' of type " + base + ".",
                    e.name);
        }

        int idxCount = (e.indices == null) ? 0 : e.indices.size();
        if (idxCount == 0) {
            error(SemanticError.Kind.INDEXING_NON_INDEXABLE,
                    "Index operator requires at least one index.",
                    e.name);
        }

        for (Expr ix : e.indices) {
            SType it = typeOf(ix);
            if (it == null || !it.isIntegralScalar()) {
                error(SemanticError.Kind.ARITH_TYPE_MISMATCH,
                        "Index must be altitude (int) scalar.",
                        tokenOf(ix));
            }
        }

        if (idxCount > base.rank()) {
            error(SemanticError.Kind.INDEXING_NON_INDEXABLE,
                    "Too many indices for '" + e.name.lexeme + "': type is " + base + " but got " + idxCount + ".",
                    e.name);
        }

        return base.withRank(base.rank() - idxCount);
    }


    @Override
    public SType visitGrouping(Expr.Grouping e) {
        return typeOf(e.inner);
    }

    @Override
    public SType visitCall(Expr.Call e) {
        if (e.callee.type == SHOUT) {
            if (e.args != null) for (Expr a : e.args) typeOf(a);
            return Types.VOID;
        }

        if (e.callee.type == RADIO) {
            if (e.args == null || e.args.isEmpty()) {
                return Types.STRING;
            }
            for (Expr a : e.args) {
                requireLValue(a, "radio(...) requires lvalue arguments.");
            }
            return Types.VOID;
        }

        if (e.callee.type != IDENT) {
            error(SemanticError.Kind.CALLING_NON_FUNCTION,
                    "Cannot call '" + e.callee.lexeme + "' as a function.",
                    e.callee);
        }

        Symbol sym = symbols.lookup(e.callee.lexeme);
        if (sym == null) {
            error(SemanticError.Kind.UNDECLARED_NAME,
                    "Undeclared function: '" + e.callee.lexeme + "'.",
                    e.callee);
        }
        if (!(sym instanceof FuncSymbol)) {
            error(SemanticError.Kind.CALLING_NON_FUNCTION,
                    "Name '" + e.callee.lexeme + "' is not a function.",
                    e.callee);
        }
        FuncSymbol fs = (FuncSymbol) sym;

        int got = (e.args == null) ? 0 : e.args.size();
        int exp = fs.arity();
        if (got != exp) {
            error(SemanticError.Kind.PARAM_COUNT_MISMATCH,
                    "Function '" + fs.name + "' expects " + exp + " args, got " + got + ".",
                    e.callee);
        }

        for (int i = 0; i < got; i++) {
            SType argT = typeOf(e.args.get(i));
            SType parT = fs.paramTypes.get(i);
            if (!TypeUtil.canAssign(parT, argT)) {
                error(SemanticError.Kind.PARAM_TYPE_MISMATCH,
                        "Arg " + (i + 1) + " of '" + fs.name + "' expects " + parT + ", got " + argT + ".",
                        tokenOf(e.args.get(i)));
            }
        }

        return fs.returnType;
    }


    @Override
    public SType visitBinary(Expr.Binary e) {
        TokenType op = e.op.type;

        // assignment "<>"
        if (op == ASSIGN) {
            SType lt = typeOfLValue(e.left);
            SType rt = typeOf(e.right);

            if (!TypeUtil.canAssign(lt, rt)) {
                error(SemanticError.Kind.ARRAY_ELEMENT_TYPE_MISMATCH,
                        "Cannot assign " + rt + " to " + lt + ".",
                        e.op);
            }

            if (e.left instanceof Expr.Ident id) {
                Symbol sym = symbols.lookup(id.name.lexeme);
                if (sym instanceof VarSymbol vs) {
                    if (vs.type.isScalar() && vs.type.kind == SType.Kind.DOUBLE) {
                        if (e.right instanceof Expr.Literal lit
                                && lit.token != null
                                && lit.token.type == TokenType.SLOPE_LIT
                                && lit.value instanceof Double dv) {
                            knownSlopeValues.put(vs, dv);
                        } else {
                            knownSlopeValues.remove(vs);
                        }
                    }
                }
            }

            return lt;
        }

        // logical
        if (op == AND || op == OR) {
            SType a = typeOf(e.left);
            SType b = typeOf(e.right);
            if (a == null || b == null || !a.isBoolScalar() || !b.isBoolScalar()) {
                error(SemanticError.Kind.LOGICAL_TYPE_ERROR,
                        "Logical operator '" + e.op.lexeme + "' requires secure (bool) scalar operands.",
                        e.op);
            }
            return Types.BOOL;
        }

        // equality
        if (op == EQ || op == NEQ) {
            SType a = typeOf(e.left);
            SType b = typeOf(e.right);

            if (a != null && a.isNull()) {
                if (!(b != null && (b.isStringScalar() || b.isArrayLike()))) {
                    error(SemanticError.Kind.RELATIONAL_TYPE_ERROR,
                            "Cannot compare whiteout/null with " + b + ".",
                            e.op);
                }
                return Types.BOOL;
            }
            if (b != null && b.isNull()) {
                if (!(a != null && (a.isStringScalar() || a.isArrayLike()))) {
                    error(SemanticError.Kind.RELATIONAL_TYPE_ERROR,
                            "Cannot compare " + a + " with whiteout/null.",
                            e.op);
                }
                return Types.BOOL;
            }

            if (a == null || b == null || !a.isScalar() || !b.isScalar() || !a.equals(b)) {
                error(SemanticError.Kind.RELATIONAL_TYPE_ERROR,
                        "Equality operator '" + e.op.lexeme + "' requires operands of the same scalar type.",
                        e.op);
            }
            return Types.BOOL;
        }

        // relational numeric
        if (op == LT || op == LE || op == GT || op == GE) {
            SType a = typeOf(e.left);
            SType b = typeOf(e.right);
            if (a == null || b == null || !a.isNumericScalar() || !b.isNumericScalar() || !a.equals(b)) {
                error(SemanticError.Kind.RELATIONAL_TYPE_ERROR,
                        "Relational operator '" + e.op.lexeme + "' requires same numeric scalar operands.",
                        e.op);
            }
            return Types.BOOL;
        }

        // arithmetic
        if (op == PLUS || op == MINUS || op == MULTIPLY || op == DIV || op == POW || op == MODULO) {
            SType a = typeOf(e.left);
            SType b = typeOf(e.right);

            if ((a != null && a.isVoid()) || (b != null && b.isVoid())) {
                error(SemanticError.Kind.RETURN_USED_AS_WRONG_TYPE,
                        "Cannot use basecamp/void value in arithmetic expression.",
                        e.op);
            }

            if (a == null || b == null || !a.isNumericScalar() || !b.isNumericScalar() || !a.equals(b)) {
                error(SemanticError.Kind.ARITH_TYPE_MISMATCH,
                        "Arithmetic operator '" + e.op.lexeme + "' requires same numeric scalar operands.",
                        e.op);
            }

            if (op == MODULO && !a.isIntegralScalar()) {
                error(SemanticError.Kind.ARITH_TYPE_MISMATCH,
                        "Operator '%' requires altitude (int) operands.",
                        e.op);
            }

            return a;
        }

        error(SemanticError.Kind.ARITH_TYPE_MISMATCH,
                "Unsupported binary operator: '" + e.op.lexeme + "'.",
                e.op);
        return null;
    }

    @Override
    public SType visitUnary(Expr.Unary e) {
        TokenType op = e.op.type;

        if (op == NOT) {
            SType t = typeOf(e.expr);
            if (t == null || !t.isBoolScalar()) {
                error(SemanticError.Kind.LOGICAL_TYPE_ERROR,
                        "Operator 'not' requires secure (bool) scalar operand.",
                        e.op);
            }
            return Types.BOOL;
        }

        if (op == PLUS || op == MINUS) {
            SType t = typeOf(e.expr);
            if (t == null || !t.isNumericScalar()) {
                error(SemanticError.Kind.ARITH_TYPE_MISMATCH,
                        "Unary '" + e.op.lexeme + "' requires numeric scalar operand.",
                        e.op);
            }
            return t;
        }

        // postfix ++ / -- are represented as Expr.Unary with PLUS2/MINUS2 in your parser
        if (op == PLUS2 || op == MINUS2) {
            SType lv = typeOfLValue(e.expr);
            if (lv == null || !lv.isNumericScalar()) {
                error(SemanticError.Kind.ARITH_TYPE_MISMATCH,
                        "Increment/decrement requires numeric scalar lvalue.",
                        e.op);
            }
            return lv;
        }

        error(SemanticError.Kind.ARITH_TYPE_MISMATCH,
                "Unsupported unary operator: '" + e.op.lexeme + "'.",
                e.op);
        return null;
    }

    // -----------------------------
    // LValue helpers
    // -----------------------------

    private void requireLValue(Expr e, String msg) {
        if (e instanceof Expr.Ident) return;
        if (e instanceof Expr.Index) return;
        error(SemanticError.Kind.INVALID_ASSIGN_TARGET, msg, tokenOf(e));
    }

    private SType typeOfLValue(Expr e) {
        if (e instanceof Expr.Ident) {
            Expr.Ident id = (Expr.Ident) e;

            Symbol sym = symbols.lookup(id.name.lexeme);
            if (sym == null) {
                error(SemanticError.Kind.UNDECLARED_NAME,
                        "Undeclared name: '" + id.name.lexeme + "'.",
                        id.name);
            }
            if (!(sym instanceof VarSymbol)) {
                error(SemanticError.Kind.INVALID_ASSIGN_TARGET,
                        "Left side must be a variable (lvalue): '" + id.name.lexeme + "'.",
                        id.name);
            }
            return ((VarSymbol) sym).type;
        }

        if (e instanceof Expr.Index) {
            return visitIndex((Expr.Index) e); // ovo već validira i indeksiranje
        }

        error(SemanticError.Kind.INVALID_ASSIGN_TARGET,
                "Left side of assignment must be an lvalue (variable or indexed element).",
                tokenOf(e));
        return null;
    }



    // -----------------------------
    // Errors & token helpers
    // -----------------------------

    private void error(SemanticError.Kind kind, String message, Token token) {
        throw new SemanticException(new SemanticError(kind, message, token));
    }

    private Token tokenOf(Expr e) {
        return switch (e) {
            case Expr.Literal lit -> lit.token;
            case Expr.Ident id -> id.name;
            case Expr.Index ix -> ix.name;
            case Expr.Binary b -> b.op;
            case Expr.Unary u -> u.op;
            case Expr.Call c -> c.callee;
            case Expr.Grouping g -> tokenOf(g.inner);
            case Expr.Cast c -> c.castTok;
            case null, default -> null;
        };

    }

    private static Object reflectFieldOrNull(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Ast.FuncDef findFuncDefByName(String name) {
        if (programRef == null || programRef.items == null) return null;
        for (Ast.TopItem it : programRef.items) {
            if (it instanceof Ast.FuncDef fd) {
                if (fd.name != null && name.equals(fd.name.lexeme)) return fd;
            }
        }
        return null;
    }

    private void collectReturns(List<Stmt> stmts, List<Stmt.Return> out) {
        if (stmts == null) return;
        for (Stmt s : stmts) {
            if (s instanceof Stmt.Return r) {
                out.add(r);
            } else if (s instanceof Stmt.If iff) {
                collectReturns(iff.thenBranch, out);
                collectReturns(iff.elseBranch, out);
            } else if (s instanceof Stmt.Climb c) {
                collectReturns(c.body, out);
            } else if (s instanceof Stmt.While w) {
                collectReturns(w.body, out);
            } else if (s instanceof Stmt.DoWhile dw) {
                collectReturns(dw.body, out);
            }
            // else pass
        }
    }


    @Override
    public SType visitCast(Expr.Cast e) {
        // target type from AST (altitude/slope/mark/route/secure)
        SType target = TypeUtil.fromAstType(e.targetType);
        if (target == null) {
            error(SemanticError.Kind.INVALID_CAST, "Invalid cast target type.", e.castTok);
            return null;
        }

        // no casting to void
        if (target.isVoid()) {
            error(SemanticError.Kind.INVALID_CAST, "Cannot cast to basecamp/void.", e.castTok);
            return null;
        }

        // scalar only (no arrays/matrices)
        if (!target.isScalar()) {
            error(SemanticError.Kind.INVALID_CAST,
                    "Cast target must be a scalar type (no arrays/matrices).",
                    e.castTok);
            return null;
        }

        SType src = typeOf(e.expr);

        // SPEC RULE: slope(double) -> altitude(int) only if fractional part is 0...0
        if (target.isIntegralScalar()
                && src != null
                && src.isScalar()
                && src.kind == SType.Kind.DOUBLE) {

            // 1) literal: (altitude) 7.0 / 7.2
            if (e.expr instanceof Expr.Literal lit
                    && lit.token != null
                    && lit.token.type == TokenType.SLOPE_LIT
                    && lit.value instanceof Double dv) {

                double v = dv.doubleValue();
                if (v != Math.rint(v)) {
                    error(SemanticError.Kind.INVALID_CAST,
                            "Invalid cast: slope->altitude allowed only when fractional part is 0.",
                            e.castTok);
                    return null;
                }
                return target; // ok
            }

            // 2) variable: (altitude) s  (only if we know s was initialized with slope literal)
            if (e.expr instanceof Expr.Ident id) {
                Symbol sym = symbols.lookup(id.name.lexeme);
                if (!(sym instanceof VarSymbol vs)) {
                    error(SemanticError.Kind.UNDECLARED_NAME,
                            "Undeclared name: '" + id.name.lexeme + "'.",
                            id.name);
                    return null;
                }

                Double known = knownSlopeValues.get(vs);
                if (known == null) {
                    error(SemanticError.Kind.INVALID_CAST,
                            "Invalid cast: cannot prove slope->altitude is safe for variable '" + id.name.lexeme +
                                    "'. It must be initialized with a slope literal whose fractional part is 0.",
                            e.castTok);
                    return null;
                }

                double v = known.doubleValue();
                if (v != Math.rint(v)) {
                    error(SemanticError.Kind.INVALID_CAST,
                            "Invalid cast: slope->altitude not allowed because '" + id.name.lexeme +
                                    "' is " + known + " (fractional part is not 0).",
                            e.castTok);
                    return null;
                }

                return target; // ok
            }

            // 3) function call: (altitude) func()
            // We check ONLY if function returns direct slope literal(s).
            // If function returns variable/expression, we do NOT check (allowed by your rule).
            if (e.expr instanceof Expr.Call call
                    && call.callee != null
                    && call.callee.type == TokenType.IDENT) {

                Ast.FuncDef fd = findFuncDefByName(call.callee.lexeme);
                if (fd != null) {
                    List<Stmt.Return> returns = new ArrayList<>();
                    collectReturns(fd.body, returns);

                    if (!returns.isEmpty()) {
                        boolean allReturnsAreSlopeLiterals = true;

                        for (Stmt.Return r : returns) {
                            if (!(r.expr instanceof Expr.Literal rl)
                                    || rl.token == null
                                    || rl.token.type != TokenType.SLOPE_LIT
                                    || !(rl.value instanceof Double)) {
                                allReturnsAreSlopeLiterals = false;
                                break;
                            }
                        }

                        // only if ALL returns are direct slope literals -> enforce .0..0
                        if (allReturnsAreSlopeLiterals) {
                            for (Stmt.Return r : returns) {
                                Expr.Literal rl = (Expr.Literal) r.expr;
                                double v = ((Double) rl.value).doubleValue();
                                if (v != Math.rint(v)) {
                                    error(SemanticError.Kind.INVALID_CAST,
                                            "Invalid cast: slope->altitude allowed only when fractional part is 0.",
                                            e.castTok);
                                    return null;
                                }
                            }
                            return target; // ok
                        }
                    }
                }

                // if fd not found, or return is not direct slope literal -> allow (no extra checking)
                return target;
            }

            // 4) all other double->int casts we can't prove -> reject
            error(SemanticError.Kind.INVALID_CAST,
                    "Invalid cast: slope->altitude is allowed only for slope literals with .0...0, " +
                            "variables initialized with such literals, or function calls that directly return such literals.",
                    e.castTok);
            return null;
        }

        // general explicit cast check (everything else)
        if (!TypeUtil.canCast(target, src)) {
            error(SemanticError.Kind.INVALID_CAST,
                    "Invalid cast: cannot cast " + src + " to " + target + ".",
                    e.castTok);
            return null;
        }

        return target;
    }


}
