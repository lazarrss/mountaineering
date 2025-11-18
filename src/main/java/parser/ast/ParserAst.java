package parser.ast;

import java.util.ArrayList;
import java.util.List;

import lexer.token.Token;
import lexer.token.TokenType;

import static lexer.token.TokenType.*;


public final class ParserAst {
    private final List<Token> tokens;
    private int current = 0;

    public ParserAst(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Ast.Program parseProgram() {
        List<Ast.TopItem> items = parseProgramBody();
        consume(EOF, "expected EOF");
        return new Ast.Program(items);
    }

    private List<Ast.TopItem> parseProgramBody() {
        List<Ast.TopItem> items = new ArrayList<>();
        while (!check(EOF)) {
            items.add(parseTopItem());
        }
        return items;
    }

    private Ast.TopItem parseTopItem() {
        if (check(ROUTEPLAN)) {
            return parseFuncDecl();
        }
        Stmt.VarDecl decl = parseVarDecl();
        return new Ast.TopVarDecl(decl);
    }

    private Ast.FuncDef parseFuncDecl() {
        Token routeplanTok = consume(ROUTEPLAN, "expected routeplan keyword");
        Ast.Type returnType = parseType();
        Token name = consume(IDENT, "expected function name");

        consume(LPAREN, "expected '(' after function name");
        List<Ast.Param> params = new ArrayList<>();
        if (!check(RPAREN)) {
            params.add(parseParam());
            while (match(SEPARATOR_COMMA)) {
                params.add(parseParam());
            }
        }
        consume(RPAREN, "expected ')' after parameter list");

        consume(LCURLY, "expected '{' to start function body");
        List<Stmt> body = parseBlock();
        consume(RCURLY, "expected '}' after function body");

        return new Ast.FuncDef(routeplanTok, returnType, name, params, body);
    }

    private Ast.Param parseParam() {
        Ast.Type type = parseType();
        Token name = consume(IDENT, "expected parameter name");
        return new Ast.Param(name, type);
    }

    private List<Stmt> parseBlock() {
        List<Stmt> stmts = new ArrayList<>();
        while (!check(RCURLY) && !check(EOF)) {
            stmts.add(parseStmt());
        }
        return stmts;
    }

    private Ast.Type parseType() {
        Token t = advance(); // ALTITUDE/SLOPE/MARK/ROUTE/SECURE/BASECAMP

        Ast.Type.Kind kind;
        switch (t.type) {
            case ALTITUDE -> kind = Ast.Type.Kind.INT;
            case SLOPE -> kind = Ast.Type.Kind.DOUBLE;
            case MARK -> kind = Ast.Type.Kind.CHAR;
            case ROUTE -> kind = Ast.Type.Kind.STRING;
            case SECURE -> kind = Ast.Type.Kind.BOOL;
            case BASECAMP -> kind = Ast.Type.Kind.VOID;
            default -> throw error(t,
                    "expected type keyword (altitude/slope/mark/route/secure/basecamp)");
        }

        int rank = 0;
        while (match(LBRACKET)) {
            consume(RBRACKET, "expected ']'");
            rank++;
        }

        return new Ast.Type(kind, t, rank);
    }

    private boolean startsType() {
        return check(ALTITUDE) || check(SLOPE) || check(MARK)
                || check(ROUTE) || check(SECURE) || check(BASECAMP);
    }

    private Stmt.VarDecl parseVarDecl() {
        // altitude/slope/mark/route/secure/basecamp [ opt []]
        Ast.Type type = parseType();

        List<Expr> dims = new ArrayList<>();
        // var names
        List<Token> names = new ArrayList<>();

        Token name = consume(IDENT, "expected variable name");
        names.add(name);

        while (match(LBRACKET)) {
            Expr size = parseExpr();
            consume(RBRACKET, "expected ']' after array size");
            dims.add(size);
        }

        if (match(ASSIGN)) {
            Expr init = parseExpr();
        }

        while (match(SEPARATOR_COMMA)) {
            Token n = consume(IDENT, "expected variable name after ','");
            names.add(n);

            if (match(ASSIGN)) {
                Expr init2 = parseExpr();
            }
        }

        consume(SEMICOLON, "expected ';' after variable declaration");

        return new Stmt.VarDecl(type, dims, names);
    }

    private Stmt parseStmt() {
        if (startsType()) {
            return parseVarDecl();
        }

        if (check(DESCEND)) {
            return parseDescendStmt();
        }

        if (check(IFRIDGE)) {
            return parseIfStmt();
        }

        if (check(CLIMB)) {
            return parseClimbStmt();
        }

        if (check(BELAY)) {
            return parseWhileStmt();
        }

        if (check(ON)) {
            return parseDoWhileStmt();
        }

        Expr e = parseExpr();
        consume(SEMICOLON, "expected ';' after expression");
        return new Stmt.ExprStmt(e);
    }

    private Stmt.Return parseDescendStmt() {
        Token kw = consume(DESCEND, "expected descend keyword");
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = parseExpr();
        }
        consume(SEMICOLON, "expected ';' after descend");
        return new Stmt.Return(kw, value);
    }

