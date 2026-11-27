package rhynia.nyx.common.mte.gui

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.utils.MouseData
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler
import com.cleanroommc.modularui.widgets.ButtonWidget

/** Apply machine button actions only when the server receives the click. */
internal fun machineActionButton(overlay: IDrawable, action: (MouseData) -> Unit): ButtonWidget<*> {
    val button: ButtonWidget<*> = ButtonWidget()
    return button
        .overlay(overlay)
        .size(18)
        .syncHandler(
            InteractionSyncHandler().setOnMousePressed { click ->
                if (!click.isClient) action(click)
            },
        )
}
