package com.atex.onecms.app.dam.integration.camel.component.escenic.exception;

public class FailedToProcessSectionIdException  extends EscenicException {
	public FailedToProcessSectionIdException(final String message) {
		super(message);
	}

	public FailedToProcessSectionIdException(final String format, final Object... args) {
		super(String.format(format, args));
	}
}
