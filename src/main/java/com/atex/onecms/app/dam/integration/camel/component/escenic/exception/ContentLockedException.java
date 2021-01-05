package com.atex.onecms.app.dam.integration.camel.component.escenic.exception;

/**
 *
 * @author jakub
 */
public class ContentLockedException extends EscenicException {
	public ContentLockedException(final String message) {
		super(message);
	}

	public ContentLockedException(final String format, final Object... args) {
		super(String.format(format, args));
	}
}
