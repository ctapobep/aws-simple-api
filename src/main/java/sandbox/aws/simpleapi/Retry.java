package sandbox.aws.simpleapi;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplateBuilder;

class Retry {
    static <T> void retry(int maxWait, Object obj, RetryCallback<T, RuntimeException> retryCallback) {
        new RetryTemplateBuilder()
                .exponentialBackoff(1000, 2, 20_000).withinMillis(maxWait).build()
                .execute(retryCallback, (c) -> {
                    throw new IllegalStateException("After " + c.getRetryCount() + " attempts, still didn't succeed: " + obj);
                });
    }
    static <T> void retry(Object obj, RetryCallback<T, RuntimeException> retryCallback) {
        retry(300_000, obj, retryCallback);
    }
}
