package com.atex.onecms.app.dam.integration.camel.component.escenic;

import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Entry;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Field;
import com.atex.onecms.app.dam.integration.camel.component.escenic.model.Value;
import org.apache.commons.lang.StringEscapeUtils;

import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author jakub
 */
public class ValueAdapter extends XmlAdapter<String, Object> {
	@Override

	public Object unmarshal(String s) throws Exception {
		return s;
	}

	@Override
	public String marshal(Object value) throws Exception {
		if (value == null) {
			return "";
		} else {

			if (value instanceof String) {
				return null;
			} else if (value instanceof List) {
				List list = ((List) value);
				if (list.isEmpty()) {
					return "";
				} else {
					String v = list.get(0).toString();
//					v = StringEscapeUtils.unescapeHtml(v);
					System.out.println("returning");
					return v;
				}


			} else if (value instanceof Field) {
				return ((Field) value).getValue().toString();
			}
			return "";
		}
	}
}