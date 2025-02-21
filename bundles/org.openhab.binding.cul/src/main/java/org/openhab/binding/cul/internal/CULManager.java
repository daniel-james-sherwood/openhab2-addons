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
package org.openhab.binding.cul.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.cul.CULCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles all CULHandler. You can only obtain CULHandlers via this
 * manager.
 *
 * @author Till Klocke - Initial contribution
 * @author Johannes Goehr (johgoe) - Migration to OpenHab 3.0
 * @since 1.4.0
 */
@NonNullByDefault
public class CULManager {

    private final Logger logger = LoggerFactory.getLogger(CULManager.class);

    private static CULManager instance = new CULManager();

    private Map<String, DeviceClasses> deviceTypeClasses = new HashMap<String, DeviceClasses>();
    private Map<String, CULConfigFactory> deviceTypeConfigFactories = new HashMap<String, CULConfigFactory>();

    private Map<String, CULHandlerInternal> openDevices = new HashMap<String, CULHandlerInternal>();

    public static CULManager getInstance() {
        return instance;
    }

    /**
     * Retrieve the config factory for a certain device type.
     *
     * @param deviceType device type
     * @return config factory for the device type
     */
    public @Nullable CULConfigFactory getConfigFactory(String deviceType) {
        return deviceTypeConfigFactories.get(deviceType);
    }

    /**
     * Get CULHandler for the given device in the given mode. The same
     * CULHandler can be returned multiple times if you ask multiple times for
     * the same device in the same mode. It is not possible to obtain a
     * CULHandler of an already openend device for another RF mode.
     *
     * @param config
     *            The configuration for the handler.
     * @return A CULHandler to communicate with the culfw based device.
     * @throws CULDeviceException
     */
    public CULHandlerInternal getOpenCULHandler(CULConfig config) throws CULDeviceException {
        CULMode mode = config.getMode();
        String deviceName = config.getDeviceName();
        logger.debug("Trying to open device {} in mode {}", deviceName, mode.toString());
        synchronized (openDevices) {
            if (openDevices.containsKey(deviceName)) {
                CULHandlerInternal handler = openDevices.get(deviceName);
                if (handler == null) {
                    throw new CULDeviceException("Found null handler for device name " + deviceName);
                }
                if (handler.getConfig().equals(config)) {
                    logger.debug("Device {} is already open in mode {}, returning already openend handler", deviceName,
                            mode.toString());
                    return handler;
                } else {
                    throw new CULDeviceException(
                            "The device " + deviceName + " is already open in mode " + mode.toString());
                }
            }
            CULHandlerInternal handler = createNewHandler(config);
            openDevices.put(deviceName, handler);
            return handler;
        }
    }

    /**
     * Return a CULHandler to the manager. The CULHandler will only be closed if
     * there aren't any listeners registered with it. So it is save to call this
     * methods as soon as you don't need the CULHandler any more.
     *
     * @param handler
     */
    public void close(CULHandlerInternal handler) {
        synchronized (openDevices) {
            if (!handler.hasListeners() && !handler.hasCreditListeners()) {
                openDevices.remove(handler.getConfig().getDeviceName());
                try {
                    handler.send("X00");
                } catch (CULCommunicationException e) {
                    logger.warn("Couldn't reset rf mode to X00");
                }
                handler.close();
            } else {
                logger.warn("Can't close device because it still has listeners");
            }
        }
    }

    static class DeviceClasses {
        Class<? extends CULHandlerInternal> clazz;
        Class<?>[] parameterTypes;
        Object[] parameters;

        public DeviceClasses(Class<? extends CULHandlerInternal> clazz, Class<?>[] parameterTypes,
                Object[] parameters) {
            this.clazz = clazz;
            this.parameterTypes = parameterTypes;
            this.parameters = parameters;
        }
    }

    public void registerHandlerClass(String deviceType, Class<? extends CULHandlerInternal> clazz,
            CULConfigFactory configFactory, Class<?>[] parameterTypes, Object[] parameters) {
        logger.debug("Registering class {} for device type {}", clazz.getCanonicalName(), deviceType);
        deviceTypeClasses.put(deviceType, new DeviceClasses(clazz, parameterTypes, parameters));
        deviceTypeConfigFactories.put(deviceType, configFactory);
    }

    private CULHandlerInternal createNewHandler(CULConfig config) throws CULDeviceException {
        String deviceType = config.getDeviceType();
        CULMode mode = config.getMode();
        logger.debug("Searching class for device type {}", deviceType);
        DeviceClasses culHandlerclass = deviceTypeClasses.get(deviceType);
        if (culHandlerclass == null) {
            throw new CULDeviceException("No class for the device type " + deviceType + " is registred");
        }

        Class<?>[] constructorParametersTypes = add2BeginningOfArray(culHandlerclass.parameterTypes, CULConfig.class);
        Object[] parameters = add2BeginningOfArray(culHandlerclass.parameters, config);

        try {
            Constructor<? extends CULHandlerInternal> culHanlderConstructor = culHandlerclass.clazz
                    .getConstructor(constructorParametersTypes);
            @Nullable
            CULHandlerInternal culHandler = culHanlderConstructor.newInstance(parameters);
            List<String> initCommands = mode.getCommands();
            if (!(culHandler instanceof CULHandlerInternal)) {
                logger.error("Class {} does not implement the internal interface",
                        culHandlerclass.clazz.getCanonicalName());
                throw new CULDeviceException("This CULHandler class does not implement the internal interface: "
                        + culHandlerclass.clazz.getCanonicalName());
            }
            CULHandlerInternal internalHandler = culHandler;
            internalHandler.open();
            for (String command : initCommands) {
                internalHandler.sendWithoutCheck(command);
            }
            return culHandler;
        } catch (SecurityException e1) {
            throw new CULDeviceException("Not allowed to access the constructor ", e1);
        } catch (NoSuchMethodException e1) {
            throw new CULDeviceException("Can't find the constructor to build the CULHandler", e1);
        } catch (IllegalArgumentException e) {
            throw new CULDeviceException("Invalid arguments for constructor. CULConfig: " + config, e);
        } catch (InstantiationException e) {
            throw new CULDeviceException("Can't instantiate CULHandler object", e);
        } catch (IllegalAccessException e) {
            throw new CULDeviceException("Can't instantiate CULHandler object", e);
        } catch (InvocationTargetException e) {
            throw new CULDeviceException("Can't instantiate CULHandler object", e);
        } catch (CULCommunicationException e) {
            throw new CULDeviceException("Can't initialise RF mode", e);
        }
    }

    public static <T> T[] add2BeginningOfArray(T[] elements, T element) {
        T[] newArray = Arrays.copyOf(elements, elements.length + 1);
        newArray[0] = element;
        System.arraycopy(elements, 0, newArray, 1, elements.length);

        return newArray;
    }
}
