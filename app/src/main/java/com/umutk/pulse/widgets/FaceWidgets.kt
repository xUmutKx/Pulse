package com.umutk.pulse.widgets

/** The drawn face widgets: two Windows 7 clocks of Pulse's own, five iOS tiles and one Windows 11 frosted clock. The real gadget clocks are in ThemeWidgets.kt. */
object FaceWidgets {
    val all: List<FaceWidget> by lazy { listOf(Win7Clock10Widget(), Win7Clock11Widget(), IosClockWidget(), IosDayWidget(), IosCalendarWidget(), IosBatteryWidget(), IosSunTimeWidget(), Win11FrostClockWidget()) }

    /** The design the Widgets page files a face under (a SkinFamily theme id). */
    fun theme(look: Int) = when (look) { 10, 11 -> "win7"; 17 -> "win11"; else -> "ios" }

    /** The name the Widgets page shows for a face. */
    val labels = mapOf(10 to "Clock · Clear", 11 to "Clock · Wood", 12 to "Clock", 13 to "Day", 14 to "Calendar", 15 to "Battery", 16 to "Sun and time", 17 to "Frosted clock")
}

/** Win7 clock transparent. */
class Win7Clock10Widget : FaceWidget(10)

/** Win7 clock wood. */
class Win7Clock11Widget : FaceWidget(11)

/** iOS clock. */
class IosClockWidget : FaceWidget(12)

/** iOS day. */
class IosDayWidget : FaceWidget(13)

/** iOS calendar month. */
class IosCalendarWidget : FaceWidget(14)

/** iOS battery ring with the charge percentage. */
class IosBatteryWidget : FaceWidget(15)

/** iOS sun and time. */
class IosSunTimeWidget : FaceWidget(16)

/** Windows 11 clock on a light frosted blue panel. */
class Win11FrostClockWidget : FaceWidget(17)
