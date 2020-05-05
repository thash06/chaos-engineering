package com.company.subdomain.resiliency.refapp.util;

import io.github.resilience4j.decorators.Decorators;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class DecoratorUtil<T, R> {

    public Decorators.DecorateFunction<T, R> decorateFunction(Function<T, R> function) {
        Decorators.DecorateFunction<T, R> decoratedFunction =
                Decorators.ofFunction(function);
        return decoratedFunction;
    }
}
