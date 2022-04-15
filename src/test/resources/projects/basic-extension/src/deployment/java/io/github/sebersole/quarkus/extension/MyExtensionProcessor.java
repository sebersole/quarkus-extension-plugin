package io.github.sebersole.quarkus.extension;

import io.quarkus.deployment.annotations.BuildStep;

/**
 * @author Steve Ebersole
 */
public class MyExtensionProcessor {

	@BuildStep
	public void config() {
		// make sure the runtime artifact is available for compile
		MyExtensionConfig cfg;
	}
}
