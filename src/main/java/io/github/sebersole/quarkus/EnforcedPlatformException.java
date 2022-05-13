package io.github.sebersole.quarkus;

import java.util.Locale;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

/**
 * Indicates the extension tried to apply an enforced platform, which is not allowed.
 * Use a non-enforced platform instead.
 *
 * @see org.gradle.api.artifacts.dsl.DependencyHandler#enforcedPlatform
 * @see org.gradle.api.artifacts.dsl.DependencyHandler#platform
 *
 * @apiNote The `maven-publish` plugin also does this check, but the check here
 * happens immediately rather than waiting until trying to publish.
 *
 * @author Steve Ebersole
 */
public class EnforcedPlatformException extends RuntimeException {
	public EnforcedPlatformException(Dependency dependency, Configuration configuration) {
		super(
				String.format(
						Locale.ROOT,
						"Dependency `%s` was defined as an enforced-platform as part of the `%s` Configuration",
						dependency,
						configuration.getName()
				)
		);
	}
}
