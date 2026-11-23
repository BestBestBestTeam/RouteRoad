package com.routeroad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationProfilesTests {

	private static final String DATA_SOURCE_AUTO_CONFIGURATION =
			"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer());

	@Test
	void dataSourceExclusionIsOnlyEnabledForContextSmokeProfile() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			List<String> exclusions = Binder.get(context.getEnvironment())
					.bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
					.orElse(List.of());
			assertThat(exclusions).doesNotContain(DATA_SOURCE_AUTO_CONFIGURATION);
		});
		contextRunner.withPropertyValues("spring.profiles.active=context-smoke").run(context -> {
			assertThat(context).hasNotFailed();
			List<String> exclusions = Binder.get(context.getEnvironment())
					.bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
					.orElse(List.of());
			assertThat(exclusions).contains(DATA_SOURCE_AUTO_CONFIGURATION);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"local", "test", "local,test", "local,context-smoke", "test,context-smoke", "local,test,context-smoke"})
	void databaseProfilesDoNotInheritBootstrapExclusion(String profiles) {
		contextRunner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
			assertThat(context).hasNotFailed();
			var environment = context.getEnvironment();
			List<String> exclusions = Binder.get(environment)
					.bind("spring.autoconfigure.exclude", Bindable.listOf(String.class))
					.orElse(List.of());
			assertThat(exclusions).doesNotContain(DATA_SOURCE_AUTO_CONFIGURATION);
			assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
			assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
			assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
		});
	}
}
