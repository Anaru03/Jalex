package com.jalex.gen;

import com.jalex.dfa.DFA;
import java.io.*;

/**
 * LexerGenerator:
 * Genera GeneratedLexer.java a partir del DFA.
 *

 * ══════════════════════════════════════════════════
 *
 * El archivo generado debe tener esta estructura:
 *
 *   // ── header del .yal ──
 *   import myToken;  // (si había header)
 *
 *   public class GeneratedLexer {
 *
 *       private String input;
 *       private int pos = 0;
 *
 *       public GeneratedLexer(String input) {
 *           this.input = input;
 *       }
 *
 *       public Object nextToken() {
 *           // Longest-match con el DFA:
 *           // 1. Desde estado inicial, leer char por char
 *           // 2. Guardar último estado aceptante (posición + acción)
 *           // 3. Al atascarse, retroceder al último aceptante
 *           // 4. Ejecutar la acción de ese estado
 *           // 5. Si nunca hubo aceptante → error léxico
 *       }
 *
 *       // Tabla de transiciones generada desde el DFA
 *       private int transition(int state, char c) {
 *           switch (state) {
 *               case 0: switch(c) { case 'a': return 1; ... }
 *               ...
 *               default: return -1; // estado de error
 *           }
 *       }
 *   }
 *
 *   // ── trailer del .yal ──
 *
 * PARÁMETROS:
 *   dfa        → DFA con estados y transiciones
 *   outputPath → ruta del archivo a escribir
 *   header     → contenido del {header} del .yal (puede ser null)
 *   trailer    → contenido del {trailer} del .yal (puede ser null)
 *
 * PISTA: recorrer dfa.getStates() para generar el switch de transiciones.
 * Cada DFAState tiene: id, isAccepting, ruleIndex, action, getAllTransitions()
 */
public class LexerGenerator {

    public static void generate(DFA dfa, String outputPath,
                                 String header, String trailer) throws IOException {
        // TODO: implementar
        throw new UnsupportedOperationException("TODO: implementar LexerGenerator.generate()");
    }
}
