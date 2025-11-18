package app;

import lexer.Lexer;
import lexer.token.Token;
import lexer.token.TokenFormatter;
import parser.ast.Ast;
import parser.ast.JsonAstPrinter;
import parser.ast.ParserAst;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java main.Application <source-file>");
            System.exit(64);
        }
        Path inputFile = null;
        try {
            inputFile = Paths.get(args[0]);
            String code = Files.readString(inputFile);
            Lexer lexer = new Lexer(code);
            List<Token> tokens = lexer.scanTokens();

            System.out.println(TokenFormatter.formatList(tokens));

            ParserAst parser = new ParserAst(tokens);
            Ast.Program prog = parser.parseProgram();

            String json = new JsonAstPrinter().print(prog);
            Path out = Path.of("program.json");
            Files.writeString(out, json);
            System.out.println("AST written to: " + out);
        }
        catch (FileNotFoundException e) {
            System.err.println("File not found: " + inputFile);
            System.exit(65);

        } catch (IOException e) {
            System.err.println("I/O error while reading " + inputFile + ": " + e.getMessage());
            System.exit(66);
        }
        catch (Exception e) {
            System.err.println("Error: " + escapeVisible(e.getMessage()));
            System.exit(1);
        }
    }

    private static String escapeVisible(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
