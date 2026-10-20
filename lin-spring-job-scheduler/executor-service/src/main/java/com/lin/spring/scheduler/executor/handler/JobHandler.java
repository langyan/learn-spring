package com.lin.spring.scheduler.executor.handler;

import com.lin.spring.scheduler.common.messaging.JobMessage;

/** 作业处理器扩展点：真实业务可提供多个实现（按 payload 路由） */
public interface JobHandler {

    void execute(JobMessage message) throws Exception;
}
