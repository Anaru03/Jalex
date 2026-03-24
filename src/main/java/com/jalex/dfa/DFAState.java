package com.jalex.dfa;

import com.jalex.nfa.NFAState;
import java.util.*;

public class DFAState {

    public Set<NFAState> nfaStates;
    public Map<String, DFAState> transitions;
    public boolean isAccepting;

    public DFAState(Set<NFAState> nfaStates) {
        this.nfaStates = nfaStates;
        this.transitions = new HashMap<>();
        this.isAccepting = false;
    }
}