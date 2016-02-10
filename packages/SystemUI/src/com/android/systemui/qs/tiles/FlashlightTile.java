/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs.tiles;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.FlashlightController;

import cyanogenmod.app.StatusBarPanelCustomTile;

import java.util.Arrays;

/** Quick settings tile: Control flashlight **/
public class FlashlightTile extends QSTile<QSTile.BooleanState> implements
        FlashlightController.FlashlightListener {

    private static final String TORCH_OFF_ENTRIES_NAME = "torch_screen_off_delay_entries";
    private static final String TORCH_OFF_VALUES_NAME = "torch_screen_off_delay_values";
    private static final String SETTINGS_PACKAGE_NAME = "com.android.settings";

    private final AnimationIcon mEnable
            = new AnimationIcon(R.drawable.ic_signal_flashlight_enable_animation);
    private final AnimationIcon mDisable
            = new AnimationIcon(R.drawable.ic_signal_flashlight_disable_animation);

    private final FlashlightController mFlashlightController;
    private final FlashlightDetailAdapter mDetailAdapter;
    private String[] mEntries, mValues;

    public FlashlightTile(Host host) {
        super(host);
        mFlashlightController = host.getFlashlightController();
        mFlashlightController.addListener(this);
        mDetailAdapter = new FlashlightDetailAdapter();
        populateList();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mFlashlightController.removeListener(this);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    private ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
        }
    };

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.DISABLE_TORCH_ON_SCREEN_OFF),
                    false, mObserver);
            mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.DISABLE_TORCH_ON_SCREEN_OFF_DELAY),
                    false, mObserver);
        } else {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
    }

    @Override
    protected void handleUserSwitch(int newUserId) {
    }

    @Override
    protected void handleClick() {
        if (ActivityManager.isUserAMonkey()) {
            return;
        }
        MetricsLogger.action(mContext, getMetricsCategory(), !mState.value);
        boolean newState = !mState.value;
        refreshState(newState ? UserBoolean.USER_TRUE : UserBoolean.USER_FALSE);
        mFlashlightController.setFlashlight(newState);
    }

    @Override
    protected void handleLongClick() {
        showDetail(true);
    }

    @Override
    protected void handleSecondaryClick() {
        showDetail(true);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = mFlashlightController.isAvailable();
        state.label = mHost.getContext().getString(R.string.quick_settings_flashlight_label);
        if (arg instanceof UserBoolean) {
            boolean value = ((UserBoolean) arg).value;
            if (value == state.value) {
                return;
            }
            state.value = value;
        } else {
            state.value = mFlashlightController.isEnabled();
        }
        final AnimationIcon icon = state.value ? mEnable : mDisable;
        icon.setAllowAnimation(arg instanceof UserBoolean && ((UserBoolean) arg).userInitiated);
        state.icon = icon;
        int onOrOffId = state.value
                ? R.string.accessibility_quick_settings_flashlight_on
                : R.string.accessibility_quick_settings_flashlight_off;
        state.contentDescription = mContext.getString(onOrOffId);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.QS_FLASHLIGHT;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_flashlight_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_flashlight_changed_off);
        }
    }

    @Override
    public void onFlashlightChanged(boolean enabled) {
        refreshState(enabled ? UserBoolean.BACKGROUND_TRUE : UserBoolean.BACKGROUND_FALSE);
    }

    @Override
    public void onFlashlightError() {
        refreshState(UserBoolean.BACKGROUND_FALSE);
    }

    @Override
    public void onFlashlightAvailabilityChanged(boolean available) {
        refreshState();
    }

    private Runnable mRecentlyOnTimeout = new Runnable() {
        @Override
        public void run() {
            refreshState();
        }
    };

    private void populateList() {
        try {
            Context context = mContext.createPackageContext(SETTINGS_PACKAGE_NAME, 0);
            Resources mSettingsResources = context.getResources();
            int id = mSettingsResources.getIdentifier(TORCH_OFF_ENTRIES_NAME,
                    "array", SETTINGS_PACKAGE_NAME);
            if (id <= 0) {
                return;
            }
            mEntries = mSettingsResources.getStringArray(id);
            id = mSettingsResources.getIdentifier(TORCH_OFF_VALUES_NAME,
                    "array", SETTINGS_PACKAGE_NAME);
            if (id <= 0) {
                return;
            }
            mValues = mSettingsResources.getStringArray(id);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void toggleState() {
        Settings.System.putInt(mContext.getContentResolver(),
            Settings.System.DISABLE_TORCH_ON_SCREEN_OFF, isDisableFlashlightEnabled() ? 0 : 1);
    }

    private boolean isDisableFlashlightEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DISABLE_TORCH_ON_SCREEN_OFF, 0) == 1;
    }

    private int getFlashlightTimeout() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DISABLE_TORCH_ON_SCREEN_OFF_DELAY, 0);
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return new FlashlightDetailAdapter();
    }

    private class RadioAdapter extends ArrayAdapter<String> {

        public RadioAdapter(Context context, int resource, String[] objects) {
            super(context, resource, objects);
        }

        public RadioAdapter(Context context, int resource,
                            int textViewResourceId, String[] objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            view = super.getView(position, view, parent);

            view.setMinimumHeight(mContext.getResources() .getDimensionPixelSize(
                    R.dimen.qs_detail_item_height));

            return view;
        }
    }

    private class FlashlightDetailAdapter implements DetailAdapter,
            AdapterView.OnItemClickListener {
        private QSDetailItemsList mItems;

        @Override
        public int getTitle() {
            return R.string.disable_torch_screen_off_title;
        }

        @Override
        public Boolean getToggleState() {
            boolean enabled = isDisableFlashlightEnabled();
            return enabled;
        }

        @Override
        public Intent getSettingsIntent() {
            return null;
        }

        @Override
        public StatusBarPanelCustomTile getCustomTile() {
            return null;
        }

        @Override
        public int getMetricsCategory() {
            return MetricsLogger.QS_FLASHLIGHT;
        }

        @Override
        public void setToggleState(boolean state) {
            toggleState();
            fireToggleStateChanged(state);
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mItems = QSDetailItemsList.convertOrInflate(context, convertView, parent);
            ListView listView = mItems.getListView();
            listView.setOnItemClickListener(this);
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            listView.setDivider(null);
            RadioAdapter adapter = new RadioAdapter(context,
                    android.R.layout.simple_list_item_single_choice, mEntries);
            int indexOfSelection = Arrays.asList(mValues).indexOf(String.valueOf(getFlashlightTimeout()));
            mItems.setAdapter(adapter);
            listView.setItemChecked(indexOfSelection, true);

            return mItems;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            int selectedTimeout = Integer.valueOf(mValues[position]);
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.DISABLE_TORCH_ON_SCREEN_OFF_DELAY, selectedTimeout);
        }
    }
}
