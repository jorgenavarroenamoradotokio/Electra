package com.redur.electra.core.concurrency;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

/** Executor del hilo principal: para entregar a la UI resultados calculados en segundo plano. */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface MainExecutor {
}
