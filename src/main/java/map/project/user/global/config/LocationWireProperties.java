package map.project.user.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LocationWireProperties — 서비스 사이로 나가는 좌표를 감싸는 설정
 *
 * 저장할 때 쓰는 열쇠와 따로 둔다. 쓰임이 다르고 나눠 가지는 상대도 다르기
 * 때문이다. 저장 열쇠는 이 서비스 혼자 쓰지만, 이 열쇠는 좌표를 받아 실제로
 * 써야 하는 서비스들이 함께 가진다. 하나로 합치면 그중 한 서비스가 뚫렸을 때
 * 저장된 것까지 함께 열린다.
 *
 * 좌표는 늘 봉투로만 오간다. 평문으로 보내거나 받는 길은 없다.
 *
 * key: 32바이트로 풀리는 base64. 없거나 길이가 틀리면 부팅을 멈춘다.
 *      좌표를 주고받는 hub·agent 와 같은 값이어야 한다.
 * max-age-seconds: 열 때 보는 봉투 나이 상한(초). 이 길로 오는 값은 스트림에
 *          쌓였다가 뒤늦게 처리될 수 있어 보내는 쪽 만료보다 느슨하게 둔다.
 *          0 이면 나이를 보지 않는다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "location.wire")
public class LocationWireProperties {

    private String key = "";

    private long maxAgeSeconds = 3600;
}
