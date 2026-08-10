package com.diet.service.intent;

import com.diet.enums.Intent;
import com.diet.enums.SourceMode;
import com.diet.model.IntentResult;
import com.diet.model.SessionState;
import com.diet.model.SlotBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentReviseServiceTest {

    private final IntentReviseService service = new IntentReviseService();

    @Test
    void shouldReviseLowConfidenceRecommendationToClarify() {
        IntentResult result = service.revise(
                null,
                new IntentResult(Intent.MEAL_RECOMMENDATION, SlotBundle.empty(), 0.2),
                "推荐晚饭"
        );

        assertEquals(Intent.CLARIFY_NEEDED, result.intent());
        assertEquals(0.2, result.confidence());
    }

    @Test
    void shouldKeepHealthRiskRegardlessOfLowConfidence() {
        IntentResult result = service.revise(
                null,
                new IntentResult(Intent.CLARIFY_NEEDED, SlotBundle.empty(), 0.2),
                "糖尿病应该怎么吃"
        );

        assertEquals(Intent.HEALTH_RISK, result.intent());
    }

    @Test
    void shouldKeepAdjustIntentWhenHistoryContainsRecommendations() {
        SessionState state = SessionState.fresh("session-1", 1L, SourceMode.PUBLIC)
                .withLastRecommendations(List.of(10L));

        IntentResult result = service.revise(
                state,
                new IntentResult(Intent.MEAL_ADJUST, SlotBundle.empty(), 0.2),
                "换一批"
        );

        assertEquals(Intent.MEAL_ADJUST, result.intent());
    }

    @Test
    void shouldConvertAdjustToRecommendationWhenThereIsNoHistory() {
        SessionState state = SessionState.fresh("session-1", 1L, SourceMode.PUBLIC);

        IntentResult result = service.revise(
                state,
                new IntentResult(Intent.MEAL_ADJUST, SlotBundle.empty(), 0.2),
                "换一批"
        );

        assertEquals(Intent.MEAL_RECOMMENDATION, result.intent());
    }
}
