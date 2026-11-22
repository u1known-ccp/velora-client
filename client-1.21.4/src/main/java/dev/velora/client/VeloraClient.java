package dev.velora.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.velora.client.api.TickListener;
import dev.velora.client.hud.HudRenderer;
import dev.velora.client.mods.pvp.PvpTracker;
import dev.velora.client.mods.utility.WaypointsMod;
import dev.velora.core.VeloraCore;
import dev.velora.core.event.CoreEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Fabric entry point. Registers modules immediately, loads the configuration once the client
 * finished starting (all game systems available) and wires Fabric events to the module system.
 */
public final class VeloraClient implements ClientModInitializer {
    public static final String MOD_ID = "velora";
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora");

    @Override
    public void onInitializeClient() {
        long start = System.nanoTime();
        MinecraftClient mc = MinecraftClient.getInstance();
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        VeloraCore core = new VeloraCore(version, SharedConstants.getGameVersion().getName(), configDir, mc);
        Mods.registerAll(core.modules());

        core.bus().subscribe(CoreEvents.ModuleToggled.class, e -> ModuleCache.rebuild());
        core.bus().subscribe(CoreEvents.ProfileSwitched.class, e -> ModuleCache.rebuild());

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            core.start();
            ModuleCache.rebuild();
            core.config().client().equippedCape.onChange(BackendSync::pushEquippedCape);
            LOGGER.info("Velora {} ready – {} mods, profile '{}'", version, core.modules().all().size(), core.config().profiles().getActive());
            if (SelfTest.enabled()) SelfTest.onStarted();
        });
        if (SelfTest.enabled()) ClientTickEvents.END_CLIENT_TICK.register(SelfTest::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> core.shutdown());

        ClientTickEvents.END_CLIENT_TICK.register(VeloraClient::tick);
        HudRenderCallback.EVENT.register(HudRenderer::render);
        WorldRenderEvents.BLOCK_OUTLINE.register((worldCtx, outline) -> !Mods.ready()
                || Mods.blockOverlay.render(worldCtx, outline.blockPos(), outline.blockState(), outline.cameraX(), outline.cameraY(), outline.cameraZ()));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient && player == MinecraftClient.getInstance().player) PvpTracker.onAttack(entity);
            return ActionResult.PASS;
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && Mods.ready()) Mods.autoGG.onGameMessage(message);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            PvpTracker.reset();
            if (client.player != null && VeloraCore.isReady()) VeloraCore.get().cosmetics().refreshOwned(client.player.getUuid());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PvpTracker.reset());

        registerCommands();
        LOGGER.info("Velora initialised in {} ms", (System.nanoTime() - start) / 1_000_000);
    }

    private static void tick(MinecraftClient mc) {
        if (!Mods.ready()) return;
        PvpTracker.tick(mc);
        for (TickListener listener : ModuleCache.tickListeners()) {
            try {
                listener.onTick(mc);
            } catch (RuntimeException e) {
                LOGGER.error("Tick of {} failed", listener.getClass().getSimpleName(), e);
            }
        }
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("waypoint")
                        .then(ClientCommandManager.literal("add")
                                .executes(ctx -> addWaypoint(null, ctx.getSource()::sendFeedback))
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> addWaypoint(StringArgumentType.getString(ctx, "name"), ctx.getSource()::sendFeedback))))
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            boolean ok = Mods.waypoints.remove(name);
                                            ctx.getSource().sendFeedback(Text.literal(ok ? "Wegpunkt \"" + name + "\" entfernt." : "Kein Wegpunkt \"" + name + "\" gefunden.")
                                                    .formatted(ok ? Formatting.GREEN : Formatting.RED));
                                            return ok ? 1 : 0;
                                        })))
                        .then(ClientCommandManager.literal("list").executes(ctx -> {
                            var list = Mods.waypoints.current();
                            if (list.isEmpty()) ctx.getSource().sendFeedback(Text.literal("Keine Wegpunkte in dieser Welt.").formatted(Formatting.GRAY));
                            for (WaypointsMod.Waypoint w : list) {
                                ctx.getSource().sendFeedback(Text.literal("• " + w.name() + " – " + w.x() + " " + w.y() + " " + w.z() + " (" + w.dimension() + ")"));
                            }
                            return list.size();
                        }))));
    }

    private static int addWaypoint(String name, java.util.function.Consumer<Text> feedback) {
        if (!Mods.waypoints.isEnabled()) {
            feedback.accept(Text.literal("Aktiviere zuerst den Waypoints-Mod im Velora-Menü.").formatted(Formatting.YELLOW));
        }
        return Mods.waypoints.addHere(name).map(w -> {
            feedback.accept(Text.literal("Wegpunkt \"" + w.name() + "\" gesetzt: " + w.x() + " " + w.y() + " " + w.z()).formatted(Formatting.GREEN));
            return 1;
        }).orElse(0);
    }
}
