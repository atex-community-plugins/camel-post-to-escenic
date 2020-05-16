package com.atex.onecms.app.dam.integration.camel.component.escenic.exception;

/**
 *
 * @author jakub
 */
public class FailedToDeserializeContentException extends EscenicException {
	public FailedToDeserializeContentException(final String message) {
		super(message);
	}

	public FailedToDeserializeContentException(final String format, final Object... args) {
		super(String.format(format, args));
	}
}
