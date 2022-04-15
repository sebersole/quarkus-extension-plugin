package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

import io.github.sebersole.quarkus.Names;

/**
 * @author Steve Ebersole
 */
@CacheableTask
public abstract class GenerateBuildStepsList extends DefaultTask {
	private static final DotName BUILD_STEP_ANN = DotName.createSimple( "io.quarkus.deployment.annotations.BuildStep" );

	public static final String TASK_NAME = "generateBuildStepsList";

	private final IndexManager indexManager;
	private final RegularFileProperty listFile;

	@Inject
	public GenerateBuildStepsList(IndexManager indexManager) {
		this.indexManager = indexManager;

		setGroup( Names.QUARKUS_GROUP );
		setDescription( "Generates the `quarkus-build-steps.list` file ultimately bundled into the extension deployment artifact" );

		listFile = getProject().getObjects().fileProperty();
		listFile.convention( getProject().getLayout().getBuildDirectory().file( "quarkus/quarkus-build-steps.list" ) );
	}

	@InputFile
	@PathSensitive( PathSensitivity.RELATIVE )
	public Provider<RegularFile> getIndexFileReference() {
		return indexManager.getIndexFileReferenceAccess();
	}

	@OutputFile
	public RegularFileProperty getListFileReference() {
		return listFile;
	}

	@TaskAction
	public void generateList() {
		final File listFileAsFile = listFile.get().getAsFile();
		prepareListFile( listFileAsFile );

		try ( FileWriter writer = new FileWriter( listFileAsFile, StandardCharsets.UTF_8, false ) ) {
			writeEntries( writer, indexManager.getIndex() );
			writer.flush();
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to open write for `quarkus-build-steps.list` file", e );
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static void prepareListFile(File listFile) {
		listFile.getParentFile().mkdirs();
	}

	private void writeEntries(FileWriter writer, Index index) {
		final List<AnnotationInstance> buildStepAnnUsages = index.getAnnotations( BUILD_STEP_ANN );
		final Set<DotName> stepClassNames = new HashSet<>();

		buildStepAnnUsages.forEach( (usage) -> {
			try {
				final ClassInfo declaringClass = usage.target().asMethod().declaringClass();
				if ( stepClassNames.add( declaringClass.name() ) ) {
					writer.write( declaringClass.name().toString() );
					writer.write( Character.LINE_SEPARATOR );
				}
			}
			catch (IOException e) {
				throw new RuntimeException( "Error writing to `quarkus-build-steps.list` file", e );
			}
		} );
	}
}
