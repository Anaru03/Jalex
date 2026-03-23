package com.jalex.regex;

import java.util.*;

/*
 * RegexParser:
 * Preprocesa una expresión regular en notación YALex:
 *
 * 1. insertConcatenationOperators() — inserta '.' explícito entre operandos
 *    que deben concatenarse, manejando: chars, strings, clases [], ?, +, *, ), ident
 *
 * 2. toPostfix() — convierte la regexp con '.' a notación postfija
 *    usando Shunting Yard de Dijkstra.
 *
 * Operadores soportados (precedencia de mayor a menor según YALex):
 *   #   diferencia de conjuntos  (mayor precedencia)
 *   * + ?   cerradura
 *   .   concatenación (insertada explícitamente)
 *   |   alternativa  (menor precedencia)
 *
 * Referencias:
 * - E. W. Dijkstra (1961), Shunting Yard Algorithm
 * - Especificación YALex (UVG CC3071)
 */
public class RegexParser {

    private final String regex;

    // Precedencia de operadores (mayor número = mayor precedencia)
    private static final Map<Character, Integer> PRECEDENCE = Map.of(
            '|', 1,
            '.', 2,
            '?', 3,
            '+', 3,
            '*', 3,
            '#', 4
    );

    // Operadores unarios (postfijos)
    private static final Set<Character> UNARY_OPS = Set.of('*', '+', '?');

    // Operadores binarios
    private static final Set<Character> BINARY_OPS = Set.of('|', '.', '#');

