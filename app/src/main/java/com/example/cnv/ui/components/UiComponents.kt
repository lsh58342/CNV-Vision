package com.example.cnv.ui.components

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import com.example.cnv.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

/**
 * Shared Operation UI components (Phase 2).
 * Layout includes only — no domain / Repository logic.
 */
object UiComponents {

    fun inflatePrimaryButton(parent: ViewGroup, text: CharSequence): MaterialButton {
        val button = LayoutInflater.from(parent.context)
            .inflate(R.layout.component_primary_button, parent, false) as MaterialButton
        button.text = text
        return button
    }

    fun inflateSecondaryButton(parent: ViewGroup, text: CharSequence): MaterialButton {
        val button = LayoutInflater.from(parent.context)
            .inflate(R.layout.component_secondary_button, parent, false) as MaterialButton
        button.text = text
        return button
    }

    fun inflateSectionHeader(parent: ViewGroup, title: CharSequence): TextView {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.component_section_header, parent, false) as TextView
        view.text = title
        return view
    }

    fun inflateSearchBar(parent: ViewGroup): View =
        LayoutInflater.from(parent.context).inflate(R.layout.component_search_bar, parent, false)

    fun searchInput(searchBar: View): TextInputEditText =
        searchBar.findViewById(R.id.search_bar_input)

    fun inflateStatusCard(
        parent: ViewGroup,
        label: CharSequence,
        value: CharSequence,
        @ColorInt valueColor: Int,
    ): View {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.component_status_card, parent, false)
        view.findViewById<TextView>(R.id.status_card_label).text = label
        val valueView = view.findViewById<TextView>(R.id.status_card_value)
        valueView.text = value
        valueView.setTextColor(valueColor)
        return view
    }

    fun inflateInfoCard(parent: ViewGroup, title: CharSequence, body: CharSequence): View {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.component_info_card, parent, false)
        view.findViewById<TextView>(R.id.info_card_title).text = title
        view.findViewById<TextView>(R.id.info_card_body).text = body
        return view
    }

    fun inflateEmptyView(parent: ViewGroup, message: CharSequence): View {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.component_empty_view, parent, false)
        view.findViewById<TextView>(R.id.empty_view_message).text = message
        return view
    }

    fun inflateLoadingView(parent: ViewGroup): View =
        LayoutInflater.from(parent.context).inflate(R.layout.component_loading_view, parent, false)

    fun setEmptyVisible(emptyView: View, visible: Boolean) {
        emptyView.isVisible = visible
    }

    fun setLoadingVisible(loadingView: View, visible: Boolean) {
        loadingView.isVisible = visible
    }

    fun inflateSelectCard(
        parent: ViewGroup,
        title: CharSequence,
        subtitle: CharSequence,
        selected: Boolean,
        onClick: () -> Unit,
    ): MaterialCardView {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_card, parent, false) as MaterialCardView
        card.findViewById<TextView>(R.id.select_card_title).text = title
        card.findViewById<TextView>(R.id.select_card_subtitle).text = subtitle
        card.strokeWidth = if (selected) 3 else 1
        card.strokeColor = if (selected) {
            parent.context.getColor(R.color.cnv_accent)
        } else {
            parent.context.getColor(R.color.cnv_outline)
        }
        card.setOnClickListener { onClick() }
        return card
    }

    fun inflateZoneCard(
        parent: ViewGroup,
        name: CharSequence,
        lastInspection: CharSequence,
        colorHex: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): MaterialCardView {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_zone_card, parent, false) as MaterialCardView
        card.findViewById<TextView>(R.id.zone_card_name).text = name
        card.findViewById<TextView>(R.id.zone_card_inspection).text =
            parent.context.getString(R.string.op_zone_last_inspection, lastInspection)
        val colorView = card.findViewById<View>(R.id.zone_card_color)
        colorView.setBackgroundColor(parseColorSafe(colorHex))
        card.strokeWidth = if (selected) 3 else 1
        card.strokeColor = if (selected) {
            parent.context.getColor(R.color.cnv_accent)
        } else {
            parent.context.getColor(R.color.cnv_outline)
        }
        card.setOnClickListener { onClick() }
        return card
    }

    fun inflateDrawingCard(
        parent: ViewGroup,
        name: CharSequence,
        status: CharSequence,
        statusColor: Int,
        recentInspection: CharSequence,
        routeLock: CharSequence,
        selected: Boolean,
        onClick: () -> Unit,
    ): MaterialCardView {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drawing_card, parent, false) as MaterialCardView
        card.findViewById<TextView>(R.id.drawing_card_name).text = name
        val statusView = card.findViewById<TextView>(R.id.drawing_card_status)
        statusView.text = status
        statusView.setTextColor(statusColor)
        card.findViewById<TextView>(R.id.drawing_card_inspection).text = recentInspection
        card.findViewById<TextView>(R.id.drawing_card_lock).text = routeLock
        card.strokeWidth = if (selected) 3 else 1
        card.strokeColor = if (selected) {
            parent.context.getColor(R.color.cnv_accent)
        } else {
            statusColor
        }
        card.setOnClickListener { onClick() }
        return card
    }

    @Deprecated("Use inflateDrawingCard with statusColor and routeLock")
    fun inflateDrawingCard(
        parent: ViewGroup,
        name: CharSequence,
        status: CharSequence,
        recentInspection: CharSequence,
        updatedAt: CharSequence,
        selected: Boolean,
        onClick: () -> Unit,
    ): MaterialCardView = inflateDrawingCard(
        parent = parent,
        name = name,
        status = status,
        statusColor = parent.context.getColor(R.color.cnv_text_primary),
        recentInspection = recentInspection,
        routeLock = updatedAt,
        selected = selected,
        onClick = onClick,
    )

    fun clearChildren(container: LinearLayout) {
        container.removeAllViews()
    }

    @ColorInt
    fun statusColor(context: android.content.Context, status: String): Int {
        return when (status.uppercase()) {
            "OK" -> context.getColor(R.color.cnv_status_ok)
            "WARN", "WARNING" -> context.getColor(R.color.cnv_status_warn)
            else -> context.getColor(R.color.cnv_status_missing)
        }
    }

    @ColorInt
    private fun parseColorSafe(hex: String): Int = runCatching {
        Color.parseColor(hex)
    }.getOrElse { Color.GRAY }
}
