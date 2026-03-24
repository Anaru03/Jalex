package com.jalex.nfa;

import com.jalex.regex.RegexParser;
import java.util.*;

public class Thompson {

    public static NFA build(String regex) {
        RegexParser parser = new RegexParser(regex);
    return buildFromPostfix(parser.toPostfix());
    }

    public static NFA buildFromPostfix(List<String> postfix) {
        if (postfix == null || postfix.isEmpty()) return buildEpsilon();
        Deque<NFA> stack = new ArrayDeque<>();

        for (String tok : postfix) {
            if (tok.isBlank()) continue;
            switch (tok) {
                case "." -> { NFA b = stack.pop(); stack.push(concatenate(stack.pop(), b)); }
                case "|" -> { NFA b = stack.pop(); stack.push(union(stack.pop(), b)); }
                case "*" -> stack.push(kleeneStar(stack.pop()));
                case "+" -> stack.push(kleenePlus(stack.pop()));
                case "?" -> stack.push(optional(stack.pop()));
                case "#" -> { NFA b = stack.pop(); stack.push(difference(stack.pop(), b)); }
                default  -> stack.push(buildOperand(tok));
            }
        }

        if (stack.size() != 1)
            throw new IllegalStateException(
                "Expresión postfija mal formada. Stack tiene " + stack.size() +
                " elemento(s). Postfix: '" + postfix + "'");
        return stack.pop();
    }

    // ── Operandos ──────────────────────────────────────────────────────────

    private static NFA buildOperand(String tok) {
        if (tok.startsWith("'") && tok.endsWith("'") && tok.length() >= 3) {
            return buildChar(String.valueOf(parseCharLiteral(tok)));
        }
        if (tok.startsWith("\"") && tok.endsWith("\"")) {
            String inner = tok.substring(1, tok.length() - 1);
            if (inner.isEmpty()) return buildEpsilon();
            int pos = 0;
            char[] first = parseEscape(inner, pos);
            NFA result = buildChar(String.valueOf(first[0]));
            pos = (int) first[1];
            while (pos < inner.length()) {
                char[] next = parseEscape(inner, pos);
                result = concatenate(result, buildChar(String.valueOf(next[0])));
                pos = (int) next[1];
            }
            return result;
        }
        if (tok.startsWith("["))  return buildCharClass(tok);
        if (tok.equals("_"))      return buildWildcard();
        if (tok.equals("eof"))    return buildChar("EOF");
        if (tok.length() == 1)    return buildChar(tok);
        throw new IllegalArgumentException(
            "Token '" + tok + "' no reconocido. ¿Olvidaste expandir los `let` antes de Thompson?");
    }

    private static NFA buildChar(String symbol) {
        NFAState s = new NFAState(), e = new NFAState();
        s.addTransition(symbol, e);
        return new NFA(s, e);
    }

    static NFA buildEpsilon() {
        NFAState s = new NFAState(), e = new NFAState();
        s.addEpsilon(e);
        return new NFA(s, e);
    }

    private static NFA buildWildcard() {
        NFAState s = new NFAState(), e = new NFAState();
        s.addTransition("WILDCARD", e);
        return new NFA(s, e);
    }

    // ── Clase de caracteres ────────────────────────────────────────────────

    private static NFA buildCharClass(String tok) {
        boolean negated = tok.startsWith("[^");
        String inner    = negated ? tok.substring(2, tok.length() - 1)
                                  : tok.substring(1, tok.length() - 1);
        Set<Character> chars = parseCharSet(inner);
        StringBuilder sb = new StringBuilder(negated ? "CLASS_NEG:" : "CLASS:");
        new ArrayList<>(chars).stream().sorted().forEach(c -> sb.append(c).append(","));
        NFAState s = new NFAState(), e = new NFAState();
        s.addTransition(sb.toString(), e);
        return new NFA(s, e);
    }

    // ── parseCharSet ───────────────────────────────────────────────────────
    // Parsea el interior de [...] respetando ' ' como carácter literal válido.

