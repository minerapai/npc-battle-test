package kr.npcbattletest;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.npc.NPCClass;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.actor.TrainerBattleActor;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.battle.BattleManager;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerBag;
import kotlin.Unit;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Test only: /npcbattle puts two NPC trainers in front of the caller and starts a real battle between them. */
public final class NpcBattleTest implements ModInitializer {
    static final Logger LOGGER = LoggerFactory.getLogger("npcbattletest");
    private static final List<UUID> BATTLES = new ArrayList<>();
    private static final List<NPCEntity> NPCS = new ArrayList<>();
    private static final boolean KICK = false;
    private static boolean flag;
    private static final java.util.Set<UUID> kicked = new java.util.HashSet<>();
    private static boolean random;
    /** AI choices waiting for the next tick (see NpcActor). */
    private static final List<Runnable> DEFERRED = new ArrayList<>();

    /**
     * RCT's NPC actor, but choosing a tick later: when both sides are AIs they answer within the same call, and
     * Cobblemon then sends only the first side's choice to showdown and the battle waits forever for the other.
     */
    static final class NpcActor extends BattleManager.TrainerEntityBattleActor {
        NpcActor(String name, NPCEntity entity, List<BattlePokemon> team) {
            super(name, entity, UUID.randomUUID(), team, new TrainerBag(), new StrongBattleAI(5));
        }

