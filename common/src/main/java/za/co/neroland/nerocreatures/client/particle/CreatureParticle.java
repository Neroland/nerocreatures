package za.co.neroland.nerocreatures.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * The one particle class behind all 16 NeroCreatures particle types: frames picked by age, alpha
 * faded over the last quarter of the life, size eased from the style's start to its end, optional
 * full-bright, gravity and friction from {@link ParticleStyle}. Before 1.21.9 it is a
 * {@code TextureSheetParticle}; from 1.21.9 sprite particles are {@code SingleQuadParticle}s.
 */
public final class CreatureParticle
        //? if >=1.21.9 {
        extends net.minecraft.client.particle.SingleQuadParticle {
        //?} else {
        /*extends net.minecraft.client.particle.TextureSheetParticle {
        *///?}

    private static final int FULL_BRIGHT = 0xF000F0;

    private final SpriteSet sprites;
    private final ParticleStyle style;
    private final float size;
    private final float swayPhase;

    CreatureParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites,
            ParticleStyle style) {
        //? if >=1.21.9 {
        super(level, x, y, z, sprites.get(0, 1));
        //?} else {
        /*super(level, x, y, z);
        *///?}
        this.sprites = sprites;
        this.style = style;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = style.lifeMin() + (style.lifeMax() > style.lifeMin()
                ? this.random.nextInt(style.lifeMax() - style.lifeMin() + 1) : 0);
        this.size = 1.0F + (style.sizeJitter() > 0.0F ? this.random.nextFloat() * style.sizeJitter() / Math.max(0.01F,
                style.sizeStart()) : 0.0F);
        this.quadSize = style.sizeStart() * this.size;
        this.gravity = style.gravity();
        this.friction = style.friction() <= 0.0F ? 1.0F : style.friction();
        this.hasPhysics = false;
        this.swayPhase = this.random.nextFloat() * Mth.TWO_PI;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        if (this.style.friction() <= 0.0F) {
            this.xd = 0.0D;
            this.yd = 0.0D;
            this.zd = 0.0D;
        }
        if (this.style.sway() > 0.0F) {
            this.xd += Mth.sin(this.age * 0.2F + this.swayPhase) * this.style.sway() * 0.1F;
        }
        float t = this.lifetime <= 0 ? 1.0F : this.age / (float) this.lifetime;
        this.quadSize = Mth.lerp(t, this.style.sizeStart(), this.style.sizeEnd()) * this.size;
        this.alpha = t < 0.75F ? 1.0F : Math.max(0.0F, 1.0F - (t - 0.75F) * 4.0F);
        this.setSpriteFromAge(this.sprites);
    }

    //? if >=26.1 {
    @Override
    protected int getLightCoords(float partialTick) {
        return this.style.fullBright() ? FULL_BRIGHT : super.getLightCoords(partialTick);
    }
    //?} else {
    /*@Override
    protected int getLightColor(float partialTick) {
        return this.style.fullBright() ? FULL_BRIGHT : super.getLightColor(partialTick);
    }
    *///?}

    //? if >=1.21.9 {
    @Override
    protected net.minecraft.client.particle.SingleQuadParticle.Layer getLayer() {
        return net.minecraft.client.particle.SingleQuadParticle.Layer.TRANSLUCENT;
    }
    //?} else {
    /*@Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    *///?}

    /** The one provider; each particle type gets its own instance with its sprite set and style. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;
        private final ParticleStyle style;

        public Provider(SpriteSet sprites, String id) {
            this.sprites = sprites;
            this.style = ParticleStyle.of(id);
        }

        //? if >=1.21.9 {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd, net.minecraft.util.RandomSource random) {
            return new CreatureParticle(level, x, y, z, xd, yd, zd, this.sprites, this.style);
        }
        //?} else {
        /*@Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd) {
            return new CreatureParticle(level, x, y, z, xd, yd, zd, this.sprites, this.style);
        }
        *///?}
    }
}
