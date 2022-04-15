/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package io.github.sebersole.quarkus.tasks;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import io.github.sebersole.quarkus.Names;


/**
 * Task for creating Jandex Index within Gradle UP-TO-DATE handling
 *
 * @author Steve Ebersole
 */
@CacheableTask
public abstract class IndexerTask extends DefaultTask {
	private final IndexManager indexManager;

	@Inject
	public IndexerTask(IndexManager indexManager) {
		this.indexManager = indexManager;
		setGroup( Names.TASK_GROUP );
		setDescription( "Builds a Jandex Index from the `" + indexManager.getSourceSetToIndex().getName() + "` SourceSet" );
		dependsOn( indexManager.getSourceSetToIndex().getCompileJavaTaskName() );
	}

	@InputDirectory
	@PathSensitive( PathSensitivity.RELATIVE )
	@SkipWhenEmpty
	public Provider<Directory> getClassesToProcess() {
		return indexManager.getSourceSetToIndex().getJava().getDestinationDirectory();
	}

	@OutputFile
	public Provider<RegularFile> getIndexFileReference() {
		return indexManager.getIndexFileReferenceAccess();
	}

	@TaskAction
	public void createIndex() {
		indexManager.index();
	}
}
