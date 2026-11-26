package com.gdgoc.otp.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * DB가 필요한 통합 테스트의 부모 클래스.
 *
 * <p>무엇을 하나: 테스트가 시작될 때 진짜 PostgreSQL 서버를 임시 폴더에 띄우고,
 * 스프링의 datasource 설정(url, username, password)을 그 서버로 바꿔 끼운다.
 * 그래서 로컬에 PostgreSQL이나 Docker가 없어도 {@code ./gradlew test}가 돌아간다.</p>
 *
 * <p>H2 같은 메모리 DB 대신 진짜 PostgreSQL을 쓰는 이유: 우리 스키마는 jsonb, CHECK 정규식,
 * identity 컬럼 등 PostgreSQL 전용 문법을 쓴다. 다른 DB로 테스트하면 운영에서만 터지는 오류를 놓친다.</p>
 *
 * <p>서버는 static 필드라 테스트 JVM 하나당 한 번만 뜨고, 모든 테스트 클래스가 같이 쓴다.
 * 앱이 켜질 때 Flyway가 V1, V2 마이그레이션을 실행해 테이블과 시드 데이터를 만든다.</p>
 *
 * <p>사용법: 테스트 클래스가 이 클래스를 상속하기만 하면 된다.</p>
 */
@SpringBootTest
public abstract class IntegrationTestSupport {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException("테스트용 PostgreSQL을 띄우지 못했습니다.", e);
        }
    }

    /**
     * application.yml의 datasource 값을 테스트 서버 값으로 덮어쓴다.
     * 포트는 매번 비어 있는 번호로 정해지기 때문에 고정 설정 파일 대신 여기서 동적으로 넣는다.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }
}
