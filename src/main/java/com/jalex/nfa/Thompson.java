package com.jalex.nfa;

import com.jalex.regex.RegexParser;

import java.util.*;

/**
 * Thompson:
 * Implementa la Construcción de Thompson para convertir una expresión regular
 * en notación postfija a un NFA.
 *
 * Operadores manejados:
 *   .   concatenación
 *   |   alternativa (unión)
 *   *   Kleene (cerradura)
 *   +   cerradura positiva
 *   ?   opcional
 *   #   diferencia de conjuntos (character-set difference)
 *
 * Operandos:
 *   'c'         carácter literal
 *   '\n' etc.   secuencias de escape
 *   "abc"       string (concatenación implícita de chars)
 *   [...]       clase de caracteres (rango e.g. 'a'-'z')
 *   [^...]      negación de clase
 *   _           wildcard (cualquier carácter)
 *   eof         fin de entrada (estado especial)
 *
 * Referencia: Alfred V. Aho et al., "Compilers: Principles, Techniques, and Tools"
 *             Sección 3.7.4 - Thompson's construction
 */
public class Thompson {

    // ── Punto de entrada ───────────────────────────────────────────────────

    /**
     * Construye el NFA para una expresión regular YALex.
     * Internamente convierte la regexp a postfijo y aplica Thompson.
     *
     * @param regex expresión regular en notación infija YALex (ya expandida)
     * @return NFA resultante
     */
    public static NFA build(String regex) {
        RegexParser parser = new RegexParser(regex);
        String postfix = parser.toPostfix();
        return buildFromPostfix(postfix);
    }

    /**
     * Construye el NFA directamente desde expresión postfija (tokens separados por espacio).
     */
    public static NFA buildFromPostfix(String postfix) {
        if (postfix == null || postfix.isBlank()) {
            // NFA vacío: acepta cadena vacía (ε)
            return buildEpsilon();
        }

        String[] tokens = postfix.split(" ");
        Deque<NFA> stack = new ArrayDeque<>();

        for (String tok : tokens) {
            if (tok.isBlank()) continue;

            switch (tok) {
                case "." -> {
                    NFA b = stack.pop();
                    NFA a = stack.pop();
                    stack.push(concatenate(a, b));
                }
                case "|" -> {
                    NFA b = stack.pop();
                    NFA a = stack.pop();
                    stack.push(union(a, b));
                }
                case "*" -> stack.push(kleeneStar(stack.pop()));
                case "+" -> stack.push(kleenePlus(stack.pop()));
                case "?" -> stack.push(optional(stack.pop()));
                case "#" -> {
                    NFA b = stack.pop();   // conjunto a restar
                    NFA a = stack.pop();   // conjunto base
                    stack.push(difference(a, b));
                }
                default  -> stack.push(buildOperand(tok));
            }
        }

        if (stack.size() != 1) {
            throw new IllegalStateException(
                    "Expresión postfija mal formada. Stack tiene " + stack.size() +
                    " elemento(s) al final. Postfix: '" + postfix + "'");
        }

        return stack.pop();
    }

    // ── Construcción de operandos ──────────────────────────────────────────

