package com.junaid.loan.service;

public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(String id) {
        super("No case with id " + id + " (cases are kept in memory and cleared on restart)");
    }
}
