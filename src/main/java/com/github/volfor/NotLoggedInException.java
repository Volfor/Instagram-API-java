package com.github.volfor;

public class NotLoggedInException extends RuntimeException {

    public NotLoggedInException() {
        super("Not logged in!");
    }

}
