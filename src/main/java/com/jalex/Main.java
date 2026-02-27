package com.jalex;

import com.jalex.regex.RegexParser;

public class Main {
    public static void main(String[] args) {

        RegexParser parser = new RegexParser("(a|b)*abb");
        String result = parser.insertConcatenationOperators();

        System.out.println(result);
    }
}