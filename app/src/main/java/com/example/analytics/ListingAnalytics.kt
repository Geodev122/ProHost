package com.example.analytics

import com.example.data.model.SpaceListing
import com.example.data.model.Subdivision
import com.example.data.model.publicCode
import com.example.ui.util.AttendeePricing
import com.example.ui.util.SpaceCalculationUtils

/** GA4 `items[]` entry for a listing, or one of its rooms when [subdivision] is given. */
internal fun SpaceListing.toAnalyticsItem(subdivision: Subdivision? = null, index: Int? = null): Map<String, Any> {
    val cfg = subdivision?.pricing ?: pricing
    val price = if (subdivision != null) {
        if (AttendeePricing.isPerAttendee(subdivision)) null
        else SpaceCalculationUtils.lowestPriceFor(cfg)?.amount
    } else {
        SpaceCalculationUtils.lowestPriceSummary(this).first
    }
    return buildMap {
        put(Param.ITEM_ID, publicCode)
        put(Param.ITEM_NAME, title)
        put(Param.ITEM_CATEGORY, spaceType.name)
        subdivision?.let { put(Param.ITEM_CATEGORY2, it.type.name) }
        put(Param.ITEM_CATEGORY3, cfg.strategyType.name)
        country.takeIf { it.isNotBlank() }?.let { put(Param.ITEM_CATEGORY4, it) }
        put(Param.ITEM_CATEGORY5, governorate.name)
        put(Param.ITEM_VARIANT, if (AttendeePricing.isPerAttendee(subdivision)) "per_attendee" else "per_booking")
        subdivision?.let { put(Param.ROOM_ID, it.publicCode) }
        price?.takeIf { it > 0 }?.let { put(Param.PRICE, it) }
        put(Param.CURRENCY, "USD")
        index?.let { put(Param.INDEX, it.toLong()) }
    }
}
