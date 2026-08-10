package com.diet.service.trace;

import com.diet.mapper.AgentTraceMapper;
import com.diet.model.RequestTraceRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTraceServiceTest {

    @Test
    void shouldRecordFailedAgentCallAndMarkTraceFailed() throws Exception {
        AgentTraceMapper mapper = mock(AgentTraceMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTraceService service = new AgentTraceService(mapper, objectMapper);
        ReActAgent agent = mock(ReActAgent.class);
        when(agent.call(any(Msg.class)))
                .thenReturn(Mono.error(new RuntimeException("模拟模型超时")));

        try (AgentTraceService.TraceScope ignored = service.openTrace("trace-1", "session-1", 1L)) {
            assertThrows(RuntimeException.class, () -> service.callAgent(
                    "session-1", "IntentAgent", "test-model", agent, "测试输入"
            ));
        }

        ArgumentCaptor<RequestTraceRow> rowCaptor = ArgumentCaptor.forClass(RequestTraceRow.class);
        verify(mapper).insert(rowCaptor.capture());
        RequestTraceRow row = rowCaptor.getValue();

        assertEquals("FAILED", row.getStatus());
        assertEquals(1, row.getEventCount());
        assertTrue(row.getErrorMessage().contains("模拟模型超时"));

        JsonNode trace = objectMapper.readTree(row.getTraceJson());
        JsonNode event = trace.path("events").get(0);
        assertEquals("AGENT_CALL", event.path("eventType").asText());
        assertEquals("IntentAgent", event.path("agentName").asText());
        assertEquals("test-model", event.path("modelName").asText());
        assertTrue(event.path("errorMessage").asText().contains("模拟模型超时"));
        assertTrue(event.path("outputPayload").isNull());
    }
}
