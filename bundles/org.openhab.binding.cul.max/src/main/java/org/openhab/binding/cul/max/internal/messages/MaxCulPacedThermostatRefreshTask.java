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

import static org.openhab.binding.cul.max.internal.MaxCulBindingConstants.*;

import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.cul.max.internal.handler.MaxCulCunBridgeHandler;
import org.openhab.binding.cul.max.internal.handler.MaxDevicesHandler;
import org.openhab.binding.cul.max.internal.messages.constants.ThermostatControlMode;
import org.openhab.core.thing.ThingTypeUID;

/**
 * @author Paul Hampson (cyclingengineer) - Initial contribution
 * @author Johannes Goehr (johgoe) - Migration to OpenHab 3.0
 */
@NonNullByDefault
public class MaxCulPacedThermostatRefreshTask {

    public static final int REFRESH_TIME = 5 * 60000;
    public static final int INITIAL_REFRESH_TIME = 10000;
    
    private @Nullable MaxCulCunBridgeHandler messageHandler;
    private MaxDevicesHandler maxDevicesHandler;
    private @Nullable Timer timer = null;    

    public MaxCulPacedThermostatRefreshTask(MaxDevicesHandler maxDevicesHandler, @Nullable MaxCulCunBridgeHandler messageHandler) {
        this.maxDevicesHandler = maxDevicesHandler;
        this.messageHandler = messageHandler;
    }

    class Task  extends TimerTask {
        private final MaxCulPacedThermostatRefreshTask parent;

        public Task(MaxCulPacedThermostatRefreshTask parent) {
            this.parent = parent;
        }

        public void run() {
            parent.run();
        }
    }

    public synchronized void start()
    {
        int refreshTime = INITIAL_REFRESH_TIME;
        if (timer != null)
        {
            timer.cancel();
            timer = null;
            refreshTime = REFRESH_TIME;
        }
        timer = new Timer();
        timer.schedule(new Task(this), refreshTime);
    }

    public void run() {
        ThingTypeUID thingTypeUID = maxDevicesHandler.getThing().getThingTypeUID();
        if (HEATINGTHERMOSTAT_THING_TYPE.equals(thingTypeUID) || HEATINGTHERMOSTATPLUS_THING_TYPE.equals(thingTypeUID)
                || WALLTHERMOSTAT_THING_TYPE.equals(thingTypeUID)) {
            messageHandler.sendSetDisplayActualTemp(maxDevicesHandler, false);
        }
        start();
    }
}
