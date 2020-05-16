package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Field;
import org.apache.commons.lang.StringEscapeUtils;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.util.List;

/**
 *
 * @author jakub
 */
public class HrefAdapter extends XmlAdapter<String, String> {

		@Override
		public String unmarshal(String s) throws Exception {
			return s;
		}

		@Override
		public String marshal(String value) throws Exception {
			if (value == null) {
				return "";
			} else {
				String updatedValue = StringEscapeUtils.escapeHtml(value);

				return updatedValue;
			}
		}
	}

