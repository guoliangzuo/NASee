package com.nasee.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 文件夹树节点，对应服务端 FolderNode。
 *
 * @property path 文件夹路径（如 /movies）
 * @property name 显示名称（如 movies）
 * @property count 该文件夹下的视频数量
 * @property children 子文件夹列表
 */
data class FolderNode(
    @SerializedName("path")
    val path: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("count")
    val count: Int,

    @SerializedName("children")
    val children: List<FolderNode> = emptyList()
) {
    /**
     * 递归计算所有子文件夹的视频总数。
     */
    fun totalVideoCount(): Int {
        return count + children.sumOf { it.totalVideoCount() }
    }

    /**
     * 判断是否有子文件夹。
     */
    fun hasChildren(): Boolean = children.isNotEmpty()
}

/**
 * 文件夹树列表响应数据。
 */
data class FolderListResponse(
    @SerializedName("folders")
    val folders: List<FolderNode> = emptyList()
)
