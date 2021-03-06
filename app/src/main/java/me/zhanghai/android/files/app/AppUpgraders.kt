/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.content.SharedPreferences
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.core.content.edit
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.PreferenceManagerCompat
import me.zhanghai.android.files.compat.writeBooleanCompat
import me.zhanghai.android.files.compat.writeParcelableListCompat
import me.zhanghai.android.files.filelist.FileSortOptions
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.StandardDirectorySettings
import me.zhanghai.android.files.provider.common.ByteString
import me.zhanghai.android.files.provider.common.moveToByteString
import me.zhanghai.android.files.util.asBase64
import me.zhanghai.android.files.util.toBase64
import me.zhanghai.android.files.util.toByteArray
import me.zhanghai.android.files.util.use

internal fun upgradeAppTo1_1_0(lastVersionCode: Int) {
    // Migrate settings.
    migratePathSetting(R.string.pref_key_file_list_default_directory)
    migrateFileSortOptionsSetting()
    migrateCreateArchiveTypeSetting()
    migrateStandardDirectorySettingsSetting()
    migrateBookmarkDirectoriesSetting()
    migratePathSetting(R.string.pref_key_ftp_server_home_directory)
    for (key in pathSharedPreferences.all.keys) {
        migrateFileSortOptionsSetting(pathSharedPreferences, key)
    }
}

private const val PARCEL_VAL_PARCELABLE = 4
private const val PARCEL_VAL_LIST = 11

private fun migratePathSetting(@StringRes keyRes: Int) {
    val key = application.getString(keyRes)
    val oldBytes = defaultSharedPreferences.getString(key, null)?.asBase64()?.toByteArray()
        ?: return
    val newBytes = Parcel.obtain().use { newParcel ->
        newParcel.writeInt(PARCEL_VAL_PARCELABLE)
        Parcel.obtain().use { oldParcel ->
            oldParcel.unmarshall(oldBytes, 0, oldBytes.size)
            oldParcel.setDataPosition(0)
            migratePath(oldParcel, newParcel)
        }
        newParcel.marshall()
    }
    defaultSharedPreferences.edit { putString(key, newBytes.toBase64().value) }
}

private fun migrateFileSortOptionsSetting() {
    migrateFileSortOptionsSetting(
        defaultSharedPreferences, application.getString(R.string.pref_key_file_list_sort_options)
    )
}

private fun migrateFileSortOptionsSetting(sharedPreferences: SharedPreferences, key: String) {
    val oldBytes = sharedPreferences.getString(key, null)?.asBase64()?.toByteArray() ?: return
    val newBytes = Parcel.obtain().use { newParcel ->
        newParcel.writeInt(PARCEL_VAL_PARCELABLE)
        Parcel.obtain().use { oldParcel ->
            oldParcel.unmarshall(oldBytes, 0, oldBytes.size)
            oldParcel.setDataPosition(0)
            newParcel.writeString(oldParcel.readString())
            newParcel.writeString(FileSortOptions.By.values()[oldParcel.readInt()].name)
            newParcel.writeString(FileSortOptions.Order.values()[oldParcel.readInt()].name)
            newParcel.writeInt(oldParcel.readByte().toInt())
        }
        newParcel.marshall()
    }
    sharedPreferences.edit { putString(key, newBytes.toBase64().value) }
}

fun migrateCreateArchiveTypeSetting() {
    val key = application.getString(R.string.pref_key_create_archive_type)
    val oldValue = defaultSharedPreferences.getString(key, null) ?: return
    val newValue = oldValue.replace(Regex("type_.+$")) {
        when (it.value) {
            "type_zip" -> "zipRadio"
            "type_tar_xz" -> "tarXzRadio"
            "type_seven_z" -> "sevenZRadio"
            else -> "zipRadio"
        }
    }
    defaultSharedPreferences.edit { putString(key, newValue) }
}

private fun migrateStandardDirectorySettingsSetting() {
    val key = application.getString(R.string.pref_key_standard_directory_settings)
    val oldBytes = defaultSharedPreferences.getString(key, null)?.asBase64()?.toByteArray()
        ?: return
    val newBytes = Parcel.obtain().use { newParcel ->
        newParcel.writeInt(PARCEL_VAL_LIST)
        Parcel.obtain().use { oldParcel ->
            oldParcel.unmarshall(oldBytes, 0, oldBytes.size)
            oldParcel.setDataPosition(0)
            val size = oldParcel.readInt()
            newParcel.writeInt(size)
            repeat(size) {
                oldParcel.readInt()
                newParcel.writeInt(PARCEL_VAL_PARCELABLE)
                newParcel.writeString(StandardDirectorySettings::class.java.name)
                newParcel.writeString(oldParcel.readString())
                newParcel.writeString(oldParcel.readString())
                newParcel.writeInt(oldParcel.readByte().toInt())
            }
        }
        newParcel.marshall()
    }
    defaultSharedPreferences.edit { putString(key, newBytes.toBase64().value) }
}

private fun migrateBookmarkDirectoriesSetting() {
    val key = application.getString(R.string.pref_key_bookmark_directories)
    val oldBytes = defaultSharedPreferences.getString(key, null)?.asBase64()?.toByteArray()
        ?: return
    val newBytes = Parcel.obtain().use { newParcel ->
        newParcel.writeInt(PARCEL_VAL_LIST)
        Parcel.obtain().use { oldParcel ->
            oldParcel.unmarshall(oldBytes, 0, oldBytes.size)
            oldParcel.setDataPosition(0)
            val size = oldParcel.readInt()
            newParcel.writeInt(size)
            repeat(size) {
                oldParcel.readInt()
                newParcel.writeInt(PARCEL_VAL_PARCELABLE)
                newParcel.writeString(BookmarkDirectory::class.java.name)
                newParcel.writeLong(oldParcel.readLong())
                newParcel.writeString(oldParcel.readString())
                migratePath(oldParcel, newParcel)
            }
        }
        newParcel.marshall()
    }
    defaultSharedPreferences.edit { putString(key, newBytes.toBase64().value) }
}

private val oldByteStringCreator = object : Parcelable.Creator<ByteString> {
    override fun createFromParcel(source: Parcel): ByteString =
        source.createByteArray()!!.moveToByteString()

    override fun newArray(size: Int): Array<ByteString?> = arrayOfNulls(size)
}

private fun migratePath(oldParcel: Parcel, newParcel: Parcel) {
    newParcel.writeString(oldParcel.readString())
    newParcel.writeByte(oldParcel.readByte())
    newParcel.writeBooleanCompat(oldParcel.readByte() != 0.toByte())
    newParcel.writeParcelableListCompat(oldParcel.createTypedArrayList(oldByteStringCreator), 0)
}

private val pathSharedPreferences: SharedPreferences
    get() {
        val name = "${PreferenceManagerCompat.getDefaultSharedPreferencesName(application)}_path"
        val mode = PreferenceManagerCompat.defaultSharedPreferencesMode
        return application.getSharedPreferences(name, mode)
    }
