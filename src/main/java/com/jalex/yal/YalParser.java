package com.jalex.yal;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/*
 * YalParser:
 * Responsable de leer y estructurar un archivo .yal con el formato:
 *
 *   { header }
 *   let ident = regexp
 *   rule entrypoint =
 *       regexp { action }
 *     | regexp { action }
 *   { trailer }
 *
 * Produce un objeto YalFile con todas las secciones parseadas.
 */
public class YalParser {

    // ── Resultado del parseo ───────────────────────────────────────────────

    /** Contenido completo de la sección {header} (puede ser null) */
    public String header;

    /** Contenido completo de la sección {trailer} (puede ser null) */
    public String trailer;

    /** Nombre del entrypoint (ej: "gettoken") */
    public String entrypoint;

    /** Argumentos del entrypoint (puede estar vacío) */
    public List<String> entrypointArgs = new ArrayList<>();

    /**
     * Definiciones `let ident = regexp` en orden de aparición.
     * Clave = nombre del ident, Valor = regexp cruda.
     */
    public LinkedHashMap<String, String> lets = new LinkedHashMap<>();

    /**
     * Reglas del entrypoint en orden de aparición.
     * Cada elemento es un par (regexp, action).
     * action puede ser null si no se especificó.
     */
    public List<Rule> rules = new ArrayList<>();

    // ── Clase interna ──────────────────────────────────────────────────────

    public static class Rule {
        public final String regexp;
        public final String action; // puede ser null

        public Rule(String regexp, String action) {
            this.regexp = regexp;
            this.action = action;
        }

        @Override
        public String toString() {
            return "Rule{ regexp='" + regexp + "', action=" +
                    (action == null ? "null" : "'" + action + "'") + " }";
        }
    }

    // ── Punto de entrada ───────────────────────────────────────────────────

    /**
     * Parsea el archivo .yal en la ruta indicada.
     *
     * @param path ruta al archivo .yal
     * @return instancia de YalParser con todos los campos llenos
     * @throws IOException si el archivo no existe o no se puede leer
     * @throws YalParseException si la sintaxis es inválida
     */
    public static YalParser parse(String path) throws IOException, YalParseException {
        String raw = Files.readString(Path.of(path));
        return new YalParser().doParse(raw);
    }

    /**
     * Parsea directamente desde un String (útil para tests).
     */
    public static YalParser parseString(String source) throws YalParseException {
        return new YalParser().doParse(source);
    }

    // ── Lógica interna ─────────────────────────────────────────────────────

    private YalParser() {}

    private YalParser doParse(String source) throws YalParseException {
        // 1. Eliminar comentarios (* ... *)
        String cleaned = removeComments(source);

        // 2. Separar en tokens de alto nivel: { ... }, let, rule
        parseStructure(cleaned);

        return this;
    }

    // ── Paso 1: eliminar comentarios ───────────────────────────────────────

