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
package org.openhab.binding.cul.max.internal.messages.constants;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author Johannes Goehr (johgoe) - Initial contribution
 */
@NonNullByDefault
public enum MaxCulBoostDuration {
    MIN_0(0, 0),
    MIN_5(1, 5),
    MIN_10(2, 10),
    MIN_15(3, 15),
    MIN_20(4, 20),
    MIN_25(5, 25),
    MIN_30(6, 30),
    MIN_60(7, 60),
    UNKNOWN(-1, -1);

    private final int durationIndex;
    private final int duration;

    private MaxCulBoostDuration(int idx, int dur) {
        durationIndex = idx;
        duration = dur;
    }

    public int getDurationIndex() {
        return durationIndex;
    }

    public int getDuration() {
        return duration;
    }

    public static MaxCulBoostDuration getBoostDurationFromIdx(int idx) {
        for (int i = 0; i < MaxCulBoostDuration.values().length; i++) {
            if (MaxCulBoostDuration.values()[i].getDurationIndex() == idx)
                return MaxCulBoostDuration.values()[i];
        }
        return UNKNOWN;
    }

    public static MaxCulBoostDuration getBoostDurationFromDur(int dur) {
        for (int i = 0; i < MaxCulBoostDuration.values().length; i++) {
            if (MaxCulBoostDuration.values()[i].getDuration() == dur)
                return MaxCulBoostDuration.values()[i];
        }
        return UNKNOWN;
    }
}
