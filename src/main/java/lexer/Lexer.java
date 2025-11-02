package lexer;

import lexer.token.Token;
import lexer.token.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final ScannerCore sc;
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("altitude", TokenType.ALTITUDE),
            Map.entry("slope", TokenType.SLOPE),
            Map.entry("mark", TokenType.MARK),
            Map.entry("route", TokenType.ROUTE),
            Map.entry("secure", TokenType.SECURE),
            Map.entry("[]", TokenType.ARRAY),
            Map.entry("basecamp", TokenType.BASECAMP),
            Map.entry("shout", TokenType.SHOUT),
            Map.entry("radio", TokenType.RADIO),
            Map.entry("routeplan", TokenType.ROUTEPLAN),
            Map.entry("summit", TokenType.SUMMIT),
            Map.entry("anchored", TokenType.ANCHORED),
            Map.entry("loose", TokenType.LOOSE),
            Map.entry("whiteout", TokenType.WHITEOUT),
            Map.entry("<>", TokenType.ASSIGN),
            Map.entry("+", TokenType.ADD),
            Map.entry("-", TokenType.SUBTRACT),
            Map.entry("/", TokenType.DIVIDE),
            Map.entry("*", TokenType.MULTIPLY),
            Map.entry("%", TokenType.MODULO),
            Map.entry("==", TokenType.EQ),
            Map.entry("ifridge", TokenType.IFRIDGE),
            Map.entry("elsecamp", TokenType.ELSECAMP),
            Map.entry("climb", TokenType.CLIMB),
            Map.entry("belay", TokenType.BELAY),
            Map.entry("on", TokenType.ON),
            Map.entry("descend", TokenType.DESCEND)
    );

    public Lexer(String source) {
        this.source = source;
        this.sc = new ScannerCore(source);
    }

    public List<Token> scanTokens() {
        while (!sc.isAtEnd()) {
            sc.beginToken();
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "\0", null, sc.getLine(), sc.getCol(), sc.getCol()));
        return tokens;
    }

    private void scanToken() {
        char c = sc.advance();

        switch (c) {
            case '(' -> add(TokenType.LPAREN);
            case ')' -> add(TokenType.RPAREN);
            case '[' -> add(TokenType.LBRACKET);
            case ']' -> add(TokenType.RBRACKET);
            case '{' -> add(TokenType.LCURLY);
            case '}' -> add(TokenType.RCURLY);
            case ',' -> add(TokenType.SEPARATOR_COMMA);
            case ':' -> add(TokenType.TYPE_COLON);
            case '+' -> add(TokenType.ADD);
            case '-' -> add(TokenType.SUBTRACT);
            case '*' -> add(TokenType.MULTIPLY);
            case '/' -> add(TokenType.DIVIDE);
            case '%' -> add(TokenType.MODULO);
            case '<' -> add(sc.match('=') ? TokenType.LE : sc.match('>') ? TokenType.ASSIGN : TokenType.LT);
            case '>' -> add(sc.match('=') ? TokenType.GE : TokenType.GT);
            case ';' -> add(TokenType.SEMICOLON);
            case '=' -> {
                if (sc.match('=')) add(TokenType.EQ);
                else throw error("Unexpected '='");
            }
            case '!' -> {
                if (sc.match('=')) add(TokenType.NEQ);
                else throw error("Unexpected '!'");
            }
//            case '\n' -> tokens.add(new Token(
//                    TokenType.NEWLINE, "\n", null, sc.getStartLine(), sc.getStartCol(), sc.getStartCol()
//            ));
            case ' ', '\r', '\t', '\n' -> {}
            default -> {
                if (Character.isDigit(c)) number();
                else if (isIdentStart(c)) identifier();
                else throw error("Unexpected character");
            }
        }
    }

    private void number() {
        while (Character.isDigit(sc.peek())) sc.advance();
        if(sc.peek() == '.' && Character.isDigit(sc.peekNext())) {
            do sc.advance();
            while (Character.isDigit(sc.peek()));
        }
        String text = source.substring(sc.getStartIdx(), sc.getCur());
        char nextChar = sc.peek();
        if (Character.isAlphabetic(nextChar)) {
            throw error("Error: Character in numeric literal");
        }
        if(text.contains("."))
            addLiteralAltitude(text);
        else
            addLiteralSlope(text);
    }

    private void identifier() {
        while (isIdentPart(sc.peek())) sc.advance();
        String text = source.substring(sc.getStartIdx(), sc.getCur());
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENT);
        add(type, text);
    }

    private boolean isIdentStart(char c) { return Character.isLetter(c) || c == '_'; }
    private boolean isIdentPart(char c)  { return isIdentStart(c) || Character.isDigit(c); }

    private void add(TokenType type) {
        String lex = source.substring(sc.getStartIdx(), sc.getCur());
        tokens.add(new Token(type, lex, null,
                sc.getStartLine(), sc.getStartCol(), sc.getCol() - 1));
    }

    private void add(TokenType type, String text) {
        tokens.add(new Token(type, text, null,
                sc.getStartLine(), sc.getStartCol(), sc.getCol() - 1));
    }

    private void addLiteralAltitude(String literal) {
        tokens.add(new Token(TokenType.ALTITUDE, literal, Integer.valueOf(literal),
                sc.getStartLine(), sc.getStartCol(), sc.getCol() - 1));
    }

    private void addLiteralSlope(String literal) {
        tokens.add(new Token(TokenType.DOUBLE_LIT, literal, Double.valueOf(literal),
                sc.getStartLine(), sc.getStartCol(), sc.getCol() - 1));
    }

    private RuntimeException error(String msg) {
        String near = source.substring(sc.getStartIdx(), Math.min(sc.getCur(), source.length()));
        return new RuntimeException("LEXER > " + msg + " at " + sc.getStartLine() + ":" + sc.getStartCol() + " near '" + near + "'");
    }
}

