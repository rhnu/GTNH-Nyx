@file:Suppress("DEPRECATION")

package rhynia.nyx.common.mte.sing

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.utils.Color
import com.cleanroommc.modularui.value.sync.IntSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import gregtech.api.enums.GTValues.VN
import gregtech.api.enums.Mods.GregTech
import gregtech.api.enums.Textures
import gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS
import gregtech.api.enums.Textures.BlockIcons.OVERLAYS_ENERGY_IN
import gregtech.api.enums.Textures.BlockIcons.OVERLAYS_ENERGY_IN_MULTI_LASER
import gregtech.api.enums.Textures.BlockIcons.OVERLAYS_ENERGY_OUT
import gregtech.api.enums.Textures.BlockIcons.OVERLAYS_ENERGY_OUT_MULTI_LASER
import gregtech.api.interfaces.ITexture
import gregtech.api.interfaces.tileentity.IGregTechTileEntity
import gregtech.api.metatileentity.MetaTileEntity
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock
import gregtech.api.modularui2.GTGuiTextures
import gregtech.api.modularui2.GTGuis
import gregtech.api.render.TextureFactory
import gregtech.api.util.GTUtility
import gregtech.common.misc.WirelessNetworkManager.addEUToGlobalEnergyMap
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.StatCollector
import net.minecraftforge.common.util.ForgeDirection
import rhynia.nyx.api.enums.CommonString
import rhynia.nyx.common.mte.gui.machineActionButton
import tectech.util.CommonValues.TRANSFER_AT
import tectech.util.TTUtility
import java.util.UUID
import kotlin.math.abs
import kotlin.reflect.KMutableProperty0

class NyxInjector : MTETieredMachineBlock {
    private var wireless = false
    private var ownerUUID: UUID? = null
    var eUT: Int = 0
    var aMP: Int = 0
    val producing: Boolean get() = totalPower >= 0

    private val totalPower: Long get() = eUT.toLong() * aMP

    constructor(
        aID: Int,
        aName: String,
        aTier: Int,
    ) : super(
        aID,
        aName,
        StatCollector.translateToLocal("$aName.name"),
        aTier,
        0,
        arrayOf(
            CommonString.ChangeModeByScrewdriver,
            CommonString.AddByNyx,
        ),
    ) {
        TTUtility.setTier(aTier, this)
    }

    constructor(
        aName: String,
        aTier: Int,
        aDescription: Array<String?>?,
        aTextures: Array<Array<Array<ITexture?>?>?>?,
    ) : super(aName, aTier, 0, aDescription, aTextures) {
        TTUtility.setTier(aTier, this)
    }

    override fun newMetaEntity(aTileEntity: IGregTechTileEntity?): MetaTileEntity =
        NyxInjector(mName, mTier.toInt(), mDescriptionArray, mTextures)

    override fun onScrewdriverRightClick(
        side: ForgeDirection?,
        aPlayer: EntityPlayer?,
        aX: Float,
        aY: Float,
        aZ: Float,
        aTool: ItemStack?,
    ) {
        wireless = !wireless
        if (wireless) {
            GTUtility.sendChatToPlayer(aPlayer, "WIRELESS: ON")
        } else {
            GTUtility.sendChatToPlayer(aPlayer, "WIRELESS: OFF")
        }
    }

    @SideOnly(Side.CLIENT)
    override fun registerIcons(aBlockIconRegister: IIconRegister?) {
        super.registerIcons(aBlockIconRegister)
        GENNY
    }

    override fun getTexture(
        aBaseMetaTileEntity: IGregTechTileEntity?,
        side: ForgeDirection?,
        facing: ForgeDirection?,
        colorIndex: Int,
        aActive: Boolean,
        aRedstone: Boolean,
    ): Array<ITexture> {
        val tier = mTier.toInt()
        val overlay =
            if (side != facing) {
                when {
                    wireless && aActive -> OVERLAYS_ENERGY_OUT_MULTI_LASER[tier + 1]
                    wireless -> OVERLAYS_ENERGY_IN_MULTI_LASER[tier + 1]
                    aActive -> OVERLAYS_ENERGY_OUT[tier + 1]
                    else -> OVERLAYS_ENERGY_IN[tier + 1]
                }
            } else {
                GENNY
            }
        return arrayOf(MACHINE_CASINGS[tier][colorIndex + 1], overlay)
    }

    override fun getTextureSet(aTextures: Array<ITexture?>?): Array<Array<Array<ITexture?>?>?>? = null

    override fun allowPutStack(
        iGregTechTileEntity: IGregTechTileEntity?,
        i: Int,
        side: ForgeDirection?,
        itemStack: ItemStack?,
    ): Boolean = false

    override fun allowPullStack(
        iGregTechTileEntity: IGregTechTileEntity?,
        i: Int,
        side: ForgeDirection?,
        itemStack: ItemStack?,
    ): Boolean = false

    override fun saveNBTData(aNBT: NBTTagCompound) {
        aNBT.setInteger("eEUT", this.eUT)
        aNBT.setInteger("eAMP", this.aMP)
        aNBT.setBoolean("eWireless", wireless)
    }

    override fun loadNBTData(aNBT: NBTTagCompound) {
        this.eUT = aNBT.getInteger("eEUT")
        this.aMP = aNBT.getInteger("eAMP")
        wireless = aNBT.getBoolean("eWireless")

        baseMetaTileEntity?.isActive = producing
    }

