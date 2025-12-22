package app;

import lexer.Lexer;
import lexer.token.Token;
import lexer.token.TokenFormatter;
import parser.ast.Ast;
import parser.ast.JsonAstPrinter;
import parser.ast.ParserAst;
import semantic.SemanticAnalyzer;
import semantic.errors.SemanticException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public class Main {

    // Putanja do jasmin.jar (stavi ga u projekat pod tools/)
    private static final Path JASMIN_JAR = Path.of("tools", "jasmin.jar");

    // Ako želiš automatski da izvršiš generisani bytecode posle generisanja
    private static final boolean RUN_GENERATED = true;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java app.Main <source-file>");
            System.exit(64);
        }

        Path inputFile = null;

        try {
            inputFile = Paths.get(args[0]);
            String code = Files.readString(inputFile);

            // 1) LEX
            Lexer lexer = new Lexer(code);
            List<Token> tokens = lexer.scanTokens();
            System.out.println(TokenFormatter.formatList(tokens));

            // 2) PARSE
            ParserAst parser = new ParserAst(tokens);
            Ast.Program prog = parser.parseProgram();

            // 3) SEMANTIC
            System.out.println("=== SEMANTIC: start ===");
            SemanticAnalyzer sem = new SemanticAnalyzer();
            sem.analyze(prog);
            System.out.println("=== SEMANTIC: OK (no errors) ===");
            System.out.println("=== SEMANTIC: typed expr nodes = " + sem.getExprTypes().size() + " ===");

            // 4) OUTPUT DIR
            Path outDir = Path.of("out");
            Files.createDirectories(outDir);

            // 5) JSON (TYPED)
            Path outTyped = outDir.resolve("program.json");
            String jsonTyped = new JsonAstPrinter(sem.getExprTypes()).print(prog);
            Files.writeString(outTyped, jsonTyped, StandardCharsets.UTF_8);
            System.out.println("Typed AST written to: " + outTyped.toAbsolutePath());

            // 6) JASMIN
            intermidiate.IntermediateGenerator gen = new intermidiate.IntermediateGenerator();
            intermidiate.JasminProgram jp = gen.generate(prog, sem.getExprTypes());

            Path outJ = outDir.resolve("program.j");
            jp.writeTo(outJ);
            System.out.println("Jasmin written to: " + outJ.toAbsolutePath());

            if (RUN_GENERATED) {
                compileAndRunJasmin(outDir, outJ);
            }

        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + inputFile);
            System.exit(65);

        } catch (SemanticException se) {
            System.err.println(se.error.format());
            System.exit(1);

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(66);

        } catch (Exception e) {
            System.err.println("Error: " + escapeVisible(e.getMessage()));
            System.exit(1);
        }
    }

    private static void compileAndRunJasmin(Path outDir, Path jasminFile) {
        if (!Files.exists(JASMIN_JAR)) {
            System.err.println("=== RUN: skipped ===");
            System.err.println("Missing " + JASMIN_JAR.toAbsolutePath() + " (place jasmin.jar there or change JASMIN_JAR path).");
            return;
        }

        System.out.println("=== RUN: jasmin compile ===");
        int c1 = runProcess(outDir,
                javaExe(),
                "-jar", JASMIN_JAR.toAbsolutePath().toString(),
                jasminFile.toAbsolutePath().toString()
        );
        if (c1 != 0) {
            System.err.println("Jasmin compilation failed (exit=" + c1 + ")");
            return;
        }

        // Ime klase mora da se poklopi sa .class u .j
        String mainClass = "MountaineeringProgram";
        Path classFile = outDir.resolve(mainClass + ".class");

        if (!Files.exists(classFile)) {
            System.err.println("Jasmin finished, but " + classFile.toAbsolutePath() + " not found.");
            return;
        }

        System.out.println("=== RUN: java " + mainClass + " ===");
        int c2 = runProcess(outDir,
                javaExe(),
                "-cp", outDir.toAbsolutePath().toString(),
                mainClass
        );
        if (c2 != 0) {
            System.err.println("Program exited with code " + c2);
        }
    }

    private static int runProcess(Path workingDir, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workingDir.toFile());
            pb.inheritIO(); // stdout/stderr u istoj IntelliJ konzoli
            Process p = pb.start();
            return p.waitFor();
        } catch (Exception e) {
            System.err.println("Failed to run: " + String.join(" ", cmd));
            System.err.println("Reason: " + escapeVisible(e.getMessage()));
            return 127;
        }
    }

    private static String javaExe() {
        // koristi isto java koje pokrece IntelliJ run
        String home = System.getProperty("java.home");
        Path p = Path.of(home, "bin", isWindows() ? "java.exe" : "java");
        return p.toString();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private static String escapeVisible(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
