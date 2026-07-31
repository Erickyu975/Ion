package net.horizonsend.ion.server.features.multiblock.type.processing

import net.horizonsend.ion.server.features.client.display.modular.DisplayHandlers
import net.horizonsend.ion.server.features.client.display.modular.display.PowerEntityDisplayModule
import net.horizonsend.ion.server.features.client.display.modular.display.StatusDisplayModule
import net.horizonsend.ion.server.features.multiblock.Multiblock
import net.horizonsend.ion.server.features.multiblock.entity.PersistentMultiblockData
import net.horizonsend.ion.server.features.multiblock.entity.type.FurnaceBasedMultiblockEntity
import net.horizonsend.ion.server.features.multiblock.entity.type.LegacyMultiblockEntity
import net.horizonsend.ion.server.features.multiblock.entity.type.StatusMultiblockEntity
import net.horizonsend.ion.server.features.multiblock.entity.type.power.SimplePoweredEntity
import net.horizonsend.ion.server.features.multiblock.entity.type.ticked.StatusTickedMultiblockEntity
import net.horizonsend.ion.server.features.multiblock.entity.type.ticked.SyncTickingMultiblockEntity
import net.horizonsend.ion.server.features.multiblock.entity.type.ticked.TickedMultiblockEntityParent.TickingManager
import net.horizonsend.ion.server.features.multiblock.manager.MultiblockManager
import net.horizonsend.ion.server.features.multiblock.shape.MultiblockShape
import net.horizonsend.ion.server.features.multiblock.type.DisplayNameMultilblock
import net.horizonsend.ion.server.features.multiblock.type.EntityMultiblock
import net.horizonsend.ion.server.miscellaneous.utils.LegacyItemUtils
import net.horizonsend.ion.server.miscellaneous.utils.LegacyItemUtils.addToInventory
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.NamedTextColor.GREEN
import net.kyori.adventure.text.format.NamedTextColor.RED
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.inventory.FurnaceInventory
import org.bukkit.inventory.ItemStack

object CrusherMultiblock : Multiblock(), EntityMultiblock<CrusherMultiblock.CrusherMultiblockEntity>, DisplayNameMultilblock {
	override val name = "crusher"

	override val signText = createSignText(
		line1 = "&bCrusher",
		line2 = null,
		line3 = null,
		line4 = null
	)

	override val displayName: Component get() = text("Crusher")
	override val description: Component get() = text("Crushes items")

	override fun MultiblockShape.buildStructure() {
		z(+0) {
			y(-1) {
				x(-1).powerInput()
				x(0).anyStairs()
				x(1).anyPipedInventory()
			}
			y(0) {
				x(-1).anyStairs()
				x(+0).machineFurnace()
				x(+1).anyStairs()
			}
		}
		z(+1) {
			y(-1) {
				x(-1).terracottaOrDoubleSlab()
				x(+0).type(Material.MAGMA_BLOCK)
				x(+1).extractor()
			}
			y(+0) {
				x(-1).terracottaOrDoubleSlab()
				x(+0).anyGlass()
				x(+1).terracottaOrDoubleSlab()
			}
			y(+1){
				x(-1).anySlab()
				x(+0).anySlab()
				x(+1).anySlab()
			}
		}
		z(+2) {
			y(-1) {
				x(-1).goldBlock()
				x(+0).anyStairs()
				x(+1).goldBlock()
			}
			y(+0) {
				x(-1).ironBlock()
				x(+1).ironBlock()
			}
			y(+1) {
				x(-1).anyStairs()
				x(+0).anySlab()
				x(+1).anyStairs()
			}
		}
	}

	override fun createEntity(manager: MultiblockManager, data: PersistentMultiblockData, world: World, x: Int, y: Int, z: Int, structureDirection: BlockFace): CrusherMultiblockEntity {
		return CrusherMultiblockEntity(data, manager, this, x, y, z, world, structureDirection)
	}

	class CrusherMultiblockEntity(
		data: PersistentMultiblockData,
		manager: MultiblockManager,
		override val multiblock: CrusherMultiblock,
		x: Int,
		y: Int,
		z: Int,
		world: World,
		structureDirection: BlockFace
	) : SimplePoweredEntity(data, multiblock, manager, x, y, z, world, structureDirection, 50_000), LegacyMultiblockEntity, StatusTickedMultiblockEntity, SyncTickingMultiblockEntity, FurnaceBasedMultiblockEntity {
		override val tickingManager: TickingManager = TickingManager(interval = 1)
		override val statusManager: StatusMultiblockEntity.StatusManager = StatusMultiblockEntity.StatusManager()

		override val displayHandler = DisplayHandlers.newMultiblockSignOverlay(
			this,
			{ PowerEntityDisplayModule(it, this) },
			{ StatusDisplayModule(it, statusManager) }
		).register()

		override fun loadFromSign(sign: Sign) {
			migrateLegacyPower(sign)
		}

		override fun tick() {
			val furnaceInventory = getInventory(0, 0, 0) as? FurnaceInventory ?: return sleepWithStatus(text("No Furnace"), 250)
			val outputInventory = getInventory(1, -1, 0) ?: return sleepWithStatus(status = text("No Output Inventory", NamedTextColor.RED), sleepTicks = 250)

			if (powerStorage.getPower() < 100) return sleepWithStatus(text("No Power", RED), 50)

			val topFuel = furnaceInventory.smelting
			val bottomFuel = furnaceInventory.fuel

			val validInputs = listOf(Material.COBBLESTONE, Material.SANDSTONE, Material.RED_SANDSTONE)

			val usingFuel = if (topFuel?.type in validInputs) topFuel!!
			else if (bottomFuel?.type in validInputs) bottomFuel!!
			else return sleepWithStatus(text("Out of Resources", RED), 50)

			val output = when (usingFuel.type) {
				Material.COBBLESTONE -> ItemStack(Material.GRAVEL)
				Material.SANDSTONE -> ItemStack(Material.SAND)
				else -> ItemStack(Material.RED_SAND)
			}


			if (!LegacyItemUtils.canFit(outputInventory, output)) return sleepWithStatus(text("No Space", NamedTextColor.RED), 50)
			addToInventory(outputInventory, output)

			usingFuel.amount--

			powerStorage.removePower(100)

			sleepWithStatus(text("Working", GREEN), 50)
			setBurningForTicks(50)
		}
	}
}
