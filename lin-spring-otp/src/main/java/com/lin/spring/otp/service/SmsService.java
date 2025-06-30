package com.lin.spring.otp.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsService {
    private final Environment env;

    public void sendOtpSms(String phoneNumber, String otpCode) {
        String accountSid = env.getProperty("twilio.account.sid");
        String authToken = env.getProperty("twilio.auth.token");
        String twilioNumber = env.getProperty("twilio.phone.number");

        Twilio.init(accountSid, authToken);

        Message.creator(
                new PhoneNumber(phoneNumber),
                new PhoneNumber(twilioNumber),
                "Your OTP code is: " + otpCode + ". This code will expire in 5 minutes."
        ).create();
    }
}