    private Stmt parseIfStmt() {
        consume(IFRIDGE, "expected ifridge keyword");
        consume(LPAREN, "expected '(' after ifridge");
        Expr condition = parseExpr();
        consume(RPAREN, "expected ')' after condition");

        consume(LCURLY, "expected '{' to start if block");
        List<Stmt> thenBranch = parseBlock();
        consume(RCURLY, "expected '}' after if block");

        List<Stmt> elseBranch = null;

        if (match(ELSECAMP)) {
            if (check(IFRIDGE)) {
                Stmt nested = parseIfStmt();
                elseBranch = new ArrayList<>();
                elseBranch.add(nested);
            } else {
                consume(LCURLY, "expected '{' after elsecamp");
                elseBranch = parseBlock();
                consume(RCURLY, "expected '}' after else block");
            }
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt parseClimbStmt() {
        consume(CLIMB, "expected climb keyword");
        consume(LPAREN, "expected '(' after climb");

        Ast.Type initType = parseType();
        Token varName = consume(IDENT, "expected loop variable name");
        consume(ASSIGN, "expected '<>' after loop variable");
        Expr initExpr = parseExpr();
        consume(SEMICOLON, "expected ';' after for-init");

        Expr condition = parseExpr();
        consume(SEMICOLON, "expected ';' after for-condition");
        Expr update = parseExpr();
        consume(RPAREN, "expected ')' after for-update");

        consume(LCURLY, "expected '{' to start climb body");
        List<Stmt> body = parseBlock();
        consume(RCURLY, "expected '}' after climb body");

        return new Stmt.Climb(initType, varName, initExpr, condition, update, body);
    }

    private Stmt parseWhileStmt() {
        consume(BELAY, "expected belay keyword");
        consume(LPAREN, "expected '(' after belay");
        Expr condition = parseExpr();
        consume(RPAREN, "expected ')' after condition");

        consume(LCURLY, "expected '{' to start while body");
        List<Stmt> body = parseBlock();
        consume(RCURLY, "expected '}' after while body");

        return new Stmt.While(condition, body);
    }

    private Stmt parseDoWhileStmt() {
        consume(ON, "expected on keyword");
        consume(LCURLY, "expected '{' after on");
        List<Stmt> body = parseBlock();
        consume(RCURLY, "expected '}' after on block");

        consume(BELAY, "expected belay keyword after on-block");
        consume(LPAREN, "expected '(' after belay");
        Expr condition = parseExpr();
        consume(RPAREN, "expected ')' after condition");
        consume(SEMICOLON, "expected ';' after do-while");

        return new Stmt.DoWhile(body, condition);
    }

    private Expr parseExpr() {
        return parseAssignment();
    }

    private Expr parseAssignment() {
        Expr expr = parseOr();
        if (match(ASSIGN)) {
            Token op = previous();
            Expr value = parseAssignment();
            return new Expr.Binary(expr, op, value);
        }
        return expr;
    }

    private Expr parseOr() {
        Expr expr = parseAnd();
        while (match(OR)) {
            Token op = previous();
            Expr right = parseAnd();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr parseAnd() {
        Expr expr = parseEquality();
        while (match(AND)) {
            Token op = previous();
            Expr right = parseEquality();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr parseEquality() {
        Expr expr = parseComparison();
        while (match(EQ, NEQ)) {
            Token op = previous();
            Expr right = parseComparison();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr parseComparison() {
        Expr expr = parseAdd();
        while (match(LT, LE, GT, GE)) {
            Token op = previous();
            Expr right = parseAdd();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr parseAdd() {
        Expr expr = parseMul();
        while (match(PLUS, MINUS)) {
            Token op = previous();
            Expr right = parseMul();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr parseMul() {
        Expr expr = parseUnary();
        while (match(MULTIPLY, DIV, MODULO)) {
            Token op = previous();
            Expr right = parseUnary();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr parseUnary() {
        if (match(PLUS, MINUS, NOT)) {
            Token op = previous();
            Expr right = parseUnary();
            return new Expr.Unary(op, right);
        }
        return parsePostfix();
    }

    private Expr parsePostfix() {
        Expr expr = parsePower();
        while (match(PLUS2, MINUS2)) {
            Token op = previous();
            expr = new Expr.Unary(op, expr);
        }
        return expr;
    }

    private Expr parsePower() {
        Expr expr = parsePrimary();
        if (match(POW)) {
            Token op = previous();
            Expr right = parsePower();
            expr = new Expr.Binary(expr, op, right);
        }
        return expr;
    }

    private Expr parsePrimary() {
        if (match(ALTITUDE_LIT, SLOPE_LIT, MARK_LIT, ROUTE_LIT, ANCHORED, LOOSE, WHITEOUT)) {
            Token t = previous();
            Object value = t.literal;
            return new Expr.Literal(t, value);
        }

        if (match(IDENT, SHOUT, RADIO, BELAY)) {
            Token name = previous();

            if (match(LPAREN)) {
                List<Expr> args = new ArrayList<>();
                if (!check(RPAREN)) {
                    args.add(parseExpr());
                    while (match(SEPARATOR_COMMA)) {
                        args.add(parseExpr());
                    }
                }
                consume(RPAREN, "expected ')' after arguments");
                return new Expr.Call(name, args);
            }

            if (match(LBRACKET)) {
                List<Expr> indices = new ArrayList<>();
                indices.add(parseExpr());
                consume(RBRACKET, "expected ']' after index");
                while (match(LBRACKET)) {
                    indices.add(parseExpr());
                    consume(RBRACKET, "expected ']' after index");
                }
                return new Expr.Index(name, indices);
            }

            return new Expr.Ident(name);
        }

        if (match(LPAREN)) {
            Expr inner = parseExpr();
            consume(RPAREN, "expected ')'");
            return new Expr.Grouping(inner);
        }

        throw error(peek(), "expected expression");
    }

    private void skipNewlines() {
        while (current < tokens.size()
                && tokens.get(current).type == TokenType.NEWLINE) {
            current++;
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        skipNewlines();
        if (isAtEnd()) {
            return type == EOF;
        }
        return tokens.get(current).type == type;
    }


    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private Token advance() {
        skipNewlines();
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return current >= tokens.size() || tokens.get(current).type == EOF;
    }

    private Token peek() {
        if (current >= tokens.size()) {
            return tokens.get(tokens.size() - 1); // last -> EOF
        }
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        String where = token.type == EOF ? " at end" : " at '" + token.lexeme + "'";
        return new ParseError("Parse error" + where + ": " + message +
                " (line: " + token.line + ", col: " + token.colStart + ")");
    }

    private static final class ParseError extends RuntimeException {
        ParseError(String s) { super(s); }
    }
}
