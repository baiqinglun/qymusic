package com.qymusic.player.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class FolderStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<MusicFolder> {
        val raw = preferences.getString(KEY_FOLDERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val uri = item.optString(KEY_URI)
                    val name = item.optString(KEY_NAME)
                    if (uri.isNotBlank() && name.isNotBlank()) {
                        add(MusicFolder(Uri.parse(uri), name))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(folders: List<MusicFolder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(
                JSONObject()
                    .put(KEY_URI, folder.uri.toString())
                    .put(KEY_NAME, folder.name),
            )
        }
        preferences.edit {
            putString(KEY_FOLDERS, array.toString())
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "qy_music_folders"
        const val KEY_FOLDERS = "folders"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
    }
}
