package com.lin.spring.scheduler.common.util;

import com.lin.spring.scheduler.common.messaging.JobMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JsonCodecTest {

    @Test
    void jobMessageRoundTripPreservesAllFields() {
        Instant scheduled = Instant.parse("2026-10-19T08:00:00Z");
        JobMessage message = new JobMessage(
                "job-1", "demo", "{\"sleepMs\":50}",
                2, 3, scheduled, "trace-abc");

        String json = JsonCodec.write(message);
        JobMessage decoded = JsonCodec.read(json, JobMessage.class);

        assertThat(decoded).isEqualTo(message);
        assertThat(decoded.scheduledTime()).isEqualTo(scheduled);
        assertThat(decoded.attemptNumber()).isEqualTo(2);
        assertThat(decoded.maxRetries()).isEqualTo(3);
    }

    @Test
    void instantSerializedAsIso8601String() {
        Instant instant = Instant.parse("2026-10-19T08:00:00Z");
        String json = JsonCodec.write(new Wrapper(instant));
        assertThat(json).contains("\"2026-10-19T08:00:00Z\"");
        assertThat(JsonCodec.read(json, Wrapper.class).at).isEqualTo(instant);
    }

    record Wrapper(Instant at) {
    }
}
