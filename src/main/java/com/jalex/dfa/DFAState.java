package com.jalex.dfa;

import com.jalex.nfa.NFAState;
import java.util.*;

/**
 * DFAState: un estado del DFA.
 * Corresponde a un subconjunto de estados NFA (Subset Construction).
 * Ya implementado — no modificar.
 */
public class DFAState {

    private static int counter = 0;
    public static void resetCounter() { counter = 0; }

    public final int id;
    public final Set<NFAState> nfaStates;
    private final Map<String, DFAState> transitions = new LinkedHashMap<>();

    public boolean isAccepting = false;
    public int ruleIndex = -1;
    public String action = null;

    public DFAState(Set<NFAState> nfaStates) {
        this.id = counter++;
        this.nfaStates = Collections.unmodifiableSet(new LinkedHashSet<>(nfaStates));
        resolveAccepting();
    }

    /** Si algún NFA del subconjunto acepta, este DFA acepta con menor ruleIndex. */
    private void resolveAccepting() {
        for (NFAState s : nfaStates) {
            if (s.isAccepting) {
                if (!isAccepting || s.ruleIndex < ruleIndex) {
                    isAccepting = true;
                    ruleIndex   = s.ruleIndex;
                    action      = s.action;
                }
            }
        }
    }

    public void addTransition(String symbol, DFAState target) {
        transitions.put(symbol, target);
    }

    public DFAState getTransition(String symbol) {
        return transitions.get(symbol);
    }

    public Map<String, DFAState> getAllTransitions() {
        return Collections.unmodifiableMap(transitions);
    }

    @Override
    public String toString() {
        return "D" + id + (isAccepting ? "[R" + ruleIndex + "]" : "");
    }
}
