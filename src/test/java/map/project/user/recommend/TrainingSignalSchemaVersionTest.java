package map.project.user.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import map.project.user.global.crypto.TestPayloadCiphers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 학습 신호의 판 표기를 정수(예전)와 문자열(지금) 모두 받는지 본다.
 *
 * <p>agent 는 이 소비 코드가 배포된 뒤에 표기를 "1.0.0" 으로 바꾼다. 그 전에 바꾸면
 * 신호가 경고만 남기고 버려지므로, 두 표기를 다 받는다는 사실을 여기서 묶어 둔다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TrainingSignalSchemaVersionTest {
    @Autowired RecommendCancellationRepository cancellations;
    @Autowired RecommendJobRepository jobs;
    @Autowired RecommendEditRepository edits;
    @Autowired RecommendTrainingRepository training;
    @Autowired RecommendEditRequestRepository receipts;
    @Autowired TestEntityManager em;
    RecommendJobStore store;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        store = new RecommendJobStore(jobs, training, edits, mapper,
                TestPayloadCiphers.enabled(), receipts, cancellations, TestJobOwners.active());
        ReflectionTestUtils.setField(store, "trainingCaptureEnabled", true);
    }

    private String complete(String trainingJson) {
        String id = UUID.randomUUID().toString();
        store.insertInProgress(id, null, RecommendJobStore.JobOrigin.agent("init").ownedBy(7L));
        store.recordCompletion(id, "done", "{\"v\":0}", trainingJson);
        em.flush(); em.clear();
        return id;
    }

    @Test
    @DisplayName("문자열 판 \"1.0.0\" 신호를 그대로 저장한다")
    void storesStringVersion() {
        String id = complete("{\"schema_version\":\"1.0.0\",\"path\":\"select\"}");
        assertThat(training.findById(UUID.fromString(id)))
                .hasValueSatisfying(t -> assertThat(t.getSchemaVersion()).isEqualTo("1.0.0"));
    }

    @Test
    @DisplayName("예전 정수 판 신호는 같은 숫자 문자열로 저장한다")
    void storesIntegerVersionAsText() {
        String id = complete("{\"schema_version\":2,\"path\":\"select\"}");
        assertThat(training.findById(UUID.fromString(id)))
                .hasValueSatisfying(t -> assertThat(t.getSchemaVersion()).isEqualTo("2"));
    }

    @Test
    @DisplayName("판 표기 모양이 아니면 신호를 버리고 완료 기록은 남긴다")
    void dropsMalformedVersion() throws Exception {
        String id = complete("{\"schema_version\":\"1.0.0; drop\",\"path\":\"select\"}");
        assertThat(training.findById(UUID.fromString(id))).isEmpty();
        assertThat(store.findFinishedPayload(id)).isPresent();
        assertThat(RecommendJobStore.schemaVersion(mapper.readTree("1.5"))).isNull();
        assertThat(RecommendJobStore.schemaVersion(mapper.readTree("\"\""))).isNull();
        assertThat(RecommendJobStore.schemaVersion(null)).isNull();
    }
}
