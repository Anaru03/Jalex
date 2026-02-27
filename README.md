# Jalex - Generador de Analizadores Léxicos en Java

Proyecto desarrollado para el curso ** Diseño de Lenguajes de
Programación**\ en la Universidad del Valle de Guatemala.

Jalex es un generador de analizadores léxicos inspirado en YALex.\
A partir de una especificación en lenguaje YALex (.yal), el sistema
construye un analizador léxico funcional utilizando teoría de autómatas
finitos (NFA y DFA).

------------------------------------------------------------------------

## Integrantes

-   **Alejandro Antón** - [Anton17303](https://github.com/Anton17303)
-   **Ruth de Léon** - [Anaru03](https://github.com/Anaru03)

------------------------------------------------------------------------

## Objetivo del Proyecto

Implementar un generador de analizadores léxicos que:

1.  Lea una especificación en lenguaje YALex (.yal).
2.  Construya el autómata correspondiente (NFA → DFA).
3.  Genere un analizador léxico independiente.
4.  Detecte tokens válidos o errores léxicos.

------------------------------------------------------------------------

## Fundamento Teórico

El proyecto implementa:

-   Expresiones regulares
-   Construcción de Thompson (NFA)
-   Subset Construction (DFA)
-   Algoritmo de longest match
-   Prioridad por orden de definición
-   Manejo de ε-transiciones

⚠️ No se utilizan librerías de expresiones regulares.\
Toda la funcionalidad se desarrolla utilizando teoría formal de
autómatas.

------------------------------------------------------------------------

## Estructura del Proyecto

    Jalex/
    │
    ├── README.md
    ├── src/
    │   └── main/
    │       └── java/
    │           └── com/
    │               └── jalex/
    │                   ├── Main.java
    └── out/ (generado al compilar)
* Pendiente aún*

------------------------------------------------------------------------

## ⚙️ Requisitos

-   Java JDK 17 o superior (Recomendado: Java 20)
-   VS Code o cualquier IDE compatible con Java

Verificar instalación:

    java -version
    javac -version

------------------------------------------------------------------------

## ▶️ Cómo Compilar el Proyecto

Desde la raíz del proyecto:

    javac -d out src/main/java/com/jalex/Main.java

------------------------------------------------------------------------

## ▶️ Cómo Ejecutar el Proyecto

    java -cp out com.jalex.Main

Si todo está correcto debería imprimirse:

    Jalex iniciado correctamente.

------------------------------------------------------------------------

## Flujo General del Generador

1.  Lectura del archivo `.yal`.
2.  Análisis y expansión de expresiones regulares.
3.  Construcción de NFA (Algoritmo de Thompson).
4.  Conversión a DFA (Subset Construction).
5.  Aplicación de reglas:
    -   Longest match
    -   Prioridad por orden de definición
6.  Generación de archivo `GeneratedLexer.java`.
7.  Ejecución independiente del analizador generado.

------------------------------------------------------------------------

## 📌 Restricciones del Proyecto

-   No se utilizan librerías de expresiones regulares.
-   Todo se implementa con autómatas finitos.
-   El analizador generado es independiente del generador.
-   Se incluyen casos de prueba de baja, media y alta complejidad.
-   Se implementará una interfaz gráfica amigable.

------------------------------------------------------------------------

## Casos de Prueba

El proyecto incluirá:

-   Caso de complejidad baja
-   Caso de complejidad media
-   Caso de complejidad alta

Cada caso incluirá:

-   Archivo `.yal`
-   Archivo de entrada
-   Salida esperada

------------------------------------------------------------------------

## Tecnologías Utilizadas

-   Java 20
-   Programación Orientada a Objetos
-   Estructuras de Datos (Map, Set, Stack)
-   Teoría de Autómatas Finitos

------------------------------------------------------------------------

## 📄 Licencia

Proyecto académico. Uso exclusivo para fines educativos.
