package com.jalex.regex;

import java.util.*;

/*
 * RegexParser:
 * Encargado del preprocesamiento de expresiones regulares:
 * 1. Inserta operadores de concatenación explícitos.
 * 2. Convierte expresiones en notación infija a postfija
 *    usando el algoritmo Shunting Yard de Dijkstra.
 *
 * Referencias:
 * - E. W. Dijkstra (1961)
 * - https://algs4.cs.princeton.edu/13stacks/
 */
public class RegexParser {

    private String regex;

    public RegexParser(String regex) {
        this.regex = regex;
    }

    public String insertConcatenationOperators() {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < regex.length(); i++) {
            char current = regex.charAt(i);
            result.append(current);

            if (i + 1 < regex.length()) {
                char next = regex.charAt(i + 1);

                if (needsConcatenation(current, next)) {
                    result.append('.');
                }
            }
        }

        return result.toString();
    }

    private boolean needsConcatenation(char current, char next) {
        if (current == '(' || current == '|') return false;
        if (next == ')' || next == '|' || next == '*' || next == '+' || next == '?') return false;
        return true;
    }
}