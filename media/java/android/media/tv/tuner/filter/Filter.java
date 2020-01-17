/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.tv.tuner.filter;

import android.annotation.BytesLong;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.tv.tuner.V1_0.Constants;
import android.media.tv.tuner.Tuner.FilterCallback;
import android.media.tv.tuner.TunerConstants.Result;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Tuner data filter.
 *
 * <p>This class is used to filter wanted data according to the filter's configuration.
 *
 * @hide
 */
public class Filter implements AutoCloseable {
    /** @hide */
    @IntDef(prefix = "TYPE_",
            value = {TYPE_TS, TYPE_MMTP, TYPE_IP, TYPE_TLV, TYPE_ALP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    /**
     * TS filter type.
     */
    public static final int TYPE_TS = Constants.DemuxFilterMainType.TS;
    /**
     * MMTP filter type.
     */
    public static final int TYPE_MMTP = Constants.DemuxFilterMainType.MMTP;
    /**
     * IP filter type.
     */
    public static final int TYPE_IP = Constants.DemuxFilterMainType.IP;
    /**
     * TLV filter type.
     */
    public static final int TYPE_TLV = Constants.DemuxFilterMainType.TLV;
    /**
     * ALP filter type.
     */
    public static final int TYPE_ALP = Constants.DemuxFilterMainType.ALP;

    /** @hide */
    @IntDef(prefix = "SUBTYPE_",
            value = {SUBTYPE_UNDEFINED, SUBTYPE_SECTION, SUBTYPE_PES, SUBTYPE_AUDIO, SUBTYPE_VIDEO,
                    SUBTYPE_DOWNLOAD, SUBTYPE_RECORD, SUBTYPE_TS, SUBTYPE_PCR, SUBTYPE_TEMI,
                    SUBTYPE_MMTP, SUBTYPE_NTP, SUBTYPE_IP_PAYLOAD, SUBTYPE_IP,
                    SUBTYPE_PAYLOAD_THROUGH, SUBTYPE_TLV, SUBTYPE_PTP, })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Subtype {}
    /**
     * Filter subtype undefined.
     * @hide
     */
    public static final int SUBTYPE_UNDEFINED = 0;
    /**
     * Section filter subtype.
     * @hide
     */
    public static final int SUBTYPE_SECTION = 1;
    /**
     * PES filter subtype.
     * @hide
     */
    public static final int SUBTYPE_PES = 2;
    /**
     * Audio filter subtype.
     * @hide
     */
    public static final int SUBTYPE_AUDIO = 3;
    /**
     * Video filter subtype.
     * @hide
     */
    public static final int SUBTYPE_VIDEO = 4;
    /**
     * Download filter subtype.
     * @hide
     */
    public static final int SUBTYPE_DOWNLOAD = 5;
    /**
     * Record filter subtype.
     * @hide
     */
    public static final int SUBTYPE_RECORD = 6;
    /**
     * TS filter subtype.
     * @hide
     */
    public static final int SUBTYPE_TS = 7;
    /**
     * PCR filter subtype.
     * @hide
     */
    public static final int SUBTYPE_PCR = 8;
    /**
     * TEMI filter subtype.
     * @hide
     */
    public static final int SUBTYPE_TEMI = 9;
    /**
     * MMTP filter subtype.
     * @hide
     */
    public static final int SUBTYPE_MMTP = 10;
    /**
     * NTP filter subtype.
     * @hide
     */
    public static final int SUBTYPE_NTP = 11;
    /**
     * Payload filter subtype.
     * @hide
     */
    public static final int SUBTYPE_IP_PAYLOAD = 12;
    /**
     * IP filter subtype.
     * @hide
     */
    public static final int SUBTYPE_IP = 13;
    /**
     * Payload through filter subtype.
     * @hide
     */
    public static final int SUBTYPE_PAYLOAD_THROUGH = 14;
    /**
     * TLV filter subtype.
     * @hide
     */
    public static final int SUBTYPE_TLV = 15;
    /**
     * PTP filter subtype.
     * @hide
     */
    public static final int SUBTYPE_PTP = 16;


    private long mNativeContext;
    private FilterCallback mCallback;
    private final int mId;

    private native int nativeConfigureFilter(
            int type, int subType, FilterConfiguration settings);
    private native int nativeGetId();
    private native int nativeSetDataSource(Filter source);
    private native int nativeStartFilter();
    private native int nativeStopFilter();
    private native int nativeFlushFilter();
    private native int nativeRead(byte[] buffer, long offset, long size);
    private native int nativeClose();

    private Filter(int id) {
        mId = id;
    }

    private void onFilterStatus(int status) {
    }

    /**
     * Configures the filter.
     *
     * @param config the configuration of the filter.
     * @return result status of the operation.
     */
    @Result
    public int configure(@NonNull FilterConfiguration config) {
        int subType = -1;
        Settings s = config.getSettings();
        if (s != null) {
            subType = s.getType();
        }
        return nativeConfigureFilter(config.getType(), subType, config);
    }

    /**
     * Gets the filter Id.
     */
    @Result
    public int getId() {
        return nativeGetId();
    }

    /**
     * Sets the filter's data source.
     *
     * A filter uses demux as data source by default. If the data was packetized
     * by multiple protocols, multiple filters may need to work together to
     * extract all protocols' header. Then a filter's data source can be output
     * from another filter.
     *
     * @param source the filter instance which provides data input. Switch to
     * use demux as data source if the filter instance is NULL.
     * @return result status of the operation.
     */
    @Result
    public int setDataSource(@Nullable Filter source) {
        return nativeSetDataSource(source);
    }

    /**
     * Starts filtering data.
     *
     * @return result status of the operation.
     */
    @Result
    public int start() {
        return nativeStartFilter();
    }


    /**
     * Stops filtering data.
     *
     * @return result status of the operation.
     */
    @Result
    public int stop() {
        return nativeStopFilter();
    }

    /**
     * Flushes the filter. Data in filter buffer is cleared.
     *
     * @return result status of the operation.
     */
    @Result
    public int flush() {
        return nativeFlushFilter();
    }

    /**
     * Copies filtered data from filter buffer to the given byte array.
     *
     * @param buffer the buffer to store the filtered data.
     * @param offset the index of the first byte in {@code buffer} to write.
     * @param size the maximum number of bytes to read.
     * @return the number of bytes read.
     */
    @Result
    public int read(@NonNull byte[] buffer, @BytesLong long offset, @BytesLong long size) {
        size = Math.min(size, buffer.length - offset);
        return nativeRead(buffer, offset, size);
    }

    /**
     * Releases the Filter instance.
     */
    @Override
    public void close() {
        nativeClose();
    }
}
