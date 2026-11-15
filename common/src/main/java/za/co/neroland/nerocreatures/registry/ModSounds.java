package za.co.neroland.nerocreatures.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.sound.CreatureSound;

/**
 * Every NeroCreatures sound event. <b>Generated</b> by {@code tools/sound/gen_sound_assets.py} from
 * {@code tools/sound/manifest.json}; do not edit by hand. All sounds are original and synthesised
 * ({@code tools/sound/synth.py}); subtitles live in {@code sounds.json} + {@code en_us.json}.
 */
public final class ModSounds {

    public static final RegistrationProvider<SoundEvent> SOUNDS =
            RegistrationProvider.get(Registries.SOUND_EVENT, NeroCreaturesCommon.MOD_ID);

    // lumen_moth (entity)
    public static final CreatureSound LUMEN_MOTH_AMBIENT = CreatureSound.register(SOUNDS, "lumen_moth.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound LUMEN_MOTH_HURT = CreatureSound.register(SOUNDS, "lumen_moth.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound LUMEN_MOTH_DEATH = CreatureSound.register(SOUNDS, "lumen_moth.death", 16, 0.92F, 1.05F);
    public static final CreatureSound LUMEN_MOTH_PULSE = CreatureSound.register(SOUNDS, "lumen_moth.pulse", 16, 0.95F, 1.05F);
    // echo_shrike (entity)
    public static final CreatureSound ECHO_SHRIKE_AMBIENT = CreatureSound.register(SOUNDS, "echo_shrike.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound ECHO_SHRIKE_HURT = CreatureSound.register(SOUNDS, "echo_shrike.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound ECHO_SHRIKE_DEATH = CreatureSound.register(SOUNDS, "echo_shrike.death", 16, 0.92F, 1.05F);
    public static final CreatureSound ECHO_SHRIKE_SCREECH = CreatureSound.register(SOUNDS, "echo_shrike.screech", 24, 0.95F, 1.05F);
    public static final CreatureSound ECHO_SHRIKE_LISTEN = CreatureSound.register(SOUNDS, "echo_shrike.listen", 16, 0.95F, 1.05F);
    // lode_tortoise (entity)
    public static final CreatureSound LODE_TORTOISE_AMBIENT = CreatureSound.register(SOUNDS, "lode_tortoise.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound LODE_TORTOISE_HURT = CreatureSound.register(SOUNDS, "lode_tortoise.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound LODE_TORTOISE_DEATH = CreatureSound.register(SOUNDS, "lode_tortoise.death", 16, 0.92F, 1.05F);
    public static final CreatureSound LODE_TORTOISE_GRAZE = CreatureSound.register(SOUNDS, "lode_tortoise.graze", 16, 0.95F, 1.05F);
    // lunar_stalker (entity)
    public static final CreatureSound LUNAR_STALKER_AMBIENT = CreatureSound.register(SOUNDS, "lunar_stalker.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound LUNAR_STALKER_HURT = CreatureSound.register(SOUNDS, "lunar_stalker.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound LUNAR_STALKER_DEATH = CreatureSound.register(SOUNDS, "lunar_stalker.death", 16, 0.92F, 1.05F);
    public static final CreatureSound LUNAR_STALKER_HOWL = CreatureSound.register(SOUNDS, "lunar_stalker.howl", 32, 0.95F, 1.05F);
    // dune_loper (entity)
    public static final CreatureSound DUNE_LOPER_AMBIENT = CreatureSound.register(SOUNDS, "dune_loper.ambient", 24, 0.90F, 1.10F);
    public static final CreatureSound DUNE_LOPER_HURT = CreatureSound.register(SOUNDS, "dune_loper.hurt", 24, 0.90F, 1.15F);
    public static final CreatureSound DUNE_LOPER_DEATH = CreatureSound.register(SOUNDS, "dune_loper.death", 24, 0.92F, 1.05F);
    public static final CreatureSound DUNE_LOPER_LEAP = CreatureSound.register(SOUNDS, "dune_loper.leap", 24, 0.95F, 1.05F);
    // strata_hound (entity)
    public static final CreatureSound STRATA_HOUND_AMBIENT = CreatureSound.register(SOUNDS, "strata_hound.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound STRATA_HOUND_HURT = CreatureSound.register(SOUNDS, "strata_hound.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound STRATA_HOUND_DEATH = CreatureSound.register(SOUNDS, "strata_hound.death", 16, 0.92F, 1.05F);
    public static final CreatureSound STRATA_HOUND_SNIFF = CreatureSound.register(SOUNDS, "strata_hound.sniff", 16, 0.95F, 1.05F);
    public static final CreatureSound STRATA_HOUND_POINT = CreatureSound.register(SOUNDS, "strata_hound.point", 16, 0.95F, 1.05F);
    // dust_devil (entity)
    public static final CreatureSound DUST_DEVIL_AMBIENT = CreatureSound.register(SOUNDS, "dust_devil.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound DUST_DEVIL_HURT = CreatureSound.register(SOUNDS, "dust_devil.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound DUST_DEVIL_DEATH = CreatureSound.register(SOUNDS, "dust_devil.death", 16, 0.92F, 1.05F);
    public static final CreatureSound DUST_DEVIL_WHIRL = CreatureSound.register(SOUNDS, "dust_devil.whirl", 24, 0.95F, 1.05F);
    public static final CreatureSound DUST_DEVIL_THROW = CreatureSound.register(SOUNDS, "dust_devil.throw", 24, 0.95F, 1.05F);
    // slag_beetle (entity)
    public static final CreatureSound SLAG_BEETLE_AMBIENT = CreatureSound.register(SOUNDS, "slag_beetle.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound SLAG_BEETLE_HURT = CreatureSound.register(SOUNDS, "slag_beetle.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound SLAG_BEETLE_DEATH = CreatureSound.register(SOUNDS, "slag_beetle.death", 16, 0.92F, 1.05F);
    public static final CreatureSound SLAG_BEETLE_EAT = CreatureSound.register(SOUNDS, "slag_beetle.eat", 16, 0.95F, 1.05F);
    public static final CreatureSound SLAG_BEETLE_DIGEST = CreatureSound.register(SOUNDS, "slag_beetle.digest", 16, 0.95F, 1.05F);
    public static final CreatureSound SLAG_BEETLE_COUGH = CreatureSound.register(SOUNDS, "slag_beetle.cough", 16, 0.95F, 1.05F);
    // magma_wader (entity)
    public static final CreatureSound MAGMA_WADER_AMBIENT = CreatureSound.register(SOUNDS, "magma_wader.ambient", 24, 0.90F, 1.10F);
    public static final CreatureSound MAGMA_WADER_HURT = CreatureSound.register(SOUNDS, "magma_wader.hurt", 24, 0.90F, 1.15F);
    public static final CreatureSound MAGMA_WADER_DEATH = CreatureSound.register(SOUNDS, "magma_wader.death", 24, 0.92F, 1.05F);
    public static final CreatureSound MAGMA_WADER_SLAG = CreatureSound.register(SOUNDS, "magma_wader.slag", 24, 0.95F, 1.05F);
    public static final CreatureSound MAGMA_WADER_SLAG_LAND = CreatureSound.register(SOUNDS, "magma_wader.slag_land", 16, 0.95F, 1.05F);
    // cinder_tyrant (entity)
    public static final CreatureSound CINDER_TYRANT_AMBIENT = CreatureSound.register(SOUNDS, "cinder_tyrant.ambient", 32, 0.90F, 1.10F);
    public static final CreatureSound CINDER_TYRANT_HURT = CreatureSound.register(SOUNDS, "cinder_tyrant.hurt", 32, 0.90F, 1.15F);
    public static final CreatureSound CINDER_TYRANT_DEATH = CreatureSound.register(SOUNDS, "cinder_tyrant.death", 32, 0.92F, 1.05F);
    public static final CreatureSound CINDER_TYRANT_SLAM = CreatureSound.register(SOUNDS, "cinder_tyrant.slam", 32, 0.95F, 1.05F);
    public static final CreatureSound CINDER_TYRANT_PHASE = CreatureSound.register(SOUNDS, "cinder_tyrant.phase", 48, 0.95F, 1.05F);
    public static final CreatureSound CINDER_TYRANT_SUMMON = CreatureSound.register(SOUNDS, "cinder_tyrant.summon", 48, 0.95F, 1.05F);
    // rime_yak (entity)
    public static final CreatureSound RIME_YAK_AMBIENT = CreatureSound.register(SOUNDS, "rime_yak.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound RIME_YAK_HURT = CreatureSound.register(SOUNDS, "rime_yak.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound RIME_YAK_DEATH = CreatureSound.register(SOUNDS, "rime_yak.death", 16, 0.92F, 1.05F);
    public static final CreatureSound RIME_YAK_SHIVER = CreatureSound.register(SOUNDS, "rime_yak.shiver", 16, 0.95F, 1.05F);
    // frost_lurker (entity)
    public static final CreatureSound FROST_LURKER_AMBIENT = CreatureSound.register(SOUNDS, "frost_lurker.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound FROST_LURKER_HURT = CreatureSound.register(SOUNDS, "frost_lurker.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound FROST_LURKER_DEATH = CreatureSound.register(SOUNDS, "frost_lurker.death", 16, 0.92F, 1.05F);
    public static final CreatureSound FROST_LURKER_ERUPT = CreatureSound.register(SOUNDS, "frost_lurker.erupt", 24, 0.95F, 1.05F);
    // glacite_wisp (entity)
    public static final CreatureSound GLACITE_WISP_AMBIENT = CreatureSound.register(SOUNDS, "glacite_wisp.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound GLACITE_WISP_HURT = CreatureSound.register(SOUNDS, "glacite_wisp.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound GLACITE_WISP_DEATH = CreatureSound.register(SOUNDS, "glacite_wisp.death", 16, 0.92F, 1.05F);
    public static final CreatureSound GLACITE_WISP_CUSHION = CreatureSound.register(SOUNDS, "glacite_wisp.cushion", 16, 0.95F, 1.05F);
    // rime_matriarch (entity)
    public static final CreatureSound RIME_MATRIARCH_AMBIENT = CreatureSound.register(SOUNDS, "rime_matriarch.ambient", 32, 0.90F, 1.10F);
    public static final CreatureSound RIME_MATRIARCH_HURT = CreatureSound.register(SOUNDS, "rime_matriarch.hurt", 32, 0.90F, 1.15F);
    public static final CreatureSound RIME_MATRIARCH_DEATH = CreatureSound.register(SOUNDS, "rime_matriarch.death", 32, 0.92F, 1.05F);
    public static final CreatureSound RIME_MATRIARCH_BEAM = CreatureSound.register(SOUNDS, "rime_matriarch.beam", 32, 0.95F, 1.05F);
    public static final CreatureSound RIME_MATRIARCH_FREEZE = CreatureSound.register(SOUNDS, "rime_matriarch.freeze", 32, 0.95F, 1.05F);
    public static final CreatureSound RIME_MATRIARCH_PHASE = CreatureSound.register(SOUNDS, "rime_matriarch.phase", 48, 0.95F, 1.05F);
    public static final CreatureSound RIME_MATRIARCH_SUMMON = CreatureSound.register(SOUNDS, "rime_matriarch.summon", 48, 0.95F, 1.05F);
    // spore_drifter (entity)
    public static final CreatureSound SPORE_DRIFTER_AMBIENT = CreatureSound.register(SOUNDS, "spore_drifter.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound SPORE_DRIFTER_HURT = CreatureSound.register(SOUNDS, "spore_drifter.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound SPORE_DRIFTER_DEATH = CreatureSound.register(SOUNDS, "spore_drifter.death", 16, 0.92F, 1.05F);
    public static final CreatureSound SPORE_DRIFTER_PUFF = CreatureSound.register(SOUNDS, "spore_drifter.puff", 16, 0.95F, 1.05F);
    // bloom_host (entity)
    public static final CreatureSound BLOOM_HOST_AMBIENT = CreatureSound.register(SOUNDS, "bloom_host.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound BLOOM_HOST_HURT = CreatureSound.register(SOUNDS, "bloom_host.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound BLOOM_HOST_DEATH = CreatureSound.register(SOUNDS, "bloom_host.death", 16, 0.92F, 1.05F);
    public static final CreatureSound BLOOM_HOST_RAISE = CreatureSound.register(SOUNDS, "bloom_host.raise", 24, 0.95F, 1.05F);
    // mossback (entity)
    public static final CreatureSound MOSSBACK_AMBIENT = CreatureSound.register(SOUNDS, "mossback.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound MOSSBACK_HURT = CreatureSound.register(SOUNDS, "mossback.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound MOSSBACK_DEATH = CreatureSound.register(SOUNDS, "mossback.death", 16, 0.92F, 1.05F);
    public static final CreatureSound MOSSBACK_GRAZE = CreatureSound.register(SOUNDS, "mossback.graze", 16, 0.95F, 1.05F);
    // xertz_forager (entity)
    public static final CreatureSound XERTZ_FORAGER_AMBIENT = CreatureSound.register(SOUNDS, "xertz_forager.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound XERTZ_FORAGER_HURT = CreatureSound.register(SOUNDS, "xertz_forager.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound XERTZ_FORAGER_DEATH = CreatureSound.register(SOUNDS, "xertz_forager.death", 16, 0.92F, 1.05F);
    public static final CreatureSound XERTZ_FORAGER_DIG = CreatureSound.register(SOUNDS, "xertz_forager.dig", 16, 0.95F, 1.05F);
    // drift_leviathan (entity)
    public static final CreatureSound DRIFT_LEVIATHAN_AMBIENT = CreatureSound.register(SOUNDS, "drift_leviathan.ambient", 32, 0.90F, 1.10F);
    public static final CreatureSound DRIFT_LEVIATHAN_HURT = CreatureSound.register(SOUNDS, "drift_leviathan.hurt", 32, 0.90F, 1.15F);
    public static final CreatureSound DRIFT_LEVIATHAN_DEATH = CreatureSound.register(SOUNDS, "drift_leviathan.death", 32, 0.92F, 1.05F);
    public static final CreatureSound DRIFT_LEVIATHAN_SONG = CreatureSound.register(SOUNDS, "drift_leviathan.song", 64, 0.95F, 1.05F);
    // bloom_sovereign (entity)
    public static final CreatureSound BLOOM_SOVEREIGN_AMBIENT = CreatureSound.register(SOUNDS, "bloom_sovereign.ambient", 32, 0.90F, 1.10F);
    public static final CreatureSound BLOOM_SOVEREIGN_HURT = CreatureSound.register(SOUNDS, "bloom_sovereign.hurt", 32, 0.90F, 1.15F);
    public static final CreatureSound BLOOM_SOVEREIGN_DEATH = CreatureSound.register(SOUNDS, "bloom_sovereign.death", 32, 0.92F, 1.05F);
    public static final CreatureSound BLOOM_SOVEREIGN_INFECT = CreatureSound.register(SOUNDS, "bloom_sovereign.infect", 32, 0.95F, 1.05F);
    public static final CreatureSound BLOOM_SOVEREIGN_HEAL = CreatureSound.register(SOUNDS, "bloom_sovereign.heal", 32, 0.95F, 1.05F);
    public static final CreatureSound BLOOM_SOVEREIGN_PHASE = CreatureSound.register(SOUNDS, "bloom_sovereign.phase", 48, 0.95F, 1.05F);
    public static final CreatureSound BLOOM_SOVEREIGN_SUMMON = CreatureSound.register(SOUNDS, "bloom_sovereign.summon", 48, 0.95F, 1.05F);
    // prism_stag (entity)
    public static final CreatureSound PRISM_STAG_AMBIENT = CreatureSound.register(SOUNDS, "prism_stag.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound PRISM_STAG_HURT = CreatureSound.register(SOUNDS, "prism_stag.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound PRISM_STAG_DEATH = CreatureSound.register(SOUNDS, "prism_stag.death", 16, 0.92F, 1.05F);
    public static final CreatureSound PRISM_STAG_SHED = CreatureSound.register(SOUNDS, "prism_stag.shed", 16, 0.95F, 1.05F);
    public static final CreatureSound PRISM_STAG_AURA = CreatureSound.register(SOUNDS, "prism_stag.aura", 24, 0.95F, 1.05F);
    // shardback (entity)
    public static final CreatureSound SHARDBACK_AMBIENT = CreatureSound.register(SOUNDS, "shardback.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound SHARDBACK_HURT = CreatureSound.register(SOUNDS, "shardback.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound SHARDBACK_DEATH = CreatureSound.register(SOUNDS, "shardback.death", 16, 0.92F, 1.05F);
    public static final CreatureSound SHARDBACK_DEFLECT = CreatureSound.register(SOUNDS, "shardback.deflect", 24, 0.95F, 1.05F);
    public static final CreatureSound SHARDBACK_CHARGE = CreatureSound.register(SOUNDS, "shardback.charge", 24, 0.95F, 1.05F);
    // crystal_golem (entity)
    public static final CreatureSound CRYSTAL_GOLEM_AMBIENT = CreatureSound.register(SOUNDS, "crystal_golem.ambient", 24, 0.90F, 1.10F);
    public static final CreatureSound CRYSTAL_GOLEM_HURT = CreatureSound.register(SOUNDS, "crystal_golem.hurt", 24, 0.90F, 1.15F);
    public static final CreatureSound CRYSTAL_GOLEM_DEATH = CreatureSound.register(SOUNDS, "crystal_golem.death", 24, 0.92F, 1.05F);
    public static final CreatureSound CRYSTAL_GOLEM_SMASH = CreatureSound.register(SOUNDS, "crystal_golem.smash", 24, 0.95F, 1.05F);
    // facet_colossus (entity)
    public static final CreatureSound FACET_COLOSSUS_AMBIENT = CreatureSound.register(SOUNDS, "facet_colossus.ambient", 32, 0.90F, 1.10F);
    public static final CreatureSound FACET_COLOSSUS_HURT = CreatureSound.register(SOUNDS, "facet_colossus.hurt", 32, 0.90F, 1.15F);
    public static final CreatureSound FACET_COLOSSUS_DEATH = CreatureSound.register(SOUNDS, "facet_colossus.death", 32, 0.92F, 1.05F);
    public static final CreatureSound FACET_COLOSSUS_BEAM = CreatureSound.register(SOUNDS, "facet_colossus.beam", 32, 0.95F, 1.05F);
    public static final CreatureSound FACET_COLOSSUS_ROTATE = CreatureSound.register(SOUNDS, "facet_colossus.rotate", 32, 0.95F, 1.05F);
    public static final CreatureSound FACET_COLOSSUS_PHASE = CreatureSound.register(SOUNDS, "facet_colossus.phase", 48, 0.95F, 1.05F);
    public static final CreatureSound FACET_COLOSSUS_SUMMON = CreatureSound.register(SOUNDS, "facet_colossus.summon", 48, 0.95F, 1.05F);
    // magnet_crab (entity)
    public static final CreatureSound MAGNET_CRAB_AMBIENT = CreatureSound.register(SOUNDS, "magnet_crab.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound MAGNET_CRAB_HURT = CreatureSound.register(SOUNDS, "magnet_crab.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound MAGNET_CRAB_DEATH = CreatureSound.register(SOUNDS, "magnet_crab.death", 16, 0.92F, 1.05F);
    public static final CreatureSound MAGNET_CRAB_SNAP = CreatureSound.register(SOUNDS, "magnet_crab.snap", 16, 0.95F, 1.05F);
    public static final CreatureSound MAGNET_CRAB_MAGNET = CreatureSound.register(SOUNDS, "magnet_crab.magnet", 16, 0.95F, 1.05F);
    // gravity_leech (entity)
    public static final CreatureSound GRAVITY_LEECH_AMBIENT = CreatureSound.register(SOUNDS, "gravity_leech.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound GRAVITY_LEECH_HURT = CreatureSound.register(SOUNDS, "gravity_leech.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound GRAVITY_LEECH_DEATH = CreatureSound.register(SOUNDS, "gravity_leech.death", 16, 0.92F, 1.05F);
    public static final CreatureSound GRAVITY_LEECH_LATCH = CreatureSound.register(SOUNDS, "gravity_leech.latch", 16, 0.95F, 1.05F);
    public static final CreatureSound GRAVITY_LEECH_DETACH = CreatureSound.register(SOUNDS, "gravity_leech.detach", 16, 0.95F, 1.05F);
    // asteroid_worm (entity)
    public static final CreatureSound ASTEROID_WORM_AMBIENT = CreatureSound.register(SOUNDS, "asteroid_worm.ambient", 24, 0.90F, 1.10F);
    public static final CreatureSound ASTEROID_WORM_HURT = CreatureSound.register(SOUNDS, "asteroid_worm.hurt", 24, 0.90F, 1.15F);
    public static final CreatureSound ASTEROID_WORM_DEATH = CreatureSound.register(SOUNDS, "asteroid_worm.death", 24, 0.92F, 1.05F);
    public static final CreatureSound ASTEROID_WORM_EMERGE = CreatureSound.register(SOUNDS, "asteroid_worm.emerge", 32, 0.95F, 1.05F);
    // cache_mimic (entity)
    public static final CreatureSound CACHE_MIMIC_AMBIENT = CreatureSound.register(SOUNDS, "cache_mimic.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound CACHE_MIMIC_HURT = CreatureSound.register(SOUNDS, "cache_mimic.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound CACHE_MIMIC_DEATH = CreatureSound.register(SOUNDS, "cache_mimic.death", 16, 0.92F, 1.05F);
    public static final CreatureSound CACHE_MIMIC_REVEAL = CreatureSound.register(SOUNDS, "cache_mimic.reveal", 16, 0.95F, 1.05F);
    // static_swarm (entity)
    public static final CreatureSound STATIC_SWARM_AMBIENT = CreatureSound.register(SOUNDS, "static_swarm.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound STATIC_SWARM_HURT = CreatureSound.register(SOUNDS, "static_swarm.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound STATIC_SWARM_DEATH = CreatureSound.register(SOUNDS, "static_swarm.death", 16, 0.92F, 1.05F);
    public static final CreatureSound STATIC_SWARM_DRAIN = CreatureSound.register(SOUNDS, "static_swarm.drain", 16, 0.95F, 1.05F);
    // scrap_hulk (entity)
    public static final CreatureSound SCRAP_HULK_AMBIENT = CreatureSound.register(SOUNDS, "scrap_hulk.ambient", 24, 0.90F, 1.10F);
    public static final CreatureSound SCRAP_HULK_HURT = CreatureSound.register(SOUNDS, "scrap_hulk.hurt", 24, 0.90F, 1.15F);
    public static final CreatureSound SCRAP_HULK_DEATH = CreatureSound.register(SOUNDS, "scrap_hulk.death", 24, 0.92F, 1.05F);
    public static final CreatureSound SCRAP_HULK_ASSEMBLE = CreatureSound.register(SOUNDS, "scrap_hulk.assemble", 32, 0.95F, 1.05F);
    public static final CreatureSound SCRAP_HULK_SWEEP = CreatureSound.register(SOUNDS, "scrap_hulk.sweep", 24, 0.95F, 1.05F);
    public static final CreatureSound SCRAP_HULK_SHED = CreatureSound.register(SOUNDS, "scrap_hulk.shed", 24, 0.95F, 1.05F);
    // rogue_drone (entity)
    public static final CreatureSound ROGUE_DRONE_AMBIENT = CreatureSound.register(SOUNDS, "rogue_drone.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound ROGUE_DRONE_HURT = CreatureSound.register(SOUNDS, "rogue_drone.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound ROGUE_DRONE_DEATH = CreatureSound.register(SOUNDS, "rogue_drone.death", 16, 0.92F, 1.05F);
    public static final CreatureSound ROGUE_DRONE_LEAP = CreatureSound.register(SOUNDS, "rogue_drone.leap", 16, 0.95F, 1.05F);
    // rogue_android (entity)
    public static final CreatureSound ROGUE_ANDROID_AMBIENT = CreatureSound.register(SOUNDS, "rogue_android.ambient", 24, 0.90F, 1.10F);
    public static final CreatureSound ROGUE_ANDROID_HURT = CreatureSound.register(SOUNDS, "rogue_android.hurt", 24, 0.90F, 1.15F);
    public static final CreatureSound ROGUE_ANDROID_DEATH = CreatureSound.register(SOUNDS, "rogue_android.death", 24, 0.92F, 1.05F);
    public static final CreatureSound ROGUE_ANDROID_SHIELD = CreatureSound.register(SOUNDS, "rogue_android.shield", 24, 0.95F, 1.05F);
    // terraforming_drone (entity)
    public static final CreatureSound TERRAFORMING_DRONE_AMBIENT = CreatureSound.register(SOUNDS, "terraforming_drone.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound TERRAFORMING_DRONE_HURT = CreatureSound.register(SOUNDS, "terraforming_drone.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound TERRAFORMING_DRONE_DEATH = CreatureSound.register(SOUNDS, "terraforming_drone.death", 16, 0.92F, 1.05F);
    public static final CreatureSound TERRAFORMING_DRONE_WORK = CreatureSound.register(SOUNDS, "terraforming_drone.work", 16, 0.95F, 1.05F);
    // space_pirate (entity)
    public static final CreatureSound SPACE_PIRATE_AMBIENT = CreatureSound.register(SOUNDS, "space_pirate.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound SPACE_PIRATE_HURT = CreatureSound.register(SOUNDS, "space_pirate.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound SPACE_PIRATE_DEATH = CreatureSound.register(SOUNDS, "space_pirate.death", 16, 0.92F, 1.05F);
    public static final CreatureSound SPACE_PIRATE_VOLLEY = CreatureSound.register(SOUNDS, "space_pirate.volley", 24, 0.95F, 1.05F);
    // pirate_captain (entity)
    public static final CreatureSound PIRATE_CAPTAIN_AMBIENT = CreatureSound.register(SOUNDS, "pirate_captain.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound PIRATE_CAPTAIN_HURT = CreatureSound.register(SOUNDS, "pirate_captain.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound PIRATE_CAPTAIN_DEATH = CreatureSound.register(SOUNDS, "pirate_captain.death", 16, 0.92F, 1.05F);
    public static final CreatureSound PIRATE_CAPTAIN_DASH = CreatureSound.register(SOUNDS, "pirate_captain.dash", 24, 0.95F, 1.05F);
    public static final CreatureSound PIRATE_CAPTAIN_RALLY = CreatureSound.register(SOUNDS, "pirate_captain.rally", 32, 0.95F, 1.05F);
    // void_crawler (entity)
    public static final CreatureSound VOID_CRAWLER_AMBIENT = CreatureSound.register(SOUNDS, "void_crawler.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound VOID_CRAWLER_HURT = CreatureSound.register(SOUNDS, "void_crawler.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound VOID_CRAWLER_DEATH = CreatureSound.register(SOUNDS, "void_crawler.death", 16, 0.92F, 1.05F);
    public static final CreatureSound VOID_CRAWLER_BLINK = CreatureSound.register(SOUNDS, "void_crawler.blink", 16, 0.95F, 1.05F);
    // plasma_slime (entity)
    public static final CreatureSound PLASMA_SLIME_AMBIENT = CreatureSound.register(SOUNDS, "plasma_slime.ambient", 16, 0.90F, 1.10F);
    public static final CreatureSound PLASMA_SLIME_HURT = CreatureSound.register(SOUNDS, "plasma_slime.hurt", 16, 0.90F, 1.15F);
    public static final CreatureSound PLASMA_SLIME_DEATH = CreatureSound.register(SOUNDS, "plasma_slime.death", 16, 0.92F, 1.05F);
    public static final CreatureSound PLASMA_SLIME_PULSE = CreatureSound.register(SOUNDS, "plasma_slime.pulse", 16, 0.95F, 1.05F);
    // void_manta (entity)
    public static final CreatureSound VOID_MANTA_AMBIENT = CreatureSound.register(SOUNDS, "void_manta.ambient", 24, 0.90F, 1.10F);
    public static final CreatureSound VOID_MANTA_HURT = CreatureSound.register(SOUNDS, "void_manta.hurt", 24, 0.90F, 1.15F);
    public static final CreatureSound VOID_MANTA_DEATH = CreatureSound.register(SOUNDS, "void_manta.death", 24, 0.92F, 1.05F);
    public static final CreatureSound VOID_MANTA_BLINK = CreatureSound.register(SOUNDS, "void_manta.blink", 32, 0.95F, 1.05F);
    // incubator (block)
    public static final CreatureSound INCUBATOR_HATCH = CreatureSound.register(SOUNDS, "incubator.hatch", 16, 0.95F, 1.05F);
    // lure_beacon (block)
    public static final CreatureSound LURE_BEACON_PULSE = CreatureSound.register(SOUNDS, "lure_beacon.pulse", 16, 0.97F, 1.03F);
    // sonic_ward (block)
    public static final CreatureSound SONIC_WARD_HUM = CreatureSound.register(SOUNDS, "sonic_ward.hum", 12, 1.00F, 1.00F);
    // apex_altar (block)
    public static final CreatureSound APEX_ALTAR_SUMMON = CreatureSound.register(SOUNDS, "apex_altar.summon", 48, 1.00F, 1.00F);
    // capture_pod (item)
    public static final CreatureSound CAPTURE_POD_CAPTURE = CreatureSound.register(SOUNDS, "capture_pod.capture", 16, 0.95F, 1.05F);
    public static final CreatureSound CAPTURE_POD_RELEASE = CreatureSound.register(SOUNDS, "capture_pod.release", 16, 0.95F, 1.05F);
    // mirror_shield (item)
    public static final CreatureSound MIRROR_SHIELD_REFLECT = CreatureSound.register(SOUNDS, "mirror_shield.reflect", 16, 0.95F, 1.10F);
    // blink_charm (item)
    public static final CreatureSound BLINK_CHARM_USE = CreatureSound.register(SOUNDS, "blink_charm.use", 16, 0.95F, 1.05F);
    // resonant_whistle (item)
    public static final CreatureSound RESONANT_WHISTLE_USE = CreatureSound.register(SOUNDS, "resonant_whistle.use", 32, 0.97F, 1.03F);
    // static_jar (item)
    public static final CreatureSound STATIC_JAR_SHATTER = CreatureSound.register(SOUNDS, "static_jar.shatter", 16, 0.95F, 1.05F);
    // field_scanner (item)
    public static final CreatureSound FIELD_SCANNER_SCAN = CreatureSound.register(SOUNDS, "field_scanner.scan", 8, 1.00F, 1.00F);

    private ModSounds() {
    }

    /** Forces this class to initialise (and therefore its events to register on Fabric). */
    public static void init() {
    }
}
