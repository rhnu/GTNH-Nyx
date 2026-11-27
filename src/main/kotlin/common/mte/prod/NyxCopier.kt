package rhynia.nyx.common.mte.prod

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.utils.MathUtils
import com.cleanroommc.modularui.value.sync.BooleanSyncValue
import com.cleanroommc.modularui.value.sync.LongSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.value.sync.StringSyncValue
import com.cleanroommc.modularui.widgets.ListWidget
import com.cleanroommc.modularui.widgets.ToggleButton
import com.cleanroommc.modularui.widgets.layout.Flow
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget
import gregtech.api.GregTechAPI
import gregtech.api.enums.HatchElement.OutputBus
import gregtech.api.enums.HatchElement.OutputHatch
import gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_OFF
import gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_ON
import gregtech.api.interfaces.IHatchElement
import gregtech.api.interfaces.IIconContainer
import gregtech.api.interfaces.metatileentity.IMetaTileEntity
import gregtech.api.interfaces.tileentity.IGregTechTileEntity
import gregtech.api.logic.ProcessingLogic
import gregtech.api.metatileentity.BaseTileEntity
import gregtech.api.modularui2.GTGuiTextures
import gregtech.api.recipe.check.CheckRecipeResult
import gregtech.api.recipe.check.CheckRecipeResultRegistry
import gregtech.api.util.GTUtility
import gregtech.api.util.MultiblockTooltipBuilder
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui
import gregtech.common.misc.WirelessNetworkManager.strongCheckOrAddUser
import mcp.mobius.waila.api.IWailaConfigHandler
import mcp.mobius.waila.api.IWailaDataAccessor
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.StatCollector
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection
import net.minecraftforge.fluids.FluidStack
import rhynia.nyx.api.enums.CommonString
import rhynia.nyx.common.NyxItemList
import rhynia.nyx.common.material.NyxMaterials
import rhynia.nyx.common.mte.base.NyxMTECubeBase
import rhynia.nyx.config.ConfigMachine
import java.math.BigDecimal
import java.util.UUID

class NyxCopier : NyxMTECubeBase<NyxCopier> {
    constructor(
        aId: Int,
        aName: String,
    ) : super(aId, aName)

    constructor(aName: String) : super(aName)

    override fun newMetaEntity(aTileEntity: IGregTechTileEntity?): IMetaTileEntity = NyxCopier(mName)

    private var pItemMode = true
    private var pRunning = false
        set(value) {
            field = value
            if (value) {
                mMaxProgresstime = ConfigMachine.MTE_COPIER_TICK
                mEfficiency = 10000
                mEfficiencyIncrease = 10000
            } else {
                mMaxProgresstime = 0
                mEfficiency = 0
                mEfficiencyIncrease = 0
                mOutputItems = null
                mOutputFluids = null
                pDisplayName = "×"
            }
        }

    private var pDisplayName: String = "×"
    private val pCopyItemBase: ItemStack by lazy { NyxItemList.TestItem01.get(1) }
    private val pCopyFluidBase: FluidStack by lazy { NyxMaterials.Null.getFluid() }
    private var pAmount: Long = 0
        set(value) {
            field =
                if (value > 0) {
                    value
                } else {
                    0
                }
        }

    private lateinit var pUUID: UUID

    override fun onScrewdriverRightClick(
        side: ForgeDirection?,
        aPlayer: EntityPlayer?,
        aX: Float,
        aY: Float,
        aZ: Float,
        aTool: ItemStack?,
    ) {
        if (!baseMTE.isServerSide) return
        pItemMode = !pItemMode
        GTUtility.sendChatToPlayer(aPlayer, "COPIER Mode: ${if (pItemMode) "Item" else "Fluid"}")
    }

    override fun onFirstTick(aBaseMetaTileEntity: IGregTechTileEntity?) {
        super.onFirstTick(aBaseMetaTileEntity)
        pUUID = aBaseMetaTileEntity?.ownerUuid!!
        strongCheckOrAddUser(pUUID)
    }

