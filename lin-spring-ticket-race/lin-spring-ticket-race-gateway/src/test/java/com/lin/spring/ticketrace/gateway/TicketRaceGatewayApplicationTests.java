package com.lin.spring.ticketrace.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false"
        }
)
class TicketRaceGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
