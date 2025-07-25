package ru.turbovadim.abilities.main

import net.kyori.adventure.key.Key
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import ru.turbovadim.OriginSwapper.LineData.Companion.makeLineFor
import ru.turbovadim.OriginSwapper.LineData.LineComponent
import ru.turbovadim.abilities.types.VisibleAbility

class BurningWrath : VisibleAbility, Listener {

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        runForAbility(event.damager) { player ->
            if (player.fireTicks > 0) event.setDamage(event.damage + 3)
        }
    }

    override val key: Key = Key.key("origins:burning_wrath")

    override val description: MutableList<LineComponent> = makeLineFor(
            "When on fire, you deal additional damage with your attacks.",
            LineComponent.LineType.DESCRIPTION
        )

    override val title: MutableList<LineComponent> = makeLineFor("Burning Wrath", LineComponent.LineType.TITLE)

}
