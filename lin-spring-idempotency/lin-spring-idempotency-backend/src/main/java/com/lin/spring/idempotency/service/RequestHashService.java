package com.lin.spring.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class RequestHashService {

	private final ObjectMapper objectMapper;

	public RequestHashService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String hash(Object payload) {
		try {
			byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(jsonBytes);
			StringBuilder builder = new StringBuilder(digest.length * 2);
			for (byte current : digest) {
				builder.append(String.format("%02x", current));
			}
			return builder.toString();
		} catch (JsonProcessingException | NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Failed to hash request payload", ex);
		}
	}
}
