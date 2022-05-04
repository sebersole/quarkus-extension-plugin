package io.github.sebersole.quarkus.extension;

import org.hibernate.SessionFactory;
import org.hibernate.envers.AuditReader;
import org.hibernate.spatial.Spatial;
import org.junit.jupiter.api.Test;

/**
 * @author Steve Ebersole
 */
public class MyExtensionTest {
	@Test
	public void testIt() {
		// just make sure we can reference stuff from both the other artifacts
		MyExtensionConfig config;
		MyExtensionProcessor processor;
		MyExtensionSpi spi;
		SessionFactory sf;
		AuditReader r;
		Spatial s;
	}
}
