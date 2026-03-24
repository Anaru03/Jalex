package com.jalex.dfa;

import java.util.*;

/**
 * DFA: autómata finito determinista.
 * Ya implementado — no modificar.
 */
public class DFA {

    public final DFAState startState;
    private final List<DFAState> states;

    public DFA(DFAState startState, List<DFAState> states) {
        this.startState = startState;
        this.states     = Collections.unmodifiableList(new ArrayList<>(states));
    }

    public List<DFAState> getStates()          { return states; }

    public List<DFAState> getAcceptingStates() {
        List<DFAState> acc = new ArrayList<>();
        for (DFAState s : states) if (s.isAccepting) acc.add(s);
        return acc;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DFA: start=").append(startState).append("\n");
        for (DFAState s : states) {
            sb.append("  ").append(s).append(" →");
            s.getAllTransitions().forEach((sym, tgt) ->
                sb.append(" ").append(sym).append("→").append(tgt));
            sb.append("\n");
        }
        return sb.toString();
    }
}
