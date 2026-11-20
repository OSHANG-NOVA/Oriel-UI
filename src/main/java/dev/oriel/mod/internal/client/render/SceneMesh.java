package dev.oriel.mod.internal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.oriel.mod.internal.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One scene group's blocks, meshed once and drawn as often as needed: the block models' quads as the world meshes
 * them (faces between neighbours culled, models picked by their states, fluids meeting their neighbours), kept as
 * plain vertex arrays, plus their block entities (chests, signs, banners, beds). Drawing it only copies the vertices
 * out, so a scene that moves or fades costs no model work.
 *
 * <p>Blocks are lit like the inventory's: full bright, no face shading or ambient occlusion baked in, and drawn with
 * the item render types so the GUI's directional light shades them.
 */
public final class SceneMesh {
    /** Floats per vertex: position, uv and normal. */
    private static final int FLOATS = 8;

    private final Vertices opaque = new Vertices(), translucent = new Vertices();
    private final List<PlacedState> blockEntities = new ArrayList<>();

    private record PlacedState(BlockPos pos, BlockEntity entity) {}

    private SceneMesh() {}

    /**
     * Meshes {@code blocks} (their states by position) as one group, with their block entities
     * ({@link #blockEntity}) by position.
     */
    public static SceneMesh build(Map<BlockPos, BlockState> blocks, Map<BlockPos, BlockEntity> blockEntities) {
        Minecraft mc = Minecraft.getInstance();
        SceneMesh mesh = new SceneMesh();
        SceneLevel level = new SceneLevel(blocks, blockEntities);
        ModelBlockRenderer blockRenderer = mc.getBlockRenderer().getModelRenderer();
        PoseStack pose = new PoseStack();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState state = e.getValue();
            try {
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) {
                    // The fluid renderer writes positions within the chunk section, and leaves the normals pointing
                    // up: move them back, and let the face they make give the normal.
                    Vertices into = net.minecraft.client.renderer.ItemBlockRenderTypes.getRenderLayer(fluid) == RenderType.translucent()
                            ? mesh.translucent : mesh.opaque;
                    into.offset(pos.getX() - (pos.getX() & 15), pos.getY() - (pos.getY() & 15), pos.getZ() - (pos.getZ() & 15));
                    into.fluid(true);
                    mc.getBlockRenderer().renderLiquid(pos, level, into, state, fluid);
                    into.fluid(false);
                    into.offset(0, 0, 0);
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    // The model renderer writes positions within the block: move them back.
                    Vertices into = mesh.opaque;
                    into.offset(pos.getX(), pos.getY(), pos.getZ());
                    blockRenderer.tesselateBlock(level, mc.getBlockRenderer().getBlockModel(state), state,
                            pos, pose, into, false, RandomSource.create(state.getSeed(pos)), state.getSeed(pos),
                            OverlayTexture.NO_OVERLAY);
                    into.offset(0, 0, 0);
                }
                BlockEntity blockEntity = blockEntities.get(pos);
                if (blockEntity != null && renders(blockEntity)) {
                    mesh.blockEntities.add(new PlacedState(pos, blockEntity));
                }
            } catch (RuntimeException ex) {
                Constants.LOG.warn("Oriel: cannot draw {} in a scene: {}", state, ex.toString());
            }
        }
        return mesh;
    }

    /** Whether the block entity has a renderer that draws it at rest. */
    private static boolean renders(BlockEntity blockEntity) {
        BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        return blockEntity.getType().isValid(blockEntity.getBlockState()) && dispatcher.getRenderer(blockEntity) != null;
    }

    /**
     * A block entity for {@code state} at {@code pos}: loaded from {@code nbt} when given, else empty; null when the
     * block has none. It has no level, as block entities in item renders have none, so renderers draw it at rest.
     */
    public static @Nullable BlockEntity blockEntity(BlockPos pos, BlockState state, net.minecraft.nbt.@Nullable CompoundTag nbt) {
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock block)) return null;
        var level = Minecraft.getInstance().level;
        if (nbt != null && level != null) {
            net.minecraft.nbt.CompoundTag tag = nbt.copy();
            BlockEntity probe = block.newBlockEntity(pos, state);
            if (probe == null) return null;
            if (!tag.contains("id")) {
                tag.putString("id", net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(probe.getType()).toString());
            }
            BlockEntity loaded = BlockEntity.loadStatic(pos, state, tag);
            if (loaded != null) return loaded;
        }
        return block.newBlockEntity(pos, state);
    }

    /** How many block and fluid quads it has. */
    public int quads() {
        return (opaque.count + translucent.count) / 4;
    }

    /** Whether it draws anything. */
    public boolean isEmpty() {
        return opaque.count == 0 && translucent.count == 0 && blockEntities.isEmpty();
    }

    /**
     * Submits it in {@code pose}'s space (block coordinates), its vertices' alpha times {@code alpha}. Faded, every
     * quad draws translucent; block entities draw only while it is at least half opaque, as items do.
     */
    public void submit(PoseStack pose, MultiBufferSource buffers, float alpha) {
        boolean faded = alpha < 1;
        if (opaque.count > 0) {
            RenderType type = faded ? Sheets.translucentItemSheet() : Sheets.cutoutBlockSheet();
            opaque.emit(pose.last(), buffers.getBuffer(type), alpha);
        }
        if (translucent.count > 0) {
            translucent.emit(pose.last(), buffers.getBuffer(Sheets.translucentItemSheet()), alpha);
        }
        if (alpha < 0.5F || blockEntities.isEmpty()) return;
        BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        for (PlacedState be : blockEntities) {
            pose.pushPose();
            pose.translate(be.pos().getX(), be.pos().getY(), be.pos().getZ());
            dispatcher.render(be.entity(), 0F, pose, buffers);
            pose.popPose();
        }
    }

    /**
     * Vertices as the block and fluid renderers write them, kept in arrays. Light and overlay are dropped (it is drawn
     * full bright); fluids get their faces' real normals, which the fluid renderer leaves pointing up.
     */
    private static final class Vertices implements VertexConsumer {
        float[] data = new float[FLOATS * 256];
        int[] colors = new int[256];
        int count;
        /** Added to every position, for renderers that write local ones. */
        private int ox, oy, oz;
        /** The vertex being written. */
        private float px, py, pz, pu, pv, pnx, pny, pnz;
        private int pcolor = -1;
        private int defaultColor = -1;
        /** Fluid vertices are corrected by their face's normal once every four of them is in. */
        private boolean fluid;

        void offset(int x, int y, int z) {
            ox = x;
            oy = y;
            oz = z;
        }

        /** Fluid vertices come within the chunk section of {@code pos}: their normal is their face's. */
        void fluid(boolean on) {
            fluid = on;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            px = (float) x;
            py = (float) y;
            pz = (float) z;
            pu = pv = pnx = pny = pnz = 0;
            pcolor = -1;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            pcolor = a << 24 | r << 16 | g << 8 | b;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            pu = u;
            pv = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            pnx = x;
            pny = y;
            pnz = z;
            return this;
        }

        @Override
        public void endVertex() {
            if (count == colors.length) {
                colors = Arrays.copyOf(colors, count * 2);
                data = Arrays.copyOf(data, count * 2 * FLOATS);
            }
            int i = count * FLOATS;
            data[i] = px + ox;
            data[i + 1] = py + oy;
            data[i + 2] = pz + oz;
            data[i + 3] = pu;
            data[i + 4] = pv;
            data[i + 5] = pnx;
            data[i + 6] = pny;
            data[i + 7] = pnz;
            colors[count++] = pcolor == -1 ? defaultColor : pcolor;
            if (fluid && count % 4 == 0) faceNormal(count - 4);
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            defaultColor = a << 24 | r << 16 | g << 8 | b;
        }

        @Override
        public void unsetDefaultColor() {
            defaultColor = -1;
        }

        /** Sets the four vertices from {@code first} to the normal of the quad they make. */
        private void faceNormal(int first) {
            int a = first * FLOATS, b = a + FLOATS, c = b + FLOATS;
            Vector3f e1 = new Vector3f(data[b] - data[a], data[b + 1] - data[a + 1], data[b + 2] - data[a + 2]);
            Vector3f e2 = new Vector3f(data[c] - data[a], data[c + 1] - data[a + 1], data[c + 2] - data[a + 2]);
            Vector3f n = e1.cross(e2);
            if (n.lengthSquared() < 1e-12F) return;
            n.normalize();
            for (int v = 0; v < 4; v++) {
                int i = (first + v) * FLOATS;
                data[i + 5] = n.x;
                data[i + 6] = n.y;
                data[i + 7] = n.z;
            }
        }

        /** Copies the vertices into {@code buffer}, through {@code pose}, the alpha times {@code alpha}. */
        void emit(PoseStack.Pose pose, VertexConsumer buffer, float alpha) {
            Matrix4f m = pose.pose();
            Vector3f p = new Vector3f(), n = new Vector3f();
            int full = LightTexture.FULL_BRIGHT, overlay = OverlayTexture.NO_OVERLAY;
            for (int v = 0; v < count; v++) {
                int i = v * FLOATS;
                m.transformPosition(data[i], data[i + 1], data[i + 2], p);
                pose.normal().transform(new Vector3f(data[i + 5], data[i + 6], data[i + 7]), n);
                int color = colors[v];
                if (alpha < 1) color = (Math.round((color >>> 24) * alpha) << 24) | (color & 0xFFFFFF);
                buffer.vertex(p.x, p.y, p.z)
                        .color(color >>> 16 & 0xFF, color >>> 8 & 0xFF, color & 0xFF, color >>> 24)
                        .uv(data[i + 3], data[i + 4])
                        .overlayCoords(overlay)
                        .uv2(full)
                        .normal(n.x, n.y, n.z)
                        .endVertex();
            }
        }
    }
}
