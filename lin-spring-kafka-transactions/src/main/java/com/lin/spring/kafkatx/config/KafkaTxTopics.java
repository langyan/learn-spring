package com.lin.spring.kafkatx.config;

/**
 * 演示用 Topic 常量，与 {@code application.yml}、集成测试保持一致。
 */
public final class KafkaTxTopics {

	private KafkaTxTopics() {
	}

	/** 事务演示 Topic A */
	public static final String DEMO_TX_A = "demo-tx-a";

	/** 事务演示 Topic B */
	public static final String DEMO_TX_B = "demo-tx-b";
}
