package map.project.user.trip.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * BudgetRange — trip 생성 요청의 예산 구간 (앱 입력의 예산 단계)
 *
 * TripGenerateRequest 의 budget 필드로 중첩되어 사용된다. 원 단위 정수.
 * agent 로 위임할 때는 단일 budget 으로 환산하며, 환산 규칙은
 * TripMapping.toAgentBudget 가 담당한다(현재 상한 max 를 쓴다).
 *
 * min: 예산 하한(원). JSON key "min". @NotNull, 0 이상.
 * max: 예산 상한(원). JSON key "max". @NotNull, 0 이상.
 */
public record BudgetRange(
        @NotNull @Min(0) Integer min,
        @NotNull @Min(0) Integer max
) {
}
