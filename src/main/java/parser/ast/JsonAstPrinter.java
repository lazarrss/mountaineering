package parser.ast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import semantic.types.SType;

import java.util.Map;

public final class JsonAstPrinter implements Expr.Visitor<JsonNode>, Stmt.Visitor<JsonNode> {

    private static final ObjectMapper M = new ObjectMapper();

    // optional: if null -> prints untyped AST
    private final Map<Expr, SType> exprTypes;

    public JsonAstPrinter() {
        this.exprTypes = null;
    }

    public JsonAstPrinter(Map<Expr, SType> exprTypes) {
        this.exprTypes = exprTypes;
    }

    public String print(Ast.Program p) {
        try {
            ObjectNode root = M.createObjectNode();
            root.put("kind", "program");
            ArrayNode items = M.createArrayNode();

            for (Ast.TopItem ti : p.items) {
                if (ti instanceof Ast.TopVarDecl tv) {
                    items.add(printTopVarDecl(tv));
                } else if (ti instanceof Ast.FuncDef fd) {
                    items.add(printFuncDef(fd));
                }
            }

            root.set("items", items);
            return M.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("failed to print AST to JSON", e);
        }
    }

    // ---------------- helpers ----------------

    private void attachSType(ObjectNode o, Expr e) {
        if (exprTypes == null || e == null) return;
        SType t = exprTypes.get(e);
        if (t == null) return;

        ObjectNode st = M.createObjectNode();
        st.put("kind", t.kind.toString());
        st.put("rank", t.rank());
        st.put("repr", t.toString()); // korisno za proveru (npr. INT[][])
        o.set("stype", st);
    }

    private ObjectNode printAstType(Ast.Type type) {
        ObjectNode t = M.createObjectNode();
        t.put("kind", type.kind.toString());
        t.put("rank", type.rank);
        if (type.baseType != null) {
            t.put("baseLexeme", type.baseType.lexeme);
        }
        return t;
    }

    private JsonNode printTopVarDecl(Ast.TopVarDecl tv) {
        ObjectNode o = M.createObjectNode();
        o.put("top", "varDecl");
        o.set("decl", tv.decl.accept(this));
        return o;
    }

    private JsonNode printFuncDef(Ast.FuncDef fd) {
        ObjectNode o = M.createObjectNode();
        o.put("top", "funcDef");
        o.put("name", fd.name.lexeme);

        // return type
        o.set("returnType", printAstType(fd.returnType));

        // params
        ArrayNode ps = M.createArrayNode();
        for (Ast.Param p : fd.params) {
            ObjectNode pp = M.createObjectNode();
            pp.put("name", p.name.lexeme);
            pp.set("type", printAstType(p.type));
            ps.add(pp);
        }
        o.set("params", ps);

        // body
        o.set("body", printBlock(fd.body));

        return o;
    }

    private ArrayNode printBlock(Iterable<Stmt> stmts) {
        ArrayNode arr = M.createArrayNode();
        for (Stmt s : stmts) {
            arr.add(s.accept(this));
        }
        return arr;
    }

    // ---------------- Expr visitors (typed) ----------------

    @Override
    public JsonNode visitLiteral(Expr.Literal e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "literal");
        o.put("tokenType", e.token.type.toString());
        if (e.value == null) o.putNull("value");
        else o.put("value", e.value.toString());

        attachSType(o, e);
        return o;
    }

    @Override
    public JsonNode visitIdent(Expr.Ident e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "ident");
        o.put("name", e.name.lexeme);

        attachSType(o, e);
        return o;
    }

    @Override
    public JsonNode visitIndex(Expr.Index e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "index");
        o.put("name", e.name.lexeme);

        ArrayNode idx = M.createArrayNode();
        for (Expr ex : e.indices) idx.add(ex.accept(this));
        o.set("indices", idx);

        attachSType(o, e);
        return o;
    }

    @Override
    public JsonNode visitGrouping(Expr.Grouping e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "grouping");
        o.set("inner", e.inner.accept(this));

        attachSType(o, e);
        return o;
    }

    @Override
    public JsonNode visitCall(Expr.Call e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "call");
        o.put("name", e.callee.lexeme);

        ArrayNode args = M.createArrayNode();
        for (Expr a : e.args) args.add(a.accept(this));
        o.set("args", args);

        attachSType(o, e);
        return o;
    }

    @Override
    public JsonNode visitBinary(Expr.Binary e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "binary");
        o.put("op", e.op.lexeme);
        o.set("left", e.left.accept(this));
        o.set("right", e.right.accept(this));

        attachSType(o, e);
        return o;
    }

    @Override
    public JsonNode visitUnary(Expr.Unary e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "unary");
        o.put("op", e.op.lexeme);

        // BITNO: ne koristi "expr" opet, jer prepisuješ string "unary"
        o.set("operand", e.expr.accept(this));

        attachSType(o, e);
        return o;
    }

    @Override
    public JsonNode visitCast(Expr.Cast e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "cast");
        o.set("targetType", printAstType(e.targetType));
        o.set("inner", e.expr.accept(this));

        attachSType(o, e);
        return o;
    }

    // ---------------- Stmt visitors ----------------

    @Override
    public JsonNode visitVarDecl(Stmt.VarDecl s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "varDecl");

        o.set("type", printAstType(s.type));

        ArrayNode vars = M.createArrayNode();
        for (Stmt.VarDecl.Var v : s.vars) {
            ObjectNode vv = M.createObjectNode();
            vv.put("name", v.name.lexeme);

            ArrayNode dims = M.createArrayNode();
            for (Expr d : v.dims) dims.add(d.accept(this));
            vv.set("dims", dims);

            if (v.init != null) vv.set("init", v.init.accept(this));
            else vv.putNull("init");

            vars.add(vv);
        }
        o.set("vars", vars);

        return o;
    }

    @Override
    public JsonNode visitReturn(Stmt.Return s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "return");
        if (s.expr != null) o.set("expr", s.expr.accept(this));
        else o.putNull("expr");
        return o;
    }

    @Override
    public JsonNode visitExprStmt(Stmt.ExprStmt s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "expr");
        o.set("expr", s.expr.accept(this));
        return o;
    }

    @Override
    public JsonNode visitIf(Stmt.If s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "if");
        o.set("condition", s.condition.accept(this));
        o.set("then", printBlock(s.thenBranch));
        if (s.elseBranch != null) o.set("else", printBlock(s.elseBranch));
        else o.putNull("else");
        return o;
    }

    @Override
    public JsonNode visitClimb(Stmt.Climb s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "climb");

        if (s.initType == null) {
            o.putNull("initType");
        } else {
            o.set("initType", printAstType(s.initType));
        }

        o.put("var", s.varName.lexeme);
        o.set("initExpr", s.initExpr.accept(this));
        o.set("condition", s.condition.accept(this));
        o.set("update", s.update.accept(this));
        o.set("body", printBlock(s.body));

        return o;
    }

    @Override
    public JsonNode visitWhile(Stmt.While s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "while");
        o.set("condition", s.condition.accept(this));
        o.set("body", printBlock(s.body));
        return o;
    }

    @Override
    public JsonNode visitDoWhile(Stmt.DoWhile s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "doWhile");
        o.set("condition", s.condition.accept(this));
        o.set("body", printBlock(s.body));
        return o;
    }
}
