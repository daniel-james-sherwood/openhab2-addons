/**
 * Copyright (c) 2010-2019 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.denonmarantz.internal;

import java.math.BigDecimal;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.types.State;

/**
 * Represents the state of the handled DenonMarantz AVR
 *
 * @author Jan-Willem Veldhuis - Initial contribution
 *
 */
public class DenonMarantzState {

    private State power;
    private State mainZonePower;
    private State mute;
    private State mainVolume;
    private State mainVolumeDB;
    private State input;
    private State surroundProgram;

    private State artist;
    private State album;
    private State track;

    // ------ Zones ------
    private State zone2Power;
    private State zone2Volume;
    private State zone2VolumeDB;
    private State zone2Mute;
    private State zone2Input;

    private State zone3Power;
    private State zone3Volume;
    private State zone3VolumeDB;
    private State zone3Mute;
    private State zone3Input;

    private DenonMarantzStateChangedListener handler;

    public DenonMarantzState(DenonMarantzStateChangedListener handler) {
        this.handler = handler;
    }

    public void connectionError(String errorMessage) {
        handler.connectionError(errorMessage);
    }

    public State getStateForChannelID(String channelID) {
        switch (channelID) {
            case DenonMarantzBindingConstants.CHANNEL_POWER:
                return power;
            case DenonMarantzBindingConstants.CHANNEL_MAIN_ZONE_POWER:
                return mainZonePower;
            case DenonMarantzBindingConstants.CHANNEL_MUTE:
                return mute;
            case DenonMarantzBindingConstants.CHANNEL_MAIN_VOLUME:
                return mainVolume;
            case DenonMarantzBindingConstants.CHANNEL_MAIN_VOLUME_DB:
                return mainVolumeDB;
            case DenonMarantzBindingConstants.CHANNEL_INPUT:
                return input;
            case DenonMarantzBindingConstants.CHANNEL_SURROUND_PROGRAM:
                return surroundProgram;

            case DenonMarantzBindingConstants.CHANNEL_NOW_PLAYING_ARTIST:
                return artist;
            case DenonMarantzBindingConstants.CHANNEL_NOW_PLAYING_ALBUM:
                return album;
            case DenonMarantzBindingConstants.CHANNEL_NOW_PLAYING_TRACK:
                return track;

            case DenonMarantzBindingConstants.CHANNEL_ZONE2_POWER:
                return zone2Power;
            case DenonMarantzBindingConstants.CHANNEL_ZONE2_VOLUME:
                return zone2Volume;
            case DenonMarantzBindingConstants.CHANNEL_ZONE2_VOLUME_DB:
                return zone2VolumeDB;
            case DenonMarantzBindingConstants.CHANNEL_ZONE2_MUTE:
                return zone2Mute;
            case DenonMarantzBindingConstants.CHANNEL_ZONE2_INPUT:
                return zone2Input;

            case DenonMarantzBindingConstants.CHANNEL_ZONE3_POWER:
                return zone3Power;
            case DenonMarantzBindingConstants.CHANNEL_ZONE3_VOLUME:
                return zone3Volume;
            case DenonMarantzBindingConstants.CHANNEL_ZONE3_VOLUME_DB:
                return zone3VolumeDB;
            case DenonMarantzBindingConstants.CHANNEL_ZONE3_MUTE:
                return zone3Mute;
            case DenonMarantzBindingConstants.CHANNEL_ZONE3_INPUT:
                return zone3Input;

            default:
                return null;
        }
    }

