package io.github.sebersole.quarkus;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.gradle.api.Project;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author Steve Ebersole
 */
public class PomAdjuster {
	static void applyDependency(File pomFile, String artifactId, Project project) {
		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			final DocumentBuilder db = dbf.newDocumentBuilder();
			final Document document = db.parse( pomFile );

			final Node dependenciesNode = locateDependenciesNode( document );
			final Element dependencyNode = document.createElement( "dependency" );
			dependenciesNode.appendChild( dependencyNode );

			final Element groupIdNode = document.createElement( "groupId" );
			groupIdNode.setTextContent( project.getGroup().toString() );
			dependencyNode.appendChild( groupIdNode );

			final Element artifactIdNode = document.createElement( "artifactId" );
			artifactIdNode.setTextContent( artifactId );
			dependencyNode.appendChild( artifactIdNode );
			dependencyNode.appendChild( groupIdNode );

			final Element versionNode = document.createElement( "version" );
			versionNode.setTextContent( project.getVersion().toString() );
			dependencyNode.appendChild( versionNode );

			final DOMSource source = new DOMSource( document );
			final FileWriter writer = new FileWriter( pomFile );
			final StreamResult result = new StreamResult( writer );

			final Transformer transformer = createTransformer();
			try {
				transformer.transform( source, result );
			}
			catch (TransformerException e) {
				throw new RuntimeException( "Unable to write XML to file", e );
			}
		}
		catch (ParserConfigurationException | SAXException | IOException e) {
			throw new RuntimeException( "Unable to parse XML", e );
		}
	}

	static Node locateDependenciesNode(Document document) {
		final Element documentElement = document.getDocumentElement();

		Node child = documentElement.getFirstChild();
		while ( child != null ) {
			if ( "dependencies".equals( child.getNodeName() ) ) {
				return child;
			}
			child = child.getNextSibling();
		}

		final Element createdNode = document.createElement( "dependencies" );
		documentElement.appendChild( createdNode );

		return createdNode;
	}

	static Transformer createTransformer() {
		try {
			final Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
			transformer.setOutputProperty( "{http://xml.apache.org/xslt}indent-amount", "4" );
			return transformer;
		}
		catch (TransformerConfigurationException e) {
			throw new RuntimeException( "Unable to create XML transformer", e );
		}
	}
}
