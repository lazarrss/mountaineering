package parser.ast;

import lexer.token.Token;

import java.util.List;

public abstract class Stmt {

    public interface Visitor<R> {
        R visitVarDecl(VarDecl s);
        R visitReturn(Return s);
        R visitExprStmt(ExprStmt s);

        R visitIf(If s);
        R visitClimb(Climb s);
        R visitWhile(While s);
        R visitDoWhile(DoWhile s);
    }

    public abstract <R> R accept(Visitor<R> v);

    public static final class VarDecl extends Stmt {
        public final Ast.Type type;
        public final List<Expr> dims;
        public final List<lexer.token.Token> names;

        public VarDecl(Ast.Type type, List<Expr> dims, List<lexer.token.Token> names) {
            this.type = type;
            this.dims = dims;
            this.names = names;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitVarDecl(this); }
    }

    public static final class Return extends Stmt {
        public final Token keyword;
        public final Expr expr;

        public Return(Token keyword, Expr expr) {
            this.keyword = keyword;
            this.expr = expr;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitReturn(this); }
    }

    public static final class ExprStmt extends Stmt {
        public final Expr expr;

        public ExprStmt(Expr expr) {
            this.expr = expr;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitExprStmt(this); }
    }

    // ifridge (cond) { stmts } [ elsecamp { stmts } | elsecamp ifridge ... ]
    public static final class If extends Stmt {
        public final Expr condition;
        public final List<Stmt> thenBranch;
        public final List<Stmt> elseBranch; // nullable

        public If(Expr condition, List<Stmt> thenBranch, List<Stmt> elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitIf(this); }
    }

    public static final class Climb extends Stmt {
        public final Ast.Type initType;
        public final Token varName;
        public final Expr initExpr;
        public final Expr condition;
        public final Expr update;
        public final List<Stmt> body;

        public Climb(Ast.Type initType,
                     Token varName,
                     Expr initExpr,
                     Expr condition,
                     Expr update,
                     List<Stmt> body) {
            this.initType = initType;
            this.varName = varName;
            this.initExpr = initExpr;
            this.condition = condition;
            this.update = update;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitClimb(this); }
    }

    public static final class While extends Stmt {
        public final Expr condition;
        public final List<Stmt> body;

        public While(Expr condition, List<Stmt> body) {
            this.condition = condition;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitWhile(this); }
    }

    public static final class DoWhile extends Stmt {
        public final List<Stmt> body;
        public final Expr condition;

        public DoWhile(List<Stmt> body, Expr condition) {
            this.body = body;
            this.condition = condition;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitDoWhile(this); }
    }

    public static final class LValue {
        public final Token name;
        public final List<Expr> indices;

        public LValue(Token name, List<Expr> indices) {
            this.name = name;
            this.indices = indices;
        }
    }
}
