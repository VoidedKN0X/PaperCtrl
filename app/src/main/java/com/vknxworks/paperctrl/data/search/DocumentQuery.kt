package com.vknxworks.paperctrl.data.search

import androidx.annotation.StringRes
import com.vknxworks.paperctrl.R
import com.vknxworks.paperctrl.data.api.SavedView
import kotlinx.serialization.Serializable

@Serializable
enum class TextMode(@StringRes val label: Int) {
    TITLE_CONTENT(R.string.search_mode_title_content),
    TITLE(R.string.search_mode_title),
    FULLTEXT(R.string.search_mode_fulltext),
    ASN(R.string.search_mode_asn),
}

@Serializable
enum class SortField(val api: String, @StringRes val label: Int) {
    CREATED("created", R.string.sort_created),
    ADDED("added", R.string.sort_added),
    TITLE("title", R.string.sort_title),
    CORRESPONDENT("correspondent__name", R.string.sort_correspondent),
    ASN("archive_serial_number", R.string.sort_asn),
    SCORE("score", R.string.sort_relevance),
}

/** Everything the Documents tab can filter and sort by, turned into Paperless' list parameters. */
@Serializable
data class DocumentQuery(
    val text: String = "",
    val textMode: TextMode = TextMode.TITLE_CONTENT,
    val moreLike: Int? = null,
    val moreLikeTitle: String? = null,
    val tags: Set<Int> = emptySet(),
    val tagsMatchAll: Boolean = false,
    val tagsExcluded: Set<Int> = emptySet(),
    val correspondents: Set<Int> = emptySet(),
    val documentTypes: Set<Int> = emptySet(),
    val storagePaths: Set<Int> = emptySet(),
    val owners: Set<Int> = emptySet(),
    val customFields: Set<Int> = emptySet(),
    val createdFrom: String? = null,
    val createdTo: String? = null,
    val addedFrom: String? = null,
    val addedTo: String? = null,
    val inboxOnly: Boolean = false,
    val sort: SortField = SortField.CREATED,
    val reverse: Boolean = true,
    /** Raw parameters from a saved view's rules. */
    val extra: Map<String, String> = emptyMap(),
    /** Name of the saved view this came from, while it's unchanged. */
    val savedViewId: Int? = null,
) {
    val filterCount: Int
        get() = listOf(
            tags.isNotEmpty(), tagsExcluded.isNotEmpty(), correspondents.isNotEmpty(), documentTypes.isNotEmpty(),
            storagePaths.isNotEmpty(), owners.isNotEmpty(), customFields.isNotEmpty(), createdFrom != null || createdTo != null,
            addedFrom != null || addedTo != null, inboxOnly, extra.isNotEmpty(),
        ).count { it }

    val isFullText: Boolean get() = (text.isNotBlank() && textMode == TextMode.FULLTEXT) || moreLike != null

    fun toParams(): Map<String, String> {
        val p = linkedMapOf<String, String>()
        p.putAll(extra)
        val t = text.trim()
        if (t.isNotEmpty()) {
            when (textMode) {
                TextMode.TITLE_CONTENT -> p["title_content"] = t
                TextMode.TITLE -> p["title__icontains"] = t
                TextMode.FULLTEXT -> p["query"] = t
                TextMode.ASN -> t.filter(Char::isDigit).takeIf { it.isNotEmpty() }?.let { p["archive_serial_number"] = it }
            }
        }
        moreLike?.let { p["more_like_id"] = "$it" }
        if (tags.isNotEmpty()) p[if (tagsMatchAll) "tags__id__all" else "tags__id__in"] = tags.joinToString(",")
        if (tagsExcluded.isNotEmpty()) p["tags__id__none"] = tagsExcluded.joinToString(",")
        if (correspondents.isNotEmpty()) p["correspondent__id__in"] = correspondents.joinToString(",")
        if (documentTypes.isNotEmpty()) p["document_type__id__in"] = documentTypes.joinToString(",")
        if (storagePaths.isNotEmpty()) p["storage_path__id__in"] = storagePaths.joinToString(",")
        if (owners.isNotEmpty()) p["owner__id__in"] = owners.joinToString(",")
        if (customFields.isNotEmpty()) p["custom_fields__id__in"] = customFields.joinToString(",")
        createdFrom?.let { p["created__gte"] = it }
        createdTo?.let { p["created__lte"] = it }
        addedFrom?.let { p["added__date__gte"] = it }
        addedTo?.let { p["added__date__lte"] = it }
        if (inboxOnly) p["is_in_inbox"] = "true"
        // Full-text results come sorted by relevance unless another order was picked.
        if (!(isFullText && sort == SortField.SCORE)) {
            val field = if (sort == SortField.SCORE) SortField.CREATED.api else sort.api
            p["ordering"] = (if (reverse) "-" else "") + field
        }
        return p
    }

    companion object {
        /**
         * A saved view's filter rules as list parameters, the way the Paperless web app applies them.
         * Rules that several values can share (tags, correspondents…) are combined.
         */
        fun fromSavedView(view: SavedView): DocumentQuery {
            val p = linkedMapOf<String, String>()
            fun add(key: String, v: String?) {
                val value = v ?: return
                p[key] = p[key]?.let { "$it,$value" } ?: value
            }
            for (r in view.filterRules) {
                val v = r.value
                when (r.ruleType) {
                    0 -> p["title__icontains"] = v.orEmpty()
                    1 -> p["content__icontains"] = v.orEmpty()
                    2 -> p["archive_serial_number"] = v.orEmpty()
                    3 -> if (v == null) p["correspondent__isnull"] = "1" else p["correspondent__id"] = v
                    4 -> if (v == null) p["document_type__isnull"] = "1" else p["document_type__id"] = v
                    5 -> p["is_in_inbox"] = v ?: "true"
                    6 -> add("tags__id__all", v)
                    7 -> p["is_tagged"] = v ?: "true"
                    8 -> p["created__lt"] = v.orEmpty()
                    9 -> p["created__gt"] = v.orEmpty()
                    10 -> p["created__year"] = v.orEmpty()
                    11 -> p["created__month"] = v.orEmpty()
                    12 -> p["created__day"] = v.orEmpty()
                    13 -> p["added__date__lt"] = v.orEmpty()
                    14 -> p["added__date__gt"] = v.orEmpty()
                    15 -> p["modified__date__lt"] = v.orEmpty()
                    16 -> p["modified__date__gt"] = v.orEmpty()
                    17 -> add("tags__id__none", v)
                    18 -> p["archive_serial_number__isnull"] = v ?: "1"
                    19 -> p["title_content"] = v.orEmpty()
                    20 -> p["query"] = v.orEmpty()
                    21 -> p["more_like_id"] = v.orEmpty()
                    22 -> add("tags__id__in", v)
                    23 -> p["archive_serial_number__gt"] = v.orEmpty()
                    24 -> p["archive_serial_number__lt"] = v.orEmpty()
                    25 -> if (v == null) p["storage_path__isnull"] = "1" else p["storage_path__id"] = v
                    26 -> add("correspondent__id__in", v)
                    27 -> add("correspondent__id__none", v)
                    28 -> add("document_type__id__in", v)
                    29 -> add("document_type__id__none", v)
                    30 -> add("storage_path__id__in", v)
                    31 -> add("storage_path__id__none", v)
                    32 -> p["owner__id"] = v.orEmpty()
                    33 -> add("owner__id__in", v)
                    34 -> p["owner__isnull"] = v ?: "1"
                    35 -> add("owner__id__none", v)
                    36 -> p["custom_fields__icontains"] = v.orEmpty()
                    37 -> p["shared_by__id"] = v.orEmpty()
                    38 -> p["has_custom_fields"] = v ?: "1"
                    39 -> add("custom_fields__id__in", v)
                    40 -> add("custom_fields__id__none", v)
                    41 -> p["has_custom_fields"] = "0"
                    42 -> p["custom_field_query"] = v.orEmpty()
                    43 -> p["created__lte"] = v.orEmpty()
                    44 -> p["created__gte"] = v.orEmpty()
                    45 -> p["added__date__lte"] = v.orEmpty()
                    46 -> p["added__date__gte"] = v.orEmpty()
                    47 -> p["mime_type"] = v.orEmpty()
                    48 -> p["title__icontains"] = v.orEmpty()
                    49 -> p["title_content"] = v.orEmpty()
                    50 -> p["has_duplicates"] = v ?: "true"
                }
            }
            val sort = SortField.entries.firstOrNull { it.api == view.sortField?.removePrefix("-") } ?: SortField.CREATED
            return DocumentQuery(sort = sort, reverse = view.sortReverse, extra = p.filterValues { it.isNotEmpty() }, savedViewId = view.id)
        }
    }
}
