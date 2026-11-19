package com.tlavu.moodly.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class RenderBlueprintTest {

	private Map<?, ?> service() throws Exception {
		var options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Map<?, ?> blueprint = new Yaml(new SafeConstructor(options)).load(Files.readString(Path.of("render.yaml")));
		var services = (List<?>) blueprint.get("services");
		assertThat(services).hasSize(1);
		return (Map<?, ?>) services.getFirst();
	}

	@Test
	void usesAnImmutableImageWithoutSourceBuildOrAutoDeployConfiguration() throws Exception {
		var service = service();
		assertThat(service.get("runtime")).isEqualTo("image");
		assertThat(((Map<?, ?>) service.get("image")).get("url").toString())
				.matches("ghcr\\.io/tlavu2004/moodly-backend@sha256:[0-9a-f]{64}");
		for (String key : List.of("repo", "branch", "dockerfilePath", "buildCommand", "autoDeploy", "autoDeployTrigger")) {
			assertThat(service.containsKey(key)).isFalse();
		}
	}

	@Test
	void keepsProductionSecurityAndTimezoneConfigurationExplicit() throws Exception {
		var service = service();
		assertThat(service.get("healthCheckPath")).isEqualTo("/actuator/health");
		var values = new java.util.HashMap<String, Object>();
		for (Object entry : (List<?>) service.get("envVars")) {
			var variable = (Map<?, ?>) entry;
			if (variable.containsKey("key")) { values.put(variable.get("key").toString(), variable.get("value")); }
		}
		assertThat(values).containsEntry("SPRING_PROFILES_ACTIVE", "production")
				.containsEntry("TZ", "Asia/Ho_Chi_Minh")
				.containsEntry("SERVER_PORT", "8080")
				.containsEntry("MOODLY_E2E_RESET_ENABLED", "false");
	}
}
