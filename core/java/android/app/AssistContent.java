/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.app;

import android.content.ClipData;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Holds information about the content an application is viewing, to hand to an
 * assistant at the user's request.  This is filled in by
 * {@link Activity#onProvideAssistContent Activity.onProvideAssistContent}.
 */
public class AssistContent implements Parcelable {
    private Intent mIntent;
    private ClipData mClipData;

    /**
     * Key name this data structure is stored in the Bundle generated by
     * {@link Activity#onProvideAssistData}.
     */
    public static final String ASSIST_KEY = "android:assist_content";

    /**
     * Retrieve the framework-generated AssistContent that is stored within
     * the Bundle filled in by {@link Activity#onProvideAssistContent}.
     */
    public static AssistContent getAssistContent(Bundle assistBundle) {
        return assistBundle.getParcelable(ASSIST_KEY);
    }

    public AssistContent() {
    }

    /**
     * Sets the Intent associated with the content, describing the current top-level context of
     * the activity.  If this contains a reference to a piece of data related to the activity,
     * be sure to set {@link Intent#FLAG_GRANT_READ_URI_PERMISSION} so the accessibility
     * service can access it.
     */
    public void setIntent(Intent intent) {
        mIntent = intent;
    }

    /**
     * Return the current {@link #setIntent}, which you can modify in-place.
     */
    public Intent getIntent() {
        return mIntent;
    }

    /**
     * Optional additional content items that are involved with
     * the current UI.  Access to this content will be granted to the assistant as if you
     * are sending it through an Intent with {@link Intent#FLAG_GRANT_READ_URI_PERMISSION}.
     */
    public void setClipData(ClipData clip) {
        mClipData = clip;
    }

    /**
     * Return the current {@link #setClipData}, which you can modify in-place.
     */
    public ClipData getClipData() {
        return mClipData;
    }

    AssistContent(Parcel in) {
        if (in.readInt() != 0) {
            mIntent = Intent.CREATOR.createFromParcel(in);
        }
        if (in.readInt() != 0) {
            mClipData = ClipData.CREATOR.createFromParcel(in);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mIntent != null) {
            dest.writeInt(1);
            mIntent.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (mClipData != null) {
            dest.writeInt(1);
            mClipData.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }

    public static final Parcelable.Creator<AssistContent> CREATOR
            = new Parcelable.Creator<AssistContent>() {
        public AssistContent createFromParcel(Parcel in) {
            return new AssistContent(in);
        }

        public AssistContent[] newArray(int size) {
            return new AssistContent[size];
        }
    };
}
