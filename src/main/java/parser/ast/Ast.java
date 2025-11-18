package parser.ast;

import lexer.token.Token;
import java.util.List;

public final class Ast {

    // program and top-level
    public static final class Program {
        public final List<TopItem> items;

        public Program(List<TopItem> items) {
            this.items = items;
        }
    }

    public interface TopItem {}

    // top-level var decl
    public static final class TopVarDecl implements TopItem {
        public final Stmt.VarDecl decl;

        public TopVarDecl(Stmt.VarDecl decl) {
            this.decl = decl;
        }
    }

    // func def: ROUTEPLAN type name(params) { body }
    public static final class FuncDef implements TopItem {
        public final Token routeplanTok;   // ROUTEPLAN keyword
        public final Type returnType;
        public final Token name;           // IDENT
        public final List<Param> params;
        public final List<Stmt> body;      // function body

        public FuncDef(Token routeplanTok,
                       Type returnType,
                       Token name,
                       List<Param> params,
                       List<Stmt> body) {
            this.routeplanTok = routeplanTok;
            this.returnType = returnType;
            this.name = name;
            this.params = params;
            this.body = body;
        }
    }

    // function param: type name
    public static final class Param {
        public final Token name; // IDENT
        public final Type type;

        public Param(Token name, Type type) {
            this.name = name;
            this.type = type;
        }
    }

    // type with(out) params: slope, slope[], slope[][] etc
    public static final class Type {
        public enum Kind { INT, VOID, DOUBLE, CHAR, STRING, BOOL }

        public final Kind kind;
        public final Token baseType;
        public final int rank;       // num of ()

        public Type(Kind kind, Token baseType, int rank) {
            this.kind = kind;
            this.baseType = baseType;
            this.rank = rank;
        }
    }
}
