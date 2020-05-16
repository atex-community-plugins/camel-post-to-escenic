package com.atex.onecms.app.dam.integration.camel.component.escenic.exception;

/**
 *
 * @author jakub
 */
public class FailedToRetrieveEscenicContentException extends EscenicException {
	public FailedToRetrieveEscenicContentException(final String message) {
		super(message);
	}

	public FailedToRetrieveEscenicContentException(final String format, final Object... args) {
		super(String.format(format, args));
	}
}
