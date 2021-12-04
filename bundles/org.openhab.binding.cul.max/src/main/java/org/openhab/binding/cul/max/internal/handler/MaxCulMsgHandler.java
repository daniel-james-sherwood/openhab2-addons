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
package org.openhab.binding.cul.max.internal.handler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.cul.CULCommunicationException;
import org.openhab.binding.cul.CULListener;
import org.openhab.binding.cul.max.internal.message.sequencers.MessageSequencer;
import org.openhab.binding.cul.max.internal.messages.*;
import org.openhab.binding.cul.max.internal.messages.constants.MaxCulBoostDuration;
import org.openhab.binding.cul.max.internal.messages.constants.MaxCulDevice;
import org.openhab.binding.cul.max.internal.messages.constants.MaxCulMsgType;
import org.openhab.binding.cul.max.internal.messages.constants.MaxCulWeekdays;
import org.openhab.binding.cul.max.internal.messages.constants.ThermostatControlMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle messages going to and from the CUL device. Make sure to intercept
 * control command responses first before passing on valid MAX! messages to the
 * binding itself for processing.
 *
 * @author Paul Hampson (cyclingengineer) - Initial contribution
 * @author Johannes Goehr (johgoe) - Migration to OpenHab 3.0
 * @since 1.6.0
 */
@NonNullByDefault
public class MaxCulMsgHandler implements CULListener {

    private final Logger logger = LoggerFactory.getLogger(MaxCulMsgHandler.class);

    private int msgCount = 0;
    private MaxCulCunBridgeHandler cul;
    private String srcAddr;
    private LinkedList<BaseMsg> sendQueue;
    private @Nullable BaseMsg sendItem = null;
    public String lastItemDstAddrStr = "";
    private int sendRetry = 0;
    private @Nullable Timer sendAckTimer = null;

    private final Set<String> rfAddressesToSpyOn;

    private boolean listenMode = false;

    public static final int MESSAGE_EXPIRY_PERIOD_FAST = 500;
    public static final int MESSAGE_EXPIRY_PERIOD_SLOw = 1500;
    public static final int MESSAGE_ACK_PERIOD = 100;
    public static final int MESSAGE_RETRY_COUNT = 4;

    public MaxCulMsgHandler(String srcAddr, Set<String> rfAddressesToSpyOn, MaxCulCunBridgeHandler cul) {
        this.srcAddr = srcAddr;
        this.sendQueue = new LinkedList<BaseMsg>();
        this.rfAddressesToSpyOn = rfAddressesToSpyOn;
        this.cul = cul;
    }

    private boolean disposed = false;
    public void dispose() {
        disposed = true;
    }

    private byte getMessageCount() {
        this.msgCount += 1;
        this.msgCount &= 0xFF;
        return (byte) this.msgCount;
    }

    private enum SendMsgResult {
        ACK_SENT,
        ACK_RECEIVED,
        NACK_RECEIVED,
        ACK_TIMEOUT,
    };

    private boolean enoughCredit(int requiredCredit, boolean fastSend) {
        int availableCredit = getCreditStatus();
        int preambleCredit = fastSend ? 0 : 100;
        boolean result = (availableCredit >= (requiredCredit + preambleCredit));
        logger.debug("Fast Send? {}, preambleCredit = {}, requiredCredit = {}, availableCredit = {}, enoughCredit? {}",
                fastSend, preambleCredit, requiredCredit, availableCredit, result);
        return result;
    }

    private int getCreditStatus() {
        return cul.getCredit10ms();
    }

