package com.jalex;

import com.jalex.nfa.NFA;
import com.jalex.nfa.NFAState;
import com.jalex.nfa.Thompson;
import com.jalex.regex.RegexParser;
import com.jalex.yal.YalParseException;
import com.jalex.yal.YalParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Main:
 * Punto de entrada de Jalex.
 *
 * Flujo actual (Parte 1):
 *   1. Parsear archivo .yal
 *   2. Expandir lets en cada regexp
 *   3. Convertir cada regexp a postfijo (RegexParser)
 *   4. Construir NFA por regexp (Thompson)
 *   5. Combinar todos los NFAs en uno global
 *
 * Flujo pendiente (Parte 2 - colaborador):
 *   6. Subset Construction (NFA → DFA)
 *   7. Generación de GeneratedLexer.java
 *
 * Uso:
 *   java -cp out com.jalex.Main [archivo.yal]
 *   Si no se pasa archivo, usa un ejemplo embebido.
 */
public class Main {

    public static void main(String[] args) {

        System.out.println("╔══════════════════════════════════╗");
        System.out.println("║       Jalex - YALex Generator    ║");
        System.out.println("╚══════════════════════════════════╝\n");

        if (args.length > 0) {
            // Modo: leer archivo .yal pasado como argumento
            runFromFile(args[0]);
        } else {
            // Modo demo: usar ejemplo embebido del PDF
            runDemo();
        }
    }

    // ── Modo archivo ───────────────────────────────────────────────────────

    private static void runFromFile(String path) {
        System.out.println("Leyendo archivo: " + path + "\n");
        try {
            YalParser yal = YalParser.parse(path);
            processYal(yal);
        } catch (java.io.IOException e) {
            System.err.println("ERROR: No se pudo leer el archivo: " + e.getMessage());
        } catch (YalParseException e) {
            System.err.println("ERROR de parseo .yal: " + e.getMessage());
        }
    }

    // ── Modo demo ──────────────────────────────────────────────────────────

    private static void runDemo() {
        System.out.println("── Demo: ejemplo del PDF (ejemplo.yal) ──\n");

        // Fuente del ejemplo del documento YALex
        String source = """
                (* ejemplo.yal *)
                {
                  import myToken
                }
                rule gettoken =
                    [' ' '\\t']     { return lexbuf }
                  | ['\\n' ]        { return EOL }
                  | ['0'-'9']+      { return int(lxm) }
                  | '+'             { return PLUS }
                  | '-'             { return MINUS }
                  | '*'             { return TIMES }
                  | '/'             { return DIV }
                  | '('             { return LPAREN }
                  | ')'             { return RPAREN }
                  | eof             { raise('Fin de buffer') }
                {
                  (* fin de archivo *)
                }
                """;

        try {
            YalParser yal = YalParser.parseString(source);
            processYal(yal);
        } catch (YalParseException e) {
            System.err.println("ERROR de parseo .yal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Procesamiento principal ────────────────────────────────────────────

    /**
     * Pipeline Parte 1:
     *   YalParser → RegexParser → Thompson → NFA combinado
     */
    private static void processYal(YalParser yal) {

        // ── 1. Mostrar estructura parseada ─────────────────────────────────
        System.out.println(yal);
        System.out.println("─".repeat(50));

        // ── 2. Construir NFA para cada regla ───────────────────────────────
        List<YalParser.Rule> expandedRules = yal.getExpandedRules();
        List<NFA> nfas    = new ArrayList<>();
        List<String> acts = new ArrayList<>();

        NFAState.resetCounter(); // IDs desde 0 para este NFA

        System.out.println("── Construcción de NFAs por regla ──\n");

        for (int i = 0; i < expandedRules.size(); i++) {
            YalParser.Rule rule = expandedRules.get(i);

            System.out.println("[Regla " + i + "] regexp: " + rule.regexp);

            // Postfijo
            RegexParser rp = new RegexParser(rule.regexp);
            String withConcat = rp.insertConcatenationOperators();
            String postfix    = rp.toPostfix();
            System.out.println("  → con concat : " + withConcat);
            System.out.println("  → postfijo   : " + postfix);

            // Thompson
            NFA nfa;
            try {
                nfa = Thompson.buildFromPostfix(postfix);
            } catch (Exception e) {
                System.err.println("  ✗ ERROR al construir NFA: " + e.getMessage());
                continue;
            }

            System.out.println("  → NFA         : start=" + nfa.start + ", end=" + nfa.end);
            System.out.println("  → Estados     : " + nfa.getAllStates().size());
            System.out.println(nfa);

            nfas.add(nfa);
            acts.add(rule.action);
        }

        // ── 3. Combinar en un NFA global ───────────────────────────────────
        if (!nfas.isEmpty()) {
            System.out.println("─".repeat(50));
            System.out.println("── NFA Global (combinado) ──\n");

            NFA combined = Thompson.combine(nfas, acts);

            System.out.println("Estado inicial global: " + combined.start);
            System.out.println("Total de estados     : " + combined.getAllStates().size());
            System.out.println("Alfabeto del NFA     : " + combined.getAlphabet());
            System.out.println();

            // Mostrar estados aceptantes
            System.out.println("Estados aceptantes:");
            for (var state : combined.getAllStates()) {
                if (state.isAccepting) {
                    System.out.println("  " + state + " → acción: " +
                            (state.action != null ? state.action : "(ninguna)"));
                }
            }

            System.out.println();
            System.out.println("─".repeat(50));
            System.out.println("✓ Parte 1 completa.");
            System.out.println("  El colaborador puede tomar `combined` para Subset Construction (DFA).");

        } else {
            System.out.println("No se construyeron NFAs (revisa las reglas del .yal).");
        }
    }
}
