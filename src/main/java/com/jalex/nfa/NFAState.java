package com.jalex.nfa;

import java.util.*;

/**
 * NFAState:
 * Representa un estado dentro de un Autómata Finito No Determinista (NFA).
 *
 * Cada estado mantiene:
 *  - Un ID único global (para debug e impresión).
 *  - Una tabla de transiciones: símbolo → conjunto de estados destino.
 *    El símbolo null representa la transición épsilon (ε).
 *  - Si es estado de aceptación, guarda el índice de la regla que acepta
 *    y la acción asociada (puede ser null).
 */
public class NFAState {

    // ── Contador global de IDs ─────────────────────────────────────────────
    private static int counter = 0;

    /** Reinicia el contador (útil entre tests). */
    public static void resetCounter() { counter = 0; }

    // ── Campos ─────────────────────────────────────────────────────────────

    public final int id;

    /**
     * Tabla de transiciones.
     * Clave null → transiciones ε.
     * Clave "WILDCARD" → cualquier símbolo (comodín _).
     * Clave "[...]" → clase de caracteres (se interpreta después).
     * Cualquier otra clave de longitud 1 → carácter literal.
     */
    private final Map<String, Set<NFAState>> transitions = new LinkedHashMap<>();

    /** ¿Es estado de aceptación? */
    public boolean isAccepting = false;

    /**
     * Índice de la regla YALex que acepta (0-based, orden de definición).
     * -1 si no es estado de aceptación.
     * Se usa para desempate: menor índice = mayor prioridad.
     */
    public int ruleIndex = -1;

    /**
     * Acción asociada a la regla (código del bloque {action}).
     * Puede ser null.
     */
    public String action = null;

    // ── Constructor ────────────────────────────────────────────────────────

    public NFAState() {
        this.id = counter++;
    }

    // ── Transiciones ───────────────────────────────────────────────────────

    /** Agrega una transición con símbolo `symbol` hacia `target`. */
    public void addTransition(String symbol, NFAState target) {
        transitions.computeIfAbsent(symbol, k -> new LinkedHashSet<>()).add(target);
    }

    /** Agrega una transición ε hacia `target`. */
    public void addEpsilon(NFAState target) {
        addTransition(null, target);
    }

    /**
     * Devuelve todos los estados alcanzables desde este estado con `symbol`.
     * Si no hay transición, devuelve conjunto vacío.
     */
    public Set<NFAState> getTransitions(String symbol) {
        return transitions.getOrDefault(symbol, Collections.emptySet());
    }

    /** Devuelve todas las transiciones ε desde este estado. */
    public Set<NFAState> getEpsilonTransitions() {
        return transitions.getOrDefault(null, Collections.emptySet());
    }

    /** Devuelve todos los símbolos con transición definida (incluyendo null = ε). */
    public Set<String> getSymbols() {
        return Collections.unmodifiableSet(transitions.keySet());
    }

    /** Devuelve una copia del mapa completo de transiciones (para debug). */
    public Map<String, Set<NFAState>> getAllTransitions() {
        return Collections.unmodifiableMap(transitions);
    }

    // ── Estado de aceptación ───────────────────────────────────────────────

    /**
     * Marca este estado como de aceptación con la regla `ruleIndex` y su `action`.
     */
    public void setAccepting(int ruleIndex, String action) {
        this.isAccepting = true;
        this.ruleIndex = ruleIndex;
        this.action = action;
    }

    // ── toString ───────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "q" + id + (isAccepting ? "[R" + ruleIndex + "]" : "");
    }
}
