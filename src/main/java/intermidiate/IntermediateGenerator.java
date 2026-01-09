package intermidiate;

import lexer.token.TokenType;
import parser.ast.Ast;
import parser.ast.Expr;
import parser.ast.Stmt;
import semantic.types.SType;
import semantic.types.Types;

import java.util.*;

import static lexer.token.TokenType.*;

public final class IntermediateGenerator implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    public JasminProgram generate(Ast.Program program, Map<Expr, SType> exprTypes) {
        reset();

        if (program == null) return new JasminProgram(List.of());
        this.exprTypes = (exprTypes == null) ? Map.of() : exprTypes;

        collectCallSites(program);

        calleeToReturnIds.computeIfAbsent("summit", k -> new ArrayList<>()).add(0);

        emitProgram(program);

        return new JasminProgram(E.lines());
    }

    private Map<Expr, SType> exprTypes = Map.of();
    private final JasminEmitter E = new JasminEmitter();

    private final IdentityHashMap<Expr.Call, Integer> callId = new IdentityHashMap<>();
    private final Map<String, List<Integer>> calleeToReturnIds = new HashMap<>();
    private int nextCallId = 1;

    private final Map<String, FuncInfo> funcs = new HashMap<>();

    private int lblCounter = 1;

    private static final int SLOT_SP  = 1; // int
    private static final int SLOT_RA  = 2; // int[]
    private static final int SLOT_TMP = 3; // int
    private int nextLocalSlot = 4;

    private FuncInfo currentFunc = null;

    private final Deque<Map<String, VarInfo>> scopes = new ArrayDeque<>();

    private void reset() {
        exprTypes = Map.of();
        callId.clear();
        calleeToReturnIds.clear();
        funcs.clear();
        nextCallId = 1;
        lblCounter = 1;
        nextLocalSlot = 4;
        currentFunc = null;
        scopes.clear();
        E.lines().clear();
        pushScope();
    }

    private static final class VarInfo {
        final int slot;
        final SType type;
        VarInfo(int slot, SType type) { this.slot = slot; this.type = type; }
    }

    private static final class FuncInfo {
        final String name;
        final SType returnType;
        final List<SType> paramTypes;
        final int[] paramSlots;
        final int retSlot;

        FuncInfo(String name, SType returnType, List<SType> paramTypes, int[] paramSlots, int retSlot) {
            this.name = name;
            this.returnType = returnType;
            this.paramTypes = paramTypes;
            this.paramSlots = paramSlots;
            this.retSlot = retSlot;
        }

        boolean isVoid() { return returnType != null && returnType.isVoid(); }

        String funcLabel() { return "FUNC_" + name; }
        String retDispatchLabel() { return "FUNC_" + name + "__RETURN"; }
        String retLabel(int id) { return "RET_" + name + "_" + id; }
    }

    private int allocLocal(SType t) {
        int slot = nextLocalSlot;
        if (t != null && t.isScalar() && t.kind == SType.Kind.DOUBLE) nextLocalSlot += 2;
        else nextLocalSlot += 1;
        return slot;
    }

    private void collectCallSites(Ast.Program p) {
        // signatures
        for (Ast.TopItem ti : p.items) {
            if (ti instanceof Ast.FuncDef fd) {
                String fname = fd.name.lexeme;
                SType rt = astTypeToSType(fd.returnType);

                List<SType> pts = new ArrayList<>();
                for (Ast.Param par : fd.params) pts.add(astTypeToSType(par.type));

                int[] paramSlots = new int[pts.size()];
                for (int i = 0; i < pts.size(); i++) paramSlots[i] = allocLocal(pts.get(i));

                int retSlot = rt.isVoid() ? -1 : allocLocal(rt);

                funcs.put(fname, new FuncInfo(fname, rt, pts, paramSlots, retSlot));
            }
        }

        for (Ast.TopItem ti : p.items) {
            if (ti instanceof Ast.TopVarDecl tv) {
                collectInStmt(tv.decl);
            } else if (ti instanceof Ast.FuncDef fd) {
                for (Stmt s : fd.body) collectInStmt(s);
            }
        }
    }

    private void collectInStmt(Stmt s) {
        if (s == null) return;

        switch (s) {
            case Stmt.VarDecl vd -> {
                for (Stmt.VarDecl.Var v : vd.vars) {
                    if (v.init != null) collectInExpr(v.init);
                    for (Expr d : v.dims) collectInExpr(d);
                }
            }
            case Stmt.Return r -> { if (r.expr != null) collectInExpr(r.expr); }
            case Stmt.ExprStmt es -> collectInExpr(es.expr);
            case Stmt.If iff -> {
                collectInExpr(iff.condition);
                for (Stmt x : iff.thenBranch) collectInStmt(x);
                if (iff.elseBranch != null) for (Stmt x : iff.elseBranch) collectInStmt(x);
            }
            case Stmt.While w -> {
                collectInExpr(w.condition);
                for (Stmt x : w.body) collectInStmt(x);
            }
            case Stmt.DoWhile dw -> {
                for (Stmt x : dw.body) collectInStmt(x);
                collectInExpr(dw.condition);
            }
            case Stmt.Climb c -> {
                collectInExpr(c.initExpr);
                collectInExpr(c.condition);
                collectInExpr(c.update);
                for (Stmt x : c.body) collectInStmt(x);
            }
            default -> {}
        }
    }

    private void collectInExpr(Expr e) {
        if (e == null) return;

        switch (e) {
            case Expr.Call c -> {
                if (c.callee.type == SHOUT || c.callee.type == RADIO) {
                    for (Expr a : c.args) collectInExpr(a);
                    return;
                }
                if (c.callee.type == IDENT && funcs.containsKey(c.callee.lexeme)) {
                    int id = nextCallId++;
                    callId.put(c, id);
                    calleeToReturnIds.computeIfAbsent(c.callee.lexeme, k -> new ArrayList<>()).add(id);
                }
                for (Expr a : c.args) collectInExpr(a);
            }
            case Expr.Binary b -> { collectInExpr(b.left); collectInExpr(b.right); }
            case Expr.Unary u -> collectInExpr(u.expr);
            case Expr.Grouping g -> collectInExpr(g.inner);
            case Expr.Cast c -> collectInExpr(c.expr);
            case Expr.Index ix -> { for (Expr a : ix.indices) collectInExpr(a); }
            default -> {}
        }
    }


    private void emitProgram(Ast.Program p) {
        E.emitRaw(".class public MountaineeringProgram");
        E.emitRaw(".super java/lang/Object");
        E.emitRaw("");
        E.emitRaw(".method public <init>()V");
        E.emitRaw("  aload_0");
        E.emitRaw("  invokespecial java/lang/Object/<init>()V");
        E.emitRaw("  return");
        E.emitRaw(".end method");
        E.emitRaw("");
        E.emitRaw(".method public static main([Ljava/lang/String;)V");
        E.emitRaw("  .limit stack 512");
        E.emitRaw("  .limit locals 512");
        E.emitRaw("");

        E.emit("iconst_0");
        E.emit("istore " + SLOT_SP);

        E.emit("sipush 4096");
        E.emit("newarray int");
        E.emit("astore " + SLOT_RA);

        E.emit("aload " + SLOT_RA);
        E.emit("iload " + SLOT_SP);
        E.emit("ldc 0");
        E.emit("iastore");
        E.emit("iinc " + SLOT_SP + " 1");

        E.emit("goto FUNC_summit");

        for (Ast.TopItem ti : p.items) {
            if (ti instanceof Ast.FuncDef fd) emitFunction(fd);
        }

        E.label("RET_summit_0");
        E.emit("goto END_PROGRAM");

        E.label("END_PROGRAM");
        E.emit("return");
        E.emitRaw(".end method");
    }


    private void emitFunction(Ast.FuncDef fd) {
        FuncInfo fi = funcs.get(fd.name.lexeme);
        if (fi == null) return;

        FuncInfo prev = currentFunc;
        currentFunc = fi;

        E.label(fi.funcLabel());

        pushScope();
        try {
            for (int i = 0; i < fd.params.size(); i++) {
                String pname = fd.params.get(i).name.lexeme;
                declareVar(pname, fi.paramSlots[i], fi.paramTypes.get(i));
            }

            for (Stmt st : fd.body) if (st != null) st.accept(this);

            if (fi.isVoid()) {
                E.emit("goto " + fi.retDispatchLabel());
            } else {
                emitDefaultValue(fi.returnType);
                storeToSlot(fi.returnType, fi.retSlot);
                E.emit("goto " + fi.retDispatchLabel());
            }

            emitReturnDispatchBlockOnce(fi);
        } finally {
            popScope();
            currentFunc = prev;
        }
    }
    private void pushScope() { scopes.push(new HashMap<>()); }

    private void popScope() {
        scopes.pop();
        if (scopes.isEmpty()) scopes.push(new HashMap<>());
    }

    private void declareVar(String name, int slot, SType type) {
        scopes.peek().put(name, new VarInfo(slot, type));
    }

    private VarInfo lookupVar(String name) {
        for (Map<String, VarInfo> s : scopes) {
            VarInfo v = s.get(name);
            if (v != null) return v;
        }
        return null;
    }

    private int allocSlot(SType t) { return allocLocal(t); }

    @Override
    public Void visitVarDecl(Stmt.VarDecl s) {
        SType base = astTypeToSType(s.type);

        for (Stmt.VarDecl.Var v : s.vars) {
            int extraRank = (v.dims == null) ? 0 : v.dims.size();
            SType full = base.withRank(base.rank() + extraRank);

            int slot = allocSlot(full);
            declareVar(v.name.lexeme, slot, full);

            if (full.isArrayLike()) {
                for (Expr dim : v.dims) dim.accept(this);
                emitNewArray(full, v.dims.size());
                E.emit("astore " + slot);
            } else {
                if (v.init != null) v.init.accept(this);
                else emitDefaultValue(full);
                storeToSlot(full, slot);
            }
        }
        return null;
    }

    @Override
    public Void visitReturn(Stmt.Return s) {
        if (currentFunc == null) return null;

        if (currentFunc.isVoid()) {
            E.emit("goto " + currentFunc.retDispatchLabel());
            return null;
        }

        if (s.expr != null) s.expr.accept(this);
        else emitDefaultValue(currentFunc.returnType);

        storeToSlot(currentFunc.returnType, currentFunc.retSlot);
        E.emit("goto " + currentFunc.retDispatchLabel());
        return null;
    }

    @Override
    public Void visitExprStmt(Stmt.ExprStmt s) {
        s.expr.accept(this);
        SType t = exprType(s.expr);
        if (t != null && !t.isVoid()) {
            if (t.isNumericScalar() && t.kind == SType.Kind.DOUBLE) E.emit("pop2");
            else E.emit("pop");
        }
        return null;
    }

    @Override
    public Void visitIf(Stmt.If s) {
        String Lelse = fresh("IF_ELSE");
        String Lend  = fresh("IF_END");

        s.condition.accept(this);
        E.emit("ifeq " + Lelse);

        pushScope();
        try { for (Stmt st : s.thenBranch) if (st != null) st.accept(this); }
        finally { popScope(); }

        E.emit("goto " + Lend);
        E.label(Lelse);

        if (s.elseBranch != null) {
            pushScope();
            try { for (Stmt st : s.elseBranch) if (st != null) st.accept(this); }
            finally { popScope(); }
        }

        E.label(Lend);
        return null;
    }

    @Override
    public Void visitWhile(Stmt.While s) {
        String Lcond = fresh("WHILE_COND");
        String Lend  = fresh("WHILE_END");

        E.label(Lcond);
        s.condition.accept(this);
        E.emit("ifeq " + Lend);

        pushScope();
        try { for (Stmt st : s.body) if (st != null) st.accept(this); }
        finally { popScope(); }

        E.emit("goto " + Lcond);
        E.label(Lend);
        return null;
    }

    @Override
    public Void visitDoWhile(Stmt.DoWhile s) {
        String Lbody = fresh("DO_BODY");

        E.label(Lbody);

        pushScope();
        try { for (Stmt st : s.body) if (st != null) st.accept(this); }
        finally { popScope(); }

        s.condition.accept(this);
        E.emit("ifne " + Lbody);
        return null;
    }

    @Override
    public Void visitClimb(Stmt.Climb s) {
        pushScope();
        try {
            SType t = (s.initType != null) ? astTypeToSType(s.initType) : Types.INT;
            int slot = allocSlot(t);
            declareVar(s.varName.lexeme, slot, t);

            s.initExpr.accept(this);
            storeToSlot(t, slot);

            String Lcond = fresh("CLIMB_COND");
            String Lend  = fresh("CLIMB_END");

            E.label(Lcond);
            s.condition.accept(this);
            E.emit("ifeq " + Lend);

            for (Stmt st : s.body) if (st != null) st.accept(this);

            if (s.update != null) {
                s.update.accept(this);
                SType ut = exprType(s.update);
                if (ut != null && !ut.isVoid()) {
                    if (ut.isNumericScalar() && ut.kind == SType.Kind.DOUBLE) E.emit("pop2");
                    else E.emit("pop");
                }
            }

            E.emit("goto " + Lcond);
            E.label(Lend);
        } finally {
            popScope();
        }
        return null;
    }

    @Override
    public Void visitLiteral(Expr.Literal e) {
        if (e.value == null) { E.emit("aconst_null"); return null; }

        SType t = exprType(e);

        if (t != null && t.kind == SType.Kind.DOUBLE) {
            double dv = (e.value instanceof Double d) ? d : Double.parseDouble(e.value.toString());
            E.emit("ldc2_w " + dv);
            return null;
        }

        if (t != null && t.kind == SType.Kind.STRING) {
            E.emit("ldc \"" + escapeJasminString(e.value.toString()) + "\"");
            return null;
        }

        if (t != null && t.kind == SType.Kind.BOOL) {
            boolean bv = (e.value instanceof Boolean b) ? b : Boolean.parseBoolean(e.value.toString());
            E.emit(bv ? "iconst_1" : "iconst_0");
            return null;
        }

        if (t != null && t.kind == SType.Kind.CHAR) {
            char cv;
            if (e.value instanceof Character c) cv = c;
            else {
                String s = e.value.toString();
                cv = (!s.isEmpty()) ? s.charAt(0) : 0;
            }
            E.emit("ldc " + (int) cv);
            return null;
        }

        int iv = (e.value instanceof Integer i) ? i : Integer.parseInt(e.value.toString());
        E.emit("ldc " + iv);
        return null;
    }

    @Override
    public Void visitIdent(Expr.Ident e) {
        VarInfo vi = lookupVar(e.name.lexeme);
        if (vi == null) { E.emit("aconst_null"); return null; }
        loadFromSlot(vi.type, vi.slot);
        return null;
    }

    @Override
    public Void visitIndex(Expr.Index e) {
        VarInfo vi = lookupVar(e.name.lexeme);
        if (vi == null) { E.emit("aconst_null"); return null; }

        E.emit("aload " + vi.slot);

        SType base = vi.type;
        int n = e.indices.size();

        for (int i = 0; i < n; i++) {
            e.indices.get(i).accept(this);
            boolean last = (i == n - 1);
            int remainingRank = base.rank() - (i + 1);

            if (!last) E.emit("aaload");
            else {
                SType elemType = base.withRank(remainingRank);
                emitArrayLoad(elemType);
            }
        }
        return null;
    }

    @Override
    public Void visitGrouping(Expr.Grouping e) { e.inner.accept(this); return null; }

    @Override
    public Void visitCast(Expr.Cast e) {
        SType target = astTypeToSType(e.targetType);
        SType src = exprType(e.expr);

        e.expr.accept(this);

        if (src != null && target != null) {
            if (src.kind == SType.Kind.INT && target.kind == SType.Kind.DOUBLE && src.isScalar() && target.isScalar()) {
                E.emit("i2d");
                return null;
            }
            if (src.kind == SType.Kind.DOUBLE && target.kind == SType.Kind.INT && src.isScalar() && target.isScalar()) {
                E.emit("d2i");
                return null;
            }
            if (src.kind == SType.Kind.CHAR && target.kind == SType.Kind.INT) return null;
            if (src.kind == SType.Kind.INT && target.kind == SType.Kind.CHAR) { E.emit("i2c"); return null; }
        }
        return null;
    }

    @Override
    public Void visitUnary(Expr.Unary e) {
        SType t = exprType(e);

        if (e.op.type == PLUS2 || e.op.type == MINUS2) {
            emitIncDec(e.expr, e.op.type == PLUS2);
            return null;
        }

        if (e.op.type == NOT) {
            e.expr.accept(this);
            String Ltrue = fresh("NOT_TRUE");
            String Lend  = fresh("NOT_END");
            E.emit("ifeq " + Ltrue);
            E.emit("iconst_0");
            E.emit("goto " + Lend);
            E.label(Ltrue);
            E.emit("iconst_1");
            E.label(Lend);
            return null;
        }

        if (e.op.type == PLUS) { e.expr.accept(this); return null; }

        if (e.op.type == MINUS) {
            e.expr.accept(this);
            if (t != null && t.kind == SType.Kind.DOUBLE) E.emit("dneg");
            else E.emit("ineg");
            return null;
        }

        e.expr.accept(this);
        return null;
    }

    @Override
    public Void visitBinary(Expr.Binary e) {
        TokenType op = e.op.type;
        SType t = exprType(e);

        if (op == ASSIGN) {
            emitAssign(e.left, e.right);
            emitLoadLValue(e.left);
            return null;
        }

        if (op == AND || op == OR) {
            emitLogicalShortCircuit(e, op == AND);
            return null;
        }

        if (op == PLUS || op == MINUS || op == MULTIPLY || op == DIV || op == MODULO) {
            e.left.accept(this);
            e.right.accept(this);

            boolean isDouble = t != null && t.kind == SType.Kind.DOUBLE;
            if (isDouble) {
                switch (op) {
                    case PLUS -> E.emit("dadd");
                    case MINUS -> E.emit("dsub");
                    case MULTIPLY -> E.emit("dmul");
                    case DIV -> E.emit("ddiv");
                    case MODULO -> E.emit("drem");
                }
            } else {
                switch (op) {
                    case PLUS -> E.emit("iadd");
                    case MINUS -> E.emit("isub");
                    case MULTIPLY -> E.emit("imul");
                    case DIV -> E.emit("idiv");
                    case MODULO -> E.emit("irem");
                }
            }
            return null;
        }

        if (op == LT || op == LE || op == GT || op == GE || op == EQ || op == NEQ) {
            emitCompareToBool(e, op);
            return null;
        }

        e.left.accept(this);
        e.right.accept(this);
        return null;
    }

    @Override
    public Void visitCall(Expr.Call e) {
        if (e.callee.type == SHOUT) {
            if (e.args == null || e.args.isEmpty()) return null;
            E.emit("getstatic java/lang/System/out Ljava/io/PrintStream;");
            e.args.getFirst().accept(this);

            SType at = exprType(e.args.getFirst());
            String desc = "(Ljava/lang/String;)V";
            if (at != null) {
                if (at.kind == SType.Kind.INT || at.kind == SType.Kind.BOOL || at.kind == SType.Kind.CHAR) desc = "(I)V";
                else if (at.kind == SType.Kind.DOUBLE) desc = "(D)V";
                else if (at.kind == SType.Kind.STRING) desc = "(Ljava/lang/String;)V";
            }
            E.emit("invokevirtual java/io/PrintStream/println" + desc);
            return null;
        }

        if (e.callee.type == RADIO) {
            E.emit("ldc \"\"");
            return null;
        }

        FuncInfo callee = funcs.get(e.callee.lexeme);
        if (callee == null) { E.emit("aconst_null"); return null; }

        if (e.args != null) {
            for (Expr a : e.args) a.accept(this);
            for (int i = e.args.size() - 1; i >= 0; i--) {
                storeToSlot(callee.paramTypes.get(i), callee.paramSlots[i]);
            }
        }

        Integer id = callId.get(e);
        if (id == null) id = 999999;

        E.emit("aload " + SLOT_RA);
        E.emit("iload " + SLOT_SP);
        E.emit("ldc " + id);
        E.emit("iastore");
        E.emit("iinc " + SLOT_SP + " 1");

        E.emit("goto " + callee.funcLabel());

        E.label(callee.retLabel(id));

        if (!callee.isVoid()) loadFromSlot(callee.returnType, callee.retSlot);
        return null;
    }

    private void emitReturnDispatchBlockOnce(FuncInfo fi) {
        E.label(fi.retDispatchLabel());

        E.emit("iinc " + SLOT_SP + " -1");

        E.emit("aload " + SLOT_RA);
        E.emit("iload " + SLOT_SP);
        E.emit("iaload");
        E.emit("istore " + SLOT_TMP);

        E.emit("iload " + SLOT_TMP);

        List<Integer> ids = calleeToReturnIds.getOrDefault(fi.name, List.of());
        if (ids.isEmpty()) { E.emit("goto END_PROGRAM"); return; }

        List<Integer> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);

        E.emitRaw("  lookupswitch");
        for (int id : sorted) E.emitRaw("    " + id + " : " + fi.retLabel(id));
        E.emitRaw("    default : END_PROGRAM");
    }

    private void emitAssign(Expr left, Expr right) {
        if (left instanceof Expr.Ident id) {
            VarInfo vi = lookupVar(id.name.lexeme);
            if (vi == null) return;
            right.accept(this);
            storeToSlot(vi.type, vi.slot);
            return;
        }

        if (left instanceof Expr.Index ix) {
            VarInfo vi = lookupVar(ix.name.lexeme);
            if (vi == null) return;

            E.emit("aload " + vi.slot);
            int n = ix.indices.size();

            for (int i = 0; i < n - 1; i++) {
                ix.indices.get(i).accept(this);
                E.emit("aaload");
            }

            ix.indices.get(n - 1).accept(this);
            right.accept(this);

            SType elem = vi.type.withRank(vi.type.rank() - n);
            emitArrayStore(elem);
        }
    }

    private void emitLoadLValue(Expr left) {
        if (left instanceof Expr.Ident id) {
            VarInfo vi = lookupVar(id.name.lexeme);
            if (vi == null) return;
            loadFromSlot(vi.type, vi.slot);
        } else if (left instanceof Expr.Index ix) {
            ix.accept(this);
        }
    }

    private void emitIncDec(Expr target, boolean inc) {
        if (target instanceof Expr.Ident id) {
            VarInfo vi = lookupVar(id.name.lexeme);
            if (vi == null) return;

            loadFromSlot(vi.type, vi.slot);
            if (vi.type.kind == SType.Kind.DOUBLE) {
                E.emit("ldc2_w 1.0");
                E.emit(inc ? "dadd" : "dsub");
                E.emit("dup2");
                storeToSlot(vi.type, vi.slot);
            } else {
                E.emit("iconst_1");
                E.emit(inc ? "iadd" : "isub");
                E.emit("dup");
                storeToSlot(vi.type, vi.slot);
            }
        }
    }

    private void emitCompareToBool(Expr.Binary e, TokenType op) {
        SType lt = exprType(e.left);
        boolean isDouble = lt != null && lt.kind == SType.Kind.DOUBLE;

        String Ltrue = fresh("CMP_TRUE");
        String Lend  = fresh("CMP_END");

        e.left.accept(this);
        e.right.accept(this);

        if (isDouble) {
            E.emit("dcmpg");
            switch (op) {
                case LT -> E.emit("iflt " + Ltrue);
                case LE -> E.emit("ifle " + Ltrue);
                case GT -> E.emit("ifgt " + Ltrue);
                case GE -> E.emit("ifge " + Ltrue);
                case EQ -> E.emit("ifeq " + Ltrue);
                case NEQ -> E.emit("ifne " + Ltrue);
            }
        } else {
            switch (op) {
                case LT -> E.emit("if_icmplt " + Ltrue);
                case LE -> E.emit("if_icmple " + Ltrue);
                case GT -> E.emit("if_icmpgt " + Ltrue);
                case GE -> E.emit("if_icmpge " + Ltrue);
                case EQ -> E.emit("if_icmpeq " + Ltrue);
                case NEQ -> E.emit("if_icmpne " + Ltrue);
            }
        }

        E.emit("iconst_0");
        E.emit("goto " + Lend);
        E.label(Ltrue);
        E.emit("iconst_1");
        E.label(Lend);
    }

    private void emitLogicalShortCircuit(Expr.Binary e, boolean isAnd) {
        String Lshort = fresh(isAnd ? "AND_FALSE" : "OR_TRUE");
        String Lend   = fresh("LOG_END");

        e.left.accept(this);
        if (isAnd) {
            E.emit("ifeq " + Lshort);
            e.right.accept(this);
            E.emit("ifeq " + Lshort);
            E.emit("iconst_1");
            E.emit("goto " + Lend);
            E.label(Lshort);
            E.emit("iconst_0");
            E.label(Lend);
        } else {
            E.emit("ifne " + Lshort);
            e.right.accept(this);
            E.emit("ifne " + Lshort);
            E.emit("iconst_0");
            E.emit("goto " + Lend);
            E.label(Lshort);
            E.emit("iconst_1");
            E.label(Lend);
        }
    }

    private void emitNewArray(SType fullType, int dimsCount) {
        if (dimsCount <= 0) { E.emit("aconst_null"); return; }

        if (fullType.rank() == 1 && dimsCount == 1) {
            switch (fullType.kind) {
                case INT -> E.emit("newarray int");
                case DOUBLE -> E.emit("newarray double");
                case BOOL -> E.emit("newarray boolean");
                case CHAR -> E.emit("newarray char");
                case STRING -> E.emit("anewarray java/lang/String");
                default -> E.emit("anewarray java/lang/Object");
            }
            return;
        }

        String desc = jvmArrayDescriptor(fullType);
        E.emitRaw("  multianewarray " + desc + " " + dimsCount);
    }

    private void emitArrayLoad(SType elementType) {
        if (elementType.isArrayLike()) { E.emit("aaload"); return; }
        switch (elementType.kind) {
            case INT, BOOL -> E.emit("iaload");
            case CHAR -> E.emit("caload");
            case DOUBLE -> E.emit("daload");
            case STRING, NULL -> E.emit("aaload");
            default -> E.emit("aaload");
        }
    }

    private void emitArrayStore(SType elementType) {
        if (elementType.isArrayLike()) { E.emit("aastore"); return; }
        switch (elementType.kind) {
            case INT, BOOL -> E.emit("iastore");
            case CHAR -> E.emit("castore");
            case DOUBLE -> E.emit("dastore");
            case STRING, NULL -> E.emit("aastore");
            default -> E.emit("aastore");
        }
    }

    private static String jvmArrayDescriptor(SType t) {
        return "[".repeat(Math.max(0, t.rank())) + jvmScalarDescriptor(t);
    }

    private static String jvmScalarDescriptor(SType t) {
        return switch (t.kind) {
            case INT, BOOL, CHAR -> "I";
            case DOUBLE -> "D";
            case STRING -> "Ljava/lang/String;";
            case VOID -> "V";
            case NULL -> "Ljava/lang/Object;";
        };
    }

    private void loadFromSlot(SType t, int slot) {
        if (t.isArrayLike() || t.kind == SType.Kind.STRING || t.kind == SType.Kind.NULL) { E.emit("aload " + slot); return; }
        if (t.kind == SType.Kind.DOUBLE) { E.emit("dload " + slot); return; }
        E.emit("iload " + slot);
    }

    private void storeToSlot(SType t, int slot) {
        if (t.isArrayLike() || t.kind == SType.Kind.STRING || t.kind == SType.Kind.NULL) { E.emit("astore " + slot); return; }
        if (t.kind == SType.Kind.DOUBLE) { E.emit("dstore " + slot); return; }
        E.emit("istore " + slot);
    }

    private void emitDefaultValue(SType t) {
        if (t.isArrayLike() || t.kind == SType.Kind.STRING || t.kind == SType.Kind.NULL) { E.emit("aconst_null"); return; }
        if (t.kind == SType.Kind.DOUBLE) { E.emit("dconst_0"); return; }
        E.emit("iconst_0");
    }

    private SType exprType(Expr e) {
        SType t = exprTypes.get(e);
        return (t != null) ? t : Types.NULL;
    }

    private static SType astTypeToSType(Ast.Type t) {
        if (t == null) return Types.NULL;
        return switch (t.kind) {
            case INT -> Types.INT.withRank(t.rank);
            case DOUBLE -> Types.DOUBLE.withRank(t.rank);
            case CHAR -> Types.CHAR.withRank(t.rank);
            case STRING -> Types.STRING.withRank(t.rank);
            case BOOL -> Types.BOOL.withRank(t.rank);
            case VOID -> Types.VOID;
        };
    }

    private String fresh(String prefix) { return prefix + "_" + (lblCounter++); }

    private static String escapeJasminString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
