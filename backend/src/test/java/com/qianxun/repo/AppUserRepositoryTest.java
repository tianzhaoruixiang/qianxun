package com.qianxun.repo;

import com.qianxun.config.QianxunProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AppUserRepository repository;

    @BeforeEach
    void setUp() {
        QianxunProperties properties = new QianxunProperties();
        properties.setDb("qx");
        repository = new AppUserRepository(jdbcTemplate, properties);
    }

    @Test
    void nextNumericUserId_whenNoNumericRows_shouldReturn2() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        assertThat(repository.nextNumericUserId()).isEqualTo("2");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("MAX(CAST(`id` AS UNSIGNED))");
        assertThat(sql.getValue()).contains("REGEXP");
        assertThat(sql.getValue()).contains("`qx`.`app_user`");
    }

    @Test
    void nextNumericUserId_whenMaxIs3_shouldReturn4() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);

        assertThat(repository.nextNumericUserId()).isEqualTo("4");
    }

    @Test
    void nextNumericUserId_whenMaxIs1_shouldReturn2() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);

        assertThat(repository.nextNumericUserId()).isEqualTo("2");
    }
}
