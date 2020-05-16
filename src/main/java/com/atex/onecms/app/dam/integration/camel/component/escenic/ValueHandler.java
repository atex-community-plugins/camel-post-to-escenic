package com.atex.onecms.app.dam.integration.camel.component.escenic;

import org.apache.commons.lang.StringEscapeUtils;

import javax.xml.bind.ValidationEventHandler;
import javax.xml.bind.annotation.DomHandler;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

/**
 *
 * @author jakub
 */
public class ValueHandler implements DomHandler<String, StreamResult> {


		private static final String PARAMETERS_START_TAG = "<div";
//	private static final String PARAMETERS_START_TAG = "<div xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:vdf=\"http://www.vizrt.com/types\" xmlns:metadata=\"http://xmlns.escenic.com/2010/atom-metadata\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:app=\"http://www.w3.org/2007/app\">";

		private static final String PARAMETERS_END_TAG = "</div>";
		private StringWriter xmlWriter = new StringWriter();

		public StreamResult createUnmarshaller(ValidationEventHandler errorHandler) {
			xmlWriter.getBuffer().setLength(0);
			return new StreamResult(xmlWriter);
		}

		public String getElement(StreamResult rt) {
			String xml = rt.getWriter().toString();
			if (xml.indexOf("") > 0) {
				return "";
			}
			//possibly parse it into html elements? and use that instead of looking at text directly.
			//this way will avoid issue of </link> vs />

			if (xml.contains("<div") && xml.contains("</div>")) {
				int beginIndex = xml.indexOf(PARAMETERS_START_TAG);
				int endIndex = xml.indexOf(PARAMETERS_END_TAG) + PARAMETERS_END_TAG.length();
				String r =  xml.substring(beginIndex, endIndex);
				return r;
			} else if (xml.contains("<link")) {
				int beginIndex = xml.indexOf("<link");
				int endIndex = xml.indexOf("/>") + "/>".length();
				String r =  xml.substring(beginIndex, endIndex);
				return r;
			} else {
				return xml;
			}


		}

		public Source marshal(String n, ValidationEventHandler errorHandler) {
			try {
				if (n.startsWith("&lt;div")) {
					String s = StringEscapeUtils.unescapeHtml(n);
					StringReader xmlReader = new StringReader(s);
					return new StreamSource(xmlReader);
				}
//				"<![CDATA[" + test + "]]>";

//				String xml = PARAMETERS_START_TAG + n.trim() + PARAMETERS_END_TAG;
					StringReader xmlReader = new StringReader("<div>" + n +"</div>");
					return new StreamSource(xmlReader);

			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}

}

