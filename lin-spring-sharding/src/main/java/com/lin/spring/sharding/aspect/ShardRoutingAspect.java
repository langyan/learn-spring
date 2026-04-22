package com.lin.spring.sharding.aspect;

import com.lin.spring.sharding.annotation.ShardRoutedRead;
import com.lin.spring.sharding.annotation.Sharded;
import com.lin.spring.sharding.hash.ConsistentHashRing;
import com.lin.spring.sharding.id.SnowflakeIdCodec;
import com.lin.spring.sharding.routing.ShardContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 在事务获取连接之前设置 {@link ShardContext}，保证 {@code AbstractRoutingDataSource} 路由正确。
 * <p>顺序取较小正值，确保晚于 {@code ExposeInvocationInterceptor}（避免 JoinPoint 元数据不可用），
 * 且早于默认事务切面（在打开连接前完成路由）。</p>
 */
@Aspect
@Component
@Order(1)
public class ShardRoutingAspect {

    private static final Logger log = LoggerFactory.getLogger(ShardRoutingAspect.class);

    private final ConsistentHashRing ring;
    private final ExpressionParser parser = new SpelExpressionParser();

    public ShardRoutingAspect(ConsistentHashRing ring) {
        this.ring = ring;
    }

    /**
     * 写路径：解析 SpEL 路由键并写入分片上下文。
     */
    @Before("@annotation(sharded)")
    public void beforeWrite(JoinPoint joinPoint, Sharded sharded) {
        Object key = evaluateKey(joinPoint, sharded.key());
        int shard = ring.pickShard(String.valueOf(key));
        ShardContext.set(shard);
        log.info("Routing to shard = {} (write key={})", shard, key);
    }

    @After("@annotation(com.lin.spring.sharding.annotation.Sharded)")
    public void afterWrite() {
        ShardContext.clear();
    }

    /**
     * 读路径：从雪花 ID 解析分片。
     */
    @Before("@annotation(shardRead)")
    public void beforeRead(JoinPoint joinPoint, ShardRoutedRead shardRead) {
        long id = resolveIdArgument(joinPoint, shardRead.idParam());
        int shard = SnowflakeIdCodec.extractShardId(id);
        ShardContext.set(shard);
        log.info("Routing to shard = {} (read id={})", shard, id);
    }

    @After("@annotation(com.lin.spring.sharding.annotation.ShardRoutedRead)")
    public void afterRead() {
        ShardContext.clear();
    }

    private Object evaluateKey(JoinPoint joinPoint, String spel) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String[] names = sig.getParameterNames();
        Object[] args = joinPoint.getArgs();
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        return parser.parseExpression(spel).getValue(ctx);
    }

    private long resolveIdArgument(JoinPoint joinPoint, String idParam) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String[] names = sig.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (names == null) {
            throw new IllegalStateException("无法解析方法参数名，请使用 -parameters 编译。");
        }
        for (int i = 0; i < names.length; i++) {
            if (idParam.equals(names[i])) {
                Object v = args[i];
                if (v instanceof Long l) {
                    return l;
                }
                if (v instanceof Number n) {
                    return n.longValue();
                }
                throw new IllegalArgumentException("参数 " + idParam + " 不是数值类型");
            }
        }
        throw new IllegalArgumentException("未找到参数: " + idParam);
    }
}
