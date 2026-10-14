package com.ebac.streaming.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real PostgreSQL (PostgreSQL-only migrations such as the partial
 * unique index, or end-to-end flows). One container is started per test JVM and shared by every
 * subclass; Testcontainers' Ryuk removes it when the JVM exits.
 */
public abstract class PostgresTestSupport {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
