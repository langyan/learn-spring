package com.lin.spring.ticketrace.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
                "server.port=0"
        }
)
class TicketRaceDiscoveryApplicationTests {

    @Test
    void contextLoads() {
    }
}
