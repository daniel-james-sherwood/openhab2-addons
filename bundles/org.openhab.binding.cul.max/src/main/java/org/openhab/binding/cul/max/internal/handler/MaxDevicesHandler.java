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

import static org.openhab.binding.cul.max.internal.MaxCulBindingConstants.*;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.cul.max.internal.MaxCulBindingConstants;
import org.openhab.binding.cul.max.internal.actions.MaxDevicesActions;
import org.openhab.binding.cul.max.internal.config.WeekProfileConfigHelper;
import org.openhab.binding.cul.max.internal.message.sequencers.PairingInitialisationSequence;
import org.openhab.binding.cul.max.internal.messages.*;
import org.openhab.binding.cul.max.internal.messages.constants.MaxCulBoostDuration;
import org.openhab.binding.cul.max.internal.messages.constants.MaxCulDevice;
import org.openhab.binding.cul.max.internal.messages.constants.MaxCulWeekdays;
import org.openhab.binding.cul.max.internal.messages.constants.PushButtonMode;
import org.openhab.binding.cul.max.internal.messages.constants.ShutterContactState;
import org.openhab.binding.cul.max.internal.messages.constants.ThermostatControlMode;
import org.openhab.binding.cul.max.internal.messages.PairPingMsg;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.*;
import org.openhab.core.model.item.BindingConfigParseException;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Johannes Goehr (johgoe) - Initial contribution
 */
@NonNullByDefault
public class MaxDevicesHandler extends BaseThingHandler {

    public static final int PACED_TRANSMIT_TIME = 10000;

    public MaxDevicesHandler(Thing thing) {
        super(thing);
    }

    private final Logger logger = LoggerFactory.getLogger(MaxDevicesHandler.class);
    private @Nullable MaxCulCunBridgeHandler bridgeHandler;

    private @Nullable String deviceSerial;

    private @Nullable String rfAddress;

    public @Nullable String getDeviceSerial() {
        return deviceSerial;
    }

    public @Nullable String getRfAddress() {
        return rfAddress;
    }

    private double comfortTemp = ConfigTemperaturesMsg.DEFAULT_COMFORT_TEMP;
    private double ecoTemp = ConfigTemperaturesMsg.DEFAULT_ECO_TEMP;
    private double maxTemp = ConfigTemperaturesMsg.DEFAULT_MAX_TEMP;
    private double minTemp = ConfigTemperaturesMsg.DEFAULT_MIN_TEMP;
    private double windowOpenTemperature = ConfigTemperaturesMsg.DEFAULT_WINDOW_OPEN_TEMP;
    private int windowOpenDuration = ConfigTemperaturesMsg.DEFAULT_WINDOW_OPEN_TIME;
    private int maxValveSetting = ConfigValveMsg.DEFAULT_MAX_VALVE_SETTING;
    private int valveOffset = ConfigValveMsg.DEFAULT_VALVE_OFFSET;
    private int boostValvePosition = ConfigValveMsg.DEFAULT_BOOST_VALVE_POSITION;
    private MaxCulBoostDuration boostDuration = ConfigValveMsg.DEFAULT_BOOST_DURATION;
    private MaxCulWeekdays decalcificationDay = ConfigValveMsg.DEFAULT_DECALCIFICATION_DAY;
    private int decalcificationHour = ConfigValveMsg.DEFAULT_DECALCIFICATION_HOUR;
    private int refreshPeriod = ConfigTemperaturesMsg.DEFAULT_REFRESH_PERIOD;
    private double measurementOffset = ConfigTemperaturesMsg.DEFAULT_OFFSET;
    private MaxCulWeekProfile weekProfile = ConfigWeekProfileMsg.DEFAULT_WEEK_PROFILE;
    private Set<String> associatedSerials = new HashSet<>();
    private @Nullable Timer pacingTimer = null;

