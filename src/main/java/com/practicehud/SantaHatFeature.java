package com.practicehud;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Client-only Santa hat cosmetic. It is drawn on player models only while
 * the Christmas theme is selected; it does not modify player equipment.
 */
public final class SantaHatFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final int CHRISTMAS_THEME_ID = 6;
    private static final Identifier WHITE_TEXTURE =
            Identifier.ofVanilla("textures/block/white_concrete.png");

    private final ModelPart brim;
    private final ModelPart lower;
    private final ModelPart middle;
    private final ModelPart tip;
    private final ModelPart pompom;

    public SantaHatFeature(PlayerEntityRenderer renderer) {
        this((FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) renderer);
    }

    private SantaHatFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);

        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("brim", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-4.5f, -9.0f, -4.5f, 9.0f, 1.2f, 9.0f), ModelTransform.NONE);
        root.addChild("lower", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-3.5f, -11.0f, -3.5f, 7.0f, 2.5f, 7.0f), ModelTransform.NONE);
        root.addChild("middle", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-2.5f, -13.0f, -2.5f, 5.0f, 2.5f, 5.0f), ModelTransform.NONE);
        root.addChild("tip", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-1.5f, -15.0f, -1.5f, 3.0f, 2.5f, 3.0f), ModelTransform.NONE);
        root.addChild("pompom", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-1.0f, -17.0f, -1.0f, 2.0f, 2.0f, 2.0f), ModelTransform.NONE);

        ModelPart model = TexturedModelData.of(data, 32, 32).createModel();
        brim = model.getChild("brim");
        lower = model.getChild("lower");
        middle = model.getChild("middle");
        tip = model.getChild("tip");
        pompom = model.getChild("pompom");
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (Config.get().themeId != CHRISTMAS_THEME_ID) return;

        double time = System.nanoTime() / 1_000_000_000.0;
        float bob = (float) Math.sin(time * 2.8) * 0.012f;
        float tilt = (float) Math.sin(time * 1.7) * 2.0f;

        matrices.push();
        getContextModel().getHead().applyTransform(matrices);
        RenderLayer hatLayer = getContextModel().getLayer(WHITE_TEXTURE);
        matrices.translate(0.0, bob, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tilt));

        queue.submitModelPart(brim, matrices, hatLayer, light, OverlayTexture.DEFAULT_UV,
                null, 0xFFFFFFFF, null);
        queue.submitModelPart(lower, matrices, hatLayer, light, OverlayTexture.DEFAULT_UV,
                null, 0xFFE32636, null);
        queue.submitModelPart(middle, matrices, hatLayer, light, OverlayTexture.DEFAULT_UV,
                null, 0xFFE32636, null);
        queue.submitModelPart(tip, matrices, hatLayer, light, OverlayTexture.DEFAULT_UV,
                null, 0xFFE32636, null);
        queue.submitModelPart(pompom, matrices, hatLayer, light, OverlayTexture.DEFAULT_UV,
                null, 0xFFFFFFFF, null);
        matrices.pop();
    }
}
