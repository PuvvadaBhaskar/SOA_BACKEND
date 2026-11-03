package com.archivalia.borrow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

import feign.Request;
import feign.RetryableException;

class SafeRetryerTest {

    @Test
    void getIsAlwaysRetryable() {
        assertThat(SafeRetryer.isSafeToRetry(retryable(Request.HttpMethod.GET, new SocketTimeoutException()))).isTrue();
    }

    @Test
    void putIsRetriedOnlyWhenConnectionWasRefused() {
        assertThat(SafeRetryer.isSafeToRetry(retryable(Request.HttpMethod.PUT, new ConnectException("refused"))))
                .isTrue();
        // read timeout: the PUT may already have changed stock -> do not repeat it
        assertThat(SafeRetryer.isSafeToRetry(retryable(Request.HttpMethod.PUT, new SocketTimeoutException("read"))))
                .isFalse();
    }

    @Test
    void cloneCreatesFreshRetryer() {
        SafeRetryer retryer = new SafeRetryer(10, 100, 3);
        assertThat(retryer.clone()).isNotSameAs(retryer).isInstanceOf(SafeRetryer.class);
    }

    private static RetryableException retryable(Request.HttpMethod method, Throwable cause) {
        RetryableException exception = mock(RetryableException.class);
        when(exception.method()).thenReturn(method);
        when(exception.getCause()).thenReturn(cause);
        return exception;
    }
}