    private ThermostatControlMode mode = ThermostatControlMode.UNKOWN;
    private double settemp = -1.0;
    private int valve = -1;

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(MaxDevicesActions.class);
    }

    @Override
    public void initialize() {
        disposed = false;
        mode = ThermostatControlMode.UNKOWN;
        settemp = -1.0;
        firstRefresh = true;
        final String thingVersion = this.thing.getProperties().get(MaxCulBindingConstants.PROPERTY_THING_VERSION);
        if (!MaxCulBindingConstants.CURRENT_THING_VERSION.equals(thingVersion)) {
            final Map<String, String> newProperties = new HashMap<>(thing.getProperties());
            newProperties.put(MaxCulBindingConstants.PROPERTY_THING_VERSION,
                    MaxCulBindingConstants.CURRENT_THING_VERSION);

            final ThingBuilder thingBuilder = editThing();
            thingBuilder.withProperties(newProperties);
            updateThing(thingBuilder.build());
            final Thing localThing = this.thing;
            scheduler.submit(() -> changeThingType(localThing.getThingTypeUID(), localThing.getConfiguration()));
            return;
        }
        try {
            final Configuration config = getThing().getConfiguration();
            final String configDeviceSerial = (String) config.get(Thing.PROPERTY_SERIAL_NUMBER);
            if (configDeviceSerial != null) {
                deviceSerial = configDeviceSerial;
            }
            if (deviceSerial != null) {
                logger.debug("Initialized MAX! device handler for {}.", deviceSerial);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Initialized MAX! device missing serialNumber configuration");
            }
            final String configRfAdress = (String) config.get(PROPERTY_RFADDRESS);
            if (configRfAdress != null) {
                rfAddress = configRfAdress;
            }
            if (rfAddress != null) {
                logger.debug("Initialized MAX! device handler for {} with rfAddress {}.", deviceSerial, rfAddress);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Initialized MAX! device missing rfAddress configuration");
            }
            final String configAssociated = (String) config.get(PROPERTY_ASSOSIATE);
            if (configAssociated != null) {
                associatedSerials.addAll(Arrays.asList(configAssociated.split(",")));
                logger.debug("Loaded {} associated serials for {}.", associatedSerials.size(), deviceSerial);
            }
            getConfigurationValues(config);

            getMaxCulCunBaseBridgeHandler();
            if (bridgeHandler == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Valid bridge is required");
                return;
            } else {
                ThingTypeUID thingTypeUID = getThing().getThingTypeUID();
                if ((HEATINGTHERMOSTAT_THING_TYPE.equals(thingTypeUID) || HEATINGTHERMOSTATPLUS_THING_TYPE.equals(thingTypeUID)
                        || WALLTHERMOSTAT_THING_TYPE.equals(thingTypeUID))) {
                    startThermostatRefresh(INITIAL_REFRESH_PERIOD);
                }
            }
            updateStatus(ThingStatus.UNKNOWN);
        } catch (Exception e) {
            logger.debug("Exception occurred during initialize : {}", e.getMessage(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, e.getMessage());
        }
    }

    public boolean disposed = false;
    @Override
    public void dispose() 
    {
        disposed = true;
        startThermostatRefresh(0);
    }

    private void getConfigurationValues(Configuration config) {
        final String weekProfileString = (String) config.get(PROPERTY_THERMO_WEEK_PROFILE);
        if (weekProfileString != null && !weekProfileString.isEmpty()) {
            try {
                weekProfile = WeekProfileConfigHelper.parseWeekProfileString(weekProfileString);
                logger.trace("Week profile loaded for for {}", getDeviceSerial());
            } catch (BindingConfigParseException e) {
                logger.warn("Could not parse week profile {}", weekProfileString, e);
            }
        }
        final BigDecimal comfortTempCfg = (BigDecimal) config.get(PROPERTY_THERMO_COMFORT_TEMP);
        if (comfortTempCfg != null) {
            comfortTemp = comfortTempCfg.doubleValue();
            logger.trace("Comfort temperature is set to {} for {}", comfortTemp, getDeviceSerial());
        }
        final BigDecimal ecoTempCfg = (BigDecimal) config.get(PROPERTY_THERMO_ECO_TEMP);
        if (ecoTempCfg != null) {
            ecoTemp = ecoTempCfg.doubleValue();
            logger.trace("Eco temperature is set to {} for {}", ecoTemp, getDeviceSerial());
        }
        final BigDecimal maxTempCfg = (BigDecimal) config.get(PROPERTY_THERMO_MAX_TEMP_SETPOINT);
        if (maxTempCfg != null) {
            maxTemp = maxTempCfg.doubleValue();
            logger.trace("Max temperature is set to {} for {}", maxTemp, getDeviceSerial());
        }
        final BigDecimal minTempCfg = (BigDecimal) config.get(PROPERTY_THERMO_MIN_TEMP_SETPOINT);
        if (minTempCfg != null) {
            minTemp = minTempCfg.doubleValue();
            logger.trace("Min temperature is set to {} for {}", minTemp, getDeviceSerial());
        }
        final BigDecimal windowOpenDurationCfg = (BigDecimal) config.get(PROPERTY_THERMO_WINDOW_OPEN_DURATION);
        if (windowOpenDurationCfg != null) {
            windowOpenDuration = windowOpenDurationCfg.intValue();
            logger.trace("Window open duration is set to {} for {}", windowOpenDuration, getDeviceSerial());
        }
        final BigDecimal maxValveSettingCfg = (BigDecimal) config.get(PROPERTY_THERMO_MAX_VALVE_SETTING);
        if (maxValveSettingCfg != null) {
            maxValveSetting = maxValveSettingCfg.intValue();
            logger.trace("Max valve setting is set to {} for {}", maxValveSetting, getDeviceSerial());
        }
        final BigDecimal valveOffsetCfg = (BigDecimal) config.get(PROPERTY_THERMO_VALVE_OFFSET);
        if (valveOffsetCfg != null) {
            valveOffset = valveOffsetCfg.intValue();
            logger.trace("Valve offset is set to {} for {}", valveOffset, getDeviceSerial());
        }
        final BigDecimal boostValvePositionCfg = (BigDecimal) config.get(PROPERTY_THERMO_BOOST_VALVE_POSITION);
        if (boostValvePositionCfg != null) {
            boostValvePosition = boostValvePositionCfg.intValue();
            logger.trace("Boost valve position is set to {} for {}", boostValvePosition, getDeviceSerial());
        }
        final BigDecimal boostDurationCfg = (BigDecimal) config.get(PROPERTY_THERMO_BOOST_DURATION);
        if (boostDurationCfg != null) {
            boostDuration = MaxCulBoostDuration.getBoostDurationFromDur(boostDurationCfg.intValue());
            logger.trace("Boost duration is set to {} for {}", boostDuration, getDeviceSerial());
        }
        final BigDecimal refreshPeriodCfg = (BigDecimal) config.get(PROPERTY_THERMO_REFRESH_PERIOD);
        if (refreshPeriodCfg != null) {
            refreshPeriod = refreshPeriodCfg.intValue();
            logger.trace("Refresh period is set to {} for {}", refreshPeriod, getDeviceSerial());
        }
        final BigDecimal windowOpenTempCfg = (BigDecimal) config.get(PROPERTY_THERMO_WINDOW_OPEN_TEMP);
        if (windowOpenTempCfg != null) {
            windowOpenTemperature = windowOpenTempCfg.doubleValue();
            logger.trace("Window open temperature is set to {} for {}", windowOpenTemperature, getDeviceSerial());
        }
        final BigDecimal offsetTempCfg = (BigDecimal) config.get(PROPERTY_THERMO_OFFSET_TEMP);
        if (offsetTempCfg != null) {
            measurementOffset = offsetTempCfg.doubleValue();
            logger.trace("Offset temperature is set to {} for {}", measurementOffset, getDeviceSerial());
        }
    }

    private synchronized @Nullable MaxCulCunBridgeHandler getMaxCulCunBaseBridgeHandler() {
        if (this.bridgeHandler == null) {
            final Bridge bridge = getBridge();
            if (bridge == null) {
                logger.debug("Required bridge not defined for device {}.", deviceSerial);
                return null;
            }
            final ThingHandler handler = bridge.getHandler();
            if (!(handler instanceof MaxCulCunBridgeHandler)) {
                logger.debug("No available bridge handler found for {} bridge {} .", deviceSerial, bridge.getUID());
                return null;
            }
            this.bridgeHandler = (MaxCulCunBridgeHandler) handler;
        }
        return this.bridgeHandler;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (ThingStatus.ONLINE != thing.getStatus()) {
            return;
        }
        MaxCulCunBridgeHandler bridgeHandler = this.bridgeHandler;
        if (bridgeHandler == null) {
            return;
        }
        if (command instanceof RefreshType) {
            return;
        }
        logger.debug("Command '{}' with channel `{}` received", command, channelUID);
        switch (channelUID.getIdWithoutGroup()) {
            case CHANNEL_ACTUALTEMP:
            case CHANNEL_BATTERY:
            case CHANNEL_LOCKED:
            case CHANNEL_CONTACT_STATE:
            case CHANNEL_SWITCH:
            case CHANNEL_VALVE:
                logger.debug("Channel {} is a read-only channel and cannot handle command '{}'", channelUID, command);
                break;
            case CHANNEL_MODE:
                ThermostatControlMode new_mode = ThermostatControlMode.UNKOWN;
                if (command instanceof StringType) {
                    new_mode = ThermostatControlMode.fromString(command.toString());
                } else if (command instanceof DecimalType) {
                    new_mode = ThermostatControlMode.values()[((DecimalType) command).intValue()];
                } else if (command instanceof QuantityType<?>) {
                    new_mode = ThermostatControlMode.values()[((QuantityType<?>) command).intValue()];
                }
                if (new_mode != mode) {
                    mode = new_mode;
                    /* clear out old pacing timer */
                    if (pacingTimer != null) {
                        pacingTimer.cancel();
                        pacingTimer = null;
                    }
                    /* schedule new timer */
                    pacingTimer = new Timer();
                    MaxCulPacedThermostatTransmitTask pacedThermostatTransmitTask = new MaxCulPacedThermostatTransmitTask(
                            mode, mode == ThermostatControlMode.AUTO ? 0.0 : settemp + thermostatRefeshAdjust, this, bridgeHandler);
                    pacingTimer.schedule(pacedThermostatTransmitTask, PACED_TRANSMIT_TIME);
                }
                break;
            case CHANNEL_SETTEMP:
                double new_settemp = -1.0;
                if (command instanceof OnOffType) {
                    if (command == OnOffType.ON) {
                        new_settemp = getMaxTemp();
                    } else if (command == OnOffType.OFF) {
                        new_settemp = getMinTemp();
                    }
                } else if (command instanceof StringType) {
                    switch (command.toString()) {
                        case "ON":
                            new_settemp = getMaxTemp();
                            break;
                        case "OFF":
                            new_settemp = getMinTemp();
                            break;
                        case "ECO":
                            new_settemp = getEcoTemp();
                            break;
                        case "COMFORT":
                            new_settemp = getComfortTemp();
                            break;
                    }
                } else if (command instanceof DecimalType) {
                    new_settemp = ((DecimalType) command).doubleValue();
                } else if (command instanceof QuantityType<?>) {
                    new_settemp = ((QuantityType<?>) command).doubleValue();
                }
                if (new_settemp != settemp) {
                    settemp = new_settemp;
                    thermostatRefeshAdjust = 0.0;
                    /* clear out old pacing timer */
                    if (pacingTimer != null) {
                        pacingTimer.cancel();
                        pacingTimer = null;
                    }
                    /* schedule new timer */
                    pacingTimer = new Timer();
                    MaxCulPacedThermostatTransmitTask pacedThermostatTransmitTask = new MaxCulPacedThermostatTransmitTask(
                            mode, settemp + thermostatRefeshAdjust, this, bridgeHandler);
                    pacingTimer.schedule(pacedThermostatTransmitTask, PACED_TRANSMIT_TIME);
                }
                break;
            case CHANNEL_DISPLAY_ACTUAL_TEMP:
                bridgeHandler.sendSetDisplayActualTemp(this, ((OnOffType) command == OnOffType.ON));
                break;
            default:
                logger.warn("Channel {} is a unkown channel and cannot handle command '{}'", channelUID, command);
        }
    }

    public void processMessage(BaseMsg msg) {
        if (msg.srcAddrStr.equals(rfAddress)) {
            updateStatus(ThingStatus.ONLINE);
        }
        if (msg instanceof RfErrorStateMsg && ((RfErrorStateMsg) msg).isRfError()) {
            logger.warn("Message with RfError from {} to {} of type {} received", msg.srcAddrStr, msg.dstAddrStr,
                    msg.msgType);
        }
        if (msg instanceof BatteryStateMsg) {
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_BATTERY),
                    ((BatteryStateMsg) msg).isBatteryLow() ? OnOffType.ON : OnOffType.OFF);
        }
        if (msg instanceof WallThermostatDisplayMeasuredStateMsg
                && WALLTHERMOSTAT_THING_TYPE.equals(getThing().getThingTypeUID())) {
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_DISPLAY_ACTUAL_TEMP),
                    ((WallThermostatDisplayMeasuredStateMsg) msg).isDisplayMeasuredTemp() ? OnOffType.ON
                            : OnOffType.OFF);
        }
        if (msg instanceof ThermostatValveStateMsg && (HEATINGTHERMOSTAT_THING_TYPE.equals(getThing().getThingTypeUID())
                || HEATINGTHERMOSTATPLUS_THING_TYPE.equals(getThing().getThingTypeUID()))) {
            valve = ((ThermostatValveStateMsg) msg).getValvePos();
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_VALVE),
                    new DecimalType(valve));
        }
        if (msg instanceof DesiredTemperatureStateMsg) {
            mode = ((DesiredTemperatureStateMsg) msg).getControlMode();
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_MODE),
                    new StringType(mode.toString()));
            Double desiredTemperature = ((DesiredTemperatureStateMsg) msg).getDesiredTemperature();
            if (desiredTemperature != null) {
                settemp = desiredTemperature - thermostatRefeshAdjust;
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_SETTEMP),
                        new QuantityType<>(settemp, CELSIUS));
            }
            // TODO handle UntilDate when mode is ThermostatControlMode.TEMPORARY
            // ((DesiredTemperatureStateMsg) msg).getUntilDateTime()
        }
        if (msg instanceof ThermostatCommonStateMsg) {
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_LOCKED),
                    ((ThermostatCommonStateMsg) msg).isLockedForManualSetPoint() ? OpenClosedType.CLOSED
                            : OpenClosedType.OPEN);

        }
        if (msg instanceof ActualTemperatureStateMsg) {
            Double actualTemp = ((ActualTemperatureStateMsg) msg).getMeasuredTemperature();
            if (actualTemp != null && actualTemp != 0) {
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_ACTUALTEMP),
                        new QuantityType<>(actualTemp - thermostatRefeshAdjust, CELSIUS));
                if (thermostatRefeshAdjust != 0.0) {
                    handleThermostatRefresh();
                } else {
                    startThermostatRefresh(getRefreshPeriodMs());
                }
            }
        }
        if (msg instanceof WallThermostatControlMsg) {
            Double desiredTemperature = ((WallThermostatControlMsg) msg).getDesiredTemperature();
            if (desiredTemperature != null) {
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_SETTEMP),
                        new QuantityType<>(desiredTemperature, CELSIUS));
            }

        }
        if (msg instanceof PushButtonMsg && ECOSWITCH_THING_TYPE.equals(getThing().getThingTypeUID())) {
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_SWITCH),
                    ((PushButtonMsg) msg).getMode() == PushButtonMode.AUTO ? OnOffType.ON : OnOffType.OFF);
        }
        if (msg instanceof ShutterContactStateMsg && SHUTTERCONTACT_THING_TYPE.equals(getThing().getThingTypeUID())) {
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONTACT_STATE),
                    ((ShutterContactStateMsg) msg).getState() == ShutterContactState.OPEN ? OpenClosedType.OPEN
                            : OpenClosedType.CLOSED);
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        final Configuration configuration = editConfiguration();
        for (final Map.Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            if (configurationParameter.getValue().toString().equals(BUTTON_ACTION_VALUE)) {
                configuration.put(configurationParameter.getKey(), BigDecimal.valueOf(BUTTON_NOACTION_VALUE));
                if (configurationParameter.getKey().equals(ACTION_DEVICE_RESET)) {
                    scheduler.submit(this::resetDevice);
                }

            } else {
                configuration.put(configurationParameter.getKey(), configurationParameter.getValue());
            }
        }
        super.handleConfigurationUpdate(configuration.getProperties());
    }

    public double getComfortTemp() {
        return comfortTemp;
    }

    public double getEcoTemp() {
        return ecoTemp;
    }

    public double getMaxTemp() {
        return maxTemp;
    }

    public double getMinTemp() {
        return minTemp;
    }

    public double getWindowOpenTemperature() {
        return windowOpenTemperature;
    }

    public int getWindowOpenDuration() {
        return windowOpenDuration;
    }

    public int getMaxValveSetting() {
        return maxValveSetting;
    }

    public int getValveOffset() {
        return valveOffset;
    }

    public int getBoostValvePosition() {
        return boostValvePosition;
    }

    public MaxCulBoostDuration getBoostDuration() {
        return boostDuration;
    }

    public MaxCulWeekdays getDecalcificationDay() {
        return decalcificationDay;
    }

    public int getDecalcificationHour() {
        return decalcificationHour;
    }

    public int getRefreshPeriod() {
        return refreshPeriod;
    }

    public int getRefreshPeriodMs() {
        return getRefreshPeriod() * 1000;
    }

    public double getMeasurementOffset() {
        return measurementOffset;
    }

    public MaxCulWeekProfile getWeekProfile() {
        return weekProfile;
    }

    public Set<MaxDevicesHandler> getAssociations() {
        MaxCulCunBridgeHandler bridgeHandler = this.bridgeHandler;
        if (bridgeHandler != null) {
            return bridgeHandler.getMaxDevicesHandles().stream()
                    .filter(mdh -> associatedSerials.contains(mdh.getDeviceSerial())).collect(Collectors.toSet());
        } else {
            logger.warn("Can not get associated devices without a bridge");
            return new HashSet<>();
        }
    }

    public void resetDevice() {
        MaxCulCunBridgeHandler bridgeHandler = this.bridgeHandler;
        if (bridgeHandler != null) {
            bridgeHandler.resetDevice(this);
        } else {
            logger.warn("Can not reset device without a bridge");
        }
    }


    private class ThermostatRefreshTask  extends TimerTask {
        private final MaxDevicesHandler handler;

        public ThermostatRefreshTask(MaxDevicesHandler _handler) {
            handler = _handler;
        }

        public void run() {
            handler.handleThermostatRefresh();
        }
    }

    private @Nullable Timer thermostatRefreshTimer = null;
    private double thermostatRefeshAdjust = 0.0;
    private boolean firstRefresh = true;

    private static final int INITIAL_REFRESH_PERIOD = 10000;
    private static final int SECONDARY_REFRESH_PERIOD = 180000;

    private synchronized void startThermostatRefresh(int refreshTime)
    {
        if (thermostatRefreshTimer != null)
        {
            thermostatRefreshTimer.cancel();
            thermostatRefreshTimer = null;
            if (refreshTime == 0) {
                logger.info("Stopped thermostat refresh timer on {}", rfAddress);
                return;
            }
        }
        if (refreshTime > 0)
        {
            thermostatRefreshTimer = new Timer();
            thermostatRefreshTimer.schedule(new ThermostatRefreshTask(this), refreshTime);
            logger.info("Starting thermostat refresh timer on {} for {}", rfAddress, refreshTime);
        }
    }

    private synchronized void handleThermostatRefresh()
    {
        if (disposed) {
            return;
        }
        if (mode != ThermostatControlMode.AUTO && mode != ThermostatControlMode.MANUAL && mode != ThermostatControlMode.UNKOWN) {
            logger.info("Handling thermostat refresh on {}, incompatible mode {} ignored", rfAddress, mode);
        } else if (firstRefresh) {
            logger.info("Handling initial thermostat refresh on {}, adjust {}, offset {}", rfAddress, thermostatRefeshAdjust, getMeasurementOffset() - thermostatRefeshAdjust);
            bridgeHandler.startPairingInitialisationSequence(null, rfAddress, MaxCulDevice.getDeviceTypeFromThingTypeUID(getThing().getThingTypeUID()));
            firstRefresh = false;
        } else if (mode != ThermostatControlMode.UNKOWN) {
            if (thermostatRefeshAdjust != 0.0) {
                thermostatRefeshAdjust = 0.0;
            } else if (settemp == getMaxTemp() || (settemp != getMinTemp() && valve == 0)) {
                thermostatRefeshAdjust = -0.5;
            } else {
                thermostatRefeshAdjust = 0.5;
            }
            logger.info("Handling thermostat refresh on {}, adjust {}, settemp {}, offset {}", rfAddress, thermostatRefeshAdjust, settemp + thermostatRefeshAdjust, getMeasurementOffset() + thermostatRefeshAdjust);
            bridgeHandler.sendSetTemperature(this, mode, settemp + thermostatRefeshAdjust);
            bridgeHandler.sendConfigTemperatures(this,
                                                getComfortTemp(),
                                                getEcoTemp(), 
                                                getMaxTemp(),
                                                getMinTemp(), 
                                                getMeasurementOffset() + thermostatRefeshAdjust,
                                                getWindowOpenTemperature(),
                                                getWindowOpenDuration());
        } else {
            logger.info("Handling secondary thermostat refresh on {}, adjust {}, offset {}", rfAddress, thermostatRefeshAdjust, getMeasurementOffset() - thermostatRefeshAdjust);
            bridgeHandler.sendConfigTemperatures(this,
                                                getComfortTemp(),
                                                getEcoTemp(), 
                                                getMaxTemp(),
                                                getMinTemp(), 
                                                getMeasurementOffset() + thermostatRefeshAdjust,
                                                getWindowOpenTemperature(),
                                                getWindowOpenDuration());
        }
        startThermostatRefresh(mode != ThermostatControlMode.UNKOWN || getRefreshPeriodMs() == 0 ? getRefreshPeriodMs() : SECONDARY_REFRESH_PERIOD);
    }
}
