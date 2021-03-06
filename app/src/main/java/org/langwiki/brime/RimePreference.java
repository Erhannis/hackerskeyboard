package org.langwiki.brime;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.view.View;

import org.langwiki.brime.schema.SchemaManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RimePreference extends PreferenceActivity implements Preference.OnPreferenceChangeListener {
    private static final String TAG = IMEConfig.TAG + "-Pref";

    private List<CheckBoxPreference> mSchemaPrefs = new ArrayList<>();
    private SettingManager mSettingsManager;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mSettingsManager = new SettingManager(this);
        addPreferencesFromResource(R.xml.pref_rime);
    }

    private void refresh() {
        // Add schema checkboxes
        List<Map<String, String>> schemas = Rime.getInstance().get_available_schema_list();

        PreferenceGroup parent = getPreferenceScreen();
        PreferenceCategory schemaParent = (PreferenceCategory)findPreference("rime_schemata");

        schemaParent.removeAll();
        mSchemaPrefs.clear();

        String selectedId = mSettingsManager.getCurrentRimeSchemaId();
        CheckBoxPreference first = null;
        CheckBoxPreference selected = null;

        if (schemas != null) {
            for (Map<String, String> schema : schemas) {
                String name = schema.get("name");
                String id = schema.get("schema_id");

                CheckBoxPreference pref = new CheckBoxPreference(this);
                pref.setTitle(name);
                pref.setKey(id);

                if (first == null) {
                    first = pref;
                }

                // select previous schema or first
                if (selected == null && selectedId != null && selectedId.equals(id)) {
                    selected = pref;
                }

                pref.setOnPreferenceChangeListener(this);
                mSchemaPrefs.add(pref);
                schemaParent.addPreference(pref);
                pref.setChecked(false);
            }
        }

        // Selected is gone. Use first.
        if (selected == null && first != null) {
            selected = first;
        }

        // Toggle here to avoid invoking listeners before all prefs are added
        if (selected != null) {
            selected.setChecked(true);
        }
    }

    public void onDeployButton(View v) {
        SchemaManager.getInstance().redeploy(this, false, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        boolean value = ((Boolean)newValue).booleanValue();

        // Cannot deselect last schema
        if (value == false && preference.getKey().equals(mSettingsManager.getCurrentRimeSchemaId())) {
            return false;
        }

        // Enforce one selection
        if (value) {
            for (CheckBoxPreference p : mSchemaPrefs) {
                if (!p.getKey().equals(preference.getKey())) {
                    p.setChecked(false);
                }
            }

            // Save
            mSettingsManager.setCurrentRimeSchemaId(preference.getKey());

            // Enable
            SchemaManager.getInstance().selectSchema(preference.getKey());
        }

        return true;
    }
}