    static Set<Character> parseCharSet(String inner) {
        Set<Character> result = new LinkedHashSet<>();
        int i = 0;

        while (i < inner.length()) {
            // Saltar blancos que sean separadores (NO dentro de comillas)
            while (i < inner.length()
                    && inner.charAt(i) != '\''
                    && inner.charAt(i) != '"'
                    && Character.isWhitespace(inner.charAt(i))) i++;
            if (i >= inner.length()) break;

            if (inner.charAt(i) == '\'') {
                int end = readCharLiteralEnd(inner, i);
                char c = parseCharLiteral(inner.substring(i, end));

                // ¿rango 'c1'-'c2'?
                int j = end;
                while (j < inner.length()
                        && inner.charAt(j) != '\''
                        && Character.isWhitespace(inner.charAt(j))) j++;
                if (j < inner.length() && inner.charAt(j) == '-') {
                    j++;
                    while (j < inner.length()
                            && inner.charAt(j) != '\''
                            && Character.isWhitespace(inner.charAt(j))) j++;
                    if (j < inner.length() && inner.charAt(j) == '\'') {
                        int end2 = readCharLiteralEnd(inner, j);
                        char c2 = parseCharLiteral(inner.substring(j, end2));
                        for (char r = c; r <= c2; r++) result.add(r);
                        i = end2;
                        continue;
                    }
                }
                result.add(c);
                i = end;
                continue;
            }

            if (inner.charAt(i) == '"') {
                int j = i + 1;
                while (j < inner.length() && inner.charAt(j) != '"') {
                    if (inner.charAt(j) == '\\') j++;
                    j++;
                }
                String s = inner.substring(i + 1, j);
                for (int k = 0; k < s.length(); ) {
                    char[] next = parseEscape(s, k);
                    result.add(next[0]);
                    k = (int) next[1];
                }
                i = j + 1;
                continue;
            }
            i++;
        }
        return result;
    }

    // Lee 'x', '\n', ' ' — retorna posición DESPUÉS de la comilla de cierre.
    private static int readCharLiteralEnd(String src, int start) {
        int i = start + 1;
        if (i >= src.length()) return i;
        if (src.charAt(i) == '\\') i += 2;
        else                        i += 1;
        if (i < src.length() && src.charAt(i) == '\'') i++;
        return i;
    }

    // ── Operadores de Thompson ─────────────────────────────────────────────

    static NFA concatenate(NFA a, NFA b) {
        a.end.addEpsilon(b.start);
        return new NFA(a.start, b.end);
    }

    static NFA union(NFA a, NFA b) {
        NFAState s = new NFAState(), e = new NFAState();
        s.addEpsilon(a.start); s.addEpsilon(b.start);
        a.end.addEpsilon(e);   b.end.addEpsilon(e);
        return new NFA(s, e);
    }

    static NFA kleeneStar(NFA a) {
        NFAState s = new NFAState(), e = new NFAState();
        s.addEpsilon(a.start); s.addEpsilon(e);
        a.end.addEpsilon(a.start); a.end.addEpsilon(e);
        return new NFA(s, e);
    }

    static NFA kleenePlus(NFA a) {
        NFAState s = new NFAState(), e = new NFAState();
        s.addEpsilon(a.start);
        a.end.addEpsilon(a.start); a.end.addEpsilon(e);
        return new NFA(s, e);
    }

    static NFA optional(NFA a) {
        NFAState s = new NFAState(), e = new NFAState();
        s.addEpsilon(a.start); s.addEpsilon(e);
        a.end.addEpsilon(e);
        return new NFA(s, e);
    }

    static NFA difference(NFA a, NFA b) {
        NFAState s = new NFAState(), e = new NFAState();
        s.addTransition("DIFF_START", a.start);
        b.end.addEpsilon(e); a.end.addEpsilon(e);
        return new NFA(s, e);
    }

    // ── NFA combinado ──────────────────────────────────────────────────────

    public static NFA combine(List<NFA> nfas, List<String> actions) {
        NFAState globalStart = new NFAState();
        for (int i = 0; i < nfas.size(); i++) {
            NFA n = nfas.get(i);
            String action = (actions != null && i < actions.size()) ? actions.get(i) : null;
            n.end.setAccepting(i, action);
            globalStart.addEpsilon(n.start);
        }
        return new NFA(globalStart, null);
    }

    // ── Helpers de parseo de caracteres ───────────────────────────────────

    static char parseCharLiteral(String tok) {
        if (tok.length() == 1) return tok.charAt(0);
        String inner = (tok.startsWith("'") && tok.endsWith("'") && tok.length() >= 2)
                ? tok.substring(1, tok.length() - 1) : tok;
        if (inner.isEmpty()) return 0;
        if (inner.startsWith("\\") && inner.length() >= 2)
            return parseEscapeChar(inner.charAt(1));
        return inner.charAt(0);
    }

    static char parseEscapeChar(char c) {
        return switch (c) {
            case 'n'  -> '\n'; case 't'  -> '\t'; case 'r'  -> '\r';
            case '\\' -> '\\'; case '\'' -> '\''; case '"'  -> '"';
            case '0'  -> '\0'; default   -> c;
        };
    }

    static char[] parseEscape(String s, int pos) {
        if (pos >= s.length()) return new char[]{0, (char) pos};
        char c = s.charAt(pos);
        if (c == '\\' && pos + 1 < s.length())
            return new char[]{parseEscapeChar(s.charAt(pos + 1)), (char)(pos + 2)};
        return new char[]{c, (char)(pos + 1)};
    }
}