package ru.turbovadim.abilities

import net.kyori.adventure.key.Key
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import ru.turbovadim.OriginSwapper.LineData.Companion.makeLineFor
import ru.turbovadim.OriginSwapper.LineData.LineComponent
import ru.turbovadim.OriginsRebornEnhanced.Companion.NMSInvoker

class NineLives : AttributeModifierAbility, VisibleAbility {
    override fun getKey(): Key {
        return Key.key("origins:nine_lives")
    }

    override val description: MutableList<LineComponent> = makeLineFor(
        "You have 1 less heart of health than humans.",
        LineComponent.LineType.DESCRIPTION
    )

    override val title: MutableList<LineComponent> = makeLineFor(
        "Nine Lives",
        LineComponent.LineType.TITLE
    )

    override val attribute: Attribute = NMSInvoker.maxHealthAttribute

    override val amount: Double = -2.0

    override val operation: AttributeModifier.Operation = AttributeModifier.Operation.ADD_NUMBER
}