    private String removeComments(String src) throws YalParseException {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            if (i + 1 < src.length() && src.charAt(i) == '(' && src.charAt(i + 1) == '*') {
                // buscar cierre *)
                int close = src.indexOf("*)", i + 2);
                if (close == -1) throw new YalParseException("Comentario sin cerrar: (* sin *)");
                // reemplazar por espacios para preservar líneas (ayuda a mensajes de error)
                String block = src.substring(i, close + 2);
                long lines = block.chars().filter(c -> c == '\n').count();
                sb.append("\n".repeat((int) lines));
                i = close + 2;
            } else {
                sb.append(src.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    // ── Paso 2: estructura principal ───────────────────────────────────────

    private void parseStructure(String src) throws YalParseException {
        src = src.trim();
        int pos = 0;

        // ¿Header?
        if (pos < src.length() && src.charAt(pos) == '{') {
            int[] res = readBraceBlock(src, pos);
            header = src.substring(pos + 1, res[0]).trim(); // contenido sin llaves
            pos = res[1];
            src = src.substring(pos).trim();
            pos = 0;
        }

        // ¿Trailer al final? (antes de buscar lets y rule)
        // El trailer está DESPUÉS del bloque rule, así que lo separamos al final.
        // Por ahora seguimos linealmente.

        // lets: "let ident = regexp"
        // rule: "rule ident [args] = ..."
        // Buscamos palabras clave en orden
        while (pos < src.length()) {
            // saltar blancos
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
            if (pos >= src.length()) break;

            // ¿Comienza bloque { ... }? → podría ser trailer
            if (src.charAt(pos) == '{') {
                int[] res = readBraceBlock(src, pos);
                trailer = src.substring(pos + 1, res[0]).trim();
                pos = res[1];
                continue;
            }

            // Leer siguiente palabra clave
            if (src.startsWith("let", pos)) {
                pos = parseLet(src, pos);
            } else if (src.startsWith("rule", pos)) {
                pos = parseRule(src, pos);
            } else {
                // carácter inesperado
                throw new YalParseException("Token inesperado en posición " + pos +
                        ": '" + src.charAt(pos) + "'");
            }
        }

        if (entrypoint == null) {
            throw new YalParseException("No se encontró ninguna sección 'rule' en el archivo .yal");
        }
    }

    // ── Parsear "let ident = regexp" ───────────────────────────────────────

    private int parseLet(String src, int pos) throws YalParseException {
        pos += 3; // saltar "let"
        pos = skipWhitespace(src, pos);

        // leer ident
        int identStart = pos;
        while (pos < src.length() && isIdentChar(src.charAt(pos))) pos++;
        if (pos == identStart) throw new YalParseException("Se esperaba identificador después de 'let'");
        String ident = src.substring(identStart, pos);

        pos = skipWhitespace(src, pos);

        // '='
        if (pos >= src.length() || src.charAt(pos) != '=')
            throw new YalParseException("Se esperaba '=' después de 'let " + ident + "'");
        pos++;
        pos = skipWhitespace(src, pos);

        // regexp: todo hasta el próximo let/rule/{  (respetando paréntesis y corchetes)
        int regexpStart = pos;
        pos = readUntilNextTopLevelKeyword(src, pos);
        String regexp = src.substring(regexpStart, pos).trim();

        if (regexp.isEmpty()) throw new YalParseException("Regexp vacía para 'let " + ident + "'");

        lets.put(ident, regexp);
        return pos;
    }

    // ── Parsear "rule entrypoint [args] = regexp {action} | ..." ──────────

    private int parseRule(String src, int pos) throws YalParseException {
        pos += 4; // saltar "rule"
        pos = skipWhitespace(src, pos);

        // nombre del entrypoint
        int nameStart = pos;
        while (pos < src.length() && isIdentChar(src.charAt(pos))) pos++;
        if (pos == nameStart) throw new YalParseException("Se esperaba nombre de entrypoint después de 'rule'");
        entrypoint = src.substring(nameStart, pos);

        pos = skipWhitespace(src, pos);

        // argumentos opcionales (cualquier cosa antes del '=')
        while (pos < src.length() && src.charAt(pos) != '=') {
            int argStart = pos;
            while (pos < src.length() && isIdentChar(src.charAt(pos))) pos++;
            if (pos > argStart) entrypointArgs.add(src.substring(argStart, pos));
            else pos++; // saltar carácter no-ident
        }

        // '='
        if (pos >= src.length() || src.charAt(pos) != '=')
            throw new YalParseException("Se esperaba '=' después de 'rule " + entrypoint + "'");
        pos++;
        pos = skipWhitespace(src, pos);

        // Leer alternativas separadas por '|'
        // Cada alternativa: regexp { action }  ó  regexp
        // El primer regexp puede NO tener '|' antes.
        // Terminamos cuando encontramos:  { trailer }  ó  fin de cadena

        while (pos < src.length()) {
            pos = skipWhitespace(src, pos);
            if (pos >= src.length()) break;

            // ¿Fin de rule? → { trailer } o nueva sección (no esperada en YALex simple)
            if (src.charAt(pos) == '{') break;

            // ¿Separador de alternativa?
            if (src.charAt(pos) == '|') {
                pos++;
                pos = skipWhitespace(src, pos);
            }

            if (pos >= src.length()) break;
            if (src.charAt(pos) == '{') break; // trailer

            // Leer regexp hasta '{' o '|' o fin, respetando anidamiento
            int[] regexpEnd = readRegexpInRule(src, pos);
            String regexp = src.substring(pos, regexpEnd[0]).trim();
            pos = regexpEnd[0];

            // ¿Tiene action?
            String action = null;
            pos = skipWhitespace(src, pos);
            if (pos < src.length() && src.charAt(pos) == '{') {
                int[] actionRes = readBraceBlock(src, pos);
                action = src.substring(pos + 1, actionRes[0]).trim();
                pos = actionRes[1];
            }

            if (!regexp.isEmpty()) {
                rules.add(new Rule(regexp, action));
            }
        }

        return pos;
    }

    // ── Helpers de lectura ─────────────────────────────────────────────────

    /**
     * Lee un bloque { ... } respetando llaves anidadas.
     * @return [indexDelCierre, posiciónDespuésDeCierre]
     */
    private int[] readBraceBlock(String src, int start) throws YalParseException {
        if (src.charAt(start) != '{')
            throw new YalParseException("Se esperaba '{' en posición " + start);
        int depth = 0;
        int i = start;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return new int[]{i, i + 1};
            }
            i++;
        }
        throw new YalParseException("Bloque { sin cerrar desde posición " + start);
    }

    /**
     * Lee una regexp dentro de una sección rule, deteniéndose ante
     * '{' (inicio de action), '|' al nivel top, o fin de cadena.
     * Respeta paréntesis, corchetes y strings entre comillas simples/dobles.
     * @return [posiciónFinalRegexp, _]
     */
    private int[] readRegexpInRule(String src, int start) throws YalParseException {
        int i = start;
        int parenDepth = 0;
        int bracketDepth = 0;

        while (i < src.length()) {
            char c = src.charAt(i);

            // Strings entre comillas simples: 'x' o '\n'
            if (c == '\'' ) {
                i++;
                if (i < src.length() && src.charAt(i) == '\\') i += 2; // escape
                else i++;
                if (i < src.length() && src.charAt(i) == '\'') i++;
                continue;
            }

            // Strings entre comillas dobles: "abc"
            if (c == '"') {
                i++;
                while (i < src.length() && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\\') i++;
                    i++;
                }
                if (i < src.length()) i++; // cerrar "
                continue;
            }

            if (c == '(') { parenDepth++; i++; continue; }
            if (c == ')') { parenDepth--; i++; continue; }
            if (c == '[') { bracketDepth++; i++; continue; }
            if (c == ']') { bracketDepth--; i++; continue; }

            // Parar ante '{' o '|' sólo en nivel top
            if (parenDepth == 0 && bracketDepth == 0) {
                if (c == '{' || c == '|') break;
                // Parar ante palabras clave (let/rule) — no esperado aquí, pero seguro
            }

            i++;
        }
        return new int[]{i, i};
    }

    /**
     * Lee hasta el próximo `let`, `rule`, `{` o fin de cadena (nivel top).
     * Usado para delimitar el valor del regexp en `let ident = regexp`.
     */
    private int readUntilNextTopLevelKeyword(String src, int start) {
        int i = start;
        int parenDepth = 0;
        int bracketDepth = 0;

        while (i < src.length()) {
            char c = src.charAt(i);

            if (c == '\'') {
                i++;
                if (i < src.length() && src.charAt(i) == '\\') i += 2;
                else i++;
                if (i < src.length() && src.charAt(i) == '\'') i++;
                continue;
            }
            if (c == '"') {
                i++;
                while (i < src.length() && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\\') i++;
                    i++;
                }
                if (i < src.length()) i++;
                continue;
            }

            if (c == '(') { parenDepth++; i++; continue; }
            if (c == ')') { parenDepth--; i++; continue; }
            if (c == '[') { bracketDepth++; i++; continue; }
            if (c == ']') { bracketDepth--; i++; continue; }

            if (parenDepth == 0 && bracketDepth == 0) {
                if (c == '{') break;
                // detectar "let " o "rule "
                if ((src.startsWith("let ", i) || src.startsWith("let\t", i)) && i > start) break;
                if ((src.startsWith("rule ", i) || src.startsWith("rule\t", i)) && i > start) break;
            }
            i++;
        }
        return i;
    }

    private int skipWhitespace(String src, int pos) {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        return pos;
    }

    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // ── Expansión de `let` aliases ─────────────────────────────────────────

    /**
     * Expande todas las referencias a `let` dentro de una regexp.
     * Reemplaza cada `ident` definido por su regexp entre paréntesis.
     * Itera hasta que no haya más referencias (permite lets encadenados).
     *
     * @param regexp regexp cruda que puede contener idents
     * @return regexp con todos los idents expandidos
     */
    public String expandLets(String regexp) {
        // Iteramos en orden inverso de definición para que los últimos
        // definidos no oscurezcan a los primeros si hay prefijos comunes.
        // En realidad iteramos en orden de definición pero envolvemos con ()
        // para evitar problemas de precedencia.
        String result = regexp;
        // Repetir hasta punto fijo (máx. lets.size() iteraciones)
        for (int pass = 0; pass < lets.size() + 1; pass++) {
            String prev = result;
            for (Map.Entry<String, String> entry : lets.entrySet()) {
                String name = entry.getKey();
                String value = "(" + entry.getValue() + ")";
                // Reemplazar ident como palabra completa (no dentro de otras)
                result = result.replaceAll("(?<![a-zA-Z0-9_])" +
                        Pattern.quote(name) +
                        "(?![a-zA-Z0-9_])", value);
            }
            if (result.equals(prev)) break; // punto fijo alcanzado
        }
        return result;
    }

    /**
     * Devuelve todas las reglas con sus regexps expandidas.
     */
    public List<Rule> getExpandedRules() {
        List<Rule> expanded = new ArrayList<>();
        for (Rule r : rules) {
            expanded.add(new Rule(expandLets(r.regexp), r.action));
        }
        return expanded;
    }

    // ── toString para debug ────────────────────────────────────────────────

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== YalParser ===\n");
        sb.append("Header   : ").append(header != null ? header.replace("\n", "\\n") : "null").append("\n");
        sb.append("Trailer  : ").append(trailer != null ? trailer.replace("\n", "\\n") : "null").append("\n");
        sb.append("Entry    : ").append(entrypoint).append(" ").append(entrypointArgs).append("\n");
        sb.append("Lets     :\n");
        lets.forEach((k, v) -> sb.append("  ").append(k).append(" = ").append(v).append("\n"));
        sb.append("Rules    :\n");
        for (int i = 0; i < rules.size(); i++) {
            sb.append("  [").append(i).append("] ").append(rules.get(i)).append("\n");
        }
        return sb.toString();
    }
}
