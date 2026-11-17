/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.http;

/** Why a request failed, worded for the script's author: it's the runtime error they see. */
public final class WebException extends RuntimeException {

    /**
     * A failure.
     *
     * @param message what went wrong
     */
    public WebException(String message) {
        super(message, null, false, false);
    }

    /**
     * A failure as the script's author should see it: a {@link WebException}'s message, unwrapped from the
     * {@link java.util.concurrent.CompletionException} a stage wraps it in.
     *
     * @param failure the failure
     * @return the failure to report
     */
    public static RuntimeException unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new WebException(String.valueOf(cause.getMessage() != null ? cause.getMessage() : cause));
    }
}
