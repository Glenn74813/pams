package com.avtech.pams.common;

public final class Names {

    private Names() {
    }

    /** passwordConfirmation -> password_confirmation, to match the API's JSON field names. */
    public static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