    /**
     * Crea un NFA para un operando atómico.
     * Tipos reconocidos:
     *   'c' / '\n'   → carácter literal
     *   "string"     → concatenación de literales
     *   [...]        → clase de caracteres
     *   [^...]       → negación de clase
     *   _            → wildcard
     *   eof          → token especial de fin de entrada
     *   ident        → error (debe estar expandido antes de llegar aquí)
     */
    private static NFA buildOperand(String tok) {
        // Char literal: 'x' o '\escape'
        if (tok.startsWith("'") && tok.endsWith("'")) {
            char c = parseCharLiteral(tok);
            return buildChar(String.valueOf(c));
        }

        // String literal: "abc..."
        if (tok.startsWith("\"") && tok.endsWith("\"")) {
            String inner = tok.substring(1, tok.length() - 1);
            if (inner.isEmpty()) return buildEpsilon();
            // Construir concatenación de chars
            NFA result = buildChar(String.valueOf(parseEscape(inner, 0)[0]));
            int[] pos = {parseEscape(inner, 0)[1]};
            while (pos[0] < inner.length()) {
                char[] next = parseEscape(inner, pos[0]);
                result = concatenate(result, buildChar(String.valueOf(next[0])));
                pos[0] = next[1];
            }
            return result;
        }

        // Clase de caracteres: [...] o [^...]
        if (tok.startsWith("[")) {
            return buildCharClass(tok);
        }

        // Wildcard: _
        if (tok.equals("_")) {
            return buildWildcard();
        }

        // EOF
        if (tok.equals("eof")) {
            return buildChar("EOF");
        }

        // Dígito suelto o letra (no debería llegar aquí si los lets están expandidos)
        // Tratamos como literal si es un solo carácter
        if (tok.length() == 1) {
            return buildChar(tok);
        }

        // ident no expandido — lanzar error claro
        throw new IllegalArgumentException(
                "Token '" + tok + "' no reconocido. ¿Olvidaste expandir los `let` antes de Thompson?");
    }

    // ── Primitivas de Thompson ─────────────────────────────────────────────

    /** NFA que acepta exactamente el símbolo `symbol`. */
    private static NFA buildChar(String symbol) {
        NFAState s = new NFAState();
        NFAState e = new NFAState();
        s.addTransition(symbol, e);
        return new NFA(s, e);
    }

    /** NFA que acepta ε (cadena vacía). */
    static NFA buildEpsilon() {
        NFAState s = new NFAState();
        NFAState e = new NFAState();
        s.addEpsilon(e);
        return new NFA(s, e);
    }

    /** NFA comodín: acepta cualquier símbolo. */
    private static NFA buildWildcard() {
        NFAState s = new NFAState();
        NFAState e = new NFAState();
        s.addTransition("WILDCARD", e);
        return new NFA(s, e);
    }

    // ── Clase de caracteres ────────────────────────────────────────────────

    /**
     * Construye un NFA para una clase de caracteres [...]  o [^...].
     *
     * Soporta:
     *   [abc]       → a | b | c
     *   ['a'-'z']   → a | b | ... | z  (rango)
     *   ["abc"]     → a | b | c         (string dentro de clase)
     *   [^...]      → negación (se codifica como símbolo especial "CLASS_NEG:...")
     *
     * El símbolo almacenado en la transición para clases es:
     *   "CLASS:a,b,c"        para clase normal
     *   "CLASS_NEG:a,b,c"   para clase negada
     * El colaborador deberá interpretar estos símbolos en el DFA.
     */
    private static NFA buildCharClass(String tok) {
        boolean negated = tok.startsWith("[^");
        String inner = negated
                ? tok.substring(2, tok.length() - 1)
                : tok.substring(1, tok.length() - 1);

        Set<Character> chars = parseCharSet(inner);

        // Serializar como símbolo compuesto para las transiciones del NFA
        StringBuilder sb = new StringBuilder(negated ? "CLASS_NEG:" : "CLASS:");
        List<Character> sorted = new ArrayList<>(chars);
        Collections.sort(sorted);
        for (char c : sorted) sb.append(c).append(",");

        NFAState s = new NFAState();
        NFAState e = new NFAState();
        s.addTransition(sb.toString(), e);
        return new NFA(s, e);
    }

