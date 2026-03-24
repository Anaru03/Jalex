package com.jalex.gen;

import com.jalex.dfa.DFA;
import com.jalex.dfa.DFAState;

public class DFASimulator {

    public void simulate(DFA dfa, String input) {

        int i = 0;

        while (i < input.length()) {

            DFAState current = dfa.startState;
            DFAState lastAccepting = null;
            int lastIndex = i;

            int j = i;

            while (j < input.length()) {

                char c = input.charAt(j);
                String symbol = mapSymbol(c);

                DFAState next = current.getTransition(symbol);

                if (next == null) break;

                current = next;

                if (current.isAccepting) {
                    lastAccepting = current;
                    lastIndex = j + 1;
                }

                j++;
            }

            if (lastAccepting != null) {
                String lexeme = input.substring(i, lastIndex);

                System.out.println("TOKEN → " + lexeme + " | acción: " + lastAccepting.action);

                i = lastIndex;
            } else {
                System.out.println("ERROR léxico en: " + input.charAt(i));
                i++;
            }
        }
    }

    /**
     * Mapea caracteres de entrada a los símbolos del DFA
     */
    private String mapSymbol(char c) {

        // dígitos
        if (Character.isDigit(c)) {
            return "CLASS:0,1,2,3,4,5,6,7,8,9,";
        }

        // espacios y tabs
        if (c == ' ' || c == '\t') {
            return "CLASS:   , ,";
        }

        // salto de línea
        if (c == '\n') {
            return "CLASS:\n,";
        }

        // símbolos directos (+ - * / ( ) etc.)
        return String.valueOf(c);
    }
}