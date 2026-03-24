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

                String symbol = String.valueOf(input.charAt(j));
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

                System.out.println("TOKEN: " + lexeme +
                        " → " + lastAccepting.action);

                i = lastIndex;

            } else {

                System.out.println("ERROR léxico: " + input.charAt(i));
                i++;
            }
        }
    }
}