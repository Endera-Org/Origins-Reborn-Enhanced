package ru.turbovadim.abilities.types

import org.bukkit.entity.Player

interface DependencyAbility : Ability {
    fun isEnabled(player: Player): Boolean
}
