package com.atex.onecms.app.dam.integration.camel.component.escenic;
import java.io.IOException;
import java.io.Writer;
import com.sun.xml.bind.marshaller.CharacterEscapeHandler;

/**
 * CharacterEscapeHandler that does not perform any escaping
 *
 * @author jakub
 */
public class CustomCharacterEscapeHandler  implements CharacterEscapeHandler {

	public CustomCharacterEscapeHandler() {
		super();
	}

	public void escape(char[] buf, int start, int len, boolean isAttValue,
					   Writer out) throws IOException {

		for (int i = start; i < start + len; i++) {
			char ch = buf[i];
			out.write(ch);
		}
	}
}


