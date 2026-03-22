package com.example.gatewayapi;

import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertConfigDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertContactDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertEventDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:alerts-it;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "vision.baseUrl=http://localhost:8081",
        "vision.timeoutMillis=5000"
})
@AutoConfigureMockMvc
class AlertsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldManageAlertsUsingFrontendContract() throws Exception {
        AlertConfigDTO config = objectMapper.readValue(
                performAsync(get("/alerts/config")),
                AlertConfigDTO.class
        );

        assertNotNull(config);
        assertNotNull(config.windowMinutes());
        assertNotNull(config.thresholdSick());
        assertNotNull(config.cooldownMinutes());
        assertNotNull(config.active());

        AlertConfigDTO savedConfig = objectMapper.readValue(
                performAsync(put("/alerts/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AlertConfigDTO(
                                config.id(),
                                3,
                                2,
                                1,
                                true,
                                config.lastTriggeredAt()
                        )))),
                AlertConfigDTO.class
        );

        assertNotNull(savedConfig);
        assertTrue(savedConfig.windowMinutes() == 3);
        assertTrue(savedConfig.thresholdSick() == 2);
        assertTrue(savedConfig.cooldownMinutes() == 1);

        AlertContactDTO contact = objectMapper.readValue(
                performAsync(post("/alerts/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AlertContactDTO(null, "Maria", "maria@example.com", "+5511999999999")
                        ))),
                AlertContactDTO.class
        );

        assertNotNull(contact);
        assertNotNull(contact.id());

        List<AlertContactDTO> contacts = objectMapper.readValue(
                performAsync(get("/alerts/contacts")),
                new TypeReference<>() {}
        );

        assertNotNull(contacts);
        assertTrue(contacts.stream().anyMatch(c -> "Maria".equals(c.name())));

        performAsync(post("/alerts/test"));

        List<AlertEventDTO> events = objectMapper.readValue(
                performAsync(get("/alerts/events")),
                new TypeReference<>() {}
        );

        assertNotNull(events);
        assertTrue(events.size() >= 2);
        assertTrue(events.stream().anyMatch(e -> e.channel().name().equals("EMAIL")));
        assertTrue(events.stream().anyMatch(e -> e.channel().name().equals("SMS")));

        performAsync(delete("/alerts/contacts/" + contact.id()));
    }

    private String performAsync(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        MvcResult mvcResult = mockMvc.perform(request)
                .andReturn();
        return mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
