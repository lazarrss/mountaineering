package parser.ast;

import lexer.token.Token;

import java.util.List;

public abstract class Expr {

    public interface Visitor<R> {
        R visitLiteral(Literal e);
        R visitIdent(Ident e);
        R visitIndex(Index e);
        R visitGrouping(Grouping e);
        R visitCall(Call e);
        R visitBinary(Binary e);
        R visitUnary(Unary e);
        R visitCast(Cast e);
    }

    public abstract <R> R accept(Visitor<R> v);

    // literals
    public static final class Literal extends Expr {
        public final Token token;
        public final Object value;

        public Literal(Token token, Object value) {
            this.token = token;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitLiteral(this); }
    }

    // name
    public static final class Ident extends Expr {
        public final Token name; // IDENT

        public Ident(Token name) {
            this.name = name;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitIdent(this); }
    }

    // ident[expr][expr]...
    public static final class Index extends Expr {
        public final Token name;        // IDENT / SHOUT / RADIO / BELAY
        public final List<Expr> indices;

        public Index(Token name, List<Expr> indices) {
            this.name = name;
            this.indices = indices;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitIndex(this); }
    }

    // (expr)
    public static final class Grouping extends Expr {
        public final Expr inner;

        public Grouping(Expr inner) {
            this.inner = inner;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitGrouping(this); }
    }

    // (type) expr
    public static final class Cast extends Expr {
        public final Token castTok;     // token of the target type
        public final Ast.Type targetType;
        public final Expr expr;

        public Cast(Token castTok, Ast.Type targetType, Expr expr) {
            this.castTok = castTok;
            this.targetType = targetType;
            this.expr = expr;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitCast(this); }
    }

    // callable func
    public static final class Call extends Expr {
        public final Token callee;   // IDENT / SHOUT / RADIO / BELAY
        public final List<Expr> args;

        public Call(Token callee, List<Expr> args) {
            this.callee = callee;
            this.args = args;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitCall(this); }
    }

    // +, -, *, /, %, ^, and, or
    public static final class Binary extends Expr {
        public final Expr left;
        public final Token op;
        public final Expr right;

        public Binary(Expr left, Token op, Expr right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitBinary(this); }
    }

    // +x, -x, not x, ...
    public static final class Unary extends Expr {
        public final Token op;
        public final Expr expr;

        public Unary(Token op, Expr expr) {
            this.op = op;
            this.expr = expr;
        }

        @Override
        public <R> R accept(Visitor<R> v) { return v.visitUnary(this); }
    }
}
