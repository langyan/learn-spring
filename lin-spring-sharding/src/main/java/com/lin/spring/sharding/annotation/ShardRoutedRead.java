package com.lin.spring.sharding.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 读路径：在只读事务开始前从雪花 ID 解析分片并设置上下文。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShardRoutedRead {

    /**
     * 承载雪花 ID 的方法参数名，默认 {@code id}。
     */
    String idParam() default "id";
}
