package ru.turbovadim.abilities

import net.kyori.adventure.key.Key
import net.kyori.adventure.util.TriState
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffectType
import ru.turbovadim.OriginsReforged
import ru.turbovadim.abilities.types.*
import ru.turbovadim.commands.FlightToggleCommand
import ru.turbovadim.cooldowns.CooldownAbility
import ru.turbovadim.packetsenders.NMSInvoker
import java.io.File
import java.io.IOException

object AbilityRegister {
    val abilityMap: MutableMap<Key, Ability> = mutableMapOf()
    val dependencyAbilityMap: MutableMap<Key, DependencyAbility> = mutableMapOf()
    val multiAbilityMap: MutableMap<Key, MutableList<MultiAbility>> = mutableMapOf()

    val origins: OriginsReforged = OriginsReforged.instance
    val nmsInvoker: NMSInvoker = OriginsReforged.NMSInvoker

    lateinit var attributeModifierAbilityFileConfig: FileConfiguration
    private lateinit var attributeModifierAbilityFile: File

    fun registerAbility(ability: Ability, instance: JavaPlugin) {
        if (ability is DependencyAbility) {
            dependencyAbilityMap[ability.key] = ability
        }

        if (ability is MultiAbility) {
            ability.abilities.forEach { a ->
                multiAbilityMap.getOrPut(a.key) { mutableListOf() }.add(ability)
            }
        }

        if (ability is CooldownAbility) {
            OriginsReforged.getCooldowns().registerCooldown(
                instance,
                ability.cooldownKey,
                requireNotNull(ability.cooldownInfo)
            )
        }

        if (ability is Listener) {
            Bukkit.getPluginManager().registerEvents(ability, instance)
        }

        if (ability is AttributeModifierAbility) {
            val formattedValueKey = "${ability.key}.value"
            val formattedOperationKey = "${ability.key}.operation"
            var changed = false

            if (!attributeModifierAbilityFileConfig.contains(ability.key.toString())) {
                attributeModifierAbilityFileConfig.set(formattedValueKey, "x")
                attributeModifierAbilityFileConfig.set(formattedOperationKey, "default")
                changed = true
            }

            if ("default" == attributeModifierAbilityFileConfig.get(formattedValueKey, "default")) {
                attributeModifierAbilityFileConfig.set(formattedValueKey, "x")
                changed = true
            }

            if (changed) {
                try {
                    attributeModifierAbilityFileConfig.save(attributeModifierAbilityFile)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }

        abilityMap[ability.key] = ability
    }

    fun canFly(player: Player, disabledWorld: Boolean): Boolean {
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR || FlightToggleCommand.canFly(player))
            return true
        if (disabledWorld) return false
        for (ability in abilityMap.values) {
            if (ability is FlightAllowingAbility) {
                if (ability.hasAbility(player) && ability.canFly(player)) return true
            }
        }
        return false
    }

    suspend fun isInvisible(player: Player): Boolean {
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return true
        for (ability in abilityMap.values) {
            if (ability is VisibilityChangingAbility) {
                if (ability.hasAbilityAsync(player) && ability.isInvisible(player)) return true
            }
        }
        return false
    }

    fun updateFlight(player: Player, inDisabledWorld: Boolean) {
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
            player.flySpeed = if (player.flySpeed < 0.1f) 0.1f else player.flySpeed
            return
        }

        if (FlightToggleCommand.canFly(player)) {
            player.flySpeed = 0.1f
            return
        }
        if (inDisabledWorld) return

        var flyingFallDamage: TriState = TriState.FALSE
        var speed = -1f

        for (ability in abilityMap.values) {
            if (ability !is FlightAllowingAbility) continue
            if (!ability.hasAbility(player) || !ability.canFly(player)) continue

            val abilitySpeed = ability.getFlightSpeed(player)
            speed = if (speed < 0f) abilitySpeed else minOf(speed, abilitySpeed)

            if (ability.getFlyingFallDamage(player) == TriState.TRUE) {
                flyingFallDamage = TriState.TRUE
            }
        }

        nmsInvoker.setFlyingFallDamage(player, flyingFallDamage)
        player.flySpeed = if (speed < 0f) 0f else speed
    }

    fun updateEntity(player: Player, target: Entity) {
        var data: Byte = 0

        if (target.fireTicks > 0) {
            data = (0 or 0x01).toByte()
        }
        if (target.isGlowing) {
            data = (data.toInt() or 0x40).toByte()
        }
        if (target is LivingEntity && target.isInvisible) {
            data = (data.toInt() or 0x20).toByte()
        }
        if (target is Player) {
            if (target.isSneaking) {
                data = (data.toInt() or 0x02).toByte()
            }
            if (target.isSprinting) {
                data = (data.toInt() or 0x08).toByte()
            }
            if (target.isSwimming) {
                data = (data.toInt() or 0x10).toByte()
            }
            if (target.isGliding) {
                data = (data.toInt() or 0x80).toByte()
            }

            val inventory = target.inventory
            for (slot in EquipmentSlot.entries) {
                try {
                    val item = inventory.getItem(slot)
                    player.sendEquipmentChange(target, slot, item)
                } catch (_: IllegalArgumentException) {
                    // If the slot is not supported, skip it
                }
            }
        }

        nmsInvoker.sendEntityData(player, target, data)
    }

    fun setupAMAF() {
        attributeModifierAbilityFile = File(origins.dataFolder, "attribute-modifier-ability-config.yml")
        if (!attributeModifierAbilityFile.exists()) {
            attributeModifierAbilityFile.parentFile.mkdirs()
            origins.saveResource("attribute-modifier-ability-config.yml", false)
        }

        attributeModifierAbilityFileConfig = YamlConfiguration()
        try {
            attributeModifierAbilityFileConfig.load(attributeModifierAbilityFile)
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: InvalidConfigurationException) {
            throw RuntimeException(e)
        }
    }
}
