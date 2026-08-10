package com.diet.service.recommend;

import com.diet.agent.factory.AgentFactory;
import com.diet.enums.SourceMode;
import com.diet.model.MealItem;
import com.diet.model.RecommendedMealOption;
import com.diet.model.SlotBundle;
import com.diet.service.trace.AgentTraceService;
import com.diet.util.LlmJsonService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecommendResponseAgentServiceTest {

    private AgentFactory agentFactory;
    private AgentTraceService traceService;
    private ReActAgent agent;
    private RecommendResponseAgentService service;
    private SlotBundle userSlots;
    private List<MealItem> rankedMeals;

    @BeforeEach
    void setUp() {
        agentFactory = mock(AgentFactory.class);
        traceService = mock(AgentTraceService.class);
        agent = mock(ReActAgent.class, RETURNS_DEEP_STUBS);
        when(agentFactory.get("session-1"))
                .thenReturn(new AgentFactory.AgentSet(null, null, agent));
        service = new RecommendResponseAgentService(
                agentFactory,
                new LlmJsonService(new ObjectMapper()),
                traceService,
                "test-model"
        );
        userSlots = new SlotBundle(
                List.of("晚餐"), List.of(), List.of(), List.of("减脂"),
                List.of(), List.of("清淡"), List.of()
        );
        rankedMeals = List.of(
                meal(1L, "鸡胸肉饭", 0.95),
                meal(2L, "清蒸鱼", 0.88),
                meal(3L, "蔬菜沙拉", 0.80),
                meal(4L, "牛肉面", 0.70)
        );
    }

    @Test
    void shouldKeepTopThreeCandidatesAndUseTemplatesWhenAgentCallFails() {
        failAgentCall();

        RecommendResponseAgentService.Result result = recommend(rankedMeals);

        assertTopThreeCandidatesArePreserved(result);
        assertTrue(result.recommend().recommendations().stream()
                .allMatch(option -> option.reason().contains("减脂")));
        assertFalse(result.response().speechText().isBlank());
        assertTrue(result.response().speechText().contains("日常饮食参考"));
        assertEquals(3, result.response().displayBlocks().size());
    }

    @Test
    void shouldKeepTopThreeCandidatesWhenAgentReturnsInvalidJson() {
        returnAgentText("这不是 JSON");

        RecommendResponseAgentService.Result result = recommend(rankedMeals);

        assertTopThreeCandidatesArePreserved(result);
        assertFalse(result.response().speechText().isBlank());
        assertEquals(3, result.response().displayBlocks().size());
    }

    @Test
    void shouldIgnoreInventedIdsAndFillMissingReasonsAndSpeech() {
        returnAgentText("""
                {
                  "recommendations": [
                    {"mealId": 999, "reason": "模型编造的餐食"},
                    {"mealId": 1, "reason": "模型生成的有效理由"},
                    {"mealId": 2, "reason": ""}
                  ],
                  "speechText": ""
                }
                """);

        RecommendResponseAgentService.Result result = recommend(rankedMeals);

        assertTopThreeCandidatesArePreserved(result);
        List<RecommendedMealOption> options = result.recommend().recommendations();
        assertEquals("模型生成的有效理由", options.get(0).reason());
        assertTrue(options.get(1).reason().contains("减脂"));
        assertTrue(options.get(2).reason().contains("减脂"));
        assertTrue(options.stream().noneMatch(option -> option.itemId() == 999L));
        assertFalse(result.response().speechText().isBlank());
    }

    @Test
    void shouldReturnEmptyResultWithoutCallingLlmWhenThereAreNoCandidates() {
        RecommendResponseAgentService.Result result = recommend(List.of());

        assertTrue(result.recommend().recommendations().isEmpty());
        assertTrue(result.response().displayBlocks().isEmpty());
        assertFalse(result.response().speechText().isBlank());
        verifyNoInteractions(agentFactory, traceService);
    }

    private RecommendResponseAgentService.Result recommend(List<MealItem> meals) {
        return service.recommendAndRespond(
                "session-1", "晚饭想吃清淡减脂的", SourceMode.PUBLIC, userSlots, meals
        );
    }

    private MealItem meal(Long id, String name, double score) {
        return new MealItem(id, SourceMode.PUBLIC, null, name, userSlots, score);
    }

    private void assertTopThreeCandidatesArePreserved(RecommendResponseAgentService.Result result) {
        assertEquals(List.of(1L, 2L, 3L), result.recommend().recommendations().stream()
                .map(RecommendedMealOption::itemId).toList());
        assertEquals(List.of("鸡胸肉饭", "清蒸鱼", "蔬菜沙拉"), result.recommend().recommendations().stream()
                .map(RecommendedMealOption::name).toList());
        assertEquals(List.of(0.95, 0.88, 0.80), result.recommend().recommendations().stream()
                .map(RecommendedMealOption::matchScore).toList());
    }

    private void failAgentCall() {
        when(traceService.callAgent(
                eq("session-1"), eq("RecommendResponseAgent"), anyString(), same(agent), anyString()
        )).thenThrow(new RuntimeException("模拟模型超时"));
    }

    private void returnAgentText(String text) {
        Msg response = mock(Msg.class);
        when(response.getTextContent()).thenReturn(text);
        when(traceService.callAgent(
                eq("session-1"), eq("RecommendResponseAgent"), anyString(), same(agent), anyString()
        )).thenReturn(response);
    }
}
