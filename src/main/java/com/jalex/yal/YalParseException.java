package com.jalex.yal;

/**
 * Excepción lanzada cuando el archivo .yal tiene errores sintácticos.
 */
public class YalParseException extends Exception {
    public YalParseException(String message) {
        super(message);
    }
}
