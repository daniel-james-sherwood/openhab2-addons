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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author Johannes Goehr (johgoe) - Initial contribution
 */
@NonNullByDefault
public class MaxCulWeekProfile {

    private List<MaxCulWeekProfilePart> weekProfileParts = new ArrayList<MaxCulWeekProfilePart>();

    public void addWeekProfilePart(MaxCulWeekProfilePart weekProfilePart) {
        weekProfileParts.add(weekProfilePart);
    }

    public List<MaxCulWeekProfilePart> getWeekProfileParts() {
        return weekProfileParts;
    }
}
