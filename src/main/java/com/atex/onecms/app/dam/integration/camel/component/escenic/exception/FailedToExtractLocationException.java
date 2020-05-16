package com.atex.onecms.app.dam.integration.camel.component.escenic.exception;

/**
 *
 * @author jakub
 */
public class FailedToExtractLocationException extends EscenicException {
	public FailedToExtractLocationException(final String message) {
		super(message);
	}

	public FailedToExtractLocationException(final String format, final Object... args) {
		super(String.format(format, args));
	}

}
