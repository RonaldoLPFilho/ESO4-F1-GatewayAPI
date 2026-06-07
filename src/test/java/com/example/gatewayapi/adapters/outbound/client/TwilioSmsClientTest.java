package com.example.gatewayapi.adapters.outbound.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TwilioSmsClientTest {

    @Test
    void shouldNormalizeBrazilianPhoneNumbers() {
        assertEquals("+5511999999999", TwilioSmsClient.normalizePhone("+5511999999999"));
        assertEquals("+5511999999999", TwilioSmsClient.normalizePhone("11999999999"));
        assertEquals("+5511999999999", TwilioSmsClient.normalizePhone("(11) 99999-9999"));
        assertEquals("+5511999999999", TwilioSmsClient.normalizePhone("5511999999999"));
    }

    @Test
    void shouldStayDisabledWithoutCredentials() {
        TwilioSmsClient client = new TwilioSmsClient("", "", "", true, 5000);

        assertFalse(client.isEnabled());
        TwilioSmsClient.SmsSendResult result = client.send("+5511999999999", "teste");
        assertFalse(result.success());
        assertEquals("Twilio não configurado", result.errorMessage());
    }
}
