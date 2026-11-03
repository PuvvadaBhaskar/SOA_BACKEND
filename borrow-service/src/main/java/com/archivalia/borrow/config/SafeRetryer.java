package com.archivalia.borrow.config;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;

/**
 * Feign retry with exponential back-off that is safe for non-idempotent calls:
 * GET/HEAD/OPTIONS are retried on any I/O error; PUT/POST are retried only when the connection could
 * not be established (the request never reached the server), so a stock decrement is never applied twice.
 * Each retry goes through the load balancer again and may hit another instance.
 */
public class SafeRetryer implements Retryer {

    private final long period;
    private final long maxPeriod;
    private final int maxAttempts;
    private final Retryer.Default delegate;

    public SafeRetryer(long period, long maxPeriod, int maxAttempts) {
        this.period = period;
        this.maxPeriod = maxPeriod;
        this.maxAttempts = maxAttempts;
        this.delegate = new Retryer.Default(period, maxPeriod, maxAttempts);
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        if (!isSafeToRetry(e)) {
            throw e;
        }
        delegate.continueOrPropagate(e);
    }

    static boolean isSafeToRetry(RetryableException e) {
        Request.HttpMethod method = e.method();
        if (method == Request.HttpMethod.GET || method == Request.HttpMethod.HEAD
                || method == Request.HttpMethod.OPTIONS) {
            return true;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Retryer clone() {
        return new SafeRetryer(period, maxPeriod, maxAttempts);
    }
}
