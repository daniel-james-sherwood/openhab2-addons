/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.cul.max.internal.messages;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * An exception which is thrown if a protocol vialotion for MAX! CUL is detected.
 *
 * @author Johannes Goehr (johgoe) - Initial contribution
 */
@NonNullByDefault
public class MaxCulProtocolException extends Exception {

    private static final long serialVersionUID = -621825137295549173L;

    public MaxCulProtocolException() {
        super();
    }

    public MaxCulProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public MaxCulProtocolException(String message) {
        super(message);
    }

    public MaxCulProtocolException(Throwable cause) {
        super(cause);
    }
}
