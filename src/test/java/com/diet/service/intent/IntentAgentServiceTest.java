package com.diet.service.intent;

import com.diet.agent.factory.AgentFactory;
import com.diet.enums.Intent;
import com.diet.model.IntentResult;
import com.diet.model.SlotBundle;
import com.diet.service.slot.SlotOptionService;
import com.diet.service.trace.AgentTraceService;
import com.diet.util.LlmJsonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentAgentServiceTest {

    private AgentFactory agentFactory;
    private SlotOptionService slotOptionService;
    private AgentTraceService traceService;
    private ReActAgent agent;
    private IntentAgentService service;

    @BeforeEach
    void setUp() {
        agentFactory = mock(AgentFactory.class);
        slotOptionService = mock(SlotOptionService.class);
        traceService = mock(AgentTraceService.class);
        agent = mock(ReActAgent.class, RETURNS_DEEP_STUBS);

        when(agentFactory.get("session-1"))
                .thenReturn(new AgentFactory.AgentSet(agent, null, null));
        when(slotOptionService.findAllOptions()).thenReturn(Map.of(
                "mealTime", List.of("晚餐")
        ));

        service = new IntentAgentService(
                agentFactory,
                new LlmJsonService(new ObjectMapper()),
                slotOptionService,
                traceService,
                "test-model"
        );
    }

    @ParameterizedTest
    @CsvSource(value = {
            "推荐晚饭|MEAL_RECOMMENDATION",
            "换一批|MEAL_ADJUST",
            "三餐怎么安排|MEAL_PLAN",
            "你好|OTHER",
            "糖尿病怎么吃|HEALTH_RISK",
            "完全无法识别的表达|CLARIFY_NEEDED"
    }, delimiter = '|')
    void shouldUseKeywordFallbackWhenAgentCallFails(String userInput, Intent expectedIntent) {
        failAgentCall();

        IntentResult result = recognize(userInput);

        assertEquals(expectedIntent, result.intent());
        assertTrue(result.slots().isEmpty());
        assertEquals(0.2, result.confidence());
    }

    @Test
    void shouldUseFullFallbackWhenAgentReturnsBlankText() {
        returnAgentText("   ");

        IntentResult result = recognize("推荐晚饭");

        assertEquals(Intent.MEAL_RECOMMENDATION, result.intent());
        assertTrue(result.slots().isEmpty());
        assertEquals(0.2, result.confidence());
    }

    @Test
    void shouldUseFullFallbackWhenAgentReturnsInvalidJson() {
        returnAgentText("这不是 JSON");

        IntentResult result = recognize("推荐晚饭");

        assertEquals(Intent.MEAL_RECOMMENDATION, result.intent());
        assertTrue(result.slots().isEmpty());
        assertEquals(0.2, result.confidence());
    }

    @Test
    void shouldOnlyFallbackIntentFieldWhenJsonContainsIllegalIntentName() {
        returnAgentText("""
                {
                  "intent": "UNKNOWN_INTENT",
                  "slots": {"mealTime": ["晚餐"]},
                  "confidence": 0.86
                }
                """);

        IntentResult result = recognize("推荐晚饭");

        assertEquals(Intent.MEAL_RECOMMENDATION, result.intent());
        assertEquals(List.of("晚餐"), result.slots().mealTime());
        assertEquals(0.86, result.confidence());
    }

    private IntentResult recognize(String userInput) {
        return service.recognize(
                "session-1",
                1L,
                userInput,
                SlotBundle.empty(),
                List.of()
        );
    }

    private void failAgentCall() {
        when(traceService.callAgent(
                eq("session-1"),
                eq("IntentAgent"),
                anyString(),
                same(agent),
                anyString()
        )).thenThrow(new RuntimeException("模拟模型超时"));
    }

    private void returnAgentText(String text) {
        Msg response = mock(Msg.class);
        when(response.getTextContent()).thenReturn(text);
        when(traceService.callAgent(
                eq("session-1"),
                eq("IntentAgent"),
                anyString(),
                same(agent),
                anyString()
        )).thenReturn(response);
    }
}
