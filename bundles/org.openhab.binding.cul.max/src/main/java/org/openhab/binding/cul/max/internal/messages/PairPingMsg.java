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

import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Message class to handle Ping message in pairing
 *
 * @author Paul Hampson (cyclingengineer) - Initial contribution
 * @author Johannes Goehr (johgoe) - Migration to OpenHab 3.0
 * @since 1.6.0
 */
@NonNullByDefault
public class PairPingMsg extends BaseMsg {

    public int firmwareMajor;
    public int firmwareMinor;
    public int type;
    public int testResult;
    public String serial;

    private final Logger logger = LoggerFactory.getLogger(PairPingMsg.class);

    public PairPingMsg(String rawMsg) throws MaxCulProtocolException {
        super(rawMsg);

        /* process payload */
        if (this.payload.length > 3) {
            this.firmwareMajor = this.payload[0] / 16;
            this.firmwareMinor = this.payload[0] % 16;
            this.type = this.payload[1];
            this.testResult = this.payload[2];
            this.serial = new String(Arrays.copyOfRange(this.payload, 3, this.payload.length));
        } else {
            throw new MaxCulProtocolException("PairPing Message was to short. Length " + this.payload.length);
        }
    }

    @Override
    protected void printFormattedPayload() {
        logger.debug("\tFirmware Version => {}", this.firmwareMajor + "." + this.firmwareMinor);
        logger.debug("\tDevice Type      => {}", this.type);
        logger.debug("\tTest Result      => {}", this.testResult);
        logger.debug("\tSerial Number    => {}", this.serial);
    }
}
