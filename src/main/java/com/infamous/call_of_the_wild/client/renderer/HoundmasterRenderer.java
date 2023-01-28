package com.infamous.call_of_the_wild.client.renderer;

import com.infamous.call_of_the_wild.AllBarkAllBite;
import com.infamous.call_of_the_wild.client.ABABModelLayers;
import com.infamous.call_of_the_wild.client.renderer.model.HoundmasterModel;
import com.infamous.call_of_the_wild.common.entity.houndmaster.Houndmaster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("NullableProblems")
public class HoundmasterRenderer extends MobRenderer<Houndmaster, HoundmasterModel<Houndmaster>> {
    private static final float SHADOW_RADIUS = 0.5F;
    private static final ResourceLocation HOUNDMASTER = new ResourceLocation(AllBarkAllBite.MODID, "textures/entity/illager/houndmaster.png");
    private static final float SCALE = 0.9375F;

    public HoundmasterRenderer(EntityRendererProvider.Context context) {
        super(context, new HoundmasterModel<>(context.bakeLayer(ABABModelLayers.HOUNDMASTER)), SHADOW_RADIUS);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(Houndmaster houndmaster) {
        return HOUNDMASTER;
    }

    @Override
    protected void scale(Houndmaster houndmaster, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }
}