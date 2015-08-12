package edu.stanford.bmir.protege.web.server.shortform;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.semanticweb.owlapi.model.HasLang;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.util.IRIShortFormProvider;
import org.semanticweb.owlapi.vocab.Namespaces;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.stanford.bmir.protege.web.shared.HasAnnotationAssertionAxioms;

/**
 * Matthew Horridge Stanford Center for Biomedical Informatics Research 30/01/15
 */
public class WebProtegeIRIShortFormProvider implements IRIShortFormProvider {

	private final HasAnnotationAssertionAxioms annotationAssertionAxiomProvider;

	private final HasLang languageProvider;

	private final ImmutableList<IRI> labellingIRIs;

	private final ImmutableMap<String, String> prefix2PrefixNameMap;

	@Inject
	public WebProtegeIRIShortFormProvider(ImmutableList<IRI> labellingIRIs, HasAnnotationAssertionAxioms annotationAssertionAxiomProvider, HasLang languageProvider) {

		this.labellingIRIs = checkNotNull(labellingIRIs);
		this.annotationAssertionAxiomProvider = checkNotNull(annotationAssertionAxiomProvider);
		this.languageProvider = checkNotNull(languageProvider);

		List<String> keys = new ArrayList<String>();
		
		ImmutableMap.Builder<String, String> prefixIRI2PrefixNameMapBuilder = ImmutableMap.builder();
		for (Namespaces ns : Namespaces.values()) {
			if (ns.isInUse()) {
				prefixIRI2PrefixNameMapBuilder.put(ns.getPrefixIRI(), ns.getPrefixName());
				keys.add(ns.getPrefixIRI());
			}
		}

//		// add custom prefixes
		try {
			Enumeration<String> en = ResourceBundle.getBundle("prefixes").getKeys();
			while (en.hasMoreElements()) {
				String e = en.nextElement();
				String prefixIRI = ResourceBundle.getBundle("prefixes").getString(e);
				boolean exists = false;
				for (Namespaces n : Namespaces.values()) {
					if (n.name().equals(prefixIRI)) {
						exists = true;
						break;
					}
				}
				if (!exists && !keys.contains(prefixIRI)) {
					prefixIRI2PrefixNameMapBuilder.put(prefixIRI, e);
				}
			}
		} catch (Exception e) {
			// e.printStackTrace();
		}

		prefix2PrefixNameMap = prefixIRI2PrefixNameMapBuilder.build();
	}

	private Optional<String> getBuiltInPrefix(IRI iri) {
		String iriNS = iri.getNamespace();
		if (prefix2PrefixNameMap.containsKey(iriNS)) {
			return Optional.of(iriNS);
		} else {
			return Optional.absent();
		}
	}

	@Override
	public String getShortForm(IRI iri) {
		Optional<String> builtInPrefix = getBuiltInPrefix(iri);
		if (builtInPrefix.isPresent()) {
			return prefix2PrefixNameMap.get(builtInPrefix.get()) + ":" + iri.getFragment();
		}
		int matchedIndex = Integer.MAX_VALUE;
		boolean matchedDefaultLang = false;
		OWLAnnotationValue renderingValue = null;
		// Just ask for the language once (bad coding!)
		final String defaultLanguage = languageProvider.getLang();
		for (OWLAnnotationAssertionAxiom ax : annotationAssertionAxiomProvider.getAnnotationAssertionAxioms(iri)) {
			// Think this is thread safe. The list is immutable and each indexOf
			// call creates a fresh iterator
			// object to find the index.
			int index = labellingIRIs.indexOf(ax.getProperty().getIRI());
			if (index <= matchedIndex && index > -1) {
				if (index < matchedIndex) {
					matchedIndex = index;
					renderingValue = ax.getValue();
				}
				if (index == matchedIndex || index == Integer.MAX_VALUE) {
					final OWLAnnotationValue value = ax.getValue();
					if (value instanceof OWLLiteral) {
						OWLLiteral litValue = (OWLLiteral) value;
						String lang = litValue.getLang();
						if (lang != null) {
							if (lang.equals(defaultLanguage)) {
								matchedDefaultLang = true;
								renderingValue = litValue;
							} else if (!matchedDefaultLang) {
								renderingValue = litValue;
							}
						}

					}
				}

			}
		}
		if (renderingValue instanceof OWLLiteral) {
			return ((OWLLiteral) renderingValue).getLiteral();
		} else {
			String result;
			String iriFragment = iri.getFragment();
			if (iriFragment != null) {
				result = iriFragment;
			} else {
				result = iri.toQuotedString();
			}
			try {
				return URLDecoder.decode(result, "UTF-8");
			} catch (IllegalArgumentException | UnsupportedEncodingException e) {
				return result;
			}
		}

	}
}
