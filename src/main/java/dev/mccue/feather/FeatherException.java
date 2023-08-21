package dev.mccue.feather;

public final class FeatherException extends RuntimeException {
    FeatherException(String message) {
        super(message);
    }

    FeatherException(String message, Throwable cause) {
        super(message, cause);
    }
}
