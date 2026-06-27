package com.inkflow.crm.module.audit.support;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

@Component
public class AuditExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    public String evalString(String expression, JoinPoint joinPoint, Object result, BeanFactory beanFactory) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        Object value = eval(expression, joinPoint, result, beanFactory);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    public UUID evalUuid(String expression, JoinPoint joinPoint, Object result, BeanFactory beanFactory) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        Object value = eval(expression, joinPoint, result, beanFactory);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private Object eval(String expression, JoinPoint joinPoint, Object result, BeanFactory beanFactory) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();

        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                target, method, joinPoint.getArgs(), parameterNames);
        context.setVariable("result", result);
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));

        return parser.parseExpression(expression).getValue(context);
    }
}
