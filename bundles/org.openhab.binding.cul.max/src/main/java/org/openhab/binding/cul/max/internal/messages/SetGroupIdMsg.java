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
import org.openhab.binding.cul.max.internal.messages.constants.MaxCulMsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SetGroupId Message
 *
 * @author Paul Hampson (cyclingengineer) - Initial contribution
 * @author Johannes Goehr (johgoe) - Migration to OpenHab 3.0
 * @since 1.6.0
 */
@NonNullByDefault
public class SetGroupIdMsg extends BaseMsg {

    private static final int SET_GROUP_ID_PAYLOAD_LEN = 1;

    private final Logger logger = LoggerFactory.getLogger(SetGroupIdMsg.class);

    private byte groupIdToSet;

    public SetGroupIdMsg(byte msgCount, byte msgFlag, String srcAddr, String dstAddr, byte groupIdToSet) {
        super(msgCount, msgFlag, MaxCulMsgType.SET_GROUP_ID, (byte) 0x0, srcAddr, dstAddr);

        this.groupIdToSet = groupIdToSet;
        byte[] payload = new byte[SET_GROUP_ID_PAYLOAD_LEN];

        payload[0] = groupIdToSet;

        super.appendPayload(payload);
    }

    public SetGroupIdMsg(String rawmsg) throws MaxCulProtocolException {
        super(rawmsg);
        groupIdToSet = this.payload[0];
    }

    /**
     * Print output as decoded fields
     */
    @Override
    protected void printFormattedPayload() {
        logger.debug("\t Group ID => {}", this.groupIdToSet);
    }
}
