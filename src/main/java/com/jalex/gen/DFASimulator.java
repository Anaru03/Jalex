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

                DFAState next = null;

                for (var entry : current.getAllTransitions().entrySet()) {
                    String sym = entry.getKey();

                    if (matches(sym, c)) {
                        next = entry.getValue();
                        break;
                    }
                }
                
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
    private boolean matches(String symbol, char c) {

    // Clase de caracteres
    if (symbol.startsWith("CLASS:")) {
        String chars = symbol.substring(6);

        String[] parts = chars.split(",");

        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (p.charAt(0) == c) return true;
        }

        return false;
    }

    // EOF (no aplica en runtime normal)
    if (symbol.equals("EOF")) return false;

    // símbolo literal (+ - * etc.)
    return symbol.equals(String.valueOf(c));
    }
}