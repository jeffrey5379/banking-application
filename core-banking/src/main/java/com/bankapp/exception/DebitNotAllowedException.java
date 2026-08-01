package com.bankapp.exception;

public class DebitNotAllowedException extends RuntimeException {
    public DebitNotAllowedException(String message) {
        super(message);
    }
}
