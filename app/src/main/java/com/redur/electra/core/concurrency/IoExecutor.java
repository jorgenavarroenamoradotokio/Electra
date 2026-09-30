package com.redur.electra.core.concurrency;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

/** Executor para I/O local (disco) fuera del hilo principal. */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface IoExecutor {
}
