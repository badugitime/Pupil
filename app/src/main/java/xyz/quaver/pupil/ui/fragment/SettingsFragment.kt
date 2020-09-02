/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2020  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.ui.fragment

import android.content.*
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import xyz.quaver.io.FileX
import xyz.quaver.io.util.getChild
import xyz.quaver.pupil.R
import xyz.quaver.pupil.ui.LockActivity
import xyz.quaver.pupil.ui.SettingsActivity
import xyz.quaver.pupil.ui.dialog.*
import xyz.quaver.pupil.util.*
import xyz.quaver.pupil.util.downloader.DownloadManager

class SettingsFragment :
    PreferenceFragmentCompat(),
    Preference.OnPreferenceClickListener,
    Preference.OnPreferenceChangeListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onResume() {
        super.onResume()

        val lockManager = LockManager(requireContext())

        findPreference<Preference>("app_lock")?.summary = if (lockManager.locks.isNullOrEmpty()) {
            getString(R.string.settings_lock_none)
        } else {
            lockManager.locks?.joinToString(", ") {
                when(it.type) {
                    Lock.Type.PATTERN -> getString(R.string.settings_lock_pattern)
                    Lock.Type.PIN -> getString(R.string.settings_lock_pin)
                    Lock.Type.PASSWORD -> getString(R.string.settings_lock_password)
                }
            }
        }
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        with (preference) {
            this ?: return false

            when (key) {
                "app_version" -> {
                    checkUpdate(activity as SettingsActivity, true)
                }
                "download_folder" -> {
                    DownloadLocationDialogFragment().show(requireActivity().supportFragmentManager, "Download Location Dialog")
                }
                "default_query" -> {
                    DefaultQueryDialog(requireContext()).apply {
                        onPositiveButtonClickListener = { newTags ->
                            Preferences["default_query"] = newTags.toString()
                            summary = newTags.toString()
                        }
                    }.show()
                }
                "app_lock" -> {
                    val intent = Intent(requireContext(), LockActivity::class.java)
                    activity?.startActivityForResult(intent, R.id.request_lock.normalizeID())
                }
                "mirrors" -> {
                    MirrorDialog(requireContext())
                        .show()
                }
                "proxy" -> {
                    ProxyDialog(requireContext())
                        .show()
                }
                "user_id" -> {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                        ClipData.newPlainText("user_id", Preferences.get<String>("user_id"))
                    )
                    Toast.makeText(context, R.string.settings_user_id_toast, Toast.LENGTH_SHORT).show()
                }
                else -> return false
            }
        }

        return true
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        with (preference) {
            this ?: return false

            when (key) {
                "nomedia" -> {
                    val create = (newValue as? Boolean) ?: return false

                    return kotlin.runCatching {
                        val nomedia = DownloadManager.getInstance(context).downloadFolder.getChild(".nomedia")

                        if (create)
                            nomedia.createNewFile()
                        else
                            nomedia.delete()
                    }.getOrDefault(false)
                }
                "dark_mode" -> {
                    AppCompatDelegate.setDefaultNightMode(when (newValue as Boolean) {
                        true -> AppCompatDelegate.MODE_NIGHT_YES
                        false -> AppCompatDelegate.MODE_NIGHT_NO
                    })
                }
                else -> return false
            }
        }

        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key ?: return

        with(findPreference<Preference>(key)) {
            this ?: return

            when (key) {
                "proxy" -> {
                    summary = context?.let { getProxyInfo().type.name }
                }
                "download_folder" -> {
                    summary = FileX(context, Preferences.get<String>("download_folder")).canonicalPath
                }
                "download_folder_name" -> {
                    summary = Preferences.get<String>("download_folder_name")
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        Preferences.registerOnSharedPreferenceChangeListener(this)

        initPreferences()
    }

    override fun onDestroy() {
        Preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    private fun initPreferences() {
        for (i in 0 until preferenceScreen.preferenceCount) {

            preferenceScreen.getPreference(i).run {
                if (this is PreferenceCategory)
                    (0 until preferenceCount).map { getPreference(it) }
                else
                    listOf(this)
            }.forEach { preference ->
                with (preference) with@{

                    when (key) {
                        "app_version" -> {
                            val manager = requireContext().packageManager
                            val info = manager.getPackageInfo(requireContext().packageName, 0)
                            summary = requireContext().getString(R.string.settings_app_version_description, info.versionName)

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "download_folder_name" -> {
                            summary = Preferences.get<String>("download_folder_name")

                            setOnPreferenceClickListener {
                                DownloadFolderNameDialogFragment().show(requireActivity().supportFragmentManager, "Download Location Dialog")

                                true
                            }
                        }
                        "download_folder" -> {
                            summary = FileX(context, Preferences.get<String>("download_folder")).canonicalPath

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "nomedia" -> {
                            (this as SwitchPreferenceCompat).isChecked = kotlin.runCatching {
                                DownloadManager.getInstance(context).downloadFolder.getChild(".nomedia").exists()
                            }.getOrDefault(false)

                            onPreferenceChangeListener = this@SettingsFragment
                        }
                        "default_query" -> {
                            summary = Preferences.get<String>("default_query")

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "app_lock" -> {
                            val lockManager = LockManager(requireContext())
                            summary =
                                if (lockManager.locks.isNullOrEmpty()) {
                                    getString(R.string.settings_lock_none)
                                } else {
                                    lockManager.locks?.joinToString(", ") {
                                        when (it.type) {
                                            Lock.Type.PATTERN -> getString(R.string.settings_lock_pattern)
                                            Lock.Type.PIN -> getString(R.string.settings_lock_pin)
                                            Lock.Type.PASSWORD -> getString(R.string.settings_lock_password)
                                        }
                                    }
                                }

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "mirrors" -> {
                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "proxy" -> {
                            summary = getProxyInfo().type.name

                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "dark_mode" -> {
                            onPreferenceChangeListener = this@SettingsFragment
                        }
                        "old_import_galleries" -> {
                            onPreferenceClickListener = this@SettingsFragment
                        }
                        "user_id" -> {
                            summary = Preferences.get<String>("user_id")
                            onPreferenceClickListener = this@SettingsFragment
                        }
                    }

                }
            }
        }
    }
}