    public RegexParser(String regex) {
        this.regex = regex;
    }

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Inserta operador de concatenación '.' donde sea necesario.
     *
     * Regla: se inserta '.' entre token_A y token_B cuando:
     *   - token_A es: char literal, ], ), *, +, ?, _ (wildcard), cierre de string/"
     *   - token_B es: char literal, [, (, _ (wildcard), letra (inicio de ident), " '
     *
     * Se manejan los siguientes tokens compuestos:
     *   - Char literal:  'x'  o  '\n'
     *   - String:        "abc"
     *   - Clase:         [...]  o  [^...]
     *   - Wildcard:      _
     *   - Paréntesis:    ( )
     *   - Operadores:    * + ? | # .
     *   - ident:         secuencia de letras/dígitos/_
     */
    public String insertConcatenationOperators() {
        List<String> tokens = tokenize(regex);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < tokens.size(); i++) {
            String tok = tokens.get(i);
            result.append(tok);

            if (i + 1 < tokens.size()) {
                String next = tokens.get(i + 1);
                if (needsConcatenation(tok, next)) {
                    result.append('.');
                }
            }
        }
        return result.toString();
    }

    /**
     * Convierte la regexp (ya con '.' insertado) a notación postfija.
     * Llama a insertConcatenationOperators() internamente.
     *
     * @return expresión en notación postfija
     */
    public String toPostfix() {
        String withConcat = insertConcatenationOperators();
        return shuntingYard(withConcat);
    }

    // ── Tokenizer ──────────────────────────────────────────────────────────

    /**
     * Convierte la regexp cruda en una lista de tokens atómicos.
     * Tokens: char-literal 'x', string "abc", clase [...], _, ident, operadores.
     */
    public List<String> tokenize(String src) {
        List<String> tokens = new ArrayList<>();
        int i = 0;

        while (i < src.length()) {
            char c = src.charAt(i);

            // Blancos — ignorar
            if (Character.isWhitespace(c)) { i++; continue; }

            // Char literal: 'x' o '\escape'
            if (c == '\'') {
                int[] res = readCharLiteral(src, i);
                tokens.add(src.substring(i, res[0]));
                i = res[0];
                continue;
            }

            // String literal: "abc..."
            if (c == '"') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != '"') {
                    if (src.charAt(j) == '\\') j++;
                    j++;
                }
                j++; // cierre "
                tokens.add(src.substring(i, j));
                i = j;
                continue;
            }

            // Clase de caracteres: [...] o [^...]
            if (c == '[') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != ']') {
                    if (src.charAt(j) == '\'') {
                        int[] res = readCharLiteral(src, j);
                        j = res[0];
                    } else {
                        j++;
                    }
                }
                j++; // cierre ]
                tokens.add(src.substring(i, j));
                i = j;
                continue;
            }

            // Wildcard _
            if (c == '_') { tokens.add("_"); i++; continue; }

            // Identificador (ident definido por let)
            if (Character.isLetter(c)) {
                int j = i;
                while (j < src.length() && isIdentChar(src.charAt(j))) j++;
                String ident = src.substring(i, j);
                // Excepción: "eof" es un token especial de YALex
                tokens.add(ident);
                i = j;
                continue;
            }

            // Paréntesis y operadores
            tokens.add(String.valueOf(c));
            i++;
        }

        return tokens;
    }

    // ── Leer char literal ──────────────────────────────────────────────────

    /** Lee 'x' o '\escape' y devuelve la posición post-cierre. */
    private int[] readCharLiteral(String src, int start) {
        int i = start + 1; // saltar '
        if (i < src.length() && src.charAt(i) == '\\') i += 2; // \escape
        else i++;                                                // char normal
        if (i < src.length() && src.charAt(i) == '\'') i++;    // cierre '
        return new int[]{i};
    }

    // ── Regla de concatenación ─────────────────────────────────────────────

    /**
     * Decide si hay que insertar '.' entre el token actual (left) y el siguiente (right).
     *
     * Se inserta cuando:
     *   left  puede terminar un operando: literal, ], ), *, +, ?, _, ident, string
     *   right puede comenzar un operando: literal, (, [, _, ident, string
     */
    private boolean needsConcatenation(String left, String right) {
        // left no puede terminar operando si es operador binario o '('
        if (isBinaryOp(left) || left.equals("(")) return false;

        // right no puede comenzar operando si es operador o ')'
        if (isBinaryOp(right) || right.equals(")")) return false;
        if (isUnaryOp(right)) return false;

        return true;
    }

    private boolean isBinaryOp(String tok) {
        return tok.length() == 1 && BINARY_OPS.contains(tok.charAt(0));
    }

    private boolean isUnaryOp(String tok) {
        return tok.length() == 1 && UNARY_OPS.contains(tok.charAt(0));
    }

    // ── Shunting Yard ──────────────────────────────────────────────────────

    /**
     * Convierte la expresión infija (con '.' explícito) a postfija.
     *
     * Trata cada token de la lista de tokenize como una unidad.
     * Operadores:  | . # * + ?
     * Paréntesis:  ( )
     * Todo lo demás es operando (literal, string, clase, ident, wildcard).
     */
    private String shuntingYard(String expr) {
        List<String> tokens = tokenize(expr);
        Queue<String> output = new LinkedList<>();
        Deque<String> ops = new ArrayDeque<>();

        for (String tok : tokens) {
            if (tok.equals("(")) {
                ops.push(tok);
            } else if (tok.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) {
                    output.add(ops.pop());
                }
                if (!ops.isEmpty()) ops.pop(); // remover '('
            } else if (isOperatorToken(tok)) {
                while (!ops.isEmpty()
                        && isOperatorToken(ops.peek())
                        && shouldPopOperator(ops.peek(), tok)) {
                    output.add(ops.pop());
                }
                ops.push(tok);
            } else {
                // operando
                output.add(tok);
            }
        }

        while (!ops.isEmpty()) {
            output.add(ops.pop());
        }

        return String.join(" ", output);
    }

    private boolean isOperatorToken(String tok) {
        if (tok.length() != 1) return false;
        char c = tok.charAt(0);
        return PRECEDENCE.containsKey(c);
    }

    /**
     * ¿Debemos sacar `top` de la pila antes de meter `incoming`?
     * Para operadores left-associative: cuando prec(top) >= prec(incoming).
     * Unary ops (*, +, ?) son right-associative entre sí pero en postfijo
     * se emiten inmediatamente, así que el criterio >= funciona bien.
     */
    private boolean shouldPopOperator(String top, String incoming) {
        if (!isOperatorToken(top)) return false;
        int precTop = PRECEDENCE.getOrDefault(top.charAt(0), 0);
        int precIn  = PRECEDENCE.getOrDefault(incoming.charAt(0), 0);
        return precTop >= precIn;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public String getRegex() { return regex; }
}
