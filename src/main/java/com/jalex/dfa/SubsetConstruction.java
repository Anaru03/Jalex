package com.jalex.dfa;

import com.jalex.nfa.NFA;
import com.jalex.nfa.NFAState;
import java.util.*;

public class SubsetConstruction {

    public static DFA build(NFA nfa) {
        DFAState.resetCounter();

        Set<NFAState> startClosure = NFA.epsilonClosure(nfa.start);
        DFAState startDFA = new DFAState(startClosure);

        Map<Set<NFAState>, DFAState> stateMap = new LinkedHashMap<>();
        stateMap.put(startClosure, startDFA);

        Queue<DFAState> worklist = new ArrayDeque<>();
        worklist.add(startDFA);

        while (!worklist.isEmpty()) {
            DFAState current = worklist.poll();

            for (String symbol : getAlphabetFor(current.nfaStates)) {

                Set<NFAState> moved = NFA.move(current.nfaStates, symbol);
                if (moved.isEmpty()) continue;

                DFAState next = stateMap.get(moved);

                if (next == null) {
                    next = new DFAState(moved);

                    for (NFAState s : moved) {
                        if (s.isAccepting) {
                            next.isAccepting = true;
                            break;
                        }
                    }

                    stateMap.put(moved, next);
                    worklist.add(next);
                }

                current.addTransition(symbol, next);
            }
        }

        return new DFA(startDFA, new ArrayList<>(stateMap.values()));
    }

    private static Set<String> getAlphabetFor(Set<NFAState> states) {
        Set<String> alphabet = new LinkedHashSet<>();
        for (NFAState s : states) {
            for (String sym : s.getSymbols()) {
                if (sym != null) alphabet.add(sym);
            }
        }
        return alphabet;
    }
}