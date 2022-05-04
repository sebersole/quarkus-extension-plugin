package io.github.sebersole.quarkus.extension;

import io.quarkus.deployment.annotations.BuildStep;
import org.hibernate.SessionFactory;
import org.hibernate.envers.AuditReader;
import org.hibernate.spatial.Spatial;

/**
 * @author Steve Ebersole
 */
public class MyExtensionProcessor {
	SessionFactory sf;
	AuditReader r;
	Spatial s;

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