    /**
     * Parsea el interior de una clase [...] y devuelve el conjunto de caracteres.
     * Maneja:
     *   - Char literals:  'c'  o  '\n'
     *   - Rangos:         'c1'-'c2'
     *   - Strings:        "abcd"
     */
    static Set<Character> parseCharSet(String inner) {
        Set<Character> result = new LinkedHashSet<>();
        int i = 0;

        while (i < inner.length()) {
            // Saltar blancos entre elementos de la clase
            while (i < inner.length() && Character.isWhitespace(inner.charAt(i))) i++;
            if (i >= inner.length()) break;

            // Char literal: 'c' o '\escape'
            if (inner.charAt(i) == '\'') {
                int end = i + 1;
                if (end < inner.length() && inner.charAt(end) == '\\') end += 2;
                else end++;
                if (end < inner.length() && inner.charAt(end) == '\'') end++;
                char c = parseCharLiteral(inner.substring(i, end));

                // ¿Rango?  'c1'-'c2'
                int j = end;
                while (j < inner.length() && Character.isWhitespace(inner.charAt(j))) j++;
                if (j < inner.length() && inner.charAt(j) == '-') {
                    j++; // saltar '-'
                    while (j < inner.length() && Character.isWhitespace(inner.charAt(j))) j++;
                    if (j < inner.length() && inner.charAt(j) == '\'') {
                        int end2 = j + 1;
                        if (end2 < inner.length() && inner.charAt(end2) == '\\') end2 += 2;
                        else end2++;
                        if (end2 < inner.length() && inner.charAt(end2) == '\'') end2++;
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

            // String: "abcd"
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

            i++; // carácter no reconocido → ignorar
        }

        return result;
    }

    // ── Operadores de Thompson ─────────────────────────────────────────────

    /**
     * Concatenación: a.b
     *
     *  (a.start) →...→ (a.end) --ε--> (b.start) →...→ (b.end)
     */
    static NFA concatenate(NFA a, NFA b) {
        a.end.addEpsilon(b.start);
        return new NFA(a.start, b.end);
    }

    /**
     * Unión: a|b
     *
     *          --ε--> (a.start) →...→ (a.end) --ε--\
     * (new_s) -                                       -> (new_e)
     *          --ε--> (b.start) →...→ (b.end) --ε--/
     */
    static NFA union(NFA a, NFA b) {
        NFAState s = new NFAState();
        NFAState e = new NFAState();
        s.addEpsilon(a.start);
        s.addEpsilon(b.start);
        a.end.addEpsilon(e);
        b.end.addEpsilon(e);
        return new NFA(s, e);
    }

    /**
     * Cerradura de Kleene: a*
     *
     *         /--------ε--------\
     *        v                  |
     * (new_s) --ε--> (a.start) →...→ (a.end) --ε--> (new_e)
     *  \                                              ^
     *   \--------------------ε-----------------------/
     */
    static NFA kleeneStar(NFA a) {
        NFAState s = new NFAState();
        NFAState e = new NFAState();
        s.addEpsilon(a.start);
        s.addEpsilon(e);        // acepta ε
        a.end.addEpsilon(a.start); // repetición
        a.end.addEpsilon(e);    // salida
        return new NFA(s, e);
    }

    /**
     * Cerradura positiva: a+  ≡  a.a*
     */
    static NFA kleenePlus(NFA a) {
        // a.a* — el colaborador puede optimizarlo si quiere
        // Creamos una copia del NFA 'a' para el segundo fragmento
        // En Thompson es válido compartir estados así:
        NFAState s = new NFAState();
        NFAState e = new NFAState();
        s.addEpsilon(a.start);
        a.end.addEpsilon(a.start); // repetición
        a.end.addEpsilon(e);
        return new NFA(s, e);
    }

    /**
     * Opcional: a?  ≡  a|ε
     */
    static NFA optional(NFA a) {
        NFAState s = new NFAState();
        NFAState e = new NFAState();
        s.addEpsilon(a.start);
        s.addEpsilon(e);      // ruta ε (omitir a)
        a.end.addEpsilon(e);
        return new NFA(s, e);
    }

    /**
     * Diferencia de conjuntos: a#b
     *
     * En teoría formal: L(a) - L(b).
     * En YALex, # opera sobre character-sets, no sobre lenguajes generales.
     *
     * Implementación simplificada:
     *   Se codifica como símbolo especial "DIFF" que el colaborador
     *   resolverá en la fase DFA donde puede calcular la diferencia
     *   de los conjuntos de caracteres concretos.
     *
     * Por ahora creamos un NFA marcado para diferencia:
     */
    static NFA difference(NFA a, NFA b) {
        // Estrategia: envolver en un estado con símbolo especial
        // El colaborador interpretará "DIFF" en Subset Construction
        NFAState s = new NFAState();
        NFAState e = new NFAState();

        // Marcamos con transición especial para procesamiento posterior
        s.addTransition("DIFF_START", a.start);
        b.end.addEpsilon(e);  // conectar lado B para referencia
        a.end.addEpsilon(e);

        // Nota para el colaborador:
        // En la práctica, # se aplica a clases de caracteres concretas.
        // Si los operandos son CLASS:... y CLASS:..., se puede calcular
        // la diferencia directamente en el tokenizer o en el DFA.
        // Ver: resolveClassDifference() en DFABuilder (por implementar).
        return new NFA(s, e);
    }

    // ── NFA combinado para múltiples reglas ───────────────────────────────

    /**
     * Combina múltiples NFAs (uno por regla YALex) en un único NFA global
     * con un nuevo estado inicial que tiene ε-transiciones a cada sub-NFA.
     *
     * El estado final de cada sub-NFA se marca como aceptante con el
     * índice de su regla y su acción (para prioridad por orden de definición).
     *
     * @param nfas    lista de NFAs, uno por regla, en orden de definición
     * @param actions lista de acciones correspondientes (puede contener nulls)
     * @return NFA combinado listo para Subset Construction
     */
    public static NFA combine(List<NFA> nfas, List<String> actions) {
        NFAState globalStart = new NFAState();

        for (int i = 0; i < nfas.size(); i++) {
            NFA n = nfas.get(i);
            String action = (actions != null && i < actions.size()) ? actions.get(i) : null;

            // Marcar estado final como aceptante con índice de regla
            n.end.setAccepting(i, action);

            // ε-transición desde el estado inicial global
            globalStart.addEpsilon(n.start);
        }

        // El NFA combinado no tiene un único end definido (tiene varios)
        // Usamos null como end — el colaborador trabaja con estados aceptantes
        return new NFA(globalStart, null);
    }

    // ── Helpers de parseo de caracteres ───────────────────────────────────

    /**
     * Parsea un char literal YALex: 'c' → 'c', '\n' → '\n', etc.
     */
    static char parseCharLiteral(String tok) {
        // Formato esperado: 'c' o '\x'
        // Puede venir sin comillas si es un solo char de la tabla interna
        if (tok.length() == 1) return tok.charAt(0);
        String inner = tok.startsWith("'") ? tok.substring(1, tok.length() - 1) : tok;
        if (inner.startsWith("\\")) {
            return parseEscapeChar(inner.charAt(1));
        }
        return inner.charAt(0);
    }

    /** Convierte carácter de escape al char real. */
    static char parseEscapeChar(char c) {
        return switch (c) {
            case 'n'  -> '\n';
            case 't'  -> '\t';
            case 'r'  -> '\r';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"'  -> '"';
            case '0'  -> '\0';
            default   -> c;
        };
    }

    /**
     * Parsea el siguiente carácter de una cadena, manejando escapes.
     * @return char[] de 2 elementos: [carácter, nuevaPosición]
     *         (se usa char[] para devolver dos valores sin boxing)
     */
    static char[] parseEscape(String s, int pos) {
        if (pos >= s.length()) return new char[]{0, (char) pos};
        char c = s.charAt(pos);
        if (c == '\\' && pos + 1 < s.length()) {
            return new char[]{parseEscapeChar(s.charAt(pos + 1)), (char)(pos + 2)};
        }
        return new char[]{c, (char)(pos + 1)};
    }
}
