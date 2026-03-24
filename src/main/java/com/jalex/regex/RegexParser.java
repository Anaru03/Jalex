package com.jalex.regex;

import java.util.*;

public class RegexParser {

    private final String regex;

    private static final Map<Character, Integer> PRECEDENCE = Map.of(
            '|', 1, '.', 2, '?', 3, '+', 3, '*', 3, '#', 4);
    private static final Set<Character> UNARY_OPS  = Set.of('*', '+', '?');
    private static final Set<Character> BINARY_OPS = Set.of('|', '.', '#');

    public RegexParser(String regex) { this.regex = regex; }

    public String insertConcatenationOperators() {
        List<String> tokens = tokenize(regex);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String tok = tokens.get(i);
            result.append(tok);
            if (i + 1 < tokens.size() && needsConcatenation(tok, tokens.get(i + 1)))
                result.append('.');
        }
        return result.toString();
    }

    public List<String> toPostfix() {
        return shuntingYard(insertConcatenationOperators());
    }

    // ── Tokenizer ──────────────────────────────────────────────────────────

    public List<String> tokenize(String src) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);

            if (Character.isWhitespace(c)) { i++; continue; }

            // Char literal: 'x', '\n', ' '
            if (c == '\'') {
                int end = readCharLiteral(src, i);
                tokens.add(src.substring(i, end));
                i = end;
                continue;
            }

            // String literal: "abc"
            if (c == '"') {
                int j = i + 1;
                while (j < src.length() && src.charAt(j) != '"') {
                    if (src.charAt(j) == '\\') j++;
                    j++;
                }
                j++;
                tokens.add(src.substring(i, j));
                i = j;
                continue;
            }

            // Clase de caracteres: [...] o [^...]
            if (c == '[') {
                int j = i + 1;
                if (j < src.length() && src.charAt(j) == '^') j++;

                while (j < src.length()) {
                    char cc = src.charAt(j);

                    if (cc == '\'') {
                        j = readCharLiteral(src, j); // BIEN
                        continue;
                    }

                    if (cc == '"') {
                        j++;
                        while (j < src.length() && src.charAt(j) != '"') {
                            if (src.charAt(j) == '\\') j++;
                            j++;
                        }
                        j++;
                        continue;
                    }

                    if (cc == ']') {
                        j++;
                        break;
                    }

                    j++;
                }

                tokens.add(src.substring(i, j));
                i = j;
                continue;
            }
            if (c == '_') { tokens.add("_"); i++; continue; }

            if (Character.isLetter(c)) {
                int j = i;
                while (j < src.length() && isIdentChar(src.charAt(j))) j++;
                tokens.add(src.substring(i, j));
                i = j;
                continue;
            }

            tokens.add(String.valueOf(c));
            i++;
        }
        return tokens;
    }

    // ── readCharLiteral ────────────────────────────────────────────────────
    // Lee 'x', '\n', ' ' — el espacio es un carácter válido igual que cualquier otro.
    // Retorna la posición DESPUÉS de la comilla de cierre.
    private int readCharLiteral(String src, int start) {
        int i = start + 1;              // saltar '
        if (i >= src.length()) return i;
        if (src.charAt(i) == '\\') i += 2;   // escape: '\' + char
        else                        i += 1;   // cualquier char, incluyendo espacio
        if (i < src.length() && src.charAt(i) == '\'') i++;  // cierre '
        return i;
    }

    // ── Regla de concatenación ─────────────────────────────────────────────

    private boolean needsConcatenation(String left, String right) {
        if (isBinaryOp(left) || left.equals("(")) return false;
            if (isBinaryOp(right) || right.equals(")") || isUnaryOp(right)) 
                return false;
        return true;
    }

    private boolean isBinaryOp(String tok) {
        return tok.length() == 1 && BINARY_OPS.contains(tok.charAt(0));
    }
    private boolean isUnaryOp(String tok) {
        return tok.length() == 1 && UNARY_OPS.contains(tok.charAt(0));
    }

    // ── Shunting Yard ──────────────────────────────────────────────────────

    private List<String> shuntingYard(String expr) {
        List<String> tokens = tokenize(expr);
        Queue<String> output = new LinkedList<>();
        Deque<String> ops    = new ArrayDeque<>();

        for (String tok : tokens) {
            if (tok.equals("(")) {
                ops.push(tok);
            } else if (tok.equals(")")) {
                while (!ops.isEmpty() && !ops.peek().equals("(")) output.add(ops.pop());
                if (!ops.isEmpty()) ops.pop();
            } else if (isOperatorToken(tok)) {
                while (!ops.isEmpty() && isOperatorToken(ops.peek()) && shouldPop(ops.peek(), tok))
                    output.add(ops.pop());
                ops.push(tok);
            } else {
                output.add(tok);
            }
        }
        while (!ops.isEmpty()) output.add(ops.pop());
        return new ArrayList<>(output);
    }

    private boolean isOperatorToken(String tok) {
        return tok.length() == 1 && PRECEDENCE.containsKey(tok.charAt(0));
    }
    private boolean shouldPop(String top, String incoming) {
        if (!isOperatorToken(top)) return false;
        return PRECEDENCE.getOrDefault(top.charAt(0), 0)
             >= PRECEDENCE.getOrDefault(incoming.charAt(0), 0);
    }
    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public String getRegex() { return regex; }
}