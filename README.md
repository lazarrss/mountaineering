# Mountaineering — A Custom Programming Language and Compiler

A compiler for **Mountaineering**, a small statically typed, mountaineering-themed programming language, written in **Java**.
The compiler takes `.mt` source files through a complete multi-stage pipeline and produces **Jasmin assembly**, which is assembled into runnable **JVM bytecode**.

```
source (.mt) → Lexer → Parser → AST → Semantic Analysis → Typed AST (JSON) → Jasmin (.j) → JVM .class
```

> **Branches:** `master` contains the lexer, parser and AST stages. The complete pipeline, including semantic analysis and Jasmin code generation, is on the [`testing`](../../tree/testing) branch.

## The Language

```
routeplan altitude addAltitudes(altitude a, altitude b) {
    altitude result <> a + b;
    descend result;
}

routeplan basecamp summit() {
    altitude x <> 10;
    altitude steps[5];

    climb (altitude i <> 0; i < 5; i <> i + 1) {
        steps[i] <> i * x;
    }

    ifridge (x > 5) {
        shout("High altitude");
    } elsecamp {
        shout("At basecamp");
    }

    radio(addAltitudes(x, 20));
    descend;
}
```

| Mountaineering | Meaning |
|---|---|
| `altitude`, `slope`, `mark`, `route`, `secure` | `int`, `double`, `char`, `string`, `bool` |
| `basecamp` | `void` |
| `anchored` / `loose` / `whiteout` | `true` / `false` / `null` |
| `routeplan` | function declaration |
| `summit()` | program entry point |
| `<>` | assignment |
| `ifridge` / `elsecamp` | `if` / `else` |
| `climb (...)` | `for` loop |
| `belay (...)` | `while` loop |
| `on { ... } belay (...)` | `do-while` loop |
| `descend` | `return` |
| `shout(...)` / `radio(...)` | print a string / print a value |
| `and`, `or`, `not` | logical operators |

The language also supports multidimensional arrays, nested control flow, functions with parameters and return values, and the usual arithmetic, comparison and logical operators.

## Compiler Stages

1. **Lexical analysis** (`lexer/`): hand-written scanner that turns source code into tokens and reports lexical errors such as unknown characters and malformed numbers.
2. **Parsing** (`parser/ast/`): recursive-descent parser that builds an abstract syntax tree of statements and expressions.
3. **Semantic analysis** (`semantic/`): symbol tables with nested scopes, function and variable symbols, type checking for expressions, assignments, array indexing, function calls and return values.
4. **Typed AST export**: the AST, annotated with inferred types, is written to `out/program.json`.
5. **Code generation** (`intermidiate/`): emits Jasmin assembly (`out/program.j`), which is then assembled with `jasmin.jar` and executed on the JVM.

## Test Programs

`src/main/resources/` contains example programs covering both valid code and error cases, for example:

- `01_basic_summit.mt`: variables and printing
- `03_arrays_and_indices.mt`, `11_multidimensional_arrays.mt`: arrays
- `04_belay_and_on.mt`: `while` and `do-while` loops
- `05_functions_and_params.mt`, `13_functions_returning_values.mt`: functions
- `07_syntax_error_missing_semicolon.mt`, `09_lexical_error_unknown_char.mt`: expected compile errors

## Running

Requirements: **Java 24** and **Maven**.

Build the project with `mvn compile`, then run `app.Main` with a source file as its only argument (for example from IntelliJ IDEA):

```
app.Main src/main/resources/05_functions_and_params.mt
```

To assemble and run the generated code automatically, place `jasmin.jar` in `tools/jasmin.jar`.

## Tech Stack

Java · Maven · Jackson (JSON AST export) · Jasmin · JVM