    override fun createProcessingLogic(): ProcessingLogic? = null

    override fun checkProcessing(): CheckRecipeResult {
        pRunning = false
        val stackToCopy = controllerSlot ?: return CheckRecipeResultRegistry.NO_RECIPE
        if (pItemMode) {
            if (stackToCopy.isItemEqual(pCopyItemBase)) return CheckRecipeResultRegistry.NO_RECIPE
            if (!outputItem(stackToCopy, pAmount)) return CheckRecipeResultRegistry.NO_RECIPE
            // Resolving the display name is not cheap, so only do it on the running path.
            pDisplayName = stackToCopy.displayName
        } else {
            val fluidToCopy = GTUtility.convertCellToFluid(stackToCopy) ?: return CheckRecipeResultRegistry.NO_RECIPE
            if (fluidToCopy.isFluidEqual(pCopyFluidBase)) return CheckRecipeResultRegistry.NO_RECIPE
            if (!outputFluid(fluidToCopy, pAmount)) return CheckRecipeResultRegistry.NO_RECIPE
            pDisplayName = fluidToCopy.localizedName
        }
        pRunning = true
        return CheckRecipeResultRegistry.SUCCESSFUL
    }

    override fun supportsVoidProtection(): Boolean = true

    override fun supportsInputSeparation(): Boolean = false

    override fun supportsBatchMode(): Boolean = false

    override fun supportsSingleRecipeLocking(): Boolean = false

    override val sCasingBlock: Pair<Block, Int>
        get() = GregTechAPI.sBlockCasings2 to 0

    override val sCasingHatch: Array<IHatchElement<in NyxCopier>>
        get() = arrayOf(OutputBus, OutputHatch)

    override val sControllerIcon: Pair<IIconContainer, IIconContainer>
        get() = OVERLAY_DTPF_OFF to OVERLAY_DTPF_OFF

    override val sControllerIconActive: Pair<IIconContainer, IIconContainer>
        get() = OVERLAY_DTPF_ON to OVERLAY_DTPF_ON

    override fun createTooltip(): MultiblockTooltipBuilder =
        MultiblockTooltipBuilder()
            .addMachineTypeLocalized()
            .addInfoListLocalized(2)
            .addChangeModeByScrewdriver()
            .beginStructureCube()
            .addInputBus()
            .addOutputBus()
            .toolTipFinisher(CommonString.NyxMagical)

    override fun getGui(): MTEMultiBlockBaseGui<*> = Gui()

