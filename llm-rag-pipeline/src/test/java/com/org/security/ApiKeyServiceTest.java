package com.org.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApiKeyService service = new ApiKeyService(jdbc);

    @Test
    void blankOrNullKeyIsInvalid() {
        assertThat(service.isValid(null)).isFalse();
        assertThat(service.isValid("   ")).isFalse();
    }

    @Test
    void validKeyReturnsTrue() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        assertThat(service.isValid("raw-key")).isTrue();
    }

    @Test
    void unknownKeyReturnsFalse() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        assertThat(service.isValid("nope")).isFalse();
    }

    @Test
    void dbErrorDegradesToInvalidWithoutThrowing() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString()))
                .thenThrow(new DataAccessResourceFailureException("down"));
        assertThat(service.isValid("raw-key")).isFalse();
    }

    @Test
    void queriesBySha256HashNeverRawKey() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1);
        service.isValid("raw-key");

        ArgumentCaptor<String> arg = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(anyString(), eq(Integer.class), arg.capture());
        assertThat(arg.getValue())
                .isEqualTo(ApiKeyService.sha256("raw-key"))
                .doesNotContain("raw-key")
                .hasSize(64);
    }
}