    public void setPower(boolean power) {
        OnOffType newVal = power ? OnOffType.ON : OnOffType.OFF;
        if (newVal != this.power) {
            this.power = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_POWER, this.power);
        }
    }

    public void setMainZonePower(boolean mainPower) {
        OnOffType newVal = mainPower ? OnOffType.ON : OnOffType.OFF;
        if (newVal != this.mainZonePower) {
            this.mainZonePower = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_MAIN_ZONE_POWER, this.mainZonePower);
        }
    }

    public void setMute(boolean mute) {
        OnOffType newVal = mute ? OnOffType.ON : OnOffType.OFF;
        if (newVal != this.mute) {
            this.mute = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_MUTE, this.mute);
        }
    }

    public void setMainVolume(BigDecimal volume) {
        PercentType newVal = new PercentType(volume);
        if (!newVal.equals(this.mainVolume)) {
            this.mainVolume = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_MAIN_VOLUME, this.mainVolume);
            // update the main volume in dB too
            this.mainVolumeDB = DecimalType.valueOf(volume.subtract(DenonMarantzBindingConstants.DB_OFFSET).toString());
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_MAIN_VOLUME_DB, this.mainVolumeDB);
        }
    }

    public void setInput(String input) {
        StringType newVal = StringType.valueOf(input);
        if (!newVal.equals(this.input)) {
            this.input = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_INPUT, this.input);
        }
    }

    public void setSurroundProgram(String surroundProgram) {
        StringType newVal = StringType.valueOf(surroundProgram);
        if (!newVal.equals(this.surroundProgram)) {
            this.surroundProgram = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_SURROUND_PROGRAM, this.surroundProgram);
        }
    }

    public void setNowPlayingArtist(String artist) {
        StringType newVal = StringUtils.isBlank(artist) ? StringType.EMPTY : StringType.valueOf(artist);
        if (!newVal.equals(this.artist)) {
            this.artist = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_NOW_PLAYING_ARTIST, this.artist);
        }
    }

    public void setNowPlayingAlbum(String album) {
        StringType newVal = StringUtils.isBlank(album) ? StringType.EMPTY : StringType.valueOf(album);
        if (!newVal.equals(this.album)) {
            this.album = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_NOW_PLAYING_ALBUM, this.album);
        }
    }

    public void setNowPlayingTrack(String track) {
        StringType newVal = StringUtils.isBlank(track) ? StringType.EMPTY : StringType.valueOf(track);
        if (!newVal.equals(this.track)) {
            this.track = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_NOW_PLAYING_TRACK, this.track);
        }
    }

    public void setZone2Power(boolean power) {
        OnOffType newVal = power ? OnOffType.ON : OnOffType.OFF;
        if (newVal != this.zone2Power) {
            this.zone2Power = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE2_POWER, this.zone2Power);
        }
    }

    public void setZone2Volume(BigDecimal volume) {
        PercentType newVal = new PercentType(volume);
        if (!newVal.equals(this.zone2Volume)) {
            this.zone2Volume = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE2_VOLUME, this.zone2Volume);
            // update the volume in dB too
            this.zone2VolumeDB = DecimalType
                    .valueOf(volume.subtract(DenonMarantzBindingConstants.DB_OFFSET).toString());
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE2_VOLUME_DB, this.zone2VolumeDB);
        }
    }

    public void setZone2Mute(boolean mute) {
        OnOffType newVal = mute ? OnOffType.ON : OnOffType.OFF;
        if (newVal != this.zone2Mute) {
            this.zone2Mute = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE2_MUTE, this.zone2Mute);
        }
    }

    public void setZone2Input(String zone2Input) {
        StringType newVal = StringType.valueOf(zone2Input);
        if (!newVal.equals(this.zone2Input)) {
            this.zone2Input = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE2_INPUT, this.zone2Input);
        }
    }

    public void setZone3Power(boolean power) {
        OnOffType newVal = power ? OnOffType.ON : OnOffType.OFF;
        if (newVal != this.zone3Power) {
            this.zone3Power = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE3_POWER, this.zone3Power);
        }
    }

    public void setZone3Volume(BigDecimal volume) {
        PercentType newVal = new PercentType(volume);
        if (!newVal.equals(this.zone3Volume)) {
            this.zone3Volume = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE3_VOLUME, this.zone3Volume);
            // update the volume in dB too
            this.zone3VolumeDB = DecimalType
                    .valueOf(volume.subtract(DenonMarantzBindingConstants.DB_OFFSET).toString());
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE3_VOLUME_DB, this.zone3VolumeDB);
        }

    }

    public void setZone3Mute(boolean mute) {
        OnOffType newVal = mute ? OnOffType.ON : OnOffType.OFF;
        if (newVal != this.zone3Mute) {
            this.zone3Mute = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE3_MUTE, this.zone3Mute);
        }
    }

    public void setZone3Input(String zone3Input) {
        StringType newVal = StringType.valueOf(zone3Input);
        if (!newVal.equals(this.zone3Input)) {
            this.zone3Input = newVal;
            handler.stateChanged(DenonMarantzBindingConstants.CHANNEL_ZONE2_INPUT, this.zone3Input);
        }
    }
}