    private inner class Gui : MTEMultiBlockBaseGui<NyxCopier>(this@NyxCopier) {
        private val itemMode = BooleanSyncValue({ pItemMode }, { value: Boolean -> pItemMode = value }).allowC2S()
        private val amount = LongSyncValue({ pAmount }, { value: Long -> pAmount = value }).allowC2S()

        override fun createRightPanelGapRow(panel: ModularPanel, syncManager: PanelSyncManager): Flow =
            super.createRightPanelGapRow(panel, syncManager)
                .childPadding(2)
                .child(
                    TextFieldWidget()
                        .value(amount)
                        .setValidator { parseCopyAmount(it).toString() }
                        .setTextAlignment(Alignment.CenterLeft)
                        .tooltipBuilder { it.addLine(IKey.lang("nyx.machine.copier.gui.t.2")) }
                        .tooltipShowUpTimer(BaseTileEntity.TOOLTIP_DELAY)
                        .size(70, 16),
                )
                .child(
                    ToggleButton()
                        .value(itemMode)
                        .overlay(true, GTGuiTextures.OVERLAY_BUTTON_AUTOOUTPUT_ITEM)
                        .overlay(false, GTGuiTextures.OVERLAY_BUTTON_AUTOOUTPUT_FLUID)
                        .tooltipBuilder(true) { it.addLine(IKey.lang("nyx.machine.copier.gui.t.0")) }
                        .tooltipBuilder(false) { it.addLine(IKey.lang("nyx.machine.copier.gui.t.1")) }
                        .tooltipShowUpTimer(BaseTileEntity.TOOLTIP_DELAY)
                        .size(18),
                )

        override fun createTerminalTextWidget(
            syncManager: PanelSyncManager,
            panel: ModularPanel,
        ): ListWidget<IWidget, *> {
            val displayName = StringSyncValue { pDisplayName }
            syncManager.syncValue("nyxCopyDisplayName", displayName)

            return super.createTerminalTextWidget(syncManager, panel)
                .child(statusLine(IKey.lang("nyx.machine.copier.waila.0") { arrayOf(displayName.value) }))
                .child(statusLine(IKey.lang("nyx.machine.copier.waila.1") {
                    arrayOf(NumberFormatUtil.formatNumber(amount.longValue))
                }))
                .child(statusLine(IKey.lang {
                    if (itemMode.boolValue) "nyx.machine.copier.waila.2.item" else "nyx.machine.copier.waila.2.fluid"
                }))
        }

        private fun statusLine(key: IKey): IWidget =
            key.asWidget()
                .textAlign(Alignment.CenterLeft)
                .setEnabledIf { baseMTE.isActive }
                .marginBottom(2)
                .fullWidth()
    }

    override fun getWailaBody(
        itemStack: ItemStack?,
        currentTip: MutableList<String>,
        accessor: IWailaDataAccessor,
        config: IWailaConfigHandler?,
    ) {
        super.getWailaBody(itemStack, currentTip, accessor, config)
        val displayName = accessor.nbtData.getString("pDisplayName")
        if (displayName.isEmpty()) return
        currentTip.add(StatCollector.translateToLocalFormatted("nyx.machine.copier.waila.0", displayName))
        currentTip.add(
            StatCollector.translateToLocalFormatted(
                "nyx.machine.copier.waila.1",
                accessor.nbtData.getString("pAmount"),
            ),
        )
        val mode = accessor.nbtData.getInteger("pItemMode").takeIf { it != 0 } ?: return
        if (mode == 1) {
            currentTip.add(StatCollector.translateToLocal("nyx.machine.copier.waila.2.item"))
        } else {
            currentTip.add(StatCollector.translateToLocal("nyx.machine.copier.waila.2.fluid"))
        }
    }

    override fun getWailaNBTData(
        player: EntityPlayerMP?,
        tile: TileEntity?,
        tag: NBTTagCompound,
        world: World?,
        x: Int,
        y: Int,
        z: Int,
    ) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z)
        if (baseMetaTileEntity?.isActive == true) {
            tag.setString("pDisplayName", pDisplayName)
            tag.setString("pAmount", NumberFormatUtil.formatNumber(pAmount))
            tag.setInteger("pItemMode", if (pItemMode) 1 else -1)
        }
    }

    override fun saveNBTData(aNBT: NBTTagCompound) {
        super.saveNBTData(aNBT)
        aNBT.setBoolean("pItemMode", pItemMode)
        aNBT.setLong("pAmount", pAmount)
    }

    override fun loadNBTData(aNBT: NBTTagCompound) {
        super.loadNBTData(aNBT)
        pItemMode = aNBT.getBoolean("pItemMode")
        pAmount = aNBT.getLong("pAmount")
    }
}

/** Evaluate the field without converting its exact decimal result to Double. */
internal fun parseCopyAmount(input: String): Long {
    val result = MathUtils.parseExpressionWholeNumber(input, 0.0)
    if (!result.isSuccess || !result.result.isNumberValue) return 0
    return result.result.numberValue
        .coerceIn(BigDecimal.ZERO, BigDecimal.valueOf(Long.MAX_VALUE))
        .toLong()
}
