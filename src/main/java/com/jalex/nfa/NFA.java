package com.jalex.nfa;

import java.util.*;

/**
 * NFA:
 * Representa un fragmento de Autómata Finito No Determinista compuesto
 * por un estado inicial y un estado final (de aceptación provisional).
 *
 * En la construcción de Thompson, cada sub-expresión produce un NFA
 * con exactamente UN estado inicial y UN estado final.
 * Los estados finales sólo se marcan como "aceptantes" una vez que
 * el NFA completo de una regla está terminado.
 *
 * También expone utilidades para recorrido del grafo (alcanzabilidad,
 * cierre-ε) que el colaborador necesitará en Subset Construction.
 */
public class NFA {

    public final NFAState start;
    public final NFAState end;   // estado final del fragmento (todavía no aceptante)

    public NFA(NFAState start, NFAState end) {
        this.start = start;
        this.end   = end;
    }

    // ── Utilidades de recorrido ────────────────────────────────────────────

    /**
     * Calcula el cierre-ε (epsilon-closure) de un conjunto de estados.
     * Es decir, todos los estados alcanzables sólo usando transiciones ε.
     *
     * @param states conjunto inicial
     * @return conjunto cerrado bajo ε-transiciones
     */
    public static Set<NFAState> epsilonClosure(Set<NFAState> states) {
        Set<NFAState> closure = new LinkedHashSet<>(states);
        Deque<NFAState> stack = new ArrayDeque<>(states);

        while (!stack.isEmpty()) {
            NFAState s = stack.pop();
            for (NFAState t : s.getEpsilonTransitions()) {
                if (closure.add(t)) {
                    stack.push(t);
                }
            }
        }
        return closure;
    }

    /**
     * Calcula el cierre-ε de un único estado.
     */
    public static Set<NFAState> epsilonClosure(NFAState state) {
        return epsilonClosure(Set.of(state));
    }

    /**
     * Calcula el conjunto de estados alcanzables desde `states`
     * consumiendo el símbolo `symbol`, seguido de cierre-ε.
     *
     * Equivale a: ε-closure( ∪ δ(s, symbol) para s en states )
     *
     * @param states  conjunto de estados actuales
     * @param symbol  símbolo consumido
     * @return conjunto resultante
     */
    public static Set<NFAState> move(Set<NFAState> states, String symbol) {
        Set<NFAState> reached = new LinkedHashSet<>();
        for (NFAState s : states) {
            reached.addAll(s.getTransitions(symbol));
            // También considerar WILDCARD si el símbolo no es null
            if (symbol != null) {
                reached.addAll(s.getTransitions("WILDCARD"));
            }
        }
        return epsilonClosure(reached);
    }

    /**
     * Devuelve todos los estados del NFA completo alcanzables desde `start`
     * mediante BFS (ignorando símbolos, sólo conectividad).
     */
    public Set<NFAState> getAllStates() {
        Set<NFAState> visited = new LinkedHashSet<>();
        Deque<NFAState> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            NFAState s = queue.poll();
            for (Set<NFAState> targets : s.getAllTransitions().values()) {
                for (NFAState t : targets) {
                    if (visited.add(t)) queue.add(t);
                }
            }
        }
        return visited;
    }

    /**
     * Recoge todos los símbolos (no-ε) presentes en las transiciones
     * del NFA completo. Útil para el colaborador al construir el DFA.
     */
    public Set<String> getAlphabet() {
        Set<String> alphabet = new LinkedHashSet<>();
        for (NFAState s : getAllStates()) {
            for (String sym : s.getSymbols()) {
                if (sym != null) alphabet.add(sym); // excluir ε
            }
        }
        return alphabet;
    }

    // ── toString ───────────────────────────────────────────────────────────

    /**
     * Imprime el NFA en formato legible para debug.
     * Lista cada estado con sus transiciones.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("NFA: start=").append(start).append(", end=").append(end).append("\n");

        Set<NFAState> all = getAllStates();
        for (NFAState s : all) {
            sb.append("  ").append(s).append(" →");
            Map<String, Set<NFAState>> trans = s.getAllTransitions();
            if (trans.isEmpty()) {
                sb.append(" (sin transiciones)");
            } else {
                for (Map.Entry<String, Set<NFAState>> e : trans.entrySet()) {
                    String sym = e.getKey() == null ? "ε" : e.getKey();
                    sb.append(" ").append(sym).append("→{");
                    StringJoiner sj = new StringJoiner(",");
                    for (NFAState t : e.getValue()) sj.add(t.toString());
                    sb.append(sj).append("}");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
