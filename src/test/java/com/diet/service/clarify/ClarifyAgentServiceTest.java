package com.diet.service.clarify;

import com.diet.agent.factory.AgentFactory;
import com.diet.enums.ClarifyAction;
import com.diet.model.ClarifyResult;
import com.diet.model.SlotBundle;
import com.diet.service.trace.AgentTraceService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClarifyAgentServiceTest {

    private AgentFactory agentFactory;
    private AgentTraceService traceService;
    private ReActAgent agent;
    private ClarifyAgentService service;

    @BeforeEach
    void setUp() {
        agentFactory = mock(AgentFactory.class);
        traceService = mock(AgentTraceService.class);
        agent = mock(ReActAgent.class, RETURNS_DEEP_STUBS);
        when(agentFactory.get("session-1"))
                .thenReturn(new AgentFactory.AgentSet(null, agent, null));
        service = new ClarifyAgentService(
                agentFactory, new ClarifyRuleService(), traceService, "test-model"
        );
    }

    @Test
    void shouldReturnMealTimeTemplateWhenAgentCallFails() {
        failAgentCall();

        ClarifyResult result = service.decide("session-1", "推荐点吃的", SlotBundle.empty());

        assertEquals(ClarifyAction.ASK, result.action());
        assertEquals("这顿主要是早餐、午餐还是晚餐？", result.questionToAsk());
        assertEquals(List.of("mealTime", "healthGoal"), result.missingSlots());
    }

    @Test
    void shouldReturnMealTimeTemplateWhenAgentReturnsBlankText() {
        Msg response = mock(Msg.class);
        when(response.getTextContent()).thenReturn("   ");
        when(traceService.callAgent(
                eq("session-1"), eq("ClarifyAgent"), anyString(), same(agent), anyString()
        )).thenReturn(response);

        ClarifyResult result = service.decide("session-1", "推荐点吃的", SlotBundle.empty());

        assertEquals(ClarifyAction.ASK, result.action());
        assertEquals("这顿主要是早餐、午餐还是晚餐？", result.questionToAsk());
    }

    @Test
    void shouldReturnHealthGoalTemplateWhenOnlyHealthGoalIsMissing() {
        failAgentCall();
        SlotBundle slots = new SlotBundle(
                List.of("晚餐"), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of()
        );

        ClarifyResult result = service.decide("session-1", "晚饭吃什么", slots);

        assertEquals(ClarifyAction.ASK, result.action());
        assertEquals("这顿更想清淡点、顶饱点，还是按口味来？", result.questionToAsk());
        assertEquals(List.of("healthGoal"), result.missingSlots());
    }

    @Test
    void shouldReturnReadyWithoutCallingLlmWhenSlotsAreEnough() {
        SlotBundle slots = new SlotBundle(
                List.of("晚餐"), List.of(), List.of(), List.of(),
                List.of(), List.of("清淡"), List.of()
        );

        ClarifyResult result = service.decide("session-1", "晚饭想吃清淡的", slots);

        assertEquals(ClarifyAction.READY, result.action());
        assertNull(result.questionToAsk());
        assertEquals(List.of(), result.missingSlots());
        verifyNoInteractions(agentFactory, traceService);
    }

    private void failAgentCall() {
        when(traceService.callAgent(
                eq("session-1"), eq("ClarifyAgent"), anyString(), same(agent), anyString()
        )).thenThrow(new RuntimeException("模拟模型超时"));
    }
}
