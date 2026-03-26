package com.jalex.gen;

import com.jalex.dfa.DFA;
import com.jalex.dfa.DFAState;

public class SimpleLexer {

    public void tokenize(DFA dfa, String input) {

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

                System.out.println("TOKEN: " + lexeme +
                        " → " + lastAccepting.action);

                i = lastIndex;

            } else {

                System.out.println("ERROR léxico: " + input.charAt(i));
                i++;
            }
        }
    }

    // 👇 ESTE VA FUERA del método tokenize
    private boolean matches(String symbol, char c) {

        if (symbol.startsWith("CLASS:")) {
            String chars = symbol.substring(6);
            String[] parts = chars.split(",");

            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (p.charAt(0) == c) return true;
            }
            return false;
        }

        if (symbol.equals("EOF")) return false;

        return symbol.equals(String.valueOf(c));
    }
}