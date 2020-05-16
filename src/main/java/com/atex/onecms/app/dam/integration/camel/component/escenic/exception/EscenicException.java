package com.atex.onecms.app.dam.integration.camel.component.escenic.exception;

/**
 *
 * @author jakub
 */
public class EscenicException extends Exception  {
	public EscenicException(final String message) {
		super(message);
	}

	public EscenicException(final String format, final Object... args) {
		super(String.format(format, args));
	}
}
