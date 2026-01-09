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

    private static final Path JASMIN_JAR = Path.of("tools", "jasmin.jar");

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

            Lexer lexer = new Lexer(code);
            List<Token> tokens = lexer.scanTokens();
            System.out.println(TokenFormatter.formatList(tokens));

            ParserAst parser = new ParserAst(tokens);
            Ast.Program prog = parser.parseProgram();

            SemanticAnalyzer sem = new SemanticAnalyzer();
            sem.analyze(prog);

            Path outDir = Path.of("out");
            Files.createDirectories(outDir);

            Path outTyped = outDir.resolve("program.json");
            String jsonTyped = new JsonAstPrinter(sem.getExprTypes()).print(prog);
            Files.writeString(outTyped, jsonTyped, StandardCharsets.UTF_8);
            System.out.println("Typed AST written to: " + outTyped.toAbsolutePath());

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
            System.err.println("run skipped..");
            System.err.println("Missing " + JASMIN_JAR.toAbsolutePath());
            return;
        }

        int c1 = runProcess(outDir,
                javaExe(),
                "-jar", JASMIN_JAR.toAbsolutePath().toString(),
                jasminFile.toAbsolutePath().toString()
        );
        if (c1 != 0) {
            System.err.println("Jasmin compilation failed (exit=" + c1 + ")");
            return;
        }

        String mainClass = "MountaineeringProgram";
        Path classFile = outDir.resolve(mainClass + ".class");

        if (!Files.exists(classFile)) {
            System.err.println("Jasmin finished, but " + classFile.toAbsolutePath() + " not found.");
            return;
        }

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
            pb.inheritIO();
            Process p = pb.start();
            return p.waitFor();
        } catch (Exception e) {
            System.err.println("Failed to run: " + String.join(" ", cmd));
            System.err.println("Reason: " + escapeVisible(e.getMessage()));
            return 127;
        }
    }

    private static String javaExe() {
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
