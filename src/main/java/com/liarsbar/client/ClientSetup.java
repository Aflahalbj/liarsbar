package com.liarsbar.client;

import com.liarsbar.client.anim.BotPuppets;
import com.liarsbar.client.anim.SeatAnimator;
import com.liarsbar.client.audio.TableAudio;
import com.liarsbar.client.cinematic.CameraDirector;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.cinematic.RouletteTimeline;
import com.liarsbar.client.fx.DeferredPack;
import com.liarsbar.client.fx.MuzzleFx;
import com.liarsbar.client.fx.PostFx;
import com.liarsbar.client.fx.TableLights;
import com.liarsbar.client.render.BotRenderer;
import com.liarsbar.client.render.FirstPersonGun;
import com.liarsbar.client.render.GunLayer;
import com.liarsbar.client.render.SeatRenderer;
import com.liarsbar.client.render.TableRenderer;
import com.liarsbar.client.test.AnimDev;
import com.liarsbar.client.test.ClientSelfTest;
import com.liarsbar.client.test.DevWorld;
import com.liarsbar.client.ui.TableHud;
import com.liarsbar.entity.ModEntities;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Pendaftaran semua yang khusus client. Dipanggil hanya di sisi client. */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onRenderers);
        modBus.addListener(ClientSetup::onLayers);
        modBus.addListener(TableKeys::register);
        modBus.addListener(ClientSetup::onOverlays);
        modBus.addListener(ClientSetup::onReloadListeners);
        modBus.addListener(com.liarsbar.client.fx.NoirPack::onAddPackFinders);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLogout);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(FirstPersonGun::onRenderHand);
        MinecraftForge.EVENT_BUS.addListener(com.liarsbar.client.render.FirstPersonCards::onRenderHand);
        MinecraftForge.EVENT_BUS.addListener(CameraDirector::onFov);
        MinecraftForge.EVENT_BUS.addListener(CameraDirector::onAngles);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onGuiOverlay);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onNameTag);
        VeilEventPlatform.INSTANCE.preVeilPostProcessing(PostFx::preProcess);
        DevWorld.init();
        ClientSelfTest.init();
        AnimDev.init();
    }

    /** Art Bible: tidak ada nametag melayang di meja (nama muncul di daftar Tab dan subtitle klaim). */
    private static void onNameTag(net.minecraftforge.client.event.RenderNameTagEvent event) {
        net.minecraft.world.entity.Entity e = event.getEntity();
        if (e instanceof com.liarsbar.entity.BotEntity || e.getVehicle() instanceof com.liarsbar.entity.SeatEntity
                || com.liarsbar.client.render.BarRoom.contains(e)) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TABLE.get(), TableRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), SeatRenderer::new);
        event.registerEntityRenderer(ModEntities.BOT.get(), BotRenderer::new);
        event.registerEntityRenderer(ModEntities.BAR_NPC.get(), com.liarsbar.client.render.NpcRenderer::new);
        event.registerBlockEntityRenderer(com.liarsbar.block.ModBlocks.FAN_ENTITY.get(),
                com.liarsbar.client.render.CeilingFanRenderer::new);
    }

    private static void onLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new GunLayer(renderer));
                renderer.addLayer(new com.liarsbar.client.render.HandCardsLayer(renderer));
                renderer.addLayer(new com.liarsbar.client.render.NpcPropLayer(renderer));
            }
        }
    }

    private static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new RouletteTimeline());
    }

    private static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("table", TableHud::render);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        DeferredPack.tick();
        ClientTable.tick();
        TableKeys.tick();
        TableMouse.tick();
        RouletteDirector.tick();
        com.liarsbar.client.cinematic.LiarDirector.tick();
        com.liarsbar.client.cinematic.ThrowDirector.tick();
        com.liarsbar.client.cinematic.DealDirector.tick();
        BotPuppets.tick();
        com.liarsbar.client.anim.NpcPuppets.tick();
        SeatAnimator.tick();
        SeatAnimator.endTick();
        TableLights.tick();
        com.liarsbar.client.render.BarRoom.tick();
        MuzzleFx.tick();
        TableAudio.tick();
    }

    private static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            TableMouse.frame();
            PostFx.frame(event.getPartialTick(), event.getProjectionMatrix(), event.getPoseStack().last().pose(),
                    event.getCamera().getPosition());
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            com.liarsbar.client.render.EmissivePass.flush();
        }
        MuzzleFx.onRenderStage(event);
    }

    /** Duduk di meja: HUD vanilla (hotbar, nyawa, lapar) tidak relevan; saat kamera sinematik, crosshair juga. */
    private static void onGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!ClientTable.seated() && CameraDirector.cinematicWeight() <= 0) {
            return;
        }
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())
                || id.equals(VanillaGuiOverlay.ITEM_NAME.id()) || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                || id.equals(VanillaGuiOverlay.JUMP_BAR.id())) {
            event.setCanceled(true);
        } else if (id.equals(VanillaGuiOverlay.CROSSHAIR.id()) || id.equals(VanillaGuiOverlay.PLAYER_LIST.id())
                || id.equals(VanillaGuiOverlay.SCOREBOARD.id())) {
            // Art Bible: tanpa crosshair di meja; Tab membuka daftar pemain meja (TableHud), bukan daftar server.
            event.setCanceled(true);
        }
    }

    /** Selama game, E dipakai untuk melempar kartu: inventory tidak dibuka. */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (ClientTable.inGame() && (event.getNewScreen() instanceof InventoryScreen
                || event.getNewScreen() instanceof CreativeModeInventoryScreen)) {
            event.setCanceled(true);
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTable.clear();
        RouletteDirector.reset(-1);
        com.liarsbar.client.render.BarRoom.clear();
        TableLights.clearAll();
        MuzzleFx.clear();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            com.liarsbar.client.render.BarRoom.clear();
            TableLights.clearAll();
            MuzzleFx.clear();
        }
    }
}
