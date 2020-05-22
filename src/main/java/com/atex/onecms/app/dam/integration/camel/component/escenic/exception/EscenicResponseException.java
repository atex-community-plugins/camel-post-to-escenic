package com.atex.onecms.app.dam.integration.camel.component.escenic.exception;

public class EscenicResponseException  extends EscenicException {
	public EscenicResponseException(final String message) {
		super(message);
	}

	public EscenicResponseException(final String format, final Object... args) {
		super(String.format(format, args));
	}
}
