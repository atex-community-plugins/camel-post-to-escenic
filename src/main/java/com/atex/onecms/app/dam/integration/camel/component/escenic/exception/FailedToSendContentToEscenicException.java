package com.atex.onecms.app.dam.integration.camel.component.escenic.exception;

public class FailedToSendContentToEscenicException extends EscenicException {
	public FailedToSendContentToEscenicException(final String message) {
		super(message);
	}

	public FailedToSendContentToEscenicException(final String format, final Object... args) {
		super(String.format(format, args));
	}
}
