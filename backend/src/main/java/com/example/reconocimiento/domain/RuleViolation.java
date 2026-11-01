package com.example.reconocimiento.domain;

public class RuleViolation extends RuntimeException {
    public final String code;
    public final int status;
    public RuleViolation(String code, String message) { this(code, message, 422); }
    public RuleViolation(String code, String message, int status) { super(message); this.code = code; this.status = status; }
}
