package io.github.sebersole.quarkus.extension;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

/**
 * @author Steve Ebersole
 */
@ConfigRoot(name = "my-extension", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class MyExtensionConfig {

	public MyExtensionConfig(MyExtensionSpi spi) {
	}

	@ConfigItem
	public String name;
}
