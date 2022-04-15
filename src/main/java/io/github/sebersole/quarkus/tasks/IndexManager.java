/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package io.github.sebersole.quarkus.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

/**
 * Encapsulates and manages a Jandex Index
 *
 * @author Steve Ebersole
 */
public class IndexManager {
	private final Project project;
	private final SourceSet sourceSetToIndex;
	private final Provider<RegularFile> indexFileReferenceAccess;

	private Index index;

	public IndexManager(SourceSet sourceSetToIndex, Project project) {
		this.sourceSetToIndex = sourceSetToIndex;
		this.indexFileReferenceAccess = project.getLayout()
				.getBuildDirectory()
				.file( "quarkus/jandex/" + sourceSetToIndex.getName() + ".idx" );
		this.project = project;
	}

	public SourceSet getSourceSetToIndex() {
		return sourceSetToIndex;
	}

	public Provider<RegularFile> getIndexFileReferenceAccess() {
		return indexFileReferenceAccess;
	}

	public Index getIndex() {
		if ( index == null ) {
			index = loadIndex( indexFileReferenceAccess );
		}
		return index;
	}

	private static Index loadIndex(Provider<RegularFile> indexFileReferenceAccess) {
		final File indexFile = indexFileReferenceAccess.get().getAsFile();
		if ( !indexFile.exists() ) {
			throw new IllegalStateException( "Cannot load index; the stored file does not exist - " + indexFile.getAbsolutePath() );
		}

		try ( final FileInputStream stream = new FileInputStream( indexFile ) ) {
			final IndexReader indexReader = new IndexReader( stream );
			return indexReader.read();
		}
		catch (FileNotFoundException e) {
			throw new IllegalStateException( "Cannot load index; the stored file does not exist - " + indexFile.getAbsolutePath(), e );
		}
		catch (IOException e) {
			throw new IllegalStateException( "Cannot load index; unable to read stored file - " + indexFile.getAbsolutePath(), e );
		}
	}


	/**
	 * Used from {@link IndexerTask} as its action
	 */
	void index() {
		if ( index != null ) {
			throw new IllegalStateException( "Index was already created or loaded" );
		}

		final Indexer indexer = new Indexer();

		try {
			final File classesDir = sourceSetToIndex.getJava().getDestinationDirectory().get().getAsFile();
			Files.walk( classesDir.toPath() ).forEach( (item) -> {
				if ( item.toString().endsWith( ".class" ) ) {
					indexItem( indexer, item );
				}
			} );
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to index source-set : " + sourceSetToIndex.getName(), e );
		}

		this.index = indexer.complete();
		storeIndex();
	}

	private void indexItem(Indexer indexer, Path classItem) {
		final File classFile = classItem.toFile();

		try ( final FileInputStream stream = new FileInputStream( classFile ) ) {
			final ClassInfo indexedClassInfo = indexer.index( stream );
			if ( indexedClassInfo == null ) {
				project.getLogger().warn( "Problem indexing class file - {}", classFile.getAbsolutePath() );
			}
		}
		catch (FileNotFoundException e) {
			throw new RuntimeException( "Problem locating project class file - " + classFile.getAbsolutePath(), e );
		}
		catch (IOException e) {
			throw new RuntimeException( "Error accessing project class file - " + classFile.getAbsolutePath(), e );
		}
	}

	private void storeIndex() {
		final File indexFile = prepareOutputFile( indexFileReferenceAccess );

		try ( final FileOutputStream stream = new FileOutputStream( indexFile ) ) {
			final IndexWriter indexWriter = new IndexWriter( stream );
			indexWriter.write( index );
		}
		catch (FileNotFoundException e) {
			throw new RuntimeException( "Should never happen", e );
		}
		catch (IOException e) {
			throw new RuntimeException( "Error accessing index file - " + indexFile.getAbsolutePath(), e );
		}
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private File prepareOutputFile(Provider<RegularFile> outputFileReferenceAccess) {
		final File outputFile = outputFileReferenceAccess.get().getAsFile();
		if ( outputFile.exists() ) {
			outputFile.delete();
		}

		try {
			outputFile.getParentFile().mkdirs();
			outputFile.createNewFile();
		}
		catch (IOException e) {
			throw new RuntimeException( "Unable to create index file - " + outputFile.getAbsolutePath(), e );
		}

		return outputFile;
	}
}
