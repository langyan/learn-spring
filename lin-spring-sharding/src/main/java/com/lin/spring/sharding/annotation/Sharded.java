package com.lin.spring.sharding.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 写路径：在事务开始前根据 SpEL 表达式解析路由键并设置分片上下文。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sharded {

    /**
     * 路由键 SpEL，例如 {@code #request.email} 或 {@code #email}。
     */
    String key();
}
