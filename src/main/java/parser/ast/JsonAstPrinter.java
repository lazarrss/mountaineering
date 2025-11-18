package parser.ast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

public final class JsonAstPrinter implements Expr.Visitor<JsonNode>, Stmt.Visitor<JsonNode> {

    private static final ObjectMapper M = new ObjectMapper();

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
        ObjectNode t = M.createObjectNode();
        t.put("kind", fd.returnType.kind.toString());
        t.put("rank", fd.returnType.rank);
        if (fd.returnType.baseType != null) {
            t.put("baseLexeme", fd.returnType.baseType.lexeme);
        }
        o.set("returnType", t);

        // params
        ArrayNode ps = M.createArrayNode();
        for (Ast.Param p : fd.params) {
            ObjectNode pp = M.createObjectNode();
            pp.put("name", p.name.lexeme);
            ObjectNode pt = M.createObjectNode();
            pt.put("kind", p.type.kind.toString());
            pt.put("rank", p.type.rank);
            if (p.type.baseType != null) {
                pt.put("baseLexeme", p.type.baseType.lexeme);
            }
            pp.set("type", pt);
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

    @Override
    public JsonNode visitLiteral(Expr.Literal e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "literal");
        o.put("tokenType", e.token.type.toString());
        if (e.value == null) {
            o.putNull("value");
        } else {
            o.put("value", e.value.toString());
        }
        return o;
    }

    @Override
    public JsonNode visitIdent(Expr.Ident e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "ident");
        o.put("name", e.name.lexeme);
        return o;
    }

    @Override
    public JsonNode visitIndex(Expr.Index e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "index");
        o.put("name", e.name.lexeme);
        ArrayNode idx = M.createArrayNode();
        for (Expr ex : e.indices) {
            idx.add(ex.accept(this));
        }
        o.set("indices", idx);
        return o;
    }

    @Override
    public JsonNode visitGrouping(Expr.Grouping e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "grouping");
        o.set("inner", e.inner.accept(this));
        return o;
    }

    @Override
    public JsonNode visitCall(Expr.Call e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "call");
        o.put("name", e.callee.lexeme);
        ArrayNode args = M.createArrayNode();
        for (Expr a : e.args) {
            args.add(a.accept(this));
        }
        o.set("args", args);
        return o;
    }

    @Override
    public JsonNode visitBinary(Expr.Binary e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "binary");
        o.put("op", e.op.lexeme);
        o.set("left", e.left.accept(this));
        o.set("right", e.right.accept(this));
        return o;
    }

    @Override
    public JsonNode visitUnary(Expr.Unary e) {
        ObjectNode o = M.createObjectNode();
        o.put("expr", "unary");
        o.put("op", e.op.lexeme);
        o.set("expr", e.expr.accept(this));
        return o;
    }

    @Override
    public JsonNode visitVarDecl(Stmt.VarDecl s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "varDecl");

        ObjectNode t = M.createObjectNode();
        t.put("kind", s.type.kind.toString());
        t.put("rank", s.type.rank);
        if (s.type.baseType != null) {
            t.put("baseLexeme", s.type.baseType.lexeme);
        }
        o.set("type", t);

        ArrayNode dims = M.createArrayNode();
        for (Expr d : s.dims) {
            dims.add(d.accept(this));
        }
        o.set("dims", dims);

        ArrayNode names = M.createArrayNode();
        for (var tok : s.names) {
            names.add(tok.lexeme);
        }
        o.set("names", names);

        return o;
    }

    @Override
    public JsonNode visitReturn(Stmt.Return s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "return");
        if (s.expr != null) {
            o.set("expr", s.expr.accept(this));
        } else {
            o.putNull("expr");
        }
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
        if (s.elseBranch != null) {
            o.set("else", printBlock(s.elseBranch));
        } else {
            o.putNull("else");
        }
        return o;
    }

    @Override
    public JsonNode visitClimb(Stmt.Climb s) {
        ObjectNode o = M.createObjectNode();
        o.put("stmt", "climb");

        ObjectNode t = M.createObjectNode();
        t.put("kind", s.initType.kind.toString());
        t.put("rank", s.initType.rank);
        if (s.initType.baseType != null) {
            t.put("baseLexeme", s.initType.baseType.lexeme);
        }
        o.set("initType", t);

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