    override fun onPostTick(
        aBaseMetaTileEntity: IGregTechTileEntity,
        aTick: Long,
    ) {
        if (!aBaseMetaTileEntity.isServerSide) return

        aBaseMetaTileEntity.isActive = producing
        if (!wireless) {
            euVar = if (aBaseMetaTileEntity.isActive) maxEUStore() else 0
            return
        }

        if (TRANSFER_AT != (aTick % 20).toByte()) return
        if (aBaseMetaTileEntity.isActive) {
            // Resolved once instead of on every tick; the owner cannot change in place.
            if (ownerUUID == null) ownerUUID = aBaseMetaTileEntity.ownerUuid
            euVar = maxEUStore()
            addEUToGlobalEnergyMap(ownerUUID, abs(euVar))
        } else {
            euVar = 0
        }
    }

    override fun onRightclick(
        aBaseMetaTileEntity: IGregTechTileEntity?,
        aPlayer: EntityPlayer?,
    ): Boolean {
        openGui(aPlayer)
        return true
    }

    override fun isFacingValid(facing: ForgeDirection?): Boolean = true

    override fun isAccessAllowed(aPlayer: EntityPlayer?): Boolean = true

    override fun isElectric(): Boolean = true

    override fun isEnetOutput(): Boolean = !wireless

    override fun isEnetInput(): Boolean = !wireless

    override fun isInputFacing(side: ForgeDirection?): Boolean = !producing && side != baseMetaTileEntity!!.frontFacing

    override fun isOutputFacing(side: ForgeDirection?): Boolean = producing && side != baseMetaTileEntity!!.frontFacing

    // These are polled by the energy net every tick, so keep them on integer math.
    override fun maxAmperesIn(): Long = if (producing) 0 else abs(aMP.toLong())

    override fun maxAmperesOut(): Long = if (producing) abs(aMP.toLong()) else 0

    override fun maxEUInput(): Long = if (producing) 0 else Int.MAX_VALUE.toLong()

    override fun maxEUOutput(): Long = if (producing) abs(eUT.toLong()) else 0

    override fun maxEUStore(): Long {
        val base = abs(totalPower)
        return if (wireless) {
            if (base > Long.MAX_VALUE / 24) Long.MAX_VALUE else base * 24
        } else {
            (base.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() shl 2).toLong()
        }
    }

    override fun maxProgresstime(): Int = baseMetaTileEntity!!.universalEnergyCapacity.toInt()

    protected override fun useMui2(): Boolean = true

    override fun buildUI(data: PosGuiData, syncManager: PanelSyncManager, uiSettings: UISettings): ModularPanel {
        val voltage = IntSyncValue({ eUT }, { value: Int -> eUT = value }).allowC2S()
        val amperage = IntSyncValue({ aMP }, { value: Int -> aMP = value }).allowC2S()
        return GTGuis.mteTemplatePanelBuilder(this, data, syncManager, uiSettings)
            .build()
            .child(GTGuiTextures.PICTURE_SCREEN_BLACK.asWidget().size(90, 72).pos(43, 4))
            .child(LOGO.asWidget().size(17).pos(113, 56))
            .child(powerText(IKey.dynamic { "TIER: " + VN[GTUtility.getTier(abs(eUT.toLong())).toInt()] }, 22))
            .child(powerText(IKey.dynamic { "SUM: $totalPower" }, 46))
            .apply {
                addPowerField("EUT: ", voltage, 8)
                addPowerField("AMP: ", amperage, 34)
                addAdjustments(::eUT, 4)
                addAdjustments(::aMP, 40)
            }
    }

    private fun powerText(key: IKey, y: Int) = key.color(Color.WHITE.main).asWidget().pos(46, y)

    private fun ModularPanel.addPowerField(label: String, value: IntSyncValue, y: Int) {
        child(powerText(IKey.str(label), y))
        child(
            TextFieldWidget()
                .value(value)
                .numbersInt(Int.MIN_VALUE, Int.MAX_VALUE)
                .setTextColor(Color.WHITE.main)
                .background(GTGuiTextures.BACKGROUND_TEXT_FIELD)
                .size(56, 12)
                .pos(70, y - 2),
        )
    }

    private fun ModularPanel.addAdjustments(value: KMutableProperty0<Int>, y: Int) {
        fun change(overlay: IDrawable, x: Int, y: Int, step: Int, shiftStep: Int, adjust: (Int, Int) -> Int) {
            child(machineActionButton(overlay) { click ->
                value.set(adjust(value.get(), if (click.shift) shiftStep else step))
            }.pos(x, y))
        }

        change(MINUS_LARGE, 7, y, 64, 512, Int::minus)
        change(MINUS_SMALL, 25, y, 1, 16, Int::minus)
        change(GTGuiTextures.OVERLAY_BUTTON_PLUS_SMALL, 133, y, 1, 16, Int::plus)
        change(GTGuiTextures.OVERLAY_BUTTON_PLUS_LARGE, 151, y, 64, 512, Int::plus)

        change(MINUS_LARGE, 7, y + 18, 64, 512, Int::div)
        change(MINUS_SMALL, 25, y + 18, 2, 16, Int::div)
        change(GTGuiTextures.OVERLAY_BUTTON_PLUS_SMALL, 133, y + 18, 2, 16, Int::times)
        change(GTGuiTextures.OVERLAY_BUTTON_PLUS_LARGE, 151, y + 18, 64, 512, Int::times)
    }

    companion object {
        private val LOGO = UITexture.fullImage("nyx", "gui/picture/va_logo_32_t")
        private val MINUS_LARGE = UITexture.fullImage(GregTech.ID, "gui/overlay_button/minus_large")
        private val MINUS_SMALL = UITexture.fullImage(GregTech.ID, "gui/overlay_button/minus_small")

        @Suppress("SpellCheckingInspection")
        val GENNY: ITexture by lazy {
            TextureFactory.of(Textures.BlockIcons.custom("iconsets/GENNY"))
        }
    }
}
