package io.github.sebersole.quarkus.extension;

import io.quarkus.deployment.annotations.BuildStep;

/**
 * @author Steve Ebersole
 */
public class MyExtensionProcessor {

	@BuildStep
	public void step1() {
		// make sure the runtime artifact is available for compile
		MyExtensionConfig cfg;
	}

	@BuildStep
	public void step2() {
		// make sure the runtime artifact is available for compile
		MyExtensionConfig cfg;
	}
}
