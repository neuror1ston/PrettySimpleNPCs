package ua.neuror1ston.prettysimplenpcs.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;

/**
 * Custom renderer for SimpleNpcEntity supporting player models (Alex/Steve),
 * dynamic scaling, custom skins, titles, and floating speech bubbles (Idle Barks).
 */
public class SimpleNpcEntityRenderer extends BipedEntityRenderer<SimpleNpcEntity, PlayerEntityModel<SimpleNpcEntity>> {
    private final PlayerEntityModel<SimpleNpcEntity> defaultModel;
    private final PlayerEntityModel<SimpleNpcEntity> slimModel;

    public SimpleNpcEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.defaultModel = this.model;
        this.slimModel = new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER_SLIM), true);

        // Add standard armor and held item feature renderers
        this.addFeature(new ArmorFeatureRenderer<>(this,
                new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(SimpleNpcEntity entity) {
        return ClientSkinManager.getNpcSkin(entity);
    }

    @Override
    protected void setupTransforms(SimpleNpcEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta) {
        super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta);

        // Apply Alex (slim) vs Steve (default) model dynamically
        boolean isSlim = NpcData.SkinType.SLIM.name().equalsIgnoreCase(entity.getSkinTypeValue());
        this.model = isSlim ? this.slimModel : this.defaultModel;

        // Apply scale
        float scale = entity.getNpcScale();
        matrices.scale(scale, scale, scale);
    }

    @Override
    protected void renderLabelIfPresent(SimpleNpcEntity entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        double distSq = this.dispatcher.getSquaredDistanceToCamera(entity);
        if (distSq > 4096.0) return; // 64 blocks max

        matrices.push();
        matrices.translate(0.0, entity.getHeight() + 0.5, 0.0);
        matrices.multiply(this.dispatcher.getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        TextRenderer textRenderer = this.getTextRenderer();

        // 1. Floating Idle Bark (speech bubble) if present
        String bark = entity.getCurrentBark();
        if (bark != null && !bark.isEmpty()) {
            Text barkText = Text.literal("§f«" + bark + "§f»");
            float barkX = -textRenderer.getWidth(barkText) / 2.0f;
            textRenderer.draw(barkText, barkX, -22.0f, 0xFFFFFF, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, 0x80000000, light);
        }

        // 2. Title label (e.g. "[Корчмарь]")
        String title = entity.getNpcTitle();
        if (title != null && !title.isEmpty()) {
            Text titleText = Text.literal("§7" + title);
            float titleX = -textRenderer.getWidth(titleText) / 2.0f;
            textRenderer.draw(titleText, titleX, -10.0f, 0xAAAAAA, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        }

        // 3. Name label
        float nameX = -textRenderer.getWidth(text) / 2.0f;
        textRenderer.draw(text, nameX, 0.0f, 0xFFFFFF, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0x40000000, light);

        matrices.pop();
    }
}