    private synchronized void transmitMessage(@Nullable BaseMsg data) {
        if (disposed) {
            return;
        }
        boolean fastSend = lastItemDstAddrStr.equals(data.dstAddrStr);
        logger.trace("transmitMessage(): Sending {} message to {} in {} mode attempt {}", data.msgType, data.dstAddrStr, fastSend ? "fast" : "slow", sendRetry+1);
        try {
            String rawMsg = fastSend ? data.rawMsg.replace("Zs", "Zf") : data.rawMsg.replace("Zf", "Zs");
            cul.send(rawMsg);
        } catch (CULCommunicationException e) {
            logger.error("transmitMessage(): Sending {} message to {} failed: {}", data.msgType, data.dstAddrStr, e.getMessage());
        }

        if (data.msgType != MaxCulMsgType.ACK) {
            /* awaiting ack now */
            if (sendAckTimer != null) {
                sendAckTimer.cancel();
                sendAckTimer = null;
            }
            sendAckTimer = new Timer();
            sendAckTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    /* retry expired message - or abort */
                    handleMessageComplete(SendMsgResult.ACK_TIMEOUT, null);
                }
            }, fastSend ? MESSAGE_EXPIRY_PERIOD_FAST : MESSAGE_EXPIRY_PERIOD_SLOw);
        } else {
            if (sendAckTimer != null) {
                sendAckTimer.cancel();
                sendAckTimer = null;
            }
            sendAckTimer = new Timer();
            sendAckTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    /* process next message after Ack */
                    handleMessageComplete(SendMsgResult.ACK_SENT, null);
                }
            }, MESSAGE_ACK_PERIOD);
        }
    }

    private boolean runSeq = false;
    private synchronized boolean handleMessageComplete(SendMsgResult result, @Nullable BaseMsg msg) {
        if (sendAckTimer != null) {
            sendAckTimer.cancel();
            sendAckTimer = null;
        }
        if (disposed) {
            return false;
        }

        boolean passToBinding = true;
        lastItemDstAddrStr = msg == null ? "" : msg.srcAddrStr;
        
        if (sendItem.isPartOfSequence()) {
            BaseMsg thisItem = sendItem;
            passToBinding = false;
            sendItem = null;
            runSeq = true;
            if (msg != null) {
                logger.trace("handleMessageComplete(): Sending {} message to {} complete, CONTINUE SEQUENCE: {}", thisItem.msgType, thisItem.dstAddrStr, result);
                thisItem.getMessageSequencer().runSequencer(msg);
            } else {
                logger.error("handleMessageComplete(): Sending {} message to {} failed, RETRY SEQUENCE: {}", thisItem.msgType, thisItem.dstAddrStr, result);
                thisItem.getMessageSequencer().packetLost(thisItem);
            }
            runSeq = false;
        } else {
            if (result != SendMsgResult.NACK_RECEIVED && result != SendMsgResult.ACK_TIMEOUT ) {
                logger.trace("handleMessageComplete(): Sending {} message to {} complete: {}", sendItem.msgType, sendItem.dstAddrStr, result);
            } else {
                if (sendRetry < MESSAGE_RETRY_COUNT) {
                    sendRetry++;
                    logger.error("handleMessageComplete(): Sending {} message to {} failed attempt {}, RETRY: {}", sendItem.msgType, sendItem.dstAddrStr, sendRetry, result);
                    transmitMessage(sendItem);
                    return passToBinding;
                }
                logger.error("handleMessageComplete(): Sending {} message to {} failed attempt {}, ABORT: {}", sendItem.msgType, sendItem.dstAddrStr, sendRetry, result);
            }
            sendItem = null;
        }

        kickSendQueue();
        return passToBinding;
    }

    private synchronized void kickSendQueue() {
        if (disposed) {
            return;
        }

        if (sendItem != null) {
            logger.trace("kickSendQueue(): Sending {} message to {} in progress: {} items in queue", sendItem.msgType, sendItem.dstAddrStr, sendQueue.size());
            return;
        }

        if (sendQueue.isEmpty()) {
            lastItemDstAddrStr = "";
            logger.trace("kickSendQueue(): Queue empty");
            return;
        }

        sendItem = sendQueue.remove();
        sendRetry = 0;
        logger.trace("kickSendQueue(): Sending {} message to {} start: {} items in queue", sendItem.msgType, sendItem.dstAddrStr, sendQueue.size());
        transmitMessage(sendItem);
    }

    /**
     * Send a raw Base Message
     *
     * @param msg Base message to send
     * @param queueItem queue item (used for retransmission)
     */
    public synchronized void sendMessage(BaseMsg msg) {
        if (disposed) {
            return;
        }

        if (msg.msgType != MaxCulMsgType.ACK && !runSeq) {
            sendQueue.add(msg);
            logger.trace("sendMessage(): Adding {} message to {} to tail of queue: {} items in queue", msg.msgType, msg.dstAddrStr, sendQueue.size());
        } else {
            sendQueue.addFirst(msg);
            logger.trace("sendMessage(): Adding {} message to {} to head of queue: {} items in queue", msg.msgType, msg.dstAddrStr, sendQueue.size());
        }
        if (!runSeq) {
            kickSendQueue();
        }
    }
    
    private void listenModeHandler(String data) {
        try {
            switch (BaseMsg.getMsgType(data)) {
                case WALL_THERMOSTAT_CONTROL:
                    new WallThermostatControlMsg(data).printMessage();
                    break;
                case TIME_INFO:
                    new TimeInfoMsg(data).printMessage();
                    break;
                case SET_TEMPERATURE:
                    new SetTemperatureMsg(data).printMessage();
                    break;
                case ACK:
                    new AckMsg(data).printMessage();
                    break;
                case PAIR_PING:
                    new PairPingMsg(data).printMessage();
                    break;
                case PAIR_PONG:
                    new PairPongMsg(data).printMessage();
                    break;
                case THERMOSTAT_STATE:
                    new ThermostatStateMsg(data).printMessage();
                    break;
                case SET_GROUP_ID:
                    new SetGroupIdMsg(data).printMessage();
                    break;
                case WAKEUP:
                    new WakeupMsg(data).printMessage();
                    break;
                case WALL_THERMOSTAT_STATE:
                    new WallThermostatStateMsg(data).printMessage();
                    break;
                case PUSH_BUTTON_STATE:
                    new PushButtonMsg(data).printMessage();
                    break;
                case SHUTTER_CONTACT_STATE:
                    new ShutterContactStateMsg(data).printMessage();
                    break;
                case ADD_LINK_PARTNER:
                case CONFIG_TEMPERATURES:
                case CONFIG_VALVE:
                case CONFIG_WEEK_PROFILE:
                case REMOVE_GROUP_ID:
                case REMOVE_LINK_PARTNER:
                case RESET:
                case SET_COMFORT_TEMPERATURE:
                case SET_DISPLAY_ACTUAL_TEMP:
                case SET_ECO_TEMPERATURE:
                case UNKNOWN:
                default:
                    BaseMsg baseMsg = new BaseMsg(data);
                    baseMsg.printMessage();
                    break;

            }
        } catch (MaxCulProtocolException e) {
            logger.warn("'{}' violates Max! CUL protocol.", data);
        }
    }

    @Override
    public synchronized void dataReceived(String data) {
        try {
            logger.debug("MaxCulSender Received {}", data);
            if (data.startsWith("Z")) {
                if (listenMode) {
                    listenModeHandler(data);
                    return;
                }

                /* Check message is destined for us */
                if (BaseMsg.isForUs(data, srcAddr)) {
                    boolean passToBinding = true;
                    /* Handle Internal Messages */
                    MaxCulMsgType msgType = BaseMsg.getMsgType(data);
                    if (msgType == MaxCulMsgType.ACK) {
                        passToBinding = false;

                        AckMsg msg = new AckMsg(data);

                        if ((sendItem.msgCount == msg.msgCount) && msg.dstAddrStr.compareTo(srcAddr) == 0) {
                            /* verify ACK */
                            if ((sendItem.dstAddrStr.equalsIgnoreCase(msg.srcAddrStr))
                                    && (sendItem.srcAddrStr.equalsIgnoreCase(msg.dstAddrStr))) {
                                if (msg.getIsNack()) {
                                    /* NAK'd! */
                                    // TODO resend?
                                    logger.error("Message was NAK'd, packet lost");
                                    handleMessageComplete(SendMsgResult.NACK_RECEIVED, msg);
                                } else {
                                    logger.debug("Message {} ACK'd ok!", msg.msgCount);
                                    handleMessageComplete(SendMsgResult.ACK_RECEIVED, msg);
                                    try {
                                        forwardAsBroadcastIfSpyIsEnabled(data);
                                    } catch (Exception e1) {
                                        logger.warn("Could not forward msg {}", data, e1);
                                    }
                                }
                            }
                        } else {
                            logger.info("Got ACK for message {} but it wasn't in the queue", msg.msgCount);
                        }

                    }

                    if (passToBinding) {
                        /* pass data to binding for processing */
                        this.cul.maxCulMsgReceived(data, false);
                    }
                } else if (BaseMsg.isForUs(data, "000000")) {
                    switch (BaseMsg.getMsgType(data)) {
                        case PAIR_PING:
                        case WALL_THERMOSTAT_CONTROL:
                        case THERMOSTAT_STATE:
                        case WALL_THERMOSTAT_STATE:
                            this.cul.maxCulMsgReceived(data, true);
                            break;
                        default:
                            logger.debug("Unhandled broadcast message of type {}", BaseMsg.getMsgType(data).toString());
                            break;

                    }
                } else {
                    forwardAsBroadcastIfSpyIsEnabled(data);
                }
            }
        } catch (MaxCulProtocolException e) {
            logger.warn("Invalid message received: '{}'", data);
        }
    }

    private void forwardAsBroadcastIfSpyIsEnabled(String data) {
        try {
            BaseMsg bMsg = new BaseMsg(data);
            if (rfAddressesToSpyOn.contains(bMsg.srcAddrStr) && (BaseMsg.getMsgType(data) != MaxCulMsgType.PAIR_PING)) {
                /*
                 * pass data to binding for processing - pretend it is
                 * broadcast so as not to ACK
                 */
                this.cul.maxCulMsgReceived(data, true);
            }
        } catch (MaxCulProtocolException e) {
            logger.warn("Invalid message received: '{}'", data);
        }
    }

    @Override
    public void error(Exception e) {
        /*
         * Ignore errors for now - not sure what I would need to handle here at
         * the moment
         */
        logger.error("Received CUL Error", e);
    }

    public void startSequence(MessageSequencer ps, @Nullable BaseMsg msg) {
        logger.debug("Starting sequence");
        ps.runSequencer(msg);
    }

    /**
     * Send response to PairPing as part of a message sequence
     *
     * @param dstAddr Address of device to respond to
     * @param msgSeq Message sequence to associate
     */
    public void sendPairPong(String dstAddr, @Nullable MessageSequencer msgSeq) {
        PairPongMsg pp = new PairPongMsg(getMessageCount(), (byte) 0, (byte) 0, this.srcAddr, dstAddr);
        pp.setMessageSequencer(msgSeq);
        sendMessage(pp);
    }

    public void sendPairPong(String dstAddr) {
        sendPairPong(dstAddr, null);
    }

    /**
     * Send a wakeup message as part of a message sequence
     *
     * @param devAddr Address of device to respond to
     * @param msgSeq Message sequence to associate
     */
    public void sendWakeup(String devAddr, MessageSequencer msgSeq) {
        WakeupMsg msg = new WakeupMsg(getMessageCount(), (byte) 0x0, (byte) 0, this.srcAddr, devAddr);
        msg.setMessageSequencer(msgSeq);
        sendMessage(msg);
    }

    /**
     * Send time information to device that has requested it as part of a
     * message sequence
     *
     * @param dstAddr Address of device to respond to
     * @param tzStr Time Zone String
     * @param msgSeq Message sequence to associate
     */
    public void sendTimeInfo(String dstAddr, String tzStr, @Nullable MessageSequencer msgSeq) {
        TimeInfoMsg msg = new TimeInfoMsg(getMessageCount(), (byte) 0x0, (byte) 0, this.srcAddr, dstAddr, tzStr);
        msg.setMessageSequencer(msgSeq);
        sendMessage(msg);
    }

    /**
     * Send time information to device that has requested it
     *
     * @param dstAddr Address of device to respond to
     * @param tzStr Time Zone String
     */
    public void sendTimeInfo(String dstAddr, String tzStr) {
        sendTimeInfo(dstAddr, tzStr, null);
    }

    /**
     * Set the group ID on a device
     *
     * @param devAddr Address of device to set group ID on
     * @param group_id Group id to set
     * @param msgSeq Message sequence to associate
     */
    public void sendSetGroupId(String devAddr, byte group_id, MessageSequencer msgSeq) {
        SetGroupIdMsg msg = new SetGroupIdMsg(getMessageCount(), (byte) 0x0, this.srcAddr, devAddr, group_id);
        msg.setMessageSequencer(msgSeq);
        sendMessage(msg);
    }

    /**
     * Send an ACK response to a message
     *
     * @param msg Message we are acking
     */
    public void sendAck(BaseMsg msg) {
        AckMsg ackMsg = new AckMsg(msg.msgCount, (byte) 0x0, msg.groupid, this.srcAddr, msg.srcAddrStr, false);
        sendMessage(ackMsg);
    }

    /**
     * Send an NACK response to a message
     *
     * @param msg Message we are nacking
     */
    public void sendNack(BaseMsg msg) {
        AckMsg nackMsg = new AckMsg(msg.msgCount, (byte) 0x0, msg.groupid, this.srcAddr, msg.srcAddrStr, false);
        sendMessage(nackMsg);
    }

    /**
     * Send a set temperature message
     *
     * @param devAddr Radio addr of device
     * @param mode Mode to set e.g. AUTO or MANUAL
     * @param temp Temperature value to send
     */
    public void sendSetTemperature(String devAddr, ThermostatControlMode mode, double temp) {
        if (ThermostatControlMode.UNKOWN == mode) {
            logger.warn("ThermostatControlMode.UNKOWN is not supported. Skip set temperature to {}.", temp);
            return;
        }
        SetTemperatureMsg msg = new SetTemperatureMsg(getMessageCount(), (byte) 0x0, (byte) 0x0, this.srcAddr, devAddr,
                temp, mode);
        sendMessage(msg);
    }

    /**
     * Send a set eco temperature message
     *
     * @param devAddr Radio addr of device
     */
    public void sendSetEcoTemperature(String devAddr) {
        SetEcoTempMsg msg = new SetEcoTempMsg(getMessageCount(), (byte) 0x0, (byte) 0x0, this.srcAddr, devAddr);
        sendMessage(msg);
    }

    /**
     * Send a set comfort temperature message
     *
     * @param devAddr Radio addr of device
     */
    public void sendSetComfortTemperature(String devAddr) {
        SetComfortTempMsg msg = new SetComfortTempMsg(getMessageCount(), (byte) 0x0, (byte) 0x0, this.srcAddr, devAddr);
        sendMessage(msg);
    }

    /**
     * Send week profile
     *
     * @param devAddr Radio addr of device
     * @param msgSeq Message sequencer to associate with this message
     * @param weekProfilePart week profile value
     * @param secondHalf flag if the control points > 7 should be send
     */
    public void sendWeekProfile(String devAddr, MessageSequencer msgSeq, MaxCulWeekProfilePart weekProfilePart,
            boolean secondHalf) {
        ConfigWeekProfileMsg cfgWeekProfileMsg = new ConfigWeekProfileMsg(getMessageCount(), (byte) 0, (byte) 0,
                this.srcAddr, devAddr, weekProfilePart, secondHalf);
        cfgWeekProfileMsg.setMessageSequencer(msgSeq);
        sendMessage(cfgWeekProfileMsg);
    }

    /**
     * Send temperature configuration message
     *
     * @param devAddr Radio addr of device
     * @param msgSeq Message sequencer to associate with this message
     * @param comfortTemp comfort temperature value
     * @param ecoTemp Eco temperature value
     * @param maxTemp Maximum Temperature value
     * @param minTemp Minimum temperature value
     * @param offset Offset Temperature value
     * @param windowOpenTemp Window open temperature value
     * @param windowOpenTime Window open time value
     */
    public void sendConfigTemperatures(String devAddr, @Nullable MessageSequencer msgSeq, double comfortTemp, double ecoTemp,
            double maxTemp, double minTemp, double offset, double windowOpenTemp, double windowOpenTime) {
        ConfigTemperaturesMsg cfgTempMsg = new ConfigTemperaturesMsg(getMessageCount(), (byte) 0, (byte) 0,
                this.srcAddr, devAddr, comfortTemp, ecoTemp, maxTemp, minTemp, offset, windowOpenTemp, windowOpenTime);
        cfgTempMsg.setMessageSequencer(msgSeq);
        sendMessage(cfgTempMsg);
    }

    /**
     * Send valve configuration message
     *
     * @param devAddr Radio addr of device
     * @param msgSeq Message sequencer to associate with this message
     * @param boostDuration boost duration value
     * @param boostValvePosition boostvalve position value
     * @param decalcificationDay decalcification day value
     * @param decalcificationHour decalsification hour value
     * @param maxValveSetting max valve setting value
     * @param valveOffset valve offset value
     */
    public void sendConfigValve(String devAddr, @Nullable MessageSequencer msgSeq, MaxCulBoostDuration boostDuration, 
            int boostValvePosition, MaxCulWeekdays decalcificationDay, int decalcificationHour, int maxValveSetting, int valveOffset) {
        ConfigValveMsg cfgValveMsg = new ConfigValveMsg(getMessageCount(), (byte) 0, (byte) 0, this.srcAddr,
                devAddr, boostDuration, boostValvePosition, decalcificationDay, decalcificationHour, maxValveSetting, valveOffset);
        cfgValveMsg.setMessageSequencer(msgSeq);
        sendMessage(cfgValveMsg);
    }

    /**
     * Link one device to another
     *
     * @param devAddr Destination device address
     * @param msgSeq Associated message sequencer
     * @param partnerAddr Radio address of partner
     * @param devType Type of device
     */
    public void sendAddLinkPartner(String devAddr, MessageSequencer msgSeq, String partnerAddr, MaxCulDevice devType) {
        AddLinkPartnerMsg addLinkMsg = new AddLinkPartnerMsg(getMessageCount(), (byte) 0, (byte) 0, this.srcAddr,
                devAddr, partnerAddr, devType);
        addLinkMsg.setMessageSequencer(msgSeq);
        sendMessage(addLinkMsg);
    }

    /**
     * Send a reset message to device
     *
     * @param devAddr Address of device to reset
     */
    public void sendReset(String devAddr) {
        ResetMsg resetMsg = new ResetMsg(getMessageCount(), (byte) 0, (byte) 0, this.srcAddr, devAddr);
        sendMessage(resetMsg);
    }

    /**
     * Set listen mode status. Doing this will stop proper message processing
     * and will just turn this message handler into a snooper.
     *
     * @param listenModeOn TRUE sets listen mode to ON
     */
    public void setListenMode(boolean listenModeOn) {
        listenMode = listenModeOn;
        logger.debug("Listen Mode is {}", (listenMode ? "ON" : "OFF"));
    }

    public boolean getListenMode() {
        return listenMode;
    }

    public void sendSetDisplayActualTemp(String devAddr, boolean displayActualTemp) {
        SetDisplayActualTempMsg displaySettingMsg = new SetDisplayActualTempMsg(getMessageCount(), (byte) 0, (byte) 0,
                this.srcAddr, devAddr, displayActualTemp);
        sendMessage(displaySettingMsg);
    }
}
