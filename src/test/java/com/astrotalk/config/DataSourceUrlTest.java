package com.astrotalk.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataSourceUrlTest {
    @Test
    void railwayPrivateUrlBecomesJdbcWithoutSsl() {
        DataSourceUrl.Target target = DataSourceUrl.toJdbc("postgresql://postgres:secret@postgres.railway.internal:5432/railway");
        assertEquals("jdbc:postgresql://postgres.railway.internal:5432/railway", target.url());
        assertEquals("postgres", target.username());
        assertEquals("secret", target.password());
    }

    @Test
    void publicUrlRequiresSsl() {
        DataSourceUrl.Target target = DataSourceUrl.toJdbc("postgresql://postgres:secret@example.com:5432/railway");
        assertEquals("jdbc:postgresql://example.com:5432/railway?sslmode=require", target.url());
    }

    @Test
    void jdbcUrlIsLeftAsGiven() {
        DataSourceUrl.Target target = DataSourceUrl.toJdbc("jdbc:postgresql://localhost:5435/astrotalk");
        assertEquals("jdbc:postgresql://localhost:5435/astrotalk", target.url());
        assertNull(target.username());
    }
}
