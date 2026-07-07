package com.modlint.web;

/** A rejected request: the HTTP status to answer with and a message safe to show the client. */
public final class WebInputException extends RuntimeException {

    private final int status;

    public WebInputException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
