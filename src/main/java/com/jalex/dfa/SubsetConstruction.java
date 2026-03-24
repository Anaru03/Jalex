package com.jalex.dfa;

import com.jalex.nfa.NFAState;
import java.util.*;

/*
 * SubsetConstruction
 *
 * Convierte un NFA a DFA utilizando:
 * - epsilonClosure
 * - move
 * - Subset Construction
 *
 * Los símbolos se manejan como String para soportar
 * clases de caracteres y tokens complejos.
 */
public class SubsetConstruction {

    public Set<NFAState> epsilonClosure(Set<NFAState> states) {
        Stack<NFAState> stack = new Stack<>();
        Set<NFAState> closure = new HashSet<>(states);

        stack.addAll(states);

        while (!stack.isEmpty()) {
            NFAState state = stack.pop();

            for (NFAState next : state.getEpsilonTransitions()) {
                if (!closure.contains(next)) {
                    closure.add(next);
                    stack.push(next);
                }
            }
        }

        return closure;
    }

    public Set<NFAState> move(Set<NFAState> states, String symbol) {
        Set<NFAState> result = new HashSet<>();

        for (NFAState state : states) {
            Set<NFAState> nextStates = state.getTransitions(symbol);
            if (nextStates != null) {
                result.addAll(nextStates);
            }
        }

        return result;
    }

    /*
     * Construye el DFA a partir del NFA
     */
    public Set<DFAState> buildDFA(NFAState start) {

        Set<DFAState> dfaStates = new HashSet<>();
        Queue<DFAState> queue = new LinkedList<>();

        Set<NFAState> startClosure = epsilonClosure(Set.of(start));
        DFAState startDFA = new DFAState(startClosure);

        queue.add(startDFA);
        dfaStates.add(startDFA);

        while (!queue.isEmpty()) {
            DFAState current = queue.poll();

            Set<String> alphabet = getAlphabet(current.nfaStates);

            for (String symbol : alphabet) {

                Set<NFAState> moveResult = move(current.nfaStates, symbol);
                Set<NFAState> closure = epsilonClosure(moveResult);

                if (closure.isEmpty()) continue;

                DFAState newState = new DFAState(closure);

                if (!dfaStates.contains(newState)) {
                    dfaStates.add(newState);
                    queue.add(newState);
                }

                current.transitions.put(symbol, newState);
            }
        }

        return dfaStates;
    }

    private Set<String> getAlphabet(Set<NFAState> states) {
        Set<String> alphabet = new HashSet<>();

        for (NFAState state : states) {
            alphabet.addAll(state.getSymbols());
        }

        return alphabet;
    }
}