        @Override
        public void onChoiceRequested() {
            DEFERRED.add(super::onChoiceRequested);
        }
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("npcbattle").requires(s -> s.hasPermissionLevel(2))
                .executes(ctx -> start(ctx.getSource(), true))
                .then(CommandManager.literal("plain").executes(ctx -> start(ctx.getSource(), false)))
                .then(CommandManager.literal("flag").executes(ctx -> { flag = true; return start(ctx.getSource(), false); }))
                .then(CommandManager.literal("random").executes(ctx -> { random = true; return start(ctx.getSource(), false); }))
                .then(CommandManager.literal("full").executes(ctx -> { flag = true; return start(ctx.getSource(), true); }))));

        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, event -> {
            if (BATTLES.contains(event.getBattle().getBattleId())) {
                LOGGER.info("[npcbattle] turn {}: {} fainted", event.getBattle().getTurn(),
                    event.getKilled().getEffectedPokemon().getSpecies().getName());
            }
            return Unit.INSTANCE;
        });
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            if (BATTLES.remove(event.getBattle().getBattleId())) {
                LOGGER.info("[npcbattle] VICTORY after {} turns: winners={} losers={}", event.getBattle().getTurn(),
                    event.getWinners().stream().map(a -> a.getName().getString()).toList(),
                    event.getLosers().stream().map(a -> a.getName().getString()).toList());
                NPCS.forEach(NPCEntity::discard);
                NPCS.clear();
            }
            return Unit.INSTANCE;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<Runnable> due = new ArrayList<>(DEFERRED);
            DEFERRED.clear();
            due.forEach(Runnable::run);

            if (server.getTicks() % 100 != 0) {
                return;
            }

            for (UUID id : BATTLES) {
                PokemonBattle battle = BattleRegistry.getBattle(id);
                if (battle == null) {
                    LOGGER.info("[npcbattle] {} gone", id);
                    continue;
                }

                StringBuilder actors = new StringBuilder();

                for (BattleActor actor : battle.getActors()) {
                    actors.append(String.format(" [%s request=%s mustChoose=%s responses=%d active=%s]",
                        actor.getName().getString(), actor.getRequest() != null, actor.getMustChoose(),
                        actor.getResponses().size(), actor.getActivePokemon().stream()
                            .map(a -> a.getBattlePokemon() == null ? "none" : a.getBattlePokemon().getHealth() + "hp").toList()));
                }

                if (KICK && battle.getTurn() >= 1 && kicked.add(id)) {
                    for (BattleActor actor : battle.getActors()) {
                        try {
                            actor.turn();
                            LOGGER.info("[npcbattle] kicked {}: mustChoose={} responses={}", actor.getName().getString(),
                                actor.getMustChoose(), actor.getResponses());
                        } catch (RuntimeException e) {
                            LOGGER.warn("[npcbattle] kick failed for " + actor.getName().getString(), e);
                        }
                    }
                }

                LOGGER.info("[npcbattle] running: turn {} started={} dispatches={}{}", battle.getTurn(),
                    battle.getStarted(), battle.getDispatches().size(), actors);
                List<String> log = battle.getBattleLog();
                List<String> raw = battle.getShowdownMessages();
                LOGGER.info("[npcbattle] log({}): {}", log.size(), String.join(" / ", log.subList(Math.max(0, log.size() - 15), log.size())));
                LOGGER.info("[npcbattle] showdown({}): {}", raw.size(), String.join(" / ", raw.subList(Math.max(0, raw.size() - 15), raw.size())));
            }
        });
    }

    /** entities: NPC trainers standing there (pokemon sent out), or plain AI actors with nothing in the world. */
    private static int start(ServerCommandSource source, boolean entities) {
        ServerWorld world = source.getWorld();
        Vec3d pos = source.getPosition();
        NPCEntity red = entities ? npc(world, "레드", pos.add(-4, 0, 0), -90f) : null;
        NPCEntity blue = entities ? npc(world, "그린", pos.add(4, 0, 0), 90f) : null;

        // RCT's battle manager wants a player on side one, so this goes to Cobblemon directly with RCT's NPC actor
        BattleActor a = actor("레드", red, "charizard", "blastoise", "venusaur");
        BattleActor b = actor("그린", blue, "gengar", "dragonite", "snorlax");
        BattleStartResult result = BattleRegistry.startBattle(BattleFormat.Companion.getGEN_9_SINGLES(),
            new BattleSide(a), new BattleSide(b), flag);
        LOGGER.info("[npcbattle] flag={} random={} entities={}", flag, random, entities);
        flag = false;
        random = false;
        UUID battle = result instanceof SuccessfulBattleStart ok ? ok.getBattle().getBattleId() : null;

        if (result instanceof ErroredBattleStart errored) {
            errored.getErrors().forEach(e -> LOGGER.warn("[npcbattle] start error: {}", e));
        }

        LOGGER.info("[npcbattle] start -> {}", battle);

        if (battle == null) {
            NPCS.forEach(NPCEntity::discard);
            NPCS.clear();
            source.sendError(Text.literal("배틀을 시작하지 못했습니다."));
            return 0;
        }

        BATTLES.add(battle);
        source.sendFeedback(() -> Text.literal("NPC 배틀 시작: " + battle), false);
        return 1;
    }

    private static BattleActor actor(String name, NPCEntity npc, String... species) {
        List<BattlePokemon> team = new ArrayList<>();

        for (String s : species) {
            Pokemon pokemon = PokemonProperties.Companion.parse("species=" + s + " level=50").create();
            team.add(BattlePokemon.Companion.safeCopyOf(pokemon));
        }

        if (npc == null) {
            return new TrainerBattleActor(name, UUID.randomUUID(), team,
                random ? new com.cobblemon.mod.common.battles.ai.RandomBattleAI() : new StrongBattleAI(5));
        }

        return new NpcActor(name, npc, team);
    }

    private static NPCEntity npc(ServerWorld world, String name, Vec3d pos, float yaw) {
        NPCClass npcClass = NPCClasses.getByIdentifier(Identifier.of("cobblemon", "standard"));
        NPCEntity npc = new NPCEntity(world);
        npc.setNpc(npcClass);
        npc.initialize(1);
        npc.setMovable(false);
        npc.setInvulnerable(true);
        npc.setCustomName(Text.literal(name));
        npc.setCustomNameVisible(true);
        npc.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);
        world.spawnEntity(npc);
        NPCS.add(npc);
        return npc;
    }
}
