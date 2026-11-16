package com.lin.spring.grpc.client.web;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcExceptionMapperTest {

    private final GrpcExceptionMapper mapper = new GrpcExceptionMapper();

    @Test
    void mapsGrpcCodesToHttpStatus() {
        assertThat(mapper.toHttpStatus(Status.Code.NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mapper.toHttpStatus(Status.Code.INVALID_ARGUMENT)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.toHttpStatus(Status.Code.PERMISSION_DENIED)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(mapper.toHttpStatus(Status.Code.UNAUTHENTICATED)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(mapper.toHttpStatus(Status.Code.ALREADY_EXISTS)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mapper.toHttpStatus(Status.Code.DEADLINE_EXCEEDED)).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(mapper.toHttpStatus(Status.Code.UNAVAILABLE)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(mapper.toHttpStatus(Status.Code.INTERNAL)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(mapper.toHttpStatus(Status.Code.UNKNOWN)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void buildsErrorBodyWithDescription() {
        StatusRuntimeException e = Status.NOT_FOUND
                .withDescription("Payment not found: PAY-999")
                .asRuntimeException();

        ResponseEntity<Map<String, String>> response = mapper.toHttpResponse(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Payment not found: PAY-999");
        assertThat(response.getBody()).containsEntry("grpcStatus", "NOT_FOUND");
    }

    @Test
    void fallsBackToCodeNameWhenDescriptionMissing() {
        StatusRuntimeException e = Status.UNAVAILABLE.asRuntimeException();

        ResponseEntity<Map<String, String>> response = mapper.toHttpResponse(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("error", "UNAVAILABLE");
    }
}
