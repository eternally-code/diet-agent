package com.diet.service.orchestrator;

import com.diet.agent.factory.AgentFactory;
import com.diet.enums.SessionPhase;
import com.diet.enums.SourceMode;
import com.diet.mapper.AgentTraceMapper;
import com.diet.model.ChatRequest;
import com.diet.model.ChatResponse;
import com.diet.model.MealItem;
import com.diet.model.RequestTraceRow;
import com.diet.model.SessionState;
import com.diet.model.SlotBundle;
import com.diet.service.clarify.ClarifyAgentService;
import com.diet.service.clarify.ClarifyRuleService;
import com.diet.service.intent.IntentAgentService;
import com.diet.service.intent.IntentReviseService;
import com.diet.service.meal.MealRankService;
import com.diet.service.meal.MealSearchService;
import com.diet.service.meal.MealService;
import com.diet.service.recommend.RecommendResponseAgentService;
import com.diet.service.risk.RiskGuardService;
import com.diet.service.session.SessionService;
import com.diet.service.session.SessionStateService;
import com.diet.service.slot.SlotMergeService;
import com.diet.service.slot.SlotOptionService;
import com.diet.service.trace.AgentTraceService;
import com.diet.util.LlmJsonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DietOrchestratorFallbackIntegrationTest {

    private SessionService sessionService;
    private SessionStateService sessionStateService;
    private MealSearchService mealSearchService;
    private MealRankService mealRankService;
    private MealService mealService;
    private SlotOptionService slotOptionService;
    private AgentTraceMapper traceMapper;
    private ReActAgent intentAgent;
    private ReActAgent clarifyAgent;
    private ReActAgent recommendAgent;
    private DietOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        sessionStateService = mock(SessionStateService.class);
        mealSearchService = mock(MealSearchService.class);
        mealRankService = mock(MealRankService.class);
        mealService = mock(MealService.class);
        slotOptionService = mock(SlotOptionService.class);
        traceMapper = mock(AgentTraceMapper.class);
        intentAgent = mock(ReActAgent.class, RETURNS_DEEP_STUBS);
        clarifyAgent = mock(ReActAgent.class, RETURNS_DEEP_STUBS);
        recommendAgent = mock(ReActAgent.class, RETURNS_DEEP_STUBS);

        AgentFactory agentFactory = mock(AgentFactory.class);
        when(agentFactory.get(anyString())).thenReturn(
                new AgentFactory.AgentSet(intentAgent, clarifyAgent, recommendAgent)
        );

        ObjectMapper objectMapper = new ObjectMapper();
        LlmJsonService llmJsonService = new LlmJsonService(objectMapper);
        AgentTraceService traceService = new AgentTraceService(traceMapper, objectMapper);
        IntentAgentService intentService = new IntentAgentService(
                agentFactory, llmJsonService, slotOptionService, traceService, "light-test-model"
        );
        ClarifyAgentService clarifyService = new ClarifyAgentService(
                agentFactory, new ClarifyRuleService(), traceService, "light-test-model"
        );
        RecommendResponseAgentService recommendService = new RecommendResponseAgentService(
                agentFactory, llmJsonService, traceService, "main-test-model"
        );

        orchestrator = new DietOrchestratorService(
                sessionService,
                sessionStateService,
                intentService,
                new IntentReviseService(),
                new SlotMergeService(),
                clarifyService,
                mealSearchService,
                mealRankService,
                recommendService,
                mealService,
                new RiskGuardService(),
                traceService
        );
        when(sessionService.recentConversationTurns(anyString(), anyLong(), anyInt()))
                .thenReturn(List.of());
    }

    @Test
    void shouldReturnTemplateClarificationWhenIntentAndClarifyAgentsFail() {
        when(sessionStateService.loadOrCreate("session-1", 1L, SourceMode.PUBLIC))
                .thenReturn(SessionState.fresh("session-1", 1L, SourceMode.PUBLIC));
        when(slotOptionService.findAllOptions()).thenReturn(Map.of());
        when(intentAgent.call(any(Msg.class)))
                .thenReturn(Mono.error(new RuntimeException("IntentAgent 超时")));
        when(clarifyAgent.call(any(Msg.class)))
                .thenReturn(Mono.error(new RuntimeException("ClarifyAgent 超时")));

        ChatResponse response = orchestrator.dietChat(
                1L,
                new ChatRequest("session-1", "推荐晚饭", SourceMode.PUBLIC, Map.of())
        );

        assertEquals("CLARIFY", response.responseType());
        assertEquals("这顿主要是早餐、午餐还是晚餐？", response.speechText());
        assertEquals(List.of("mealTime", "healthGoal"), response.missingSlots());

        ArgumentCaptor<SessionState> stateCaptor = ArgumentCaptor.forClass(SessionState.class);
        verify(sessionStateService).save(stateCaptor.capture());
        assertEquals(SessionPhase.CLARIFY, stateCaptor.getValue().phase());

        RequestTraceRow trace = capturedTrace();
        assertEquals("FAILED", trace.getStatus());
        assertTrue(trace.getTraceJson().contains("IntentAgent"));
        assertTrue(trace.getTraceJson().contains("ClarifyAgent"));
        assertTrue(trace.getTraceJson().contains("RESPONSE_READY"));
        assertTrue(trace.getTraceJson().contains("REQUEST_FINISHED"));
    }

    @Test
    void shouldReturnTopThreeCardsWhenRecommendAgentFails() {
        SlotBundle slots = new SlotBundle(
                List.of("晚餐"), List.of(), List.of(), List.of(),
                List.of(), List.of("清淡"), List.of()
        );
        List<MealItem> meals = List.of(
                new MealItem(1L, SourceMode.PUBLIC, null, "鸡胸肉饭", slots, 0.95),
                new MealItem(2L, SourceMode.PUBLIC, null, "清蒸鱼", slots, 0.88),
                new MealItem(3L, SourceMode.PUBLIC, null, "蔬菜沙拉", slots, 0.80)
        );

        when(sessionStateService.loadOrCreate("session-1", 1L, SourceMode.PUBLIC))
                .thenReturn(SessionState.fresh("session-1", 1L, SourceMode.PUBLIC));
        when(slotOptionService.findAllOptions()).thenReturn(Map.of(
                "mealTime", List.of("晚餐"),
                "taste", List.of("清淡")
        ));

        Msg intentResponse = mock(Msg.class);
        when(intentResponse.getTextContent()).thenReturn("""
                {
                  "intent": "MEAL_RECOMMENDATION",
                  "slots": {"mealTime": ["晚餐"], "taste": ["清淡"]},
                  "confidence": 0.9
                }
                """);
        when(intentAgent.call(any(Msg.class))).thenReturn(Mono.just(intentResponse));
        when(mealSearchService.search(any())).thenReturn(meals);
        when(mealRankService.rank(any())).thenReturn(meals);
        when(recommendAgent.call(any(Msg.class)))
                .thenReturn(Mono.error(new RuntimeException("RecommendResponseAgent 超时")));

        ChatResponse response = orchestrator.dietChat(
                1L,
                new ChatRequest("session-1", "晚饭想吃清淡的", SourceMode.PUBLIC, Map.of())
        );

        assertEquals("ANSWER", response.responseType());
        assertFalse(response.speechText().isBlank());
        assertEquals(List.of(1L, 2L, 3L), response.displayBlocks().stream()
                .map(block -> block.id()).toList());

        RequestTraceRow trace = capturedTrace();
        assertEquals("FAILED", trace.getStatus());
        assertTrue(trace.getTraceJson().contains("RecommendResponseAgent"));
        assertTrue(trace.getTraceJson().contains("RESPONSE_AGENT_RESULT"));
        assertTrue(trace.getTraceJson().contains("RESPONSE_READY"));
        assertTrue(trace.getTraceJson().contains("REQUEST_FINISHED"));
    }

    private RequestTraceRow capturedTrace() {
        ArgumentCaptor<RequestTraceRow> traceCaptor = ArgumentCaptor.forClass(RequestTraceRow.class);
        verify(traceMapper).insert(traceCaptor.capture());
        return traceCaptor.getValue();
    }
